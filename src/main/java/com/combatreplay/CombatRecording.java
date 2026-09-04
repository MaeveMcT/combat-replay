package com.combatreplay;

import java.util.ArrayList;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CombatRecording
{
	static final int FORMAT_VERSION = 5;
	private static final DateTimeFormatter NAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
		.withZone(ZoneId.systemDefault());

	final int formatVersion;
	final long startedAtEpochMillis;
	final long endedAtEpochMillis;
	final String name;
	final List<RecordedTick> ticks;

	CombatRecording(long startedAtEpochMillis)
	{
		this(FORMAT_VERSION, startedAtEpochMillis, 0L, null, new ArrayList<>());
	}

	private CombatRecording(int formatVersion, long startedAtEpochMillis, long endedAtEpochMillis,
		String name, List<RecordedTick> ticks)
	{
		this.formatVersion = formatVersion;
		this.startedAtEpochMillis = startedAtEpochMillis;
		this.endedAtEpochMillis = endedAtEpochMillis;
		this.name = name;
		this.ticks = ticks;
	}

	void add(RecordedTick tick)
	{
		ticks.add(tick);
	}

	void appendToLastTick(List<ContainerSnapshot> containers, List<RecordedEvent> events)
	{
		if (ticks.isEmpty() || containers.isEmpty() && events.isEmpty())
		{
			return;
		}
		int last = ticks.size() - 1;
		ticks.set(last, ticks.get(last).withAdditionalObservations(containers, events));
	}

	CombatRecording snapshot()
	{
		return new CombatRecording(formatVersion, startedAtEpochMillis, endedAtEpochMillis, name,
			Collections.unmodifiableList(new ArrayList<>(ticks)));
	}

	CombatRecording completed(long endedAt)
	{
		return new CombatRecording(formatVersion, startedAtEpochMillis, endedAt, generatedName(),
			Collections.unmodifiableList(new ArrayList<>(ticks)));
	}

	private String generatedName()
	{
		Map<String, Integer> npcTicks = new HashMap<>();
		Map<String, String> labels = new HashMap<>();
		String killedNpc = null;
		for (RecordedTick tick : ticks)
		{
			for (ActorSnapshot actor : tick.actors)
			{
				labels.put(actor.key, actor.label);
				if ("NPC".equals(actor.kind) && actor.targetKey != null)
				{
					npcTicks.merge(actor.label, 1, Integer::sum);
				}
			}
			for (RecordedEvent event : tick.events)
			{
				if ("DEATH".equals(event.type) && event.actorKey != null
					&& event.actorKey.startsWith("npc-"))
				{
					killedNpc = labels.get(event.actorKey);
				}
			}
		}
		String encounter = killedNpc;
		if (encounter == null)
		{
			encounter = npcTicks.entrySet().stream().max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey).orElse("Unknown encounter");
		}
		String result = killedNpc == null ? "Manual stop" : "Kill";
		int seconds = Math.max(0, ticks.size() * 3 / 5);
		return encounter + " — " + result + " — " + String.format("%d:%02d", seconds / 60, seconds % 60)
			+ " — " + NAME_TIME.format(Instant.ofEpochMilli(startedAtEpochMillis));
	}

	static CombatRecording restored(long startedAt, long endedAt, String name, List<RecordedTick> ticks)
	{
		return new CombatRecording(FORMAT_VERSION, startedAt, endedAt, name,
			Collections.unmodifiableList(new ArrayList<>(ticks)));
	}

	CombatRecording renamed(String newName)
	{
		return new CombatRecording(formatVersion, startedAtEpochMillis, endedAtEpochMillis,
			newName, Collections.unmodifiableList(new ArrayList<>(ticks)));
	}
}
