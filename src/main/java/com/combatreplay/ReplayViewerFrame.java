package com.combatreplay;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import net.runelite.api.HitsplatID;

final class ReplayViewerFrame extends JFrame
{
	private final ReplayMapPanel map = new ReplayMapPanel();
	private final EventTimeline timeline = new EventTimeline();
	private final JEditorPane inspector = new JEditorPane("text/html", "");
	private final JLabel tickLabel = new JLabel(" ", SwingConstants.CENTER);
	private final JButton play = new JButton("Play");
	private final Timer timer;
	private CombatRecording recording;
	private String selectedKey;
	private double speed = 1;

	ReplayViewerFrame(CombatRecording recording)
	{
		super(recording.name == null ? "Combat Replay" : recording.name);
		this.recording = recording;
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setMinimumSize(new Dimension(800, 520));
		setSize(1180, 760);
		setLocationByPlatform(true);

		map.setSelectionListener(key -> { selectedKey = key; updateInspector(); });
		map.setRecording(recording);
		selectedKey = localKey(recording.ticks.isEmpty() ? null : recording.ticks.get(0));
		inspector.setEditable(false);
		inspector.setBackground(new Color(35, 35, 35));
		inspector.setForeground(Color.WHITE);
		inspector.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		timer = new Timer(600, event -> advance());
		JScrollPane inspectorScroll = new JScrollPane(inspector);
		inspectorScroll.setPreferredSize(new Dimension(310, 500));
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, map, inspectorScroll);
		split.setResizeWeight(.76);

