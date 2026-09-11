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
	final String projectileKey;
	final String viewKey;
	final Integer plane;
	final int sceneX;
	final int sceneY;
	final String coordinateSpace;
	final String detail;
	final String evidence;
	final String ruleId;
	final List<String> evidenceEventIds;
	final String actionKind;
	final String menuOption;
	final String menuTarget;
	final String menuAction;
	final Integer itemId;
	final Integer widgetId;
	final Integer objectId;
	final ObjectObservation objectObservation;
	final ActivitySignalObservation activitySignal;
	final ObservationCoverage observationCoverage;

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
			null, viewKey, plane, sceneX, sceneY, coordinateSpace, detail, evidence, ruleId, evidenceEventIds);
	}

	RecordedEvent(String eventId, String type, int gameCycle, Integer sequence,
		String actorKey, String targetKey, Integer id, Integer value, Integer fromDefinitionId,
		Integer toDefinitionId, String projectileKey, String viewKey, Integer plane, int sceneX, int sceneY,
		String coordinateSpace, String detail, String evidence, String ruleId,
		List<String> evidenceEventIds)
	{
		this(eventId, type, gameCycle, sequence, actorKey, targetKey, id, value,
			fromDefinitionId, toDefinitionId, projectileKey, viewKey, plane, sceneX, sceneY,
			coordinateSpace, detail, evidence, ruleId, evidenceEventIds, null, null, null,
			null, null, null, null, null);
	}

	RecordedEvent(String eventId, String type, int gameCycle, Integer sequence,
		String actorKey, String targetKey, Integer id, Integer value, Integer fromDefinitionId,
		Integer toDefinitionId, String projectileKey, String viewKey, Integer plane, int sceneX, int sceneY,
		String coordinateSpace, String detail, String evidence, String ruleId,
		List<String> evidenceEventIds, String actionKind, String menuOption, String menuTarget,
		String menuAction, Integer itemId, Integer widgetId, Integer objectId,
		ObjectObservation objectObservation)
	{
		this(eventId, type, gameCycle, sequence, actorKey, targetKey, id, value,
			fromDefinitionId, toDefinitionId, projectileKey, viewKey, plane, sceneX, sceneY,
			coordinateSpace, detail, evidence, ruleId, evidenceEventIds, actionKind, menuOption,
			menuTarget, menuAction, itemId, widgetId, objectId, objectObservation, null);
	}

	RecordedEvent(String eventId, String type, int gameCycle, Integer sequence,
		String actorKey, String targetKey, Integer id, Integer value, Integer fromDefinitionId,
		Integer toDefinitionId, String projectileKey, String viewKey, Integer plane, int sceneX, int sceneY,
		String coordinateSpace, String detail, String evidence, String ruleId,
		List<String> evidenceEventIds, String actionKind, String menuOption, String menuTarget,
		String menuAction, Integer itemId, Integer widgetId, Integer objectId,
		ObjectObservation objectObservation, ActivitySignalObservation activitySignal)
	{
		this(eventId, type, gameCycle, sequence, actorKey, targetKey, id, value,
			fromDefinitionId, toDefinitionId, projectileKey, viewKey, plane, sceneX, sceneY,
			coordinateSpace, detail, evidence, ruleId, evidenceEventIds, actionKind, menuOption,
			menuTarget, menuAction, itemId, widgetId, objectId, objectObservation, activitySignal, null);
	}

	RecordedEvent(String eventId, String type, int gameCycle, Integer sequence,
		String actorKey, String targetKey, Integer id, Integer value, Integer fromDefinitionId,
		Integer toDefinitionId, String projectileKey, String viewKey, Integer plane, int sceneX, int sceneY,
		String coordinateSpace, String detail, String evidence, String ruleId,
		List<String> evidenceEventIds, String actionKind, String menuOption, String menuTarget,
		String menuAction, Integer itemId, Integer widgetId, Integer objectId,
		ObjectObservation objectObservation, ActivitySignalObservation activitySignal,
		ObservationCoverage observationCoverage)
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
		this.projectileKey = projectileKey;
		this.viewKey = viewKey;
		this.plane = plane;
		this.sceneX = sceneX;
		this.sceneY = sceneY;
		this.coordinateSpace = coordinateSpace;
		this.detail = detail;
		this.evidence = evidence;
		this.ruleId = ruleId;
		this.evidenceEventIds = Collections.unmodifiableList(new ArrayList<>(evidenceEventIds));
		this.actionKind = actionKind;
		this.menuOption = menuOption;
		this.menuTarget = menuTarget;
		this.menuAction = menuAction;
		this.itemId = itemId;
		this.widgetId = widgetId;
		this.objectId = objectId;
		this.objectObservation = objectObservation;
		this.activitySignal = activitySignal;
		this.observationCoverage = observationCoverage;
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
