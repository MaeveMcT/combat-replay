package com.combatreplay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JPanel;
import net.runelite.api.CollisionDataFlag;

final class ReplayMapPanel extends JPanel
{
	enum CameraMode { FOLLOW_ACTION, WHOLE_ENCOUNTER, MANUAL }
	enum ActorFilter { ENCOUNTER, NEARBY, ALL }

	private CombatRecording recording;
	private int tickIndex;
	private String selectedKey;
	private CameraMode cameraMode = CameraMode.FOLLOW_ACTION;
	private ActorFilter actorFilter = ActorFilter.ENCOUNTER;
	private final Map<String, SceneTileSnapshot> observedTiles = new LinkedHashMap<>();
	private Consumer<String> selectionListener;
	private double centerX;
	private double centerY;
	private double pixelsPerTile = 18;
	private Point dragStart;
	private double dragCenterX;
	private double dragCenterY;
	private boolean showTerrain = true;
	private boolean showObjects = true;
	private boolean showCollision;
	private boolean showGrid;
	private boolean showTrails = true;

	ReplayMapPanel()
	{
		setBackground(new Color(18, 20, 22));
		MouseAdapter mouse = new MouseAdapter()
		{
			@Override public void mousePressed(MouseEvent event)
			{
				dragStart = event.getPoint();
				dragCenterX = centerX;
				dragCenterY = centerY;
			}
			@Override public void mouseDragged(MouseEvent event)
			{
				cameraMode = CameraMode.MANUAL;
				centerX = dragCenterX - (event.getX() - dragStart.x) / pixelsPerTile;
				centerY = dragCenterY + (event.getY() - dragStart.y) / pixelsPerTile;
				repaint();
			}
			@Override public void mouseClicked(MouseEvent event)
			{
				selectAt(event.getPoint());
			}
			@Override public void mouseWheelMoved(MouseWheelEvent event)
			{
				cameraMode = CameraMode.MANUAL;
				double old = pixelsPerTile;
				pixelsPerTile = Math.max(3, Math.min(80, pixelsPerTile * Math.pow(1.15, -event.getPreciseWheelRotation())));
				centerX += (event.getX() - getWidth() / 2.0) * (1 / old - 1 / pixelsPerTile);
				centerY -= (event.getY() - getHeight() / 2.0) * (1 / old - 1 / pixelsPerTile);
				repaint();
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
		addMouseWheelListener(mouse);
	}

	void setSelectionListener(Consumer<String> listener) { selectionListener = listener; }
	void setSelectedKey(String key) { selectedKey = key; updateCamera(); repaint(); }
	void setCameraMode(CameraMode mode) { cameraMode = mode; updateCamera(); repaint(); }
	CameraMode getCameraMode() { return cameraMode; }
	void setActorFilter(ActorFilter filter) { actorFilter = filter; repaint(); }
	void setLayers(boolean terrain, boolean objects, boolean collision, boolean grid, boolean trails)
	{
		showTerrain = terrain; showObjects = objects; showCollision = collision; showGrid = grid; showTrails = trails; repaint();
	}

	void setRecording(CombatRecording recording)
	{
		this.recording = recording;
		tickIndex = 0;
		selectedKey = findLocalKey(recording == null || recording.ticks.isEmpty() ? null : recording.ticks.get(0));
		rebuildTiles();
		updateCamera();
		repaint();
	}

	void setTick(int index)
	{
		tickIndex = recording == null || recording.ticks.isEmpty() ? 0 : Math.max(0, Math.min(index, recording.ticks.size() - 1));
		rebuildTiles();
		updateCamera();
		repaint();
	}

	private void rebuildTiles()
	{
		observedTiles.clear();
		if (recording == null) return;
		for (int i = 0; i <= tickIndex && i < recording.ticks.size(); i++)
		{
			List<SceneTileSnapshot> tiles = recording.ticks.get(i).sceneTiles;
			if (tiles != null) for (SceneTileSnapshot tile : tiles) observedTiles.put(tile.mapKey(), tile);
		}
	}

	private void updateCamera()
	{
		if (recording == null || recording.ticks.isEmpty() || cameraMode == CameraMode.MANUAL) return;
		if (cameraMode == CameraMode.WHOLE_ENCOUNTER)
		{
			double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
			for (RecordedTick tick : recording.ticks) for (ActorSnapshot actor : tick.actors)
			{
				double x = mapX(tick, actor), y = mapY(tick, actor);
				minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
			}
			if (minX != Double.MAX_VALUE)
			{
				centerX = (minX + maxX) / 2; centerY = (minY + maxY) / 2;
				pixelsPerTile = Math.max(3, Math.min(30, Math.min((getWidth() - 60) / Math.max(8, maxX - minX + 4),
					(getHeight() - 60) / Math.max(8, maxY - minY + 4))));
			}
			return;
		}
		RecordedTick tick = currentTick();
		ActorSnapshot actor = actor(tick, selectedKey);
		if (actor == null) actor = actor(tick, findLocalKey(tick));
		if (actor != null)
		{
			centerX = mapX(tick, actor);
			centerY = mapY(tick, actor);
			ActorSnapshot target = actor(tick, actor.targetKey);
			if (target != null)
			{
				centerX = (centerX + mapX(tick, target)) / 2;
				centerY = (centerY + mapY(tick, target)) / 2;
			}
		}
	}

	@Override protected void paintComponent(Graphics graphics)
	{
		super.paintComponent(graphics);
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (recording == null || recording.ticks.isEmpty())
			{
				g.setColor(Color.LIGHT_GRAY); g.drawString("Open a recording to begin", 24, 32); return;
			}
			drawTiles(g);
			if (showTrails) drawTrails(g);
			drawInteractions(g);
			drawActors(g);
			drawIndicators(g);
			drawCompass(g);
		}
		finally { g.dispose(); }
	}

	private void drawTiles(Graphics2D g)
	{
		RecordedTick current = currentTick();
		for (SceneTileSnapshot tile : observedTiles.values())
		{
			if (tile.plane != plane(current) || tile.worldViewId != worldView(current)) continue;
			int x = px(tile.x), y = py(tile.y + 1), size = Math.max(1, (int) Math.ceil(pixelsPerTile));
			if (x + size < 0 || y + size < 0 || x > getWidth() || y > getHeight()) continue;
			if (showTerrain)
			{
				g.setColor(terrainColor(tile)); g.fillRect(x, y, size, size);
			}
			if (showCollision && blocksMovement(tile.collisionFlags))
			{
				g.setColor(new Color(215, 65, 70, 70)); g.fillRect(x, y, size, size);
			}
			if (showGrid && pixelsPerTile >= 8)
			{
				g.setColor(new Color(255, 255, 255, 35)); g.drawRect(x, y, size, size);
			}
			if (showObjects && (tile.wallId >= 0 || tile.gameObjectIds.length > 0 || tile.groundObjectId >= 0))
			{
				g.setColor(tile.wallId >= 0 ? new Color(220, 210, 185) : new Color(145, 125, 95));
				g.setStroke(new BasicStroke(tile.wallId >= 0 ? 2f : 1f)); g.drawRect(x + 1, y + 1, Math.max(1, size - 2), Math.max(1, size - 2));
			}
		}
	}

	private void drawTrails(Graphics2D g)
	{
		RecordedTick current = currentTick();
		for (ActorSnapshot now : visibleActors(current))
		{
			if (selectedKey != null && !selectedKey.equals(now.key) && actorFilter == ActorFilter.ENCOUNTER) continue;
			g.setColor(selectedKey != null && selectedKey.equals(now.key) ? new Color(255, 205, 55, 210) : new Color(120, 175, 220, 110));
			g.setStroke(new BasicStroke(selectedKey != null && selectedKey.equals(now.key) ? 3f : 2f));
			Point previous = null;
			for (int i = Math.max(0, tickIndex - 8); i <= tickIndex; i++)
			{
				RecordedTick tick = recording.ticks.get(i); ActorSnapshot actor = actor(tick, now.key);
				if (actor == null) { previous = null; continue; }
				Point point = new Point(px(mapX(tick, actor)), py(mapY(tick, actor)));
				if (previous != null && !previous.equals(point)) g.drawLine(previous.x, previous.y, point.x, point.y);
				else if (previous != null) g.drawOval(point.x - 3, point.y - 3, 6, 6);
				previous = point;
			}
		}
	}

	private void drawInteractions(Graphics2D g)
	{
		RecordedTick tick = currentTick(); Map<String, ActorSnapshot> actors = byKey(tick);
		g.setStroke(new BasicStroke(2)); g.setColor(new Color(255, 145, 35, 150));
		for (ActorSnapshot actor : visibleActors(tick))
		{
			ActorSnapshot target = actors.get(actor.targetKey);
			if (target != null) g.drawLine(actorX(tick, actor), actorY(tick, actor), actorX(tick, target), actorY(tick, target));
		}
	}

	private void drawActors(Graphics2D g)
	{
		RecordedTick tick = currentTick();
		for (ActorSnapshot actor : visibleActors(tick))
		{
			int x = actorX(tick, actor), y = actorY(tick, actor); int diameter = Math.max(10, Math.min(24, (int) (pixelsPerTile * Math.max(1, actor.size) * .65)));
			boolean selected = actor.key.equals(selectedKey);
			if (selected) { g.setColor(new Color(255, 205, 55)); g.fillOval(x - diameter / 2 - 4, y - diameter / 2 - 4, diameter + 8, diameter + 8); }
			g.setColor(actor.dead ? Color.DARK_GRAY : "PLAYER".equals(actor.kind) ? new Color(65, 155, 245) : new Color(225, 75, 70));
			g.fillOval(x - diameter / 2, y - diameter / 2, diameter, diameter); g.setColor(new Color(15, 15, 15)); g.drawOval(x - diameter / 2, y - diameter / 2, diameter, diameter);
			g.setColor(Color.WHITE); FontMetrics fm = g.getFontMetrics(); g.drawString(actor.label, x - fm.stringWidth(actor.label) / 2, y + diameter / 2 + fm.getAscent() + 2);
			if ("You".equals(actor.label) && tick.activePrayers != null && !tick.activePrayers.isEmpty())
			{
				g.setColor(new Color(85, 175, 255)); g.fillOval(x - 7, y - diameter / 2 - 17, 14, 14); g.setColor(Color.WHITE); g.drawString("P", x - 4, y - diameter / 2 - 5);
			}
		}
	}

	private void drawIndicators(Graphics2D g)
	{
		RecordedTick tick = currentTick(); Map<String, ActorSnapshot> actors = byKey(tick); Map<String, Integer> rows = new HashMap<>();
		ActorSnapshot local = actor(tick, findLocalKey(tick));
		int switched = tickIndex > 0 ? changedSlots(recording.ticks.get(tickIndex - 1).equipment, tick.equipment) : 0;
		if (local != null && switched > 0)
		{
			drawTag(g, local, switched + "-slot gear switch", new Color(255, 205, 65), rows, tick);
		}
		for (RecordedEvent event : tick.events)
		{
			ActorSnapshot actor = actors.get(event.actorKey);
			if ("GRAPHIC".equals(event.type) && event.sceneX >= 0)
			{
				int x = px(tick.baseX + event.sceneX), y = py(tick.baseY + event.sceneY + 1);
				g.setColor(new Color(185, 80, 255, 155)); g.fillRect(x, y, Math.max(4, (int) pixelsPerTile), Math.max(4, (int) pixelsPerTile));
				continue;
			}
			ActorSnapshot target = actors.get(event.targetKey);
			ActorSnapshot lineSource = actor;
			ActorSnapshot lineTarget = target;
			if ("HITSPLAT".equals(event.type) && DamageAttribution.isLocalPlayer(event.detail))
			{
				lineSource = local;
				lineTarget = actor;
			}
			if (lineSource != null && lineTarget != null
				&& ("PROJECTILE".equals(event.type) || "HITSPLAT".equals(event.type)))
			{
				g.setColor(new Color(255, 225, 105, 210)); g.setStroke(new BasicStroke(3f));
				g.drawLine(actorX(tick, lineSource), actorY(tick, lineSource),
					actorX(tick, lineTarget), actorY(tick, lineTarget));
			}
			if (actor == null) continue;
			String text = null; Color color = Color.WHITE;
			if ("HITSPLAT".equals(event.type)) { text = event.value == 0 ? "0" : "-" + event.value; color = event.value == 0 ? Color.LIGHT_GRAY : new Color(255, 85, 85); }
			else if ("RESOURCE_CHANGE".equals(event.type) && event.value > 0) { text = "+" + event.value + ("PRAYER".equals(event.detail) ? " Prayer" : " HP"); color = "PRAYER".equals(event.detail) ? new Color(85, 170, 255) : new Color(80, 230, 105); }
			else if ("PRAYER_CHANGE".equals(event.type)) { text = pretty(event.detail) + (event.value == 1 ? " on" : " off"); color = new Color(100, 190, 255); }
			else if ("ITEM_ACTION".equals(event.type)) { text = event.detail; color = new Color(255, 180, 55); }
			if (text != null) drawTag(g, actor, text, color, rows, tick);
		}
	}

	private void drawTag(Graphics2D g, ActorSnapshot actor, String text, Color color,
		Map<String, Integer> rows, RecordedTick tick)
	{
		int row = rows.merge(actor.key, 1, Integer::sum) - 1; int x = actorX(tick, actor) + 9, y = actorY(tick, actor) - 12 - row * 15;
		g.setColor(new Color(15, 15, 15, 220)); g.fillRoundRect(x - 2, y - 11, g.getFontMetrics().stringWidth(text) + 5, 15, 5, 5); g.setColor(color); g.drawString(text, x, y);
	}

	private static int changedSlots(List<ItemSnapshot> before, List<ItemSnapshot> after)
	{
		Map<Integer, ItemSnapshot> old = new HashMap<>(), current = new HashMap<>();
		if (before != null) for (ItemSnapshot item : before) old.put(item.slot, item);
		if (after != null) for (ItemSnapshot item : after) current.put(item.slot, item);
		int changes = 0;
		for (int slot = 0; slot < 16; slot++)
		{
			ItemSnapshot a = old.get(slot), b = current.get(slot);
			if (a == null ? b != null : b == null || a.itemId != b.itemId || a.quantity != b.quantity) changes++;
		}
		return changes;
	}

	private List<ActorSnapshot> visibleActors(RecordedTick tick)
	{
		if (actorFilter == ActorFilter.ALL) return tick.actors;
		ActorSnapshot local = actor(tick, findLocalKey(tick)); List<ActorSnapshot> result = new ArrayList<>();
		for (ActorSnapshot actor : tick.actors)
		{
			boolean nearby = local == null || (actor.worldViewId == local.worldViewId && Math.abs(actor.sceneX - local.sceneX) <= 24 && Math.abs(actor.sceneY - local.sceneY) <= 24);
			boolean focused = "You".equals(actor.label) || actor.targetKey != null || isTarget(tick, actor.key) || hasCombatEvent(tick, actor.key);
			if (nearby && (actorFilter == ActorFilter.NEARBY || focused || actor.key.equals(selectedKey))) result.add(actor);
		}
		return result;
	}

	private void selectAt(Point point)
	{
		RecordedTick tick = currentTick(); String nearest = null; double distance = 18;
		for (ActorSnapshot actor : visibleActors(tick))
		{
			double candidate = point.distance(actorX(tick, actor), actorY(tick, actor));
			if (candidate < distance) { distance = candidate; nearest = actor.key; }
		}
		if (nearest != null) { selectedKey = nearest; cameraMode = CameraMode.FOLLOW_ACTION; if (selectionListener != null) selectionListener.accept(nearest); repaint(); }
	}

	private boolean isTarget(RecordedTick tick, String key) { for (ActorSnapshot actor : tick.actors) if (key.equals(actor.targetKey)) return true; return false; }
	private boolean hasCombatEvent(RecordedTick tick, String key) { for (RecordedEvent event : tick.events) if (key.equals(event.actorKey) && ("HITSPLAT".equals(event.type) || "DEATH".equals(event.type))) return true; return false; }
	private RecordedTick currentTick() { return recording.ticks.get(tickIndex); }
	private static ActorSnapshot actor(RecordedTick tick, String key) { if (tick != null && key != null) for (ActorSnapshot actor : tick.actors) if (key.equals(actor.key)) return actor; return null; }
	private static Map<String, ActorSnapshot> byKey(RecordedTick tick) { Map<String, ActorSnapshot> map = new HashMap<>(); for (ActorSnapshot actor : tick.actors) map.put(actor.key, actor); return map; }
	private static String findLocalKey(RecordedTick tick) { if (tick != null) for (ActorSnapshot actor : tick.actors) if ("You".equals(actor.label)) return actor.key; return null; }
	private int worldView(RecordedTick tick) { ActorSnapshot actor = actor(tick, selectedKey); if (actor == null) actor = actor(tick, findLocalKey(tick)); return actor == null ? 0 : actor.worldViewId; }
	private int plane(RecordedTick tick) { ActorSnapshot actor = actor(tick, selectedKey); if (actor == null) actor = actor(tick, findLocalKey(tick)); return actor == null ? 0 : actor.plane; }
	private double mapX(RecordedTick tick, ActorSnapshot actor) { return ActorMapPosition.axis(recording.formatVersion, tick.baseX, actor.sceneX, actor.size, actor.localX); }
	private double mapY(RecordedTick tick, ActorSnapshot actor) { return ActorMapPosition.axis(recording.formatVersion, tick.baseY, actor.sceneY, actor.size, actor.localY); }
	private int actorX(RecordedTick tick, ActorSnapshot actor) { return px(mapX(tick, actor)); }
	private int actorY(RecordedTick tick, ActorSnapshot actor) { return py(mapY(tick, actor)); }
	private int px(double x) { return (int) Math.round(getWidth() / 2.0 + (x - centerX) * pixelsPerTile); }
	private int py(double y) { return (int) Math.round(getHeight() / 2.0 - (y - centerY) * pixelsPerTile); }
	private static String pretty(String value) { return value == null ? "" : value.toLowerCase().replace('_', ' '); }
	private static boolean blocksMovement(int flags)
	{
		int movement = CollisionDataFlag.BLOCK_MOVEMENT_FULL | CollisionDataFlag.BLOCK_MOVEMENT_OBJECT
			| CollisionDataFlag.BLOCK_MOVEMENT_FLOOR | CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION
			| CollisionDataFlag.BLOCK_MOVEMENT_NORTH | CollisionDataFlag.BLOCK_MOVEMENT_EAST
			| CollisionDataFlag.BLOCK_MOVEMENT_SOUTH | CollisionDataFlag.BLOCK_MOVEMENT_WEST;
		return (flags & movement) != 0;
	}

	private static Color terrainColor(SceneTileSnapshot tile)
	{
		int rgb = tile.terrainColor;
		if (rgb > 0 && rgb <= 0xffffff) { Color color = new Color(rgb); return new Color(Math.max(25, color.getRed() / 2), Math.max(25, color.getGreen() / 2), Math.max(25, color.getBlue() / 2)); }
		int shade = 42 + Math.floorMod(tile.height / 16, 18); return new Color(shade, shade + 5, shade);
	}
	private void drawCompass(Graphics2D g)
	{
		g.setColor(new Color(15, 15, 15, 190)); g.fillOval(14, 14, 38, 38); g.setColor(Color.WHITE); g.drawOval(14, 14, 38, 38); g.drawString("N", 29, 28); g.drawLine(33, 31, 33, 45);
		g.drawString(String.format("%.1f px/tile", pixelsPerTile), 14, getHeight() - 14);
	}
}
