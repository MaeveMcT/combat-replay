package com.combatreplay;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.shadowlabel.JShadowedLabel;

@Singleton
final class CombatReplayPanel extends PluginPanel
{
	static final String PRIVACY_DISCLOSURE = "Recordings contain the exact names of all visible players "
		+ "and may later be uploaded privately. Replays contain client observations, not authoritative server state.";

	private final RecordingStore store;
	private final JLabel status = new JShadowedLabel("Ready to record");
	private final JLabel liveStats = new JShadowedLabel(" ");
	private final JButton recordButton = new JButton("Start recording");
	private final JButton openCurrent = new JButton("Open last replay");
	private final DefaultListModel<StoredRecording> libraryModel = new DefaultListModel<>();
	private final JList<StoredRecording> library = new JList<>(libraryModel);
	private final List<ReplayViewerFrame> viewers = new ArrayList<>();
	private CombatRecording current;
	private Runnable toggleRecording;

	@Inject
	CombatReplayPanel(RecordingStore store)
	{
		this.store = store;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(header()); add(Box.createVerticalStrut(6)); add(recordingCard()); add(Box.createVerticalStrut(6));
		add(libraryCard()); add(Box.createVerticalStrut(6)); add(noteCard());
		recordButton.addActionListener(event -> { if (toggleRecording != null) toggleRecording.run(); });
		openCurrent.addActionListener(event -> open(current));
		refreshLibrary();
	}

	void setToggleRecording(Runnable action) { toggleRecording = action; }

