package com.combatreplay;

import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CombatRecordingTest
{
	@Test
	public void snapshotDoesNotChangeWhenRecordingContinues()
	{
		CombatRecording recording = new CombatRecording(123L);
		recording.add(tick(0));

		CombatRecording snapshot = recording.snapshot();
		recording.add(tick(1));

		assertEquals(1, snapshot.ticks.size());
		assertEquals(2, recording.ticks.size());
	}

	@Test
	public void pendingKillEvidenceIsAppendedToFinalTick()
	{
		CombatRecording recording = new CombatRecording(123L);
		recording.add(tick(0));
		RecordedEvent death = new RecordedEvent("DEATH", 25, "npc-1", null,
			0, 0, -1, -1, null);

		recording.appendToLastTick(Collections.emptyList(), Collections.singletonList(death));

		assertEquals(1, recording.ticks.size());
		assertEquals(1, recording.ticks.get(0).events.size());
		assertEquals("DEATH", recording.ticks.get(0).events.get(0).type);
	}

	private static RecordedTick tick(int number)
	{
		return new RecordedTick(number, number * 30, 0, 0, false,
			50, 50, 30, 50, Collections.emptyList(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
	}
}
