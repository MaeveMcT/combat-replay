package com.combatreplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RecordedTick
{
	final int tick;
	final int gameCycle;
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
	final List<String> activePrayers;

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
		this.tick = tick;
		this.gameCycle = gameCycle;
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
		this.activePrayers = immutableCopy(activePrayers == null ? Collections.emptyList() : activePrayers);
	}

	RecordedTick withAdditionalObservations(List<ContainerSnapshot> additionalContainers,
		List<RecordedEvent> additionalEvents)
	{
		List<ContainerSnapshot> mergedContainers = new ArrayList<>(containerChanges);
		mergedContainers.addAll(additionalContainers);
		List<RecordedEvent> mergedEvents = new ArrayList<>(events);
		mergedEvents.addAll(additionalEvents);
		return new RecordedTick(tick, gameCycle, baseX, baseY, instanced, hitpoints,
			maximumHitpoints, prayer, maximumPrayer, actors, inventory, equipment,
			mergedContainers, mergedEvents, sceneTiles, activePrayers);
	}

	private static <T> List<T> immutableCopy(List<T> values)
	{
		return Collections.unmodifiableList(new ArrayList<>(values));
	}
}