	void recordingStarted()
	{
		SwingUtilities.invokeLater(() -> { current = null; recordButton.setEnabled(true); recordButton.setText("Stop and save"); status.setText("● Recording observations"); status.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR); liveStats.setText("Waiting for first tick…"); openCurrent.setEnabled(false); });
	}

	void recordingSaving(CombatRecording recording)
	{
		SwingUtilities.invokeLater(() ->
		{
			current = recording;
			recordButton.setEnabled(false);
			recordButton.setText("Saving…");
			status.setText("Recording stopped; writing replay");
			status.setForeground(ColorScheme.BRAND_ORANGE);
		});
	}

	void updateRecording(CombatRecording recording)
	{
		SwingUtilities.invokeLater(() -> { current = recording; int actors = recording.ticks.isEmpty() ? 0 : recording.ticks.get(recording.ticks.size() - 1).actors.size(); liveStats.setText(recording.ticks.size() + " ticks · " + actors + " visible actors"); });
	}

	void recordingStopped(CombatRecording recording, String message, boolean saved)
	{
		SwingUtilities.invokeLater(() -> { current = recording; recordButton.setEnabled(true); recordButton.setText("Start recording"); status.setText(message); status.setForeground(saved ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR); liveStats.setText(recording == null ? " " : recording.ticks.size() + " ticks captured"); openCurrent.setEnabled(recording != null && !recording.ticks.isEmpty()); refreshLibrary(); });
	}

	void reset()
	{
		toggleRecording = null;
		SwingUtilities.invokeLater(() ->
		{
			for (ReplayViewerFrame viewer : new ArrayList<>(viewers)) viewer.dispose();
			viewers.clear();
		});
	}

	private JPanel header()
	{
		JPanel panel = new JPanel(new BorderLayout()); panel.setBackground(ColorScheme.DARKER_GRAY_COLOR); panel.setBorder(new EmptyBorder(8, 8, 8, 8)); panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		JLabel title = new JShadowedLabel("Combat Replay"); title.setFont(FontManager.getRunescapeBoldFont()); title.setHorizontalAlignment(JLabel.CENTER); panel.add(title); return panel;
	}

	private JPanel recordingCard()
	{
		JPanel panel = card(); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		status.setAlignmentX(LEFT_ALIGNMENT); liveStats.setForeground(ColorScheme.LIGHT_GRAY_COLOR); liveStats.setAlignmentX(LEFT_ALIGNMENT);
		configureButton(recordButton); configureButton(openCurrent); openCurrent.setEnabled(false);
		panel.add(status); panel.add(Box.createVerticalStrut(3)); panel.add(liveStats); panel.add(Box.createVerticalStrut(7)); panel.add(recordButton); panel.add(Box.createVerticalStrut(4)); panel.add(openCurrent); return panel;
	}

	private JPanel libraryCard()
	{
		JPanel panel = card(); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); panel.add(caption("RECORDING LIBRARY")); panel.add(Box.createVerticalStrut(5));
		library.setBackground(ColorScheme.DARK_GRAY_COLOR); library.setForeground(Color.WHITE); library.setVisibleRowCount(8); library.setFixedCellHeight(34); library.setToolTipText("Saved recordings");
		library.addMouseListener(new java.awt.event.MouseAdapter() { @Override public void mouseClicked(java.awt.event.MouseEvent event) { if (event.getClickCount() == 2) open(library.getSelectedValue()); } });
		JScrollPane scroll = new JScrollPane(library); scroll.setPreferredSize(new Dimension(210, 230)); scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260)); scroll.setAlignmentX(LEFT_ALIGNMENT); panel.add(scroll);
		JPanel actions = new JPanel(new java.awt.GridLayout(2, 2, 4, 4)); actions.setOpaque(false); actions.setAlignmentX(LEFT_ALIGNMENT);
		JButton open = new JButton("Open"), rename = new JButton("Rename"), delete = new JButton("Delete"), reveal = new JButton("Reveal");
		open.addActionListener(e -> open(library.getSelectedValue())); rename.addActionListener(e -> renameSelected()); delete.addActionListener(e -> deleteSelected()); reveal.addActionListener(e -> revealSelected());
		actions.add(open); actions.add(rename); actions.add(delete); actions.add(reveal); panel.add(Box.createVerticalStrut(5)); panel.add(actions); return panel;
	}

	private JPanel noteCard()
	{
		JPanel panel = card(); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); panel.add(caption("PRIVACY & EVIDENCE")); panel.add(Box.createVerticalStrut(4));
		JLabel text = new JLabel("<html><div style='width:190px'>" + PRIVACY_DISCLOSURE + "</div></html>"); text.setFont(FontManager.getRunescapeSmallFont()); text.setForeground(ColorScheme.LIGHT_GRAY_COLOR); panel.add(text); return panel;
	}

	private void refreshLibrary()
	{
		try { libraryModel.clear(); for (StoredRecording recording : store.list()) libraryModel.addElement(recording); }
		catch (IOException exception) { status.setText("Could not read recording library"); status.setForeground(ColorScheme.PROGRESS_ERROR_COLOR); }
	}

	private void open(StoredRecording stored) { if (stored != null) open(stored.recording); }
	private void open(CombatRecording recording)
	{
		if (recording == null || recording.ticks.isEmpty()) return;
		SwingUtilities.invokeLater(() ->
		{
			ReplayViewerFrame viewer = new ReplayViewerFrame(recording);
			viewers.add(viewer);
			viewer.addWindowListener(new WindowAdapter()
			{
				@Override public void windowClosed(WindowEvent event) { viewers.remove(viewer); }
			});
			viewer.setVisible(true);
		});
	}

	private void renameSelected()
	{
		StoredRecording selected = library.getSelectedValue(); if (selected == null) return;
		String name = JOptionPane.showInputDialog(this, "Recording name", selected.toString()); if (name == null || name.trim().isEmpty()) return;
		try { store.rename(selected, name); refreshLibrary(); } catch (IOException exception) { showError("Could not rename recording"); }
	}

	private void deleteSelected()
	{
		StoredRecording selected = library.getSelectedValue(); if (selected == null || JOptionPane.showConfirmDialog(this, "Delete “" + selected + "”?", "Delete recording", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
		try { store.delete(selected); refreshLibrary(); } catch (IOException exception) { showError("Could not delete recording"); }
	}

	private void revealSelected()
	{
		StoredRecording selected = library.getSelectedValue(); if (selected == null || !Desktop.isDesktopSupported()) return;
		try { Desktop.getDesktop().open(selected.path.getParent().toFile()); } catch (IOException exception) { showError("Could not reveal recording folder"); }
	}

	private void showError(String message) { JOptionPane.showMessageDialog(this, message, "Combat Replay", JOptionPane.ERROR_MESSAGE); }
	private static void configureButton(JButton button) { button.setAlignmentX(LEFT_ALIGNMENT); button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28)); }
	private static JLabel caption(String value) { JLabel label = new JShadowedLabel(value); label.setFont(FontManager.getRunescapeSmallFont()); label.setForeground(ColorScheme.BRAND_ORANGE); label.setAlignmentX(LEFT_ALIGNMENT); return label; }
	private static JPanel card() { JPanel panel = new JPanel(); panel.setBackground(ColorScheme.DARKER_GRAY_COLOR); panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR), new EmptyBorder(9, 9, 9, 9))); panel.setAlignmentX(LEFT_ALIGNMENT); return panel; }
}
