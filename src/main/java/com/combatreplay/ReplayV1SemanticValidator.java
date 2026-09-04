package com.combatreplay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Validates v1 invariants which cannot be expressed by JSON Schema. */
final class ReplayV1SemanticValidator
{
	private ReplayV1SemanticValidator()
	{
	}

	static void validate(JsonObject root)
	{
		Instant startedAt = Instant.parse(root.get("started_at").getAsString());
		Instant endedAt = Instant.parse(root.get("ended_at").getAsString());
		require(!endedAt.isBefore(startedAt), "Recording ends before it starts");

		JsonObject capture = root.getAsJsonObject("capture");
		String localKey = nullableString(capture.get("local_actor_key"));
		Set<String> capabilities = strings(capture.getAsJsonArray("capabilities"));
		Map<String, Boolean> actors = new HashMap<>();
		Set<String> eventIds = new HashSet<>();
		Set<String> evidenceReferences = new HashSet<>();
		long previousElapsed = -1L;
		Integer previousClientTick = null;
		Integer previousGameCycle = null;
		JsonArray ticks = root.getAsJsonArray("ticks");

		for (int index = 0; index < ticks.size(); index++)
		{
			JsonObject tick = ticks.get(index).getAsJsonObject();
			require(tick.get("index").getAsInt() == index, "Tick indexes must be contiguous");
			require(index != 0 || tick.get("keyframe").getAsBoolean(), "Tick zero must be a keyframe");
			JsonObject sync = tick.getAsJsonObject("sync");
			long elapsed = sync.get("elapsed_millis").getAsLong();
			require(elapsed >= previousElapsed, "Elapsed time must not regress");
			previousElapsed = elapsed;
			previousClientTick = monotonic(sync.get("client_tick"), previousClientTick, "Client tick");
			previousGameCycle = monotonic(sync.get("game_cycle"), previousGameCycle, "Game cycle");

			JsonObject context = object(tick, "context");
			if (index == 0)
			{
				require(context != null, "Tick zero must contain context");
				require(hasAll(context, "world", "view_key", "plane", "base_x", "base_y", "instanced",
					"instance_template_chunks"), "Tick zero context is incomplete");
				require(equalNullableIntegers(capture.get("initial_world"), context.get("world")),
					"Initial world does not match tick zero context");
			}
			if (context != null && context.has("instance_template_chunks")
				&& !context.get("instance_template_chunks").isJsonNull())
			{
				require(capabilities.contains("instance_templates"), "Instance templates lack a capability");
				require(context.has("instanced") && context.get("instanced").getAsBoolean(),
					"Instance templates require instanced context");
			}

			JsonObject actorOperations = tick.getAsJsonObject("actors");
			Set<String> actorKeys = new HashSet<>();
			for (JsonElement removed : actorOperations.getAsJsonArray("remove"))
			{
				String key = removed.getAsString();
				require(actorKeys.add(key), "Duplicate actor operation");
				actors.remove(key);
			}
			Map<String, String> targets = new HashMap<>();
			for (JsonElement element : actorOperations.getAsJsonArray("upsert"))
			{
				JsonObject actor = element.getAsJsonObject();
				String key = actor.get("key").getAsString();
				require(actorKeys.add(key), "Duplicate actor operation");
				boolean isLocal = actor.has("is_local_player")
					? !actor.get("is_local_player").isJsonNull() && actor.get("is_local_player").getAsBoolean()
					: Boolean.TRUE.equals(actors.get(key));
				actors.put(key, isLocal);
				if (isLocal) require(key.equals(localKey), "Local actor does not match capture metadata");
				if (actor.has("display_name") && !actor.get("display_name").isJsonNull())
					require(capabilities.contains("player_names") || !isPlayer(actor), "Player name lacks a capability");
				if ((present(actor, "local_x") || present(actor, "local_y")))
					require(capabilities.contains("actor_local_coordinates"), "Actor local coordinates lack a capability");
				if (present(actor, "target_key")) targets.put(key, actor.get("target_key").getAsString());
			}
			for (String target : targets.values())
				require(actors.containsKey(target), "Actor target refers to an absent actor");
			long localActors = actors.values().stream().filter(Boolean.TRUE::equals).count();
			require(localActors <= 1, "Multiple local actors are active");

			JsonObject localState = object(tick, "local_state");
			if (index == 0)
				require(localState != null && hasAll(localState, "hitpoints", "base_hitpoints", "prayer",
					"base_prayer", "inventory", "equipment", "active_prayers"),
					"Tick zero local state is incomplete");
			if (localState != null)
			{
				require(localKey != null && Boolean.TRUE.equals(actors.get(localKey)),
					"Local state has no local actor observation");
				capabilityForPresent(localState, "inventory", "inventory", capabilities);
				capabilityForPresent(localState, "equipment", "equipment", capabilities);
				capabilityForPresent(localState, "active_prayers", "active_prayers", capabilities);
			}

			validateScene(tick.getAsJsonObject("scene"), capabilities);
			JsonArray containers = tick.getAsJsonArray("container_changes");
			if (containers.size() > 0) require(capabilities.contains("container_changes"),
				"Container changes lack a capability");
			for (JsonElement element : containers)
			{
				String container = element.getAsJsonObject().get("container").getAsString();
				require(capabilities.contains(container), "Container data lacks a capability");
			}
			JsonArray events = tick.getAsJsonArray("events");
			if (events.size() > 0) require(capabilities.contains("event_confidence"),
				"Event confidence lacks a capability");
			for (JsonElement element : events)
			{
				JsonObject event = element.getAsJsonObject();
				String eventId = event.get("event_id").getAsString();
				require(eventIds.add(eventId), "Duplicate event ID");
				for (JsonElement reference : event.getAsJsonArray("evidence_event_ids"))
					evidenceReferences.add(reference.getAsString());
			}
		}
		require(localKey == null || Boolean.TRUE.equals(actors.get(localKey)) || observedLocal(ticks, localKey),
			"Capture local actor was never observed");
		for (String reference : evidenceReferences)
			require(eventIds.contains(reference), "Evidence reference does not resolve");
	}

