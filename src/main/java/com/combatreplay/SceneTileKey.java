package com.combatreplay;

final class SceneTileKey
{
	final String viewKey;
	final int plane;
	final int x;
	final int y;

	SceneTileKey(String viewKey, int plane, int x, int y)
	{
		this.viewKey = viewKey;
		this.plane = plane;
		this.x = x;
		this.y = y;
	}

	String mapKey()
	{
		return viewKey + ":" + plane + ":" + x + ":" + y;
	}
}
