package com.combatreplay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.Container;
import java.util.Collections;
import java.util.Arrays;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class CombatReplayPanelTest
{
	@Test
	public void sidebarKeepsRecordingAndUploadControlsWithoutLocalViewer() throws Exception
	{
		RecordingStore store = mock(RecordingStore.class);
		when(store.list()).thenReturn(Collections.emptyList());
		SwingUtilities.invokeAndWait(() ->
		{
			CombatReplayPanel panel = new CombatReplayPanel(store, mock(java.util.concurrent.ScheduledExecutorService.class));
			assertTrue(hasButton(panel, "Start recording"));
			assertTrue(hasButton(panel, "Pair web device"));
			assertTrue(hasButton(panel, "Upload / retry"));
			assertFalse(hasButton(panel, "Open"));
			assertFalse(hasButton(panel, "Open last replay"));
		});
	}

	@Test
	public void loadsLibraryOffUiThreadAndShowsOnlySelectedUploadStatus() throws Exception
	{
		RecordingStore store = mock(RecordingStore.class);
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		StoredRecording first = new StoredRecording(TestFilepaths.filepath(Path.of("first.json")), "first");
		StoredRecording second = new StoredRecording(TestFilepaths.filepath(Path.of("second.json")), "second");
		when(store.list()).thenAnswer(invocation ->
		{
			assertFalse(SwingUtilities.isEventDispatchThread());
			started.countDown();
			if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Library load timed out");
			return Arrays.asList(first, second);
		});
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		try
		{
			AtomicReference<CombatReplayPanel> reference = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() ->
			{
				CombatReplayPanel panel = new CombatReplayPanel(store, executor);
				panel.setUpload(ignored -> { });
				panel.refreshLibrary();
				reference.set(panel);
			});
			assertTrue(started.await(5, TimeUnit.SECONDS));
			// The EDT is free even while reading the library.
			SwingUtilities.invokeAndWait(() -> assertTrue(reference.get() != null));
			release.countDown();
			executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
			SwingUtilities.invokeAndWait(() ->
			{
				try
				{
					CombatReplayPanel panel = reference.get();
					Field listField = CombatReplayPanel.class.getDeclaredField("library");
					listField.setAccessible(true);
					@SuppressWarnings("unchecked") JList<StoredRecording> list = (JList<StoredRecording>) listField.get(panel);
					assertEquals(2, list.getModel().getSize());
					list.setSelectedIndex(0);
					panel.uploadStatusChanged("second", "Other upload");
					panel.uploadStatusChanged("first", "Web replay ready");
				}
				catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
			});
			SwingUtilities.invokeAndWait(() ->
			{
				try
				{
					Field statusField = CombatReplayPanel.class.getDeclaredField("uploadStatus");
					statusField.setAccessible(true);
					assertEquals("Web replay ready", ((JLabel) statusField.get(reference.get())).getText());
				}
				catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
			});
		}
		finally
		{
			release.countDown();
			executor.shutdownNow();
		}
	}

	private static boolean hasButton(Container container, String text)
	{
		for (Component component : container.getComponents())
		{
			if (component instanceof JButton && text.equals(((JButton) component).getText())) return true;
			if (component instanceof Container && hasButton((Container) component, text)) return true;
		}
		return false;
	}
}