	private static void validateScene(JsonObject operations, Set<String> capabilities)
	{
		Set<String> keys = new HashSet<>();
		for (JsonElement removed : operations.getAsJsonArray("remove"))
			require(keys.add(tileKey(removed.getAsJsonObject())), "Duplicate tile operation");
		for (JsonElement upsert : operations.getAsJsonArray("upsert"))
			require(keys.add(tileKey(upsert.getAsJsonObject())), "Duplicate tile operation");
		if (!keys.isEmpty()) require(capabilities.contains("scene_tiles"), "Scene data lacks a capability");
	}

	private static Integer monotonic(JsonElement element, Integer previous, String label)
	{
		if (element.isJsonNull()) return previous;
		int current = element.getAsInt();
		require(previous == null || current >= previous, label + " must not regress");
		return current;
	}

	private static boolean observedLocal(JsonArray ticks, String localKey)
	{
		for (JsonElement tick : ticks)
			for (JsonElement actor : tick.getAsJsonObject().getAsJsonObject("actors").getAsJsonArray("upsert"))
				if (localKey.equals(actor.getAsJsonObject().get("key").getAsString())
					&& present(actor.getAsJsonObject(), "is_local_player")
					&& actor.getAsJsonObject().get("is_local_player").getAsBoolean()) return true;
		return false;
	}

	private static boolean isPlayer(JsonObject actor)
	{
		return !actor.has("kind") || actor.get("kind").isJsonNull()
			|| "player".equals(actor.get("kind").getAsString());
	}

	private static void capabilityForPresent(JsonObject object, String property, String capability,
		Set<String> capabilities)
	{
		if (present(object, property)) require(capabilities.contains(capability),
			property + " lacks a capability");
	}

	private static boolean present(JsonObject object, String property)
	{
		return object.has(property) && !object.get(property).isJsonNull();
	}

	private static boolean hasAll(JsonObject object, String... properties)
	{
		for (String property : properties) if (!object.has(property)) return false;
		return true;
	}

	private static JsonObject object(JsonObject parent, String property)
	{
		return !parent.has(property) || parent.get(property).isJsonNull()
			? null : parent.getAsJsonObject(property);
	}

	private static String nullableString(JsonElement element)
	{
		return element == null || element.isJsonNull() ? null : element.getAsString();
	}

	private static boolean equalNullableIntegers(JsonElement left, JsonElement right)
	{
		if (left.isJsonNull() || right.isJsonNull()) return left.isJsonNull() && right.isJsonNull();
		return left.getAsInt() == right.getAsInt();
	}

	private static Set<String> strings(JsonArray values)
	{
		Set<String> result = new HashSet<>();
		for (JsonElement value : values) result.add(value.getAsString());
		return result;
	}

	private static String tileKey(JsonObject tile)
	{
		return tile.get("view_key").getAsString() + ":" + tile.get("plane").getAsInt()
			+ ":" + tile.get("x").getAsInt() + ":" + tile.get("y").getAsInt();
	}

	private static void require(boolean condition, String message)
	{
		if (!condition) throw new IllegalArgumentException(message);
	}
}
