package com.combatreplay;

import java.util.Objects;

/** Recording-local NPC definition fields exposed directly by RuneLite. */
final class NpcDefinitionMetadata
{
	final String name;
	final Integer combatLevel;
	final int size;

	NpcDefinitionMetadata(String name, Integer combatLevel, int size)
	{
		this.name = name;
		this.combatLevel = combatLevel;
		this.size = size;
	}

	@Override
	public boolean equals(Object other)
	{
		if (!(other instanceof NpcDefinitionMetadata)) return false;
		NpcDefinitionMetadata value = (NpcDefinitionMetadata) other;
		return Objects.equals(name, value.name) && Objects.equals(combatLevel, value.combatLevel)
			&& size == value.size;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, combatLevel, size);
	}
}
