package com.combatreplay;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DamageAttributionTest
{
	@Test
	public void clientOwnedHitsplatIsAttributedToLocalPlayer()
	{
		assertTrue(DamageAttribution.isLocalPlayer("mine"));
		assertEquals("You (client-attributed)", DamageAttribution.sourceDescription("mine"));
	}

	@Test
	public void otherHitsplatsRemainUnattributed()
	{
		assertNull(DamageAttribution.sourceDescription("other"));
		assertNull(DamageAttribution.sourceDescription(null));
	}
}
