package com.combatreplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ActorSnapshot
{
	final String key;
	final String kind;
	final String label;
	final boolean isLocalPlayer;
	final int npcId;
	final int worldX;
	final int worldY;
	final int sceneX;
	final int sceneY;
	final int localX;
	final int localY;
	final int plane;
	final int worldViewId;
	final String viewKey;
	final int size;
	final int orientation;
	final int animation;
	final int poseAnimation;
	final int healthRatio;
	final int healthScale;
	final String targetKey;
	final boolean dead;
	final List<ItemSnapshot> visibleEquipment;
	final String overheadIcon;
	final Integer combatLevel;
	final MovementAnimations movementAnimations;

	ActorSnapshot(String key, String kind, String label, int npcId,
		int worldX, int worldY, int sceneX, int sceneY, int plane, int worldViewId,
		int size, int orientation, int animation, int poseAnimation,
		int healthRatio, int healthScale, String targetKey, boolean dead)
	{
		this(key, kind, label, npcId, worldX, worldY, sceneX, sceneY,
			(sceneX << 7) + ((size & 1) == 0 ? 0 : 64),
			(sceneY << 7) + ((size & 1) == 0 ? 0 : 64), plane, worldViewId, size,
			orientation, animation, poseAnimation, healthRatio, healthScale, targetKey, dead,
			Collections.emptyList(), null);
	}

	ActorSnapshot(String key, String kind, String label, int npcId,
		int worldX, int worldY, int sceneX, int sceneY, int localX, int localY,
		int plane, int worldViewId, int size, int orientation, int animation, int poseAnimation,
		int healthRatio, int healthScale, String targetKey, boolean dead)
	{
		this(key, kind, label, npcId, worldX, worldY, sceneX, sceneY, localX, localY,
			plane, worldViewId, size, orientation, animation, poseAnimation, healthRatio,
			healthScale, targetKey, dead, Collections.emptyList(), null);
	}

	ActorSnapshot(String key, String kind, String label, int npcId,
		int worldX, int worldY, int sceneX, int sceneY, int localX, int localY,
		int plane, int worldViewId, int size, int orientation, int animation, int poseAnimation,
		int healthRatio, int healthScale, String targetKey, boolean dead,
		List<ItemSnapshot> visibleEquipment, String overheadIcon)
	{
		this(key, kind, label, "You".equals(label), npcId, worldX, worldY, sceneX, sceneY,
			localX, localY, plane, worldViewId, size, orientation, animation, poseAnimation,
			healthRatio, healthScale, targetKey, dead, visibleEquipment, overheadIcon);
	}

	ActorSnapshot(String key, String kind, String label, boolean isLocalPlayer, int npcId,
		int worldX, int worldY, int sceneX, int sceneY, int localX, int localY,
		int plane, int worldViewId, int size, int orientation, int animation, int poseAnimation,
		int healthRatio, int healthScale, String targetKey, boolean dead,
		List<ItemSnapshot> visibleEquipment, String overheadIcon)
	{
		this(key, kind, label, isLocalPlayer, npcId, worldX, worldY, sceneX, sceneY,
			localX, localY, plane, worldViewId, "view-" + worldViewId, size, orientation,
			animation, poseAnimation, healthRatio, healthScale, targetKey, dead,
			visibleEquipment, overheadIcon);
	}

	ActorSnapshot(String key, String kind, String label, boolean isLocalPlayer, int npcId,
		int worldX, int worldY, int sceneX, int sceneY, int localX, int localY,
		int plane, int worldViewId, String viewKey, int size, int orientation, int animation,
		int poseAnimation, int healthRatio, int healthScale, String targetKey, boolean dead,
		List<ItemSnapshot> visibleEquipment, String overheadIcon)
	{
		this(key, kind, label, isLocalPlayer, npcId, worldX, worldY, sceneX, sceneY,
			localX, localY, plane, worldViewId, viewKey, size, orientation, animation,
			poseAnimation, healthRatio, healthScale, targetKey, dead, visibleEquipment,
			overheadIcon, null, null);
	}

	ActorSnapshot(String key, String kind, String label, boolean isLocalPlayer, int npcId,
		int worldX, int worldY, int sceneX, int sceneY, int localX, int localY,
		int plane, int worldViewId, String viewKey, int size, int orientation, int animation,
		int poseAnimation, int healthRatio, int healthScale, String targetKey, boolean dead,
		List<ItemSnapshot> visibleEquipment, String overheadIcon, Integer combatLevel,
		MovementAnimations movementAnimations)
	{
		this.key = key;
		this.kind = kind;
		this.label = label;
		this.isLocalPlayer = isLocalPlayer;
		this.npcId = npcId;
		this.worldX = worldX;
		this.worldY = worldY;
		this.sceneX = sceneX;
		this.sceneY = sceneY;
		this.localX = localX;
		this.localY = localY;
		this.plane = plane;
		this.worldViewId = worldViewId;
		this.viewKey = viewKey;
		this.size = size;
		this.orientation = orientation;
		this.animation = animation;
		this.poseAnimation = poseAnimation;
		this.healthRatio = healthRatio;
		this.healthScale = healthScale;
		this.targetKey = targetKey;
		this.dead = dead;
		this.visibleEquipment = visibleEquipment == null ? null
			: Collections.unmodifiableList(new ArrayList<>(visibleEquipment));
		this.overheadIcon = overheadIcon;
		this.combatLevel = combatLevel;
		this.movementAnimations = movementAnimations;
	}

	String displayName()
	{
		return label == null || label.isEmpty() ? "Unavailable" : label;
	}
}
