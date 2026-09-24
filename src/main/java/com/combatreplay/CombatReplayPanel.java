package com.combatreplay;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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
	static final String PRIVACY_DISCLOSURE = "Recordings contain the exact names of all visible players, "
		+ "your inventory and equipment, and client-observed actions. They may later be uploaded privately "
		+ "to the configured website, whose operator can access them. Replays contain client observations, not authoritative server state.";

	private final RecordingStore store;
	private final ScheduledExecutorService executor;
	private final AtomicInteger libraryGeneration = new AtomicInteger();
	private final JLabel status = new JShadowedLabel("Ready to record");
	private final JLabel liveStats = new JShadowedLabel(" ");
	private final JButton recordButton = new JButton("Start recording");
	private final JButton pairDevice = new JButton("Pair web device");
	private final DefaultListModel<StoredRecording> libraryModel = new DefaultListModel<>();
	private final JList<StoredRecording> library = new JList<>(libraryModel);
	private Runnable toggleRecording;
	private Consumer<String> pairDeviceAction;
	private Consumer<StoredRecording> uploadAction;
	private Consumer<StoredRecording> uploadStatusAction;
	private RenameAction renameAction;
	private final JLabel uploadStatus = new JShadowedLabel(" ");

	@Inject
	CombatReplayPanel(RecordingStore store, ScheduledExecutorService executor)
	{
		this.store = store;
		this.executor = executor;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(header()); add(Box.createVerticalStrut(6)); add(recordingCard()); add(Box.createVerticalStrut(6));
		add(libraryCard()); add(Box.createVerticalStrut(6)); add(noteCard());
		recordButton.addActionListener(event -> { if (toggleRecording != null) toggleRecording.run(); });
		pairDevice.addActionListener(event -> requestPairing());
	}

	void setToggleRecording(Runnable action) { toggleRecording = action; }
	void setPairDevice(Consumer<String> action) { pairDeviceAction = action; }
	void setUpload(Consumer<StoredRecording> action) { uploadAction = action; }
	void setUploadStatus(Consumer<StoredRecording> action) { uploadStatusAction = action; }
	void setRename(RenameAction action) { renameAction = action; }

	interface RenameAction
	{
		void rename(StoredRecording recording, String name) throws IOException;
	}

	void uploadStatusChanged(String recordingId, String message)
	{
		SwingUtilities.invokeLater(() ->
		{
			StoredRecording selected = library.getSelectedValue();
			if (uploadAction != null && selected != null && selected.recordingId.equals(recordingId))
			{
				uploadStatus.setText(message.startsWith("Storage full") ? "Storage full — upload paused" : message);
				uploadStatus.setToolTipText(message);
			}
		});
	}

	void pairingFinished(boolean paired)
	{
		SwingUtilities.invokeLater(() ->
		{
			pairDevice.setEnabled(true);
			pairDevice.setText(paired ? "Web device paired" : "Pair web device");
			status.setText(paired ? "Future recordings will upload privately" : "Could not pair web device");
			status.setForeground(paired ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
		});
	}

	void recordingStarted()
	{
		SwingUtilities.invokeLater(() -> { recordButton.setEnabled(true); recordButton.setText("Stop and save"); status.setText("● Recording observations"); status.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR); liveStats.setText("Waiting for first tick…"); });
	}

	void recordingSaving(CombatRecording recording)
	{
		SwingUtilities.invokeLater(() ->
		{
			recordButton.setEnabled(false);
			recordButton.setText("Saving…");
			status.setText("Recording stopped; writing replay");
			status.setForeground(ColorScheme.BRAND_ORANGE);
		});
	}

	void updateRecording(int ticks, int actors)
	{
		SwingUtilities.invokeLater(() -> liveStats.setText(ticks + " ticks · " + actors + " visible actors"));
	}

	void recordingStopped(CombatRecording recording, String message, boolean saved)
	{
		SwingUtilities.invokeLater(() -> { recordButton.setEnabled(true); recordButton.setText("Start recording"); status.setText(message); status.setForeground(saved ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR); liveStats.setText(recording == null ? " " : recording.ticks.size() + " ticks captured"); refreshLibrary(); });
	}

	void reset()
	{
		toggleRecording = null;
		pairDeviceAction = null;
		uploadAction = null;
		uploadStatusAction = null;
		renameAction = null;
		libraryGeneration.incrementAndGet();
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
		configureButton(recordButton); configureButton(pairDevice);
		panel.add(status); panel.add(Box.createVerticalStrut(3)); panel.add(liveStats); panel.add(Box.createVerticalStrut(7)); panel.add(recordButton); panel.add(Box.createVerticalStrut(4)); panel.add(pairDevice); return panel;
	}

	private JPanel libraryCard()
	{
		JPanel panel = card(); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); panel.add(caption("RECORDING LIBRARY")); panel.add(Box.createVerticalStrut(5));
		library.setBackground(ColorScheme.DARK_GRAY_COLOR); library.setForeground(Color.WHITE); library.setVisibleRowCount(8); library.setFixedCellHeight(34); library.setToolTipText("Saved recordings");
		JScrollPane scroll = new JScrollPane(library); scroll.setPreferredSize(new Dimension(210, 230)); scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260)); scroll.setAlignmentX(LEFT_ALIGNMENT); panel.add(scroll);
		JPanel actions = new JPanel(new java.awt.GridLayout(1, 3, 4, 4)); actions.setOpaque(false); actions.setAlignmentX(LEFT_ALIGNMENT);
		JButton rename = new JButton("Rename"), delete = new JButton("Delete"), reveal = new JButton("Reveal");
		rename.addActionListener(e -> renameSelected()); delete.addActionListener(e -> deleteSelected()); reveal.addActionListener(e -> revealSelected());
		actions.add(rename); actions.add(delete); actions.add(reveal); panel.add(Box.createVerticalStrut(5)); panel.add(actions);
		JButton upload = new JButton("Upload / retry");
		configureButton(upload);
		upload.setToolTipText("After freeing web storage, select a recording and retry. The local file is retained.");
		upload.addActionListener(event ->
		{
			StoredRecording selected = library.getSelectedValue();
			if (selected != null && uploadAction != null) uploadAction.accept(selected);
		});
		library.addListSelectionListener(event ->
		{
			StoredRecording selected = library.getSelectedValue();
			if (!event.getValueIsAdjusting())
			{
				uploadStatus.setText(" ");
				uploadStatus.setToolTipText(null);
				if (selected != null && uploadStatusAction != null) uploadStatusAction.accept(selected);
			}
		});
		panel.add(Box.createVerticalStrut(5)); panel.add(upload); panel.add(uploadStatus);
		return panel;
	}

	private JPanel noteCard()
	{
		JPanel panel = card(); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); panel.add(caption("PRIVACY & EVIDENCE")); panel.add(Box.createVerticalStrut(4));
		JLabel text = new JLabel("<html><div style='width:190px'>" + PRIVACY_DISCLOSURE + "</div></html>"); text.setFont(FontManager.getRunescapeSmallFont()); text.setForeground(ColorScheme.LIGHT_GRAY_COLOR); panel.add(text); return panel;
	}

	void refreshLibrary()
	{
		int generation = libraryGeneration.incrementAndGet();
		executor.execute(() ->
		{
			try
			{
				java.util.List<StoredRecording> recordings = store.list();
				SwingUtilities.invokeLater(() ->
				{
					if (generation != libraryGeneration.get()) return;
					String selectedId = library.getSelectedValue() == null ? null : library.getSelectedValue().recordingId;
					libraryModel.clear();
					for (StoredRecording recording : recordings)
					{
						libraryModel.addElement(recording);
						if (recording.recordingId.equals(selectedId)) library.setSelectedValue(recording, true);
					}
				});
			}
			catch (IOException exception)
			{
				SwingUtilities.invokeLater(() ->
				{
					if (generation != libraryGeneration.get()) return;
					status.setText("Could not read recording library");
					status.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
				});
			}
		});
	}

	private void requestPairing()
	{
		if (pairDeviceAction == null) return;
		String code = JOptionPane.showInputDialog(this, "Pairing code from the Combat Replay website");
		if (code == null || code.trim().isEmpty()) return;
		pairDevice.setEnabled(false);
		pairDevice.setText("Pairing…");
		pairDeviceAction.accept(code.trim());
	}

	private void renameSelected()
	{
		StoredRecording selected = library.getSelectedValue(); if (selected == null) return;
		String name = JOptionPane.showInputDialog(this, "Recording name", selected.toString()); if (name == null || name.trim().isEmpty()) return;
		if (renameAction == null) return;
		try { renameAction.rename(selected, name); refreshLibrary(); } catch (IOException exception) { showError("Could not rename recording"); }
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
