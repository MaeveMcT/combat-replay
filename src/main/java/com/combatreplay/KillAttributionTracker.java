package com.combatreplay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.HitsplatID;

/**
 * Conservatively correlates client-observed combat events. A result describes
 * evidence-supported attribution, not authoritative game-server kill credit.
 */
final class KillAttributionTracker
{
	private static final int TERMINAL_HIT_WINDOW_CYCLES = 30;
	private static final int PROJECTILE_WINDOW_CYCLES = 60;

	private final Map<String, RecordedEvent> lastDamageByVictim = new LinkedHashMap<>();
	private final List<RecordedEvent> projectiles = new ArrayList<>();

	void reset()
	{
		lastDamageByVictim.clear();
		projectiles.clear();
	}

	void observe(RecordedEvent event)
	{
		projectiles.removeIf(projectile -> event.gameCycle >= projectile.gameCycle
			&& event.gameCycle - projectile.gameCycle > PROJECTILE_WINDOW_CYCLES);
		lastDamageByVictim.values().removeIf(hit -> event.gameCycle >= hit.gameCycle
			&& event.gameCycle - hit.gameCycle > TERMINAL_HIT_WINDOW_CYCLES);
		if ("PROJECTILE".equals(event.type) && event.actorKey != null && event.targetKey != null)
		{
			projectiles.add(event);
		}
		else if ("HITSPLAT".equals(event.type) && event.actorKey != null
			&& event.value != null && event.value > 0
			&& !Integer.valueOf(HitsplatID.HEAL).equals(event.id))
		{
			lastDamageByVictim.put(event.actorKey, event);
		}
	}

	Attribution attribute(RecordedEvent death, String localActorKey)
	{
		RecordedEvent hit = lastDamageByVictim.remove(death.actorKey);
		if (hit == null || death.actorKey == null || death.gameCycle < hit.gameCycle
			|| death.gameCycle - hit.gameCycle > TERMINAL_HIT_WINDOW_CYCLES)
		{
			return null;
		}
		if (DamageAttribution.isLocalPlayer(hit.detail) && localActorKey != null)
		{
			return new Attribution(localActorKey, "terminal_hitsplat_local_v1",
				java.util.Arrays.asList(hit.eventId, death.eventId));
		}

		Map<String, RecordedEvent> candidates = new LinkedHashMap<>();
		for (RecordedEvent projectile : projectiles)
		{
			if (death.actorKey.equals(projectile.targetKey)
				&& projectile.gameCycle <= hit.gameCycle
				&& hit.gameCycle - projectile.gameCycle <= PROJECTILE_WINDOW_CYCLES
				&& !("other".equals(hit.detail) && projectile.actorKey.equals(localActorKey)))
			{
				candidates.put(projectile.actorKey, projectile);
			}
		}
		if (candidates.size() != 1)
		{
			return null;
		}
		Map.Entry<String, RecordedEvent> candidate = candidates.entrySet().iterator().next();
		return new Attribution(candidate.getKey(), "terminal_hitsplat_projectile_v1",
			java.util.Arrays.asList(candidate.getValue().eventId, hit.eventId, death.eventId));
	}

	static final class Attribution
	{
		final String killerKey;
		final String ruleId;
		final List<String> evidenceEventIds;

		private Attribution(String killerKey, String ruleId, List<String> evidenceEventIds)
		{
			this.killerKey = killerKey;
			this.ruleId = ruleId;
			this.evidenceEventIds = evidenceEventIds;
		}
	}
}
