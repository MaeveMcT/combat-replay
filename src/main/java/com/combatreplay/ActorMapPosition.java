package com.combatreplay;

final class ActorMapPosition
{
	private ActorMapPosition()
	{
	}

	static double axis(int formatVersion, int base, int scene, int size, int local)
	{
		if (formatVersion >= 4)
		{
			return base + local / 128.0;
		}

		// Actor scene coordinates come from the actor's local centre, not the
		// south-west corner of its footprint. Even-sized actors are centred on a
		// tile boundary; odd-sized actors are centred halfway through a tile.
		return base + scene + ((size & 1) == 0 ? 0.0 : 0.5);
	}
}