		add(mapToolbar(), BorderLayout.NORTH);
		add(split, BorderLayout.CENTER);
		add(playbackPanel(), BorderLayout.SOUTH);
		play.addActionListener(event -> togglePlayback());
		timeline.addChangeListener(event -> setTick(timeline.getValue()));
		timeline.addMouseListener(new MouseAdapter() { @Override public void mousePressed(MouseEvent e) { pause(); } });
		timeline.setRecording(recording);
		setTick(0);
	}

	private JPanel mapToolbar()
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
		JComboBox<String> camera = new JComboBox<>(new String[]{"Follow action", "Whole encounter", "Manual"});
		camera.addActionListener(event -> map.setCameraMode(ReplayMapPanel.CameraMode.values()[camera.getSelectedIndex()]));
		JComboBox<String> actors = new JComboBox<>(new String[]{"Encounter actors", "Nearby actors", "All actors"});
		actors.addActionListener(event -> map.setActorFilter(ReplayMapPanel.ActorFilter.values()[actors.getSelectedIndex()]));
		JCheckBox terrain = check("Terrain", true), objects = check("Objects", true), collision = check("Collision", false), grid = check("Grid", false), trails = check("Trails", true);
		Runnable layers = () -> map.setLayers(terrain.isSelected(), objects.isSelected(), collision.isSelected(), grid.isSelected(), trails.isSelected());
		terrain.addActionListener(e -> layers.run()); objects.addActionListener(e -> layers.run()); collision.addActionListener(e -> layers.run()); grid.addActionListener(e -> layers.run()); trails.addActionListener(e -> layers.run());
		panel.add(new JLabel("Camera:")); panel.add(camera); panel.add(new JLabel("Show:")); panel.add(actors);
		panel.add(terrain); panel.add(objects); panel.add(collision); panel.add(grid); panel.add(trails);
		return panel;
	}

	private JPanel playbackPanel()
	{
		JPanel outer = new JPanel(); outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS)); outer.setBorder(BorderFactory.createEmptyBorder(3, 8, 6, 8));
		outer.add(timeline);
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 1));
		JButton previous = new JButton("◀ Tick"), next = new JButton("Tick ▶");
		previous.addActionListener(e -> { pause(); timeline.setValue(Math.max(0, timeline.getValue() - 1)); });
		next.addActionListener(e -> { pause(); timeline.setValue(Math.min(timeline.getMaximum(), timeline.getValue() + 1)); });
		JComboBox<String> speeds = new JComboBox<>(new String[]{"0.25×", "0.5×", "1×", "2×", "4×"}); speeds.setSelectedIndex(2);
		speeds.addActionListener(e -> { double[] values = {.25, .5, 1, 2, 4}; speed = values[speeds.getSelectedIndex()]; timer.setDelay(Math.max(25, (int) (600 / speed))); });
		controls.add(previous); controls.add(play); controls.add(next); controls.add(speeds); controls.add(tickLabel);
		outer.add(controls); return outer;
	}

	private static JCheckBox check(String label, boolean selected) { return new JCheckBox(label, selected); }

	private void togglePlayback()
	{
		if (timer.isRunning()) { pause(); return; }
		if (timeline.getValue() >= timeline.getMaximum()) timeline.setValue(0);
		timer.setDelay(Math.max(25, (int) (600 / speed))); timer.start(); play.setText("Pause");
	}

	private void pause() { timer.stop(); play.setText("Play"); }
	private void advance() { if (timeline.getValue() >= timeline.getMaximum()) pause(); else timeline.setValue(timeline.getValue() + 1); }

	private void setTick(int index)
	{
		if (recording.ticks.isEmpty()) { tickLabel.setText("No ticks"); return; }
		int actual = Math.max(0, Math.min(index, recording.ticks.size() - 1));
		map.setTick(actual); tickLabel.setText("Tick " + (actual + 1) + " / " + recording.ticks.size()); updateInspector();
	}

	private void updateInspector()
	{
		if (recording.ticks.isEmpty()) { inspector.setText("<html>No captured ticks</html>"); return; }
		int index = Math.min(timeline.getValue(), recording.ticks.size() - 1); RecordedTick tick = recording.ticks.get(index);
		ActorSnapshot selected = actor(tick, selectedKey);
		if (selected == null) { selectedKey = localKey(tick); selected = actor(tick, selectedKey); }
		StringBuilder html = new StringBuilder("<html><body style='color:#ddd;background:#232323;font-family:sans-serif;margin:10px'>");
		html.append("<h2 style='color:#ff981f'>").append(escape(selected == null ? "No actor selected" : selected.displayName())).append("</h2>");
		if (selected != null)
		{
			html.append("<b>").append(selected.kind).append("</b> &nbsp; Tile ").append(tick.baseX + selected.sceneX).append(", ").append(tick.baseY + selected.sceneY)
				.append("<br>Animation ").append(selected.animation).append(" &nbsp; Pose ").append(selected.poseAnimation);
			if (selected.healthScale > 0) html.append("<br>Observed health: ").append(selected.healthRatio).append("/").append(selected.healthScale);
		}
		if (selected != null && "PLAYER".equals(selected.kind))
		{
			if (selected.isLocalPlayer)
			{
				html.append("<h3>Resources</h3>HP ").append(resource(tick.hitpoints, tick.maximumHitpoints))
					.append("<br>Prayer ").append(resource(tick.prayer, tick.maximumPrayer));
				html.append("<h3>Active prayers</h3>").append(tick.activePrayers == null
					? "Unavailable" : tick.activePrayers.isEmpty() ? "None" : escape(prettyList(tick.activePrayers)));
				html.append("<h3>Equipment</h3>").append(items(tick.equipment));
				html.append("<h3>Inventory</h3>").append(items(tick.inventory));
			}
			else
			{
				html.append("<h3>Visible protection prayer</h3>")
					.append(selected.overheadIcon == null ? "None observed" : escape(pretty(selected.overheadIcon)));
				html.append("<h3>Visible equipment</h3>").append(items(selected.visibleEquipment));
				html.append("<small>Appearance observations only; inventory, exact resources, and offensive prayers are unavailable.</small>");
			}
		}
		html.append("<h3>Recent events</h3>");
		int shown = 0;
		for (int i = index; i >= 0 && i >= index - 8 && shown < 14; i--)
		{
			RecordedTick eventTick = recording.ticks.get(i);
			for (String event : descriptions(eventTick, selectedKey, i == index ? previousTick(index) : null))
			{
				html.append("<div><span style='color:#999'>T").append(i + 1).append("</span> ").append(escape(event)).append("</div>"); shown++;
			}
		}
		if (shown == 0) html.append("No recent events.");
		html.append("<h3>Evidence</h3><small>Map indicators and conclusions shown here are based on client-observed events. Unknown sources are not attributed.</small></body></html>");
		inspector.setText(html.toString()); inspector.setCaretPosition(0);
	}

	private RecordedTick previousTick(int index) { return index > 0 ? recording.ticks.get(index - 1) : null; }

	static List<String> descriptions(RecordedTick tick, String selectedKey, RecordedTick previous)
	{
		List<String> result = new ArrayList<>(); Map<String, String> names = new HashMap<>(); for (ActorSnapshot actor : tick.actors) names.put(actor.key, actor.displayName());
		if (previous != null)
		{
			ActorSnapshot selected = actor(tick, selectedKey);
			ActorSnapshot oldSelected = actor(previous, selectedKey);
			if (selected != null && oldSelected != null && "PLAYER".equals(selected.kind))
			{
				if (selected.isLocalPlayer)
				{
					List<String> equipment = itemChanges(previous.equipment, tick.equipment);
					if (!equipment.isEmpty()) result.add(equipment.size() + "-slot gear switch: " + String.join(", ", equipment));
					List<String> inventory = inventoryChanges(previous.inventory, tick.inventory);
					if (!inventory.isEmpty()) result.add("Inventory: " + String.join(", ", inventory));
				}
				else
				{
					List<String> equipment = itemChanges(oldSelected.visibleEquipment, selected.visibleEquipment);
					if (!equipment.isEmpty()) result.add("Visible " + equipment.size()
						+ "-slot gear switch (observed): " + String.join(", ", equipment));
					if (!java.util.Objects.equals(oldSelected.overheadIcon, selected.overheadIcon))
						result.add("Protection prayer changed (observed): "
							+ (selected.overheadIcon == null ? "none" : pretty(selected.overheadIcon)));
				}
			}
		}
		for (RecordedEvent event : tick.events)
		{
			if (selectedKey != null && event.actorKey != null && !selectedKey.equals(event.actorKey) && !selectedKey.equals(event.targetKey)) continue;
			String actor = names.getOrDefault(event.actorKey, "Actor");
			switch (event.type)
			{
				case "HITSPLAT":
					if (event.value == null)
					{
						result.add(actor + " received a hitsplat (amount unavailable)");
					}
					else if (Integer.valueOf(HitsplatID.HEAL).equals(event.id))
					{
						result.add(actor + " healed " + event.value + " (observed; method unknown)");
					}
					else
					{
						String source = DamageAttribution.sourceDescription(event.detail);
						result.add(actor + " took " + event.value + " damage"
							+ (source == null ? " (source unknown)" : " from " + source));
					}
					break;
				case "RESOURCE_CHANGE": if (event.value != null) result.add(event.detail + " " + signed(event.value)); break;
				case "ITEM_ACTION": result.add("Attempted " + event.detail
					+ (event.id == null ? " [item unavailable]" : " [item " + event.id + "]")); break;
				case "PRAYER_CHANGE": if (event.value != null) result.add(pretty(event.detail) + (event.value == 1 ? " activated" : " deactivated")); break;
				case "DEATH": result.add(actor + " died"); break;
				case "PROJECTILE": result.add("A projectile moved"
					+ (event.actorKey == null ? " (source unavailable)" : " from " + actor)
					+ (event.targetKey == null ? " (target unavailable)" : " toward " + names.getOrDefault(event.targetKey, "Actor"))
					+ (event.id == null ? " [definition unavailable]" : " [definition " + event.id + "]")); break;
				case "ANIMATION": result.add(actor + " changed animation"
					+ (event.id == null ? " [definition unavailable]" : " [definition " + event.id + "]")); break;
				case "NPC_CHANGED": result.add(actor + " changed form"
					+ " [" + (event.fromDefinitionId == null ? "unavailable" : event.fromDefinitionId)
					+ " → " + (event.toDefinitionId == null ? "unavailable" : event.toDefinitionId) + "]"); break;
				case "MECHANIC": result.add(event.detail + " (inferred)"); break;
				default: break;
			}
		}
		return result;
	}

	private static List<String> inventoryChanges(List<ItemSnapshot> before, List<ItemSnapshot> after)
	{
		Map<Integer, Integer> oldTotals = totals(before), newTotals = totals(after);
		if (oldTotals.equals(newTotals))
		{
			return new ArrayList<>();
		}
		return itemChanges(before, after);
	}

	private static Map<Integer, Integer> totals(List<ItemSnapshot> items)
	{
		Map<Integer, Integer> result = new HashMap<>();
		if (items != null) for (ItemSnapshot item : items) result.merge(item.itemId, item.quantity, Integer::sum);
		return result;
	}

	private static List<String> itemChanges(List<ItemSnapshot> before, List<ItemSnapshot> after)
	{
		Map<Integer, ItemSnapshot> old = slots(before), now = slots(after); List<String> changes = new ArrayList<>();
		for (int slot = 0; slot < 32; slot++)
		{
			ItemSnapshot a = old.get(slot), b = now.get(slot);
			if (a == null && b == null) continue;
			if (a == null) changes.add("+" + b.displayName());
			else if (b == null) changes.add("-" + a.displayName());
			else if (a.itemId != b.itemId) changes.add(a.displayName() + " → " + b.displayName());
			else if (a.quantity != b.quantity) changes.add(a.displayName() + " " + signed(b.quantity - a.quantity));
		}
		return changes;
	}

	private static Map<Integer, ItemSnapshot> slots(List<ItemSnapshot> items) { Map<Integer, ItemSnapshot> result = new HashMap<>(); if (items != null) for (ItemSnapshot item : items) result.put(item.slot, item); return result; }
	private static String items(List<ItemSnapshot> items) { if (items == null) return "Unavailable"; if (items.isEmpty()) return "Empty"; StringBuilder value = new StringBuilder(); for (ItemSnapshot item : items) value.append(escape(item.displayName())).append(item.quantity > 1 ? " ×" + item.quantity : "").append("<br>"); return value.toString(); }
	private static ActorSnapshot actor(RecordedTick tick, String key) { if (tick != null && key != null) for (ActorSnapshot actor : tick.actors) if (key.equals(actor.key)) return actor; return null; }
	private static String localKey(RecordedTick tick) { if (tick != null) for (ActorSnapshot actor : tick.actors) if (actor.isLocalPlayer) return actor.key; return null; }
	private static String resource(int current, int maximum) { return current < 0 || maximum < 0 ? "Unavailable" : current + " / " + maximum; }
	private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }
	private static String pretty(String value) { return value == null ? "" : value.toLowerCase().replace('_', ' '); }
	private static String prettyList(List<String> values) { List<String> result = new ArrayList<>(); for (String value : values) result.add(pretty(value)); return String.join(", ", result); }
	private static String escape(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
