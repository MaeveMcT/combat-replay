package com.combatreplay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class ProjectileTrackerTest
{
	@Test
	public void repeatedCallbacksUpdateOneStableProjectile()
	{
		ProjectileTracker tracker = new ProjectileTracker();
		Projectile projectile = projectile(1712, 100, 150);

		ProjectileTracker.Observation first = tracker.observe(projectile, "view-main", actor -> null);
		when(projectile.getRemainingCycles()).thenReturn(40);
		ProjectileTracker.Observation repeated = tracker.observe(projectile, "view-main", actor -> null);
		ProjectileTracker.Delta delta = tracker.drain(110, "view-main");

		assertTrue(first.first);
		assertFalse(repeated.first);
		assertEquals(first.key, repeated.key);
		assertEquals(1, delta.upserts.size());
		assertEquals(40, delta.upserts.get(0).remainingCycles);
	}

	@Test
	public void simultaneousSameDefinitionProjectilesRemainDistinctAndExpire()
	{
		ProjectileTracker tracker = new ProjectileTracker();
		Projectile first = projectile(1712, 100, 120);
		Projectile second = projectile(1712, 100, 140);

		String firstKey = tracker.observe(first, "view-main", actor -> null).key;
		String secondKey = tracker.observe(second, "view-main", actor -> null).key;
		ProjectileTracker.Delta observed = tracker.drain(110, "view-main");
		ProjectileTracker.Delta expired = tracker.drain(121, "view-main");

		assertFalse(firstKey.equals(secondKey));
		assertEquals(2, observed.upserts.size());
		assertEquals(java.util.Collections.singletonList(firstKey), expired.removals);
	}

	@Test
	public void viewTransitionRemovesTrackedProjectiles()
	{
		ProjectileTracker tracker = new ProjectileTracker();
		String key = tracker.observe(projectile(1, 100, 200), "view-main", actor -> null).key;
		tracker.drain(110, "view-main");

		assertEquals(java.util.Collections.singletonList(key), tracker.drain(111, "view-instance").removals);
	}

	private static Projectile projectile(int id, int start, int end)
	{
		Projectile projectile = mock(Projectile.class);
		when(projectile.getId()).thenReturn(id);
		when(projectile.getSourcePoint()).thenReturn(new WorldPoint(3215, 3215, 0));
		when(projectile.getTargetPoint()).thenReturn(new WorldPoint(3210, 3210, 0));
		when(projectile.getStartCycle()).thenReturn(start);
		when(projectile.getEndCycle()).thenReturn(end);
		when(projectile.getRemainingCycles()).thenReturn(end - start);
		when(projectile.getStartHeight()).thenReturn(40);
		when(projectile.getSlope()).thenReturn(16);
		when(projectile.getOrientation()).thenReturn(1024);
		return projectile;
	}
}
