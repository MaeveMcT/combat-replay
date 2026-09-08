package com.combatreplay;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import net.runelite.api.TileItem;
import org.junit.Test;

public class GroundItemTrackerTest
{
	@Test
	public void retainsIdentityAcrossQuantityChangesAndRemovesIt()
	{
		GroundItemTracker tracker = new GroundItemTracker();
		TileItem item = mock(TileItem.class);
		String first = tracker.upsert(item, 23866, "Crystal shard", 3,
			"view-main", 0, 3210, 3210, 100);
		String updated = tracker.upsert(item, 23866, "Crystal shard", 5,
			"view-main", 0, 3210, 3210, 101);

		GroundItemTracker.Delta upsert = tracker.drain();
		tracker.remove(item);
		GroundItemTracker.Delta remove = tracker.drain();

		assertEquals(first, updated);
		assertEquals(1, upsert.upserts.size());
		assertEquals(5, upsert.upserts.get(0).quantity);
		assertEquals(java.util.Collections.singletonList(first), remove.removals);
	}
}
