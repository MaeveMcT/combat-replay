package com.combatreplay;

/** A client-observed scene tile. Coordinates are map coordinates (base + scene). */
final class SceneTileSnapshot
{
	final int worldViewId;
	final String viewKey;
	final int plane;
	final int x;
	final int y;
	final int height;
	final int terrainColor;
	final int collisionFlags;
	final int wallId;
	final int groundObjectId;
	final int decorativeObjectId;
	final int[] gameObjectIds;

	SceneTileSnapshot(int worldViewId, int plane, int x, int y, int height, int terrainColor,
		int collisionFlags, int wallId, int groundObjectId, int decorativeObjectId, int[] gameObjectIds)
	{
		this(worldViewId, "view-" + worldViewId, plane, x, y, height, terrainColor,
			collisionFlags, wallId, groundObjectId, decorativeObjectId, gameObjectIds);
	}

	SceneTileSnapshot(int worldViewId, String viewKey, int plane, int x, int y, int height,
		int terrainColor, int collisionFlags, int wallId, int groundObjectId,
		int decorativeObjectId, int[] gameObjectIds)
	{
		this.worldViewId = worldViewId;
		this.viewKey = viewKey;
		this.plane = plane;
		this.x = x;
		this.y = y;
		this.height = height;
		this.terrainColor = terrainColor;
		this.collisionFlags = collisionFlags;
		this.wallId = wallId;
		this.groundObjectId = groundObjectId;
		this.decorativeObjectId = decorativeObjectId;
		this.gameObjectIds = gameObjectIds == null ? new int[0] : gameObjectIds.clone();
	}

	String mapKey()
	{
		return viewKey + ":" + plane + ":" + x + ":" + y;
	}

	String contentKey()
	{
		StringBuilder value = new StringBuilder();
		value.append(height).append(':').append(terrainColor).append(':').append(collisionFlags)
			.append(':').append(wallId).append(':').append(groundObjectId).append(':').append(decorativeObjectId);
		for (int id : gameObjectIds)
		{
			value.append(':').append(id);
		}
		return value.toString();
	}
}
