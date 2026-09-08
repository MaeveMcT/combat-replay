package com.combatreplay;

import java.util.Objects;

/** Plain immutable projectile evidence; contains no live RuneLite object. */
final class ProjectileSnapshot
{
	final String key;
	final int definitionId;
	final String sourceActorKey;
	final String targetActorKey;
	final String viewKey;
	final Integer sourcePlane;
	final Integer sourceX;
	final Integer sourceY;
	final Integer targetPlane;
	final Integer targetX;
	final Integer targetY;
	final int startCycle;
	final int endCycle;
	final int remainingCycles;
	final int startHeight;
	final int endHeight;
	final int slope;
	final int orientation;

	ProjectileSnapshot(String key, int definitionId, String sourceActorKey, String targetActorKey,
		String viewKey, Integer sourcePlane, Integer sourceX, Integer sourceY, Integer targetPlane,
		Integer targetX, Integer targetY, int startCycle, int endCycle, int remainingCycles,
		int startHeight, int endHeight, int slope, int orientation)
	{
		this.key = key;
		this.definitionId = definitionId;
		this.sourceActorKey = sourceActorKey;
		this.targetActorKey = targetActorKey;
		this.viewKey = viewKey;
		this.sourcePlane = sourcePlane;
		this.sourceX = sourceX;
		this.sourceY = sourceY;
		this.targetPlane = targetPlane;
		this.targetX = targetX;
		this.targetY = targetY;
		this.startCycle = startCycle;
		this.endCycle = endCycle;
		this.remainingCycles = remainingCycles;
		this.startHeight = startHeight;
		this.endHeight = endHeight;
		this.slope = slope;
		this.orientation = orientation;
	}

	@Override
	public boolean equals(Object other)
	{
		if (!(other instanceof ProjectileSnapshot)) return false;
		ProjectileSnapshot value = (ProjectileSnapshot) other;
		return definitionId == value.definitionId && startCycle == value.startCycle
			&& endCycle == value.endCycle && remainingCycles == value.remainingCycles
			&& startHeight == value.startHeight && endHeight == value.endHeight
			&& slope == value.slope && orientation == value.orientation
			&& Objects.equals(key, value.key) && Objects.equals(sourceActorKey, value.sourceActorKey)
			&& Objects.equals(targetActorKey, value.targetActorKey) && Objects.equals(viewKey, value.viewKey)
			&& Objects.equals(sourcePlane, value.sourcePlane) && Objects.equals(sourceX, value.sourceX)
			&& Objects.equals(sourceY, value.sourceY) && Objects.equals(targetPlane, value.targetPlane)
			&& Objects.equals(targetX, value.targetX) && Objects.equals(targetY, value.targetY);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(key, definitionId, sourceActorKey, targetActorKey, viewKey, sourcePlane,
			sourceX, sourceY, targetPlane, targetX, targetY, startCycle, endCycle, remainingCycles,
			startHeight, endHeight, slope, orientation);
	}
}
