package com.combatreplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.runelite.api.Actor;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldPoint;

/** Assigns recording-local identities while live projectiles exist. */
final class ProjectileTracker
{
	private final Map<Projectile, String> keys = new IdentityHashMap<>();
	private final Map<String, ProjectileSnapshot> active = new LinkedHashMap<>();
	private final Map<String, ProjectileSnapshot> pendingUpserts = new LinkedHashMap<>();
	private int nextId = 1;

	Observation observe(Projectile projectile, String viewKey, Function<Actor, String> actorKey)
	{
		String existing = keys.get(projectile);
		boolean first = existing == null;
		if (first && active.size() >= 4096) return new Observation(null, false);
		String key = first ? "projectile-" + nextId++ : existing;
		if (first) keys.put(projectile, key);
		WorldPoint source = projectile.getSourcePoint();
		WorldPoint target = projectile.getTargetPoint();
		ProjectileSnapshot snapshot = new ProjectileSnapshot(key, projectile.getId(),
			actorKey.apply(projectile.getSourceActor()), actorKey.apply(projectile.getTargetActor()),
			viewKey, plane(source), x(source), y(source), plane(target), x(target), y(target),
			projectile.getStartCycle(), projectile.getEndCycle(), projectile.getRemainingCycles(),
			projectile.getStartHeight(), projectile.getEndHeight(), projectile.getSlope(),
			projectile.getOrientation());
		active.put(key, snapshot);
		pendingUpserts.put(key, snapshot);
		return new Observation(key, first);
	}

	Delta drain(int gameCycle, String currentViewKey)
	{
		List<String> removals = new ArrayList<>();
		for (ProjectileSnapshot snapshot : new ArrayList<>(active.values()))
		{
			if (gameCycle > snapshot.endCycle || !java.util.Objects.equals(currentViewKey, snapshot.viewKey))
			{
				active.remove(snapshot.key);
				pendingUpserts.remove(snapshot.key);
				removals.add(snapshot.key);
			}
		}
		keys.entrySet().removeIf(entry -> !active.containsKey(entry.getValue()));
		List<ProjectileSnapshot> upserts = new ArrayList<>(pendingUpserts.values());
		pendingUpserts.clear();
		return new Delta(upserts, removals);
	}

	void reset()
	{
		keys.clear();
		active.clear();
		pendingUpserts.clear();
		nextId = 1;
	}

	private static Integer x(WorldPoint point) { return point == null ? null : point.getX(); }
	private static Integer y(WorldPoint point) { return point == null ? null : point.getY(); }
	private static Integer plane(WorldPoint point) { return point == null ? null : point.getPlane(); }

	static final class Observation
	{
		final String key;
		final boolean first;
		Observation(String key, boolean first) { this.key = key; this.first = first; }
	}

	static final class Delta
	{
		final List<ProjectileSnapshot> upserts;
		final List<String> removals;
		Delta(List<ProjectileSnapshot> upserts, List<String> removals)
		{
			this.upserts = Collections.unmodifiableList(upserts);
			this.removals = Collections.unmodifiableList(removals);
		}
	}
}
