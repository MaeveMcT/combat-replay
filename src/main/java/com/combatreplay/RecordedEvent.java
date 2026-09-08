package com.combatreplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RecordedEvent
{
	final String eventId;
	final String type;
	final int gameCycle;
	final Integer sequence;
	final String actorKey;
	final String targetKey;
	final Integer id;
	final Integer value;
	final Integer fromDefinitionId;
	final Integer toDefinitionId;
	final String viewKey;
	final Integer plane;
	final int sceneX;
	final int sceneY;
	final String coordinateSpace;
	final String detail;
	final String evidence;
	final String ruleId;
	final List<String> evidenceEventIds;

	RecordedEvent(String type, int gameCycle, String actorKey, String targetKey,
		int id, int value, int sceneX, int sceneY, String detail)
	{
		this(null, type, gameCycle, null, actorKey, targetKey,
			usesDefinitionId(type) && id >= 0 ? id : null, usesAmount(type) ? value : null, null, null,
			sceneX, sceneY, sceneX < 0 || sceneY < 0 ? null : "scene", detail,
			"observed", null, Collections.emptyList());
	}

	RecordedEvent(String eventId, String type, int gameCycle, Integer sequence,
		String actorKey, String targetKey, Integer id, Integer value, String viewKey, Integer plane,
		int sceneX, int sceneY, String coordinateSpace, String detail, String evidence,
		String ruleId, List<String> evidenceEventIds)
	{
		this(eventId, type, gameCycle, sequence, actorKey, targetKey, id, value, null, null,
			viewKey, plane, sceneX, sceneY, coordinateSpace, detail, evidence, ruleId, evidenceEventIds);
	}

	RecordedEvent(String eventId, String type, int gameCycle, Integer sequence,
		String actorKey, String targetKey, Integer id, Integer value, Integer fromDefinitionId,
		Integer toDefinitionId, String viewKey, Integer plane, int sceneX, int sceneY,
		String coordinateSpace, String detail, String evidence, String ruleId,
		List<String> evidenceEventIds)
	{
		this.eventId = eventId;
		this.type = type;
		this.gameCycle = gameCycle;
		this.sequence = sequence;
		this.actorKey = actorKey;
		this.targetKey = targetKey;
		this.id = id;
		this.value = value;
		this.fromDefinitionId = fromDefinitionId;
		this.toDefinitionId = toDefinitionId;
		this.viewKey = viewKey;
		this.plane = plane;
		this.sceneX = sceneX;
		this.sceneY = sceneY;
		this.coordinateSpace = coordinateSpace;
		this.detail = detail;
		this.evidence = evidence;
		this.ruleId = ruleId;
		this.evidenceEventIds = Collections.unmodifiableList(new ArrayList<>(evidenceEventIds));
	}

	static boolean usesDefinitionId(String type)
	{
		if (type.endsWith("_OBJECT_SPAWN") || type.endsWith("_OBJECT_DESPAWN")) return true;
		switch (type)
		{
			case "HITSPLAT":
			case "PROJECTILE":
			case "GRAPHIC":
			case "ACTOR_GRAPHIC":
			case "ANIMATION":
			case "ITEM_ACTION":
			case "NPC_SPAWN":
			case "NPC_DESPAWN":
			case "NPC_CHANGED":
			case "OBJECT_SPAWN":
			case "OBJECT_DESPAWN":
				return true;
			default:
				return false;
		}
	}

	static boolean usesAmount(String type)
	{
		return "HITSPLAT".equals(type) || "RESOURCE_CHANGE".equals(type)
			|| "PRAYER_CHANGE".equals(type);
	}
}
