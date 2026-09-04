package com.combatreplay;

import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ReplayEventDescriptionsTest
{
	@Test
	public void visibleTeammateGearChangeIsReportedAsObserved()
	{
		ActorSnapshot before = player(Collections.singletonList(item(4151, "Abyssal whip")));
		ActorSnapshot after = player(Collections.singletonList(item(12926, "Toxic blowpipe")));
		RecordedTick previous = tick(0, before);
		RecordedTick current = tick(1, after);

		List<String> descriptions = ReplayViewerFrame.descriptions(current, "player-2", previous);

		assertTrue(descriptions.stream().anyMatch(value ->
			value.contains("Visible 1-slot gear switch (observed)")
				&& value.contains("Abyssal whip") && value.contains("Toxic blowpipe")));
	}

	private static ItemSnapshot item(int id, String name)
	{
		return new ItemSnapshot(3, id, 1, name);
	}

	private static ActorSnapshot player(List<ItemSnapshot> equipment)
	{
		return new ActorSnapshot("player-2", "PLAYER", "Player 1", -1,
			3200, 3200, 10, 10, 1344, 1344, 0, 0, 1,
			0, -1, -1, -1, -1, null, false, equipment, "MELEE");
	}

	private static RecordedTick tick(int number, ActorSnapshot actor)
	{
		return new RecordedTick(number, number * 30, 3190, 3190, false,
			99, 99, 70, 70, Collections.singletonList(actor), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
	}
}
