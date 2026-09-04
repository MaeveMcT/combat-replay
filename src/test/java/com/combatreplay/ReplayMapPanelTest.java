package com.combatreplay;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ReplayMapPanelTest
{
	@Test
	public void labelsHighLevelOffensivePrayers()
	{
		assertEquals("Pi", ReplayMapPanel.offensivePrayerBadge("PIETY"));
		assertEquals("Ri", ReplayMapPanel.offensivePrayerBadge("RIGOUR"));
		assertEquals("Au", ReplayMapPanel.offensivePrayerBadge("AUGURY"));
	}

	@Test
	public void labelsLowerLevelOffensivePrayersCompactly()
	{
		assertEquals("US", ReplayMapPanel.offensivePrayerBadge("ULTIMATE_STRENGTH"));
		assertEquals("EE", ReplayMapPanel.offensivePrayerBadge("EAGLE_EYE"));
	}

	@Test
	public void excludesProtectionAndUtilityPrayersFromOffensiveBadges()
	{
		assertNull(ReplayMapPanel.offensivePrayerBadge("PROTECT_FROM_MELEE"));
		assertNull(ReplayMapPanel.offensivePrayerBadge("SMITE"));
		assertNull(ReplayMapPanel.offensivePrayerBadge("RAPID_HEAL"));
	}
}
