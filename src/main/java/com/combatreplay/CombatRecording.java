package com.combatreplay;

import java.util.ArrayList;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CombatRecording
{
	static final int FORMAT_VERSION = 1;
	private static final DateTimeFormatter NAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
		.withZone(ZoneId.systemDefault());

	final int formatVersion;
	final String recordingId;
	final String pluginVersion;
	final String runeLiteVersion;
	final Integer gameRevision;
	final long startedAtEpochMillis;
	final long endedAtEpochMillis;
	final String name;
	final List<RecordedTick> ticks;
	final Map<Integer, NpcDefinitionMetadata> npcDefinitions;
	final Map<Integer, ObjectDefinitionMetadata> objectDefinitions;

	CombatRecording(long startedAtEpochMillis)
	{
		this(startedAtEpochMillis, "development", "unknown", null);
	}

	CombatRecording(long startedAtEpochMillis, String pluginVersion, String runeLiteVersion,
		Integer gameRevision)
	{
		this(FORMAT_VERSION, UUID.randomUUID().toString(), pluginVersion, runeLiteVersion,
			gameRevision, startedAtEpochMillis, 0L, null, new ArrayList<>(),
			new HashMap<>(), new HashMap<>());
	}

	private CombatRecording(int formatVersion, String recordingId, String pluginVersion,
		String runeLiteVersion, Integer gameRevision, long startedAtEpochMillis,
		long endedAtEpochMillis, String name, List<RecordedTick> ticks,
		Map<Integer, NpcDefinitionMetadata> npcDefinitions,
		Map<Integer, ObjectDefinitionMetadata> objectDefinitions)
	{
		this.formatVersion = formatVersion;
		this.recordingId = recordingId;
		this.pluginVersion = pluginVersion;
		this.runeLiteVersion = runeLiteVersion;
		this.gameRevision = gameRevision;
		this.startedAtEpochMillis = startedAtEpochMillis;
		this.endedAtEpochMillis = endedAtEpochMillis;
		this.name = name;
		this.ticks = ticks;
		this.npcDefinitions = npcDefinitions;
		this.objectDefinitions = objectDefinitions;
	}

	void add(RecordedTick tick)
	{
		ticks.add(tick);
	}

	void recordNpcDefinition(int id, NpcDefinitionMetadata metadata)
	{
		if (id >= 0 && metadata != null
			&& (npcDefinitions.containsKey(id) || npcDefinitions.size() < 4096))
			npcDefinitions.putIfAbsent(id, metadata);
	}

	void recordObjectDefinition(int id, ObjectDefinitionMetadata metadata)
	{
		if (id >= 0 && metadata != null
			&& (objectDefinitions.containsKey(id) || objectDefinitions.size() < 8192))
			objectDefinitions.putIfAbsent(id, metadata);
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
		return copy(endedAtEpochMillis, name);
	}

	CombatRecording completed(long endedAt)
	{
		return copy(endedAt, generatedName());
	}

	private CombatRecording copy(long endedAt, String copiedName)
	{
		return new CombatRecording(formatVersion, recordingId, pluginVersion, runeLiteVersion,
			gameRevision, startedAtEpochMillis, endedAt, copiedName,
			Collections.unmodifiableList(new ArrayList<>(ticks)),
			Collections.unmodifiableMap(new HashMap<>(npcDefinitions)),
			Collections.unmodifiableMap(new HashMap<>(objectDefinitions)));
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
				if ("NPC".equals(actor.kind) && actor.label != null && actor.targetKey != null)
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

	static CombatRecording restored(String recordingId, String pluginVersion, String runeLiteVersion,
		Integer gameRevision, long startedAt, long endedAt, String name, List<RecordedTick> ticks)
	{
		return restored(recordingId, pluginVersion, runeLiteVersion, gameRevision, startedAt,
			endedAt, name, ticks, Collections.emptyMap(), Collections.emptyMap());
	}

	static CombatRecording restored(String recordingId, String pluginVersion, String runeLiteVersion,
		Integer gameRevision, long startedAt, long endedAt, String name, List<RecordedTick> ticks,
		Map<Integer, NpcDefinitionMetadata> npcDefinitions,
		Map<Integer, ObjectDefinitionMetadata> objectDefinitions)
	{
		return new CombatRecording(FORMAT_VERSION, recordingId, pluginVersion, runeLiteVersion,
			gameRevision, startedAt, endedAt, name,
			Collections.unmodifiableList(new ArrayList<>(ticks)),
			Collections.unmodifiableMap(new HashMap<>(npcDefinitions)),
			Collections.unmodifiableMap(new HashMap<>(objectDefinitions)));
	}

	static CombatRecording restored(long startedAt, long endedAt, String name, List<RecordedTick> ticks)
	{
		return restored(UUID.randomUUID().toString(), "development", "unknown", null,
			startedAt, endedAt, name, ticks);
	}

	CombatRecording renamed(String newName)
	{
		return copy(endedAtEpochMillis, newName);
	}
}
