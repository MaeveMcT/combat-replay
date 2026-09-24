package com.combatreplay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.Container;
import java.util.Collections;
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
			CombatReplayPanel panel = new CombatReplayPanel(store);
			assertTrue(hasButton(panel, "Start recording"));
			assertTrue(hasButton(panel, "Pair web device"));
			assertTrue(hasButton(panel, "Upload / retry"));
			assertFalse(hasButton(panel, "Open"));
			assertFalse(hasButton(panel, "Open last replay"));
		});
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
