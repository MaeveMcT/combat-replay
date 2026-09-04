package com.combatreplay;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ActorMapPositionTest
{
	@Test
	public void evenSizedActorSceneCoordinateIsAlreadyItsCenter()
	{
		assertEquals(3212.0, ActorMapPosition.axis(3, 3200, 12, 2, 0), 0.001);
	}

	@Test
	public void oddSizedActorCenterIsHalfwayThroughSceneTile()
	{
		assertEquals(3212.5, ActorMapPosition.axis(3, 3200, 12, 3, 0), 0.001);
	}

	@Test
	public void currentFormatPreservesExactLocalPosition()
	{
		assertEquals(3212.25, ActorMapPosition.axis(4, 3200, 12, 1, 1568), 0.001);
	}
}
