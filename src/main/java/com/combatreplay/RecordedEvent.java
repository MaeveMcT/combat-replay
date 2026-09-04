package com.combatreplay;

final class RecordedEvent
{
	final String type;
	final int gameCycle;
	final String actorKey;
	final String targetKey;
	final int id;
	final int value;
	final int sceneX;
	final int sceneY;
	final String detail;

	RecordedEvent(String type, int gameCycle, String actorKey, String targetKey,
		int id, int value, int sceneX, int sceneY, String detail)
	{
		this.type = type;
		this.gameCycle = gameCycle;
		this.actorKey = actorKey;
		this.targetKey = targetKey;
		this.id = id;
		this.value = value;
		this.sceneX = sceneX;
		this.sceneY = sceneY;
		this.detail = detail;
	}
}
