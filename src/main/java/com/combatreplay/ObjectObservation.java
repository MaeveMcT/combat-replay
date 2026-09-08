package com.combatreplay;

final class ObjectObservation
{
	final String category;
	final Integer effectiveDefinitionId;
	final Integer orientation;
	final Integer configuration;
	final Integer sizeX;
	final Integer sizeY;

	ObjectObservation(String category, Integer effectiveDefinitionId, Integer orientation,
		Integer configuration, Integer sizeX, Integer sizeY)
	{
		this.category = category;
		this.effectiveDefinitionId = effectiveDefinitionId;
		this.orientation = orientation;
		this.configuration = configuration;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
	}
}
