package com.combatreplay;

import java.util.Objects;

/** Recording-local object definition fields exposed directly by RuneLite. */
final class ObjectDefinitionMetadata
{
	final String name;
	final Integer effectiveDefinitionId;
	final int sizeX;
	final int sizeY;
	final Integer mapIconId;
	final Integer mapSceneId;

	ObjectDefinitionMetadata(String name, Integer effectiveDefinitionId, int sizeX, int sizeY,
		Integer mapIconId, Integer mapSceneId)
	{
		this.name = name;
		this.effectiveDefinitionId = effectiveDefinitionId;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.mapIconId = mapIconId;
		this.mapSceneId = mapSceneId;
	}

	@Override
	public boolean equals(Object other)
	{
		if (!(other instanceof ObjectDefinitionMetadata)) return false;
		ObjectDefinitionMetadata value = (ObjectDefinitionMetadata) other;
		return Objects.equals(name, value.name)
			&& Objects.equals(effectiveDefinitionId, value.effectiveDefinitionId)
			&& sizeX == value.sizeX && sizeY == value.sizeY
			&& Objects.equals(mapIconId, value.mapIconId) && Objects.equals(mapSceneId, value.mapSceneId);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, effectiveDefinitionId, sizeX, sizeY, mapIconId, mapSceneId);
	}
}
