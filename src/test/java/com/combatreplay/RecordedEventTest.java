package com.combatreplay;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RecordedEventTest
{
	@Test
	public void preservesSpecificSceneObjectDefinitionIds()
	{
		RecordedEvent spawn = new RecordedEvent("GAME_OBJECT_SPAWN", 1, null, null,
			3600, 0, 10, 12, null);
		RecordedEvent despawn = new RecordedEvent("GROUND_OBJECT_DESPAWN", 1, null, null,
			3601, 0, 10, 12, null);

		assertEquals(Integer.valueOf(3600), spawn.id);
		assertEquals(Integer.valueOf(3601), despawn.id);
	}
}
