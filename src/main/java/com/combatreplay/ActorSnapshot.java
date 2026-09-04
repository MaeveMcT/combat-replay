package com.combatreplay;

final class ActorSnapshot
{
	final String key;
	final String kind;
	final String label;
	final int npcId;
	final int worldX;
	final int worldY;
	final int sceneX;
	final int sceneY;
	final int localX;
	final int localY;
	final int plane;
	final int worldViewId;
	final int size;
	final int orientation;
	final int animation;
	final int poseAnimation;
	final int healthRatio;
	final int healthScale;
	final String targetKey;
	final boolean dead;

	ActorSnapshot(String key, String kind, String label, int npcId,
		int worldX, int worldY, int sceneX, int sceneY, int plane, int worldViewId,
		int size, int orientation, int animation, int poseAnimation,
		int healthRatio, int healthScale, String targetKey, boolean dead)
	{
		this(key, kind, label, npcId, worldX, worldY, sceneX, sceneY,
			(sceneX << 7) + ((size & 1) == 0 ? 0 : 64),
			(sceneY << 7) + ((size & 1) == 0 ? 0 : 64), plane, worldViewId, size,
			orientation, animation, poseAnimation, healthRatio, healthScale, targetKey, dead);
	}

	ActorSnapshot(String key, String kind, String label, int npcId,
		int worldX, int worldY, int sceneX, int sceneY, int localX, int localY,
		int plane, int worldViewId, int size, int orientation, int animation, int poseAnimation,
		int healthRatio, int healthScale, String targetKey, boolean dead)
	{
		this.key = key;
		this.kind = kind;
		this.label = label;
		this.npcId = npcId;
		this.worldX = worldX;
		this.worldY = worldY;
		this.sceneX = sceneX;
		this.sceneY = sceneY;
		this.localX = localX;
		this.localY = localY;
		this.plane = plane;
		this.worldViewId = worldViewId;
		this.size = size;
		this.orientation = orientation;
		this.animation = animation;
		this.poseAnimation = poseAnimation;
		this.healthRatio = healthRatio;
		this.healthScale = healthScale;
		this.targetKey = targetKey;
		this.dead = dead;
	}
}
