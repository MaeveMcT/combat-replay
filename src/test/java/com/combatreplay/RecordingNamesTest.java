package com.combatreplay;

import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class RecordingNamesTest
{
	@Test
	public void namesKilledEncounterAndDuration()
	{
		CombatRecording recording = new CombatRecording(0L);
		ActorSnapshot mole = new ActorSnapshot("npc-1", "NPC", "Giant Mole", 5779,
			0, 0, 10, 10, 0, 0, 2, 0, -1, -1, 0, 0, "player-2", false);
		RecordedEvent death = new RecordedEvent("DEATH", 0, "npc-1", null, 0, 0, -1, -1, null);
		for (int i = 0; i < 100; i++)
		{
			recording.add(new RecordedTick(i, i * 30, 0, 0, false, 99, 99, 70, 70,
				Collections.singletonList(mole), Collections.emptyList(), Collections.emptyList(),
				Collections.emptyList(), i == 99 ? Collections.singletonList(death) : Collections.emptyList()));
		}

		String name = recording.completed(60_000L).name;
		assertTrue(name.startsWith("Giant Mole — Kill — 1:00 —"));
	}

	@Test
	public void itemDisplayNameFallsBackToId()
	{
		assertTrue(new ItemSnapshot(0, 385, 1).displayName().contains("385"));
		assertTrue(new ItemSnapshot(0, 385, 1, "Shark").displayName().equals("Shark"));
	}
}
