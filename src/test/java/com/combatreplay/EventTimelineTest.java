package com.combatreplay;

import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EventTimelineTest
{
	@Test
	public void loadingRecordingResetsPlaybackToFirstTick()
	{
		EventTimeline timeline = new EventTimeline();
		timeline.setMinimum(0);
		timeline.setMaximum(200);
		timeline.setValue(125);

		timeline.setRecording(recording(150));

		assertEquals(0, timeline.getValue());
	}

	private static CombatRecording recording(int ticks)
	{
		CombatRecording recording = new CombatRecording(0L);
		for (int index = 0; index < ticks; index++)
		{
			recording.add(new RecordedTick(index, index * 30, 0, 0, false,
				99, 99, 70, 70, Collections.emptyList(), Collections.emptyList(),
				Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
		}
		return recording;
	}
}
