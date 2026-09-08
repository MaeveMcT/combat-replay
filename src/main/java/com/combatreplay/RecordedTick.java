package com.combatreplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RecordedTick
{
	final int tick;
	final int gameCycle;
	final int clientTick;
	final long observedAtEpochMillis;
	final long elapsedMillis;
	final Integer world;
	final String viewKey;
	final int[][][] instanceTemplateChunks;
	final int contextPlane;
	final int baseX;
	final int baseY;
	final boolean instanced;
	final int hitpoints;
	final int maximumHitpoints;
	final int prayer;
	final int maximumPrayer;
	final List<ActorSnapshot> actors;
	final List<ItemSnapshot> inventory;
	final List<ItemSnapshot> equipment;
	final List<ContainerSnapshot> containerChanges;
	final List<RecordedEvent> events;
	final List<SceneTileSnapshot> sceneTiles;
	final List<SceneTileKey> sceneRemovals;
	final List<String> activePrayers;
	final List<ProjectileSnapshot> projectileUpserts;
	final List<String> projectileRemovals;

	RecordedTick(int tick, int gameCycle, int baseX, int baseY, boolean instanced,
		int hitpoints, int maximumHitpoints, int prayer, int maximumPrayer,
		List<ActorSnapshot> actors, List<ItemSnapshot> inventory, List<ItemSnapshot> equipment,
		List<ContainerSnapshot> containerChanges, List<RecordedEvent> events)
	{
		this(tick, gameCycle, baseX, baseY, instanced, hitpoints, maximumHitpoints, prayer,
			maximumPrayer, actors, inventory, equipment, containerChanges, events,
			Collections.emptyList(), Collections.emptyList());
	}

	RecordedTick(int tick, int gameCycle, int baseX, int baseY, boolean instanced,
		int hitpoints, int maximumHitpoints, int prayer, int maximumPrayer,
		List<ActorSnapshot> actors, List<ItemSnapshot> inventory, List<ItemSnapshot> equipment,
		List<ContainerSnapshot> containerChanges, List<RecordedEvent> events,
		List<SceneTileSnapshot> sceneTiles, List<String> activePrayers)
	{
		this(tick, gameCycle, tick, 0L, (long) tick * 600L, null, null, null,
			actors == null || actors.isEmpty() ? 0 : actors.get(0).plane,
			baseX, baseY, instanced, hitpoints, maximumHitpoints, prayer, maximumPrayer,
			actors, inventory, equipment, containerChanges, events, sceneTiles, activePrayers);
	}

	RecordedTick(int tick, int gameCycle, int clientTick, long observedAtEpochMillis,
		long elapsedMillis, Integer world, String viewKey, int[][][] instanceTemplateChunks,
		int contextPlane, int baseX, int baseY, boolean instanced, int hitpoints, int maximumHitpoints,
		int prayer, int maximumPrayer, List<ActorSnapshot> actors, List<ItemSnapshot> inventory,
		List<ItemSnapshot> equipment, List<ContainerSnapshot> containerChanges,
		List<RecordedEvent> events, List<SceneTileSnapshot> sceneTiles, List<String> activePrayers)
	{
		this(tick, gameCycle, clientTick, observedAtEpochMillis, elapsedMillis, world, viewKey,
			instanceTemplateChunks, contextPlane, baseX, baseY, instanced, hitpoints, maximumHitpoints,
			prayer, maximumPrayer, actors, inventory, equipment, containerChanges, events, sceneTiles,
			Collections.emptyList(), activePrayers);
	}

	RecordedTick(int tick, int gameCycle, int clientTick, long observedAtEpochMillis,
		long elapsedMillis, Integer world, String viewKey, int[][][] instanceTemplateChunks,
		int contextPlane, int baseX, int baseY, boolean instanced, int hitpoints, int maximumHitpoints,
		int prayer, int maximumPrayer, List<ActorSnapshot> actors, List<ItemSnapshot> inventory,
		List<ItemSnapshot> equipment, List<ContainerSnapshot> containerChanges,
		List<RecordedEvent> events, List<SceneTileSnapshot> sceneTiles,
		List<SceneTileKey> sceneRemovals, List<String> activePrayers)
	{
		this(tick, gameCycle, clientTick, observedAtEpochMillis, elapsedMillis, world, viewKey,
			instanceTemplateChunks, contextPlane, baseX, baseY, instanced, hitpoints, maximumHitpoints,
			prayer, maximumPrayer, actors, inventory, equipment, containerChanges, events, sceneTiles,
			sceneRemovals, activePrayers, Collections.emptyList(), Collections.emptyList());
	}

	RecordedTick(int tick, int gameCycle, int clientTick, long observedAtEpochMillis,
		long elapsedMillis, Integer world, String viewKey, int[][][] instanceTemplateChunks,
		int contextPlane, int baseX, int baseY, boolean instanced, int hitpoints, int maximumHitpoints,
		int prayer, int maximumPrayer, List<ActorSnapshot> actors, List<ItemSnapshot> inventory,
		List<ItemSnapshot> equipment, List<ContainerSnapshot> containerChanges,
		List<RecordedEvent> events, List<SceneTileSnapshot> sceneTiles,
		List<SceneTileKey> sceneRemovals, List<String> activePrayers,
		List<ProjectileSnapshot> projectileUpserts, List<String> projectileRemovals)
	{
		this.tick = tick;
		this.gameCycle = gameCycle;
		this.clientTick = clientTick;
		this.observedAtEpochMillis = observedAtEpochMillis;
		this.elapsedMillis = elapsedMillis;
		this.world = world;
		this.viewKey = viewKey;
		this.instanceTemplateChunks = copyChunks(instanceTemplateChunks);
		this.contextPlane = contextPlane;
		this.baseX = baseX;
		this.baseY = baseY;
		this.instanced = instanced;
		this.hitpoints = hitpoints;
		this.maximumHitpoints = maximumHitpoints;
		this.prayer = prayer;
		this.maximumPrayer = maximumPrayer;
		this.actors = immutableCopy(actors);
		this.inventory = immutableCopy(inventory);
		this.equipment = immutableCopy(equipment);
		this.containerChanges = immutableCopy(containerChanges);
		this.events = immutableCopy(events);
		this.sceneTiles = immutableCopy(sceneTiles == null ? Collections.emptyList() : sceneTiles);
		this.sceneRemovals = immutableCopy(sceneRemovals == null ? Collections.emptyList() : sceneRemovals);
		this.activePrayers = immutableCopy(activePrayers == null ? Collections.emptyList() : activePrayers);
		this.projectileUpserts = immutableCopy(projectileUpserts == null ? Collections.emptyList() : projectileUpserts);
		this.projectileRemovals = immutableCopy(projectileRemovals == null ? Collections.emptyList() : projectileRemovals);
	}

	RecordedTick withAdditionalObservations(List<ContainerSnapshot> additionalContainers,
		List<RecordedEvent> additionalEvents)
	{
		List<ContainerSnapshot> mergedContainers = new ArrayList<>(containerChanges);
		mergedContainers.addAll(additionalContainers);
		List<RecordedEvent> mergedEvents = new ArrayList<>(events);
		mergedEvents.addAll(additionalEvents);
		return new RecordedTick(tick, gameCycle, clientTick, observedAtEpochMillis, elapsedMillis,
			world, viewKey, instanceTemplateChunks, contextPlane, baseX, baseY, instanced, hitpoints,
			maximumHitpoints, prayer, maximumPrayer, actors, inventory, equipment,
			mergedContainers, mergedEvents, sceneTiles, sceneRemovals, activePrayers,
			projectileUpserts, projectileRemovals);
	}

	private static int[][][] copyChunks(int[][][] source)
	{
		if (source == null) return null;
		int[][][] copy = new int[source.length][][];
		for (int plane = 0; plane < source.length; plane++)
		{
			copy[plane] = new int[source[plane].length][];
			for (int x = 0; x < source[plane].length; x++)
			{
				copy[plane][x] = source[plane][x].clone();
			}
		}
		return copy;
	}

	private static <T> List<T> immutableCopy(List<T> values)
	{
		return values == null ? null : Collections.unmodifiableList(new ArrayList<>(values));
	}
}
