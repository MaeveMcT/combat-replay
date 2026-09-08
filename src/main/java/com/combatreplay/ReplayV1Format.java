package com.combatreplay;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Canonical, schema-shaped v1 recording encoder. */
final class ReplayV1Format
{
    private static final int NOMINAL_TICK_MILLIS = 600;

    private ReplayV1Format()
    {
    }

    static JsonObject encode(CombatRecording recording)
    {
        JsonObject root = new JsonObject();
        root.addProperty("format_version", 1);
        root.addProperty("recording_id", recording.recordingId);
        root.addProperty("name", recording.name == null ? "Combat replay" : recording.name);
        root.addProperty("started_at", timestamp(recording.startedAtEpochMillis));
        root.addProperty("ended_at", timestamp(recording.endedAtEpochMillis));
        root.add("producer", producer(recording));
        root.add("capture", capture(recording));
        root.add("dictionaries", dictionaries(recording));
        root.add("ticks", ticks(recording));
        return root;
    }

    private static JsonObject producer(CombatRecording recording)
    {
        JsonObject producer = new JsonObject();
        producer.addProperty("plugin_name", "Combat Replay");
        producer.addProperty("plugin_version", recording.pluginVersion);
        producer.addProperty("runelite_version", recording.runeLiteVersion);
        producer.addProperty("java_version", System.getProperty("java.version"));
        if (recording.gameRevision == null) producer.add("game_revision", JsonNull.INSTANCE);
        else producer.addProperty("game_revision", recording.gameRevision);
        return producer;
    }

    private static JsonObject capture(CombatRecording recording)
    {
        JsonObject capture = new JsonObject();
        capture.addProperty("nominal_tick_millis", NOMINAL_TICK_MILLIS);
        String localKey = localActorKey(recording);
        addNullable(capture, "local_actor_key", localKey);
        Integer initialWorld = recording.ticks.isEmpty() ? null : recording.ticks.get(0).world;
        if (initialWorld == null) capture.add("initial_world", JsonNull.INSTANCE);
        else capture.addProperty("initial_world", initialWorld);
        JsonArray capabilities = new JsonArray();
        for (String capability : new String[]{
            "player_names", "actor_local_coordinates", "scene_tiles", "instance_templates",
            "inventory", "equipment", "active_prayers", "container_changes", "event_confidence",
            "npc_definitions", "object_definitions", "projectile_lifecycle", "local_combat_stats",
            "actor_movement_animations", "ground_items", "action_attempts"
        })
        {
            capabilities.add(capability);
        }
        capture.add("capabilities", capabilities);
        return capture;
    }

    private static JsonObject dictionaries(CombatRecording recording)
    {
        Map<Integer, String> names = new LinkedHashMap<>();
        for (RecordedTick tick : recording.ticks)
        {
            collectNames(names, tick.inventory);
            collectNames(names, tick.equipment);
            for (ActorSnapshot actor : tick.actors)
            {
                collectNames(names, actor.visibleEquipment);
            }
            for (ContainerSnapshot container : tick.containerChanges)
            {
                collectNames(names, container.items);
            }
            for (GroundItemSnapshot item : tick.groundItemUpserts)
            {
                if (item.itemName != null && !item.itemName.isEmpty()) names.putIfAbsent(item.itemId, item.itemName);
            }
        }
        JsonObject items = new JsonObject();
        names.forEach((id, name) -> items.addProperty(Integer.toString(id), name));
        JsonObject dictionaries = new JsonObject();
        dictionaries.add("items", items);
        JsonObject npcs = new JsonObject();
        new java.util.TreeMap<>(recording.npcDefinitions).forEach((id, metadata) ->
        {
            JsonObject value = new JsonObject();
            addNullable(value, "name", metadata.name);
            addNullableInteger(value, "combat_level", metadata.combatLevel);
            value.addProperty("size", metadata.size);
            npcs.add(Integer.toString(id), value);
        });
        dictionaries.add("npcs", npcs);
        JsonObject objects = new JsonObject();
        new java.util.TreeMap<>(recording.objectDefinitions).forEach((id, metadata) ->
        {
            JsonObject value = new JsonObject();
            addNullable(value, "name", metadata.name);
            addNullableInteger(value, "effective_definition_id", metadata.effectiveDefinitionId);
            value.addProperty("size_x", metadata.sizeX);
            value.addProperty("size_y", metadata.sizeY);
            addNullableInteger(value, "map_icon_id", metadata.mapIconId);
            addNullableInteger(value, "map_scene_id", metadata.mapSceneId);
            objects.add(Integer.toString(id), value);
        });
        dictionaries.add("objects", objects);
        return dictionaries;
    }

    private static JsonArray ticks(CombatRecording recording)
    {
        JsonArray result = new JsonArray();
        Map<String, ActorSnapshot> previousActors = new LinkedHashMap<>();
        int nextEventId = 1;
        LocalCombatState previousCombatState = null;
        for (int index = 0; index < recording.ticks.size(); index++)
        {
            RecordedTick tick = recording.ticks.get(index);
            JsonObject encoded = new JsonObject();
            encoded.addProperty("index", index);
            encoded.addProperty("keyframe", index == 0);
            encoded.add("sync", sync(recording, tick, index));
            encoded.add("context", context(tick));
            encoded.add("local_state", localState(tick, previousCombatState));
            encoded.add("actors", actors(tick, previousActors));
            encoded.add("scene", scene(tick));
            encoded.add("projectiles", projectiles(tick));
            encoded.add("ground_items", groundItems(tick));
            encoded.add("container_changes", containers(tick.containerChanges));
            JsonArray events = new JsonArray();
            for (RecordedEvent event : tick.events)
            {
                events.add(event(event, tick, nextEventId++));
            }
            encoded.add("events", events);
            result.add(encoded);
            previousActors.clear();
            for (ActorSnapshot actor : tick.actors)
            {
                previousActors.put(actor.key, actor);
            }
            previousCombatState = tick.combatState;
        }
        return result;
    }

    private static JsonObject sync(CombatRecording recording, RecordedTick tick, int index)
    {
        long elapsed = tick.observedAtEpochMillis == 0L
            ? (long) index * NOMINAL_TICK_MILLIS : tick.elapsedMillis;
        long observedAt = tick.observedAtEpochMillis == 0L
            ? recording.startedAtEpochMillis + elapsed : tick.observedAtEpochMillis;
        JsonObject sync = new JsonObject();
        sync.addProperty("game_cycle", tick.gameCycle);
        sync.addProperty("client_tick", tick.clientTick);
        sync.addProperty("observed_at", timestamp(observedAt));
        sync.addProperty("elapsed_millis", elapsed);
        return sync;
    }

    private static JsonObject context(RecordedTick tick)
    {
        JsonObject context = new JsonObject();
        if (tick.world == null) context.add("world", JsonNull.INSTANCE);
        else context.addProperty("world", tick.world);
        context.addProperty("view_key", viewKey(tick));
        context.addProperty("plane", tick.contextPlane);
        context.addProperty("base_x", tick.baseX);
        context.addProperty("base_y", tick.baseY);
        context.addProperty("instanced", tick.instanced);
        if (tick.instanceTemplateChunks == null)
        {
            context.add("instance_template_chunks", JsonNull.INSTANCE);
        }
        else
        {
            context.add("instance_template_chunks", chunks(tick.instanceTemplateChunks));
        }
        return context;
    }

    private static JsonObject localState(RecordedTick tick, LocalCombatState previousCombatState)
    {
        JsonObject state = new JsonObject();
        addAvailableInteger(state, "hitpoints", tick.hitpoints);
        addAvailableInteger(state, "base_hitpoints", tick.maximumHitpoints);
        addAvailableInteger(state, "prayer", tick.prayer);
        addAvailableInteger(state, "base_prayer", tick.maximumPrayer);
        state.add("inventory", tick.inventory == null ? JsonNull.INSTANCE : items(tick.inventory));
        state.add("equipment", tick.equipment == null ? JsonNull.INSTANCE : items(tick.equipment));
        JsonArray prayers = new JsonArray();
        for (String prayer : tick.activePrayers)
        {
            prayers.add(prayer);
        }
        state.add("active_prayers", prayers);
        if (tick.combatState != null && !tick.combatState.equals(previousCombatState))
        {
            JsonObject stats = new JsonObject();
            stats.add("attack", skill(tick.combatState.attackCurrent, tick.combatState.attackBase));
            stats.add("strength", skill(tick.combatState.strengthCurrent, tick.combatState.strengthBase));
            stats.add("defence", skill(tick.combatState.defenceCurrent, tick.combatState.defenceBase));
            stats.add("ranged", skill(tick.combatState.rangedCurrent, tick.combatState.rangedBase));
            stats.add("magic", skill(tick.combatState.magicCurrent, tick.combatState.magicBase));
            state.add("combat_stats", stats);
            state.addProperty("run_energy", tick.combatState.runEnergyHundredths);
            state.addProperty("special_attack_energy", tick.combatState.specialAttackEnergyTenths);
            state.addProperty("special_attack_enabled", tick.combatState.specialAttackEnabled);
        }
        return state;
    }

    private static JsonObject skill(int current, int base)
    {
        JsonObject skill = new JsonObject();
        skill.addProperty("current", current);
        skill.addProperty("base", base);
        return skill;
    }

    private static JsonObject actors(RecordedTick tick, Map<String, ActorSnapshot> previous)
    {
        JsonObject operations = new JsonObject();
        JsonArray upsert = new JsonArray();
        Map<String, ActorSnapshot> current = new LinkedHashMap<>();
        for (ActorSnapshot actor : tick.actors)
        {
            current.put(actor.key, actor);
            upsert.add(actor(actor));
        }
        JsonArray remove = new JsonArray();
        for (String key : previous.keySet())
        {
            if (!current.containsKey(key))
            {
                remove.add(key);
            }
        }
        operations.add("upsert", upsert);
        operations.add("remove", remove);
        return operations;
    }

    private static JsonObject actor(ActorSnapshot actor)
    {
        JsonObject value = new JsonObject();
        value.addProperty("key", actor.key);
        addNullable(value, "kind", actor.kind == null ? null : actor.kind.toLowerCase(Locale.ROOT));
        addNullable(value, "display_name", actor.label);
        value.addProperty("is_local_player", actor.isLocalPlayer);
        addAvailableInteger(value, "npc_id", actor.npcId);
        value.addProperty("view_key", actor.viewKey);
        value.addProperty("world_x", actor.worldX);
        value.addProperty("world_y", actor.worldY);
        value.addProperty("scene_x", actor.sceneX);
        value.addProperty("scene_y", actor.sceneY);
        value.addProperty("local_x", actor.localX);
        value.addProperty("local_y", actor.localY);
        value.addProperty("plane", actor.plane);
        value.addProperty("size", actor.size);
        value.addProperty("orientation", actor.orientation);
        addAvailableInteger(value, "animation_id", actor.animation);
        addAvailableInteger(value, "pose_animation_id", actor.poseAnimation);
        addAvailableInteger(value, "health_ratio", actor.healthRatio);
        addAvailableInteger(value, "health_scale", actor.healthScale);
        addNullable(value, "target_key", actor.targetKey);
        value.addProperty("dead", actor.dead);
        value.add("visible_equipment", actor.visibleEquipment == null
            ? JsonNull.INSTANCE : items(actor.visibleEquipment));
        addNullable(value, "overhead_icon", actor.overheadIcon);
        addNullableInteger(value, "combat_level", actor.combatLevel);
        if (actor.movementAnimations != null)
        {
            JsonObject movement = new JsonObject();
            addAvailableInteger(movement, "idle", actor.movementAnimations.idle);
            addAvailableInteger(movement, "idle_rotate_left", actor.movementAnimations.idleRotateLeft);
            addAvailableInteger(movement, "idle_rotate_right", actor.movementAnimations.idleRotateRight);
            addAvailableInteger(movement, "walk", actor.movementAnimations.walk);
            addAvailableInteger(movement, "walk_rotate_left", actor.movementAnimations.walkRotateLeft);
            addAvailableInteger(movement, "walk_rotate_right", actor.movementAnimations.walkRotateRight);
            addAvailableInteger(movement, "walk_rotate_180", actor.movementAnimations.walkRotate180);
            addAvailableInteger(movement, "run", actor.movementAnimations.run);
            value.add("movement_animations", movement);
        }
        return value;
    }

    private static JsonObject scene(RecordedTick tick)
    {
        JsonObject operations = new JsonObject();
        JsonArray upsert = new JsonArray();
        for (SceneTileSnapshot tile : tick.sceneTiles)
        {
            JsonObject value = new JsonObject();
            value.addProperty("view_key", tile.viewKey);
            value.addProperty("plane", tile.plane);
            value.addProperty("x", tile.x);
            value.addProperty("y", tile.y);
            value.addProperty("height", tile.height);
            addAvailableInteger(value, "terrain_rgb", tile.terrainColor);
            value.addProperty("collision_flags", tile.collisionFlags);
            addAvailableInteger(value, "wall_object_id", tile.wallId);
            addAvailableInteger(value, "ground_object_id", tile.groundObjectId);
            addAvailableInteger(value, "decorative_object_id", tile.decorativeObjectId);
            JsonArray objects = new JsonArray();
            for (int id : tile.gameObjectIds)
            {
                objects.add(id);
            }
            value.add("game_object_ids", objects);
            upsert.add(value);
        }
        operations.add("upsert", upsert);
        JsonArray remove = new JsonArray();
        for (SceneTileKey key : tick.sceneRemovals)
        {
            JsonObject value = new JsonObject();
            value.addProperty("view_key", key.viewKey);
            value.addProperty("plane", key.plane);
            value.addProperty("x", key.x);
            value.addProperty("y", key.y);
            remove.add(value);
        }
        operations.add("remove", remove);
        return operations;
    }

    private static JsonObject groundItems(RecordedTick tick)
    {
        JsonObject operations = new JsonObject();
        JsonArray upsert = new JsonArray();
        for (GroundItemSnapshot item : tick.groundItemUpserts)
        {
            JsonObject value = new JsonObject();
            value.addProperty("key", item.key);
            value.addProperty("item_id", item.itemId);
            value.addProperty("quantity", item.quantity);
            value.addProperty("view_key", item.viewKey);
            value.addProperty("plane", item.plane);
            value.addProperty("x", item.x);
            value.addProperty("y", item.y);
            value.addProperty("observed_cycle", item.observedCycle);
            upsert.add(value);
        }
        JsonArray remove = new JsonArray();
        for (String key : tick.groundItemRemovals) remove.add(key);
        operations.add("upsert", upsert);
        operations.add("remove", remove);
        return operations;
    }

    private static JsonObject projectiles(RecordedTick tick)
    {
        JsonObject operations = new JsonObject();
        JsonArray upsert = new JsonArray();
        for (ProjectileSnapshot projectile : tick.projectileUpserts)
        {
            JsonObject value = new JsonObject();
            value.addProperty("key", projectile.key);
            value.addProperty("definition_id", projectile.definitionId);
            addNullable(value, "source_actor_key", projectile.sourceActorKey);
            addNullable(value, "target_actor_key", projectile.targetActorKey);
            value.add("source_point", projectilePoint(projectile.viewKey, projectile.sourcePlane,
                projectile.sourceX, projectile.sourceY));
            value.add("target_point", projectilePoint(projectile.viewKey, projectile.targetPlane,
                projectile.targetX, projectile.targetY));
            value.addProperty("start_cycle", projectile.startCycle);
            value.addProperty("end_cycle", projectile.endCycle);
            value.addProperty("remaining_cycles", projectile.remainingCycles);
            value.addProperty("start_height", projectile.startHeight);
            value.addProperty("end_height", projectile.endHeight);
            value.addProperty("slope", projectile.slope);
            value.addProperty("orientation", projectile.orientation);
            upsert.add(value);
        }
        JsonArray remove = new JsonArray();
        for (String key : tick.projectileRemovals) remove.add(key);
        operations.add("upsert", upsert);
        operations.add("remove", remove);
        return operations;
    }

    private static com.google.gson.JsonElement projectilePoint(String viewKey, Integer plane,
        Integer x, Integer y)
    {
        if (plane == null || x == null || y == null) return JsonNull.INSTANCE;
        JsonObject point = new JsonObject();
        addNullable(point, "view_key", viewKey);
        point.addProperty("plane", plane);
        point.addProperty("x", x);
        point.addProperty("y", y);
        return point;
    }

    private static JsonArray containers(List<ContainerSnapshot> changes)
    {
        JsonArray result = new JsonArray();
        for (ContainerSnapshot change : changes)
        {
            JsonObject value = new JsonObject();
            value.addProperty("container", change.container.toLowerCase(Locale.ROOT));
            value.addProperty("game_cycle", change.gameCycle);
            value.add("items", items(change.items));
            result.add(value);
        }
        return result;
    }

    private static JsonObject event(RecordedEvent event, RecordedTick tick, int eventId)
    {
        JsonObject value = new JsonObject();
        value.addProperty("event_id", event.eventId == null ? "event-" + eventId : event.eventId);
        value.addProperty("type", eventType(event.type));
        value.addProperty("game_cycle", event.gameCycle);
        if (event.sequence == null) value.add("sequence", JsonNull.INSTANCE);
        else value.addProperty("sequence", event.sequence);
        addNullable(value, "actor_key", event.actorKey);
        addNullable(value, "target_key", event.targetKey);
        addNullableInteger(value, "definition_id", event.id);
        addNullableInteger(value, "amount", event.value);
        if (event.projectileKey != null || "PROJECTILE".equals(event.type))
            addNullable(value, "projectile_key", event.projectileKey);
        if (event.fromDefinitionId != null || event.toDefinitionId != null || "NPC_CHANGED".equals(event.type))
        {
            addNullableInteger(value, "from_definition_id", event.fromDefinitionId);
            addNullableInteger(value, "to_definition_id", event.toDefinitionId);
        }
        if (event.actionKind != null || "ACTION_ATTEMPT".equals(event.type))
        {
            addNullable(value, "action_kind", event.actionKind);
            addNullable(value, "menu_option", event.menuOption);
            addNullable(value, "menu_target", event.menuTarget);
            addNullable(value, "menu_action", event.menuAction);
            addNullableInteger(value, "item_id", event.itemId);
            addNullableInteger(value, "widget_id", event.widgetId);
            addNullableInteger(value, "object_id", event.objectId);
        }
        if (event.objectObservation != null)
        {
            addNullable(value, "object_category", event.objectObservation.category);
            addNullableInteger(value, "effective_definition_id", event.objectObservation.effectiveDefinitionId);
            addNullableInteger(value, "object_orientation", event.objectObservation.orientation);
            addNullableInteger(value, "object_configuration", event.objectObservation.configuration);
            addNullableInteger(value, "size_x", event.objectObservation.sizeX);
            addNullableInteger(value, "size_y", event.objectObservation.sizeY);
        }
        if (event.sceneX < 0 || event.sceneY < 0)
        {
            value.add("location", JsonNull.INSTANCE);
        }
        else
        {
            JsonObject location = new JsonObject();
            addNullable(location, "view_key", event.viewKey == null ? viewKey(tick) : event.viewKey);
            if (event.plane == null) location.addProperty("plane", tick.contextPlane);
            else location.addProperty("plane", event.plane);
            location.addProperty("x", event.sceneX);
            location.addProperty("y", event.sceneY);
            location.addProperty("coordinate_space", event.coordinateSpace == null ? "scene" : event.coordinateSpace);
            value.add("location", location);
        }
        addNullable(value, "detail", event.detail);
        value.addProperty("evidence", event.evidence);
        addNullable(value, "rule_id", event.ruleId);
        JsonArray evidenceIds = new JsonArray();
        for (String evidenceId : event.evidenceEventIds) evidenceIds.add(evidenceId);
        value.add("evidence_event_ids", evidenceIds);
        return value;
    }

    private static String eventType(String type)
    {
        String normalized = type.toLowerCase(Locale.ROOT);
        if (normalized.endsWith("_spawn")) return normalized.contains("object") ? "object_spawn" : "actor_spawn";
        if (normalized.endsWith("_despawn")) return normalized.contains("object") ? "object_despawn" : "actor_despawn";
        if ("interacting".equals(normalized)) return "interaction_change";
        if ("npc_changed".equals(normalized)) return "npc_transform";
        if ("actor_graphic".equals(normalized)) return "graphic";
        return normalized;
    }

    private static JsonArray chunks(int[][][] source)
    {
        JsonArray planes = new JsonArray();
        for (int[][] plane : source)
        {
            JsonArray columns = new JsonArray();
            for (int[] column : plane)
            {
                JsonArray rows = new JsonArray();
                for (int chunk : column) rows.add(chunk);
                columns.add(rows);
            }
            planes.add(columns);
        }
        return planes;
    }

    private static JsonArray items(List<ItemSnapshot> source)
    {
        JsonArray result = new JsonArray();
        if (source != null)
        {
            for (ItemSnapshot item : source)
            {
                JsonObject value = new JsonObject();
                value.addProperty("slot", item.slot);
                value.addProperty("item_id", item.itemId);
                value.addProperty("quantity", item.quantity);
                result.add(value);
            }
        }
        return result;
    }

    private static void collectNames(Map<Integer, String> names, List<ItemSnapshot> items)
    {
        if (items != null)
        {
            for (ItemSnapshot item : items)
            {
                if (item.name != null && !item.name.isEmpty())
                {
                    names.putIfAbsent(item.itemId, item.name);
                }
            }
        }
    }

    private static String localActorKey(CombatRecording recording)
    {
        if (recording.ticks.isEmpty()) return null;
        for (ActorSnapshot actor : recording.ticks.get(0).actors)
        {
            if (actor.isLocalPlayer) return actor.key;
        }
        return null;
    }

    private static String viewKey(RecordedTick tick)
    {
        if (tick.viewKey != null) return tick.viewKey;
        if (!tick.actors.isEmpty()) return "view-" + tick.actors.get(0).worldViewId;
        if (!tick.sceneTiles.isEmpty()) return "view-" + tick.sceneTiles.get(0).worldViewId;
        return "view-0";
    }

    static CombatRecording decode(JsonObject root)
    {
        if (root == null || integer(root, "format_version", -1) != 1)
        {
            throw new IllegalArgumentException("Unsupported combat recording format");
        }
        ReplayV1SemanticValidator.validate(root);
        JsonObject dictionaries = root.getAsJsonObject("dictionaries");
        Map<Integer, String> itemNames = itemNames(dictionaries);
        Map<Integer, NpcDefinitionMetadata> npcDefinitions = npcDefinitions(dictionaries);
        Map<Integer, ObjectDefinitionMetadata> objectDefinitions = objectDefinitions(dictionaries);
        Map<String, ActorSnapshot> actors = new LinkedHashMap<>();
        Map<String, SceneTileSnapshot> scene = new LinkedHashMap<>();
        Map<String, ProjectileSnapshot> projectiles = new LinkedHashMap<>();
        Map<String, GroundItemSnapshot> groundItems = new LinkedHashMap<>();
        List<RecordedTick> ticks = new java.util.ArrayList<>();
        Integer world = null;
        String viewKey = null;
        int plane = 0, baseX = 0, baseY = 0;
        boolean instanced = false;
        int[][][] templateChunks = null;
        int hitpoints = -1, baseHitpoints = -1, prayer = -1, basePrayer = -1;
        List<ItemSnapshot> inventory = null, equipment = null;
        List<String> activePrayers = null;
        LocalCombatState combatState = null;
        JsonArray encodedTicks = root.getAsJsonArray("ticks");
        for (int index = 0; index < encodedTicks.size(); index++)
        {
            JsonObject encoded = encodedTicks.get(index).getAsJsonObject();
            if (integer(encoded, "index", -1) != index || index == 0 && !encoded.get("keyframe").getAsBoolean())
            {
                throw new IllegalArgumentException("Invalid tick sequence");
            }
            JsonObject context = object(encoded, "context");
            if (context != null)
            {
                world = nullableInteger(context, "world", world);
                viewKey = nullableString(context, "view_key", viewKey);
                plane = nullableIntValue(context, "plane", plane, 0);
                baseX = nullableIntValue(context, "base_x", baseX, 0);
                baseY = nullableIntValue(context, "base_y", baseY, 0);
                instanced = nullableBooleanValue(context, "instanced", instanced, false);
                if (context.has("instance_template_chunks"))
                {
                    templateChunks = context.get("instance_template_chunks").isJsonNull()
                        ? null : decodeChunks(context.getAsJsonArray("instance_template_chunks"));
                }
            }
            JsonObject local = object(encoded, "local_state");
            if (local != null)
            {
                hitpoints = nullableIntValue(local, "hitpoints", hitpoints, -1);
                baseHitpoints = nullableIntValue(local, "base_hitpoints", baseHitpoints, -1);
                prayer = nullableIntValue(local, "prayer", prayer, -1);
                basePrayer = nullableIntValue(local, "base_prayer", basePrayer, -1);
                if (local.has("inventory")) inventory = decodeItems(local.get("inventory"), itemNames);
                if (local.has("equipment")) equipment = decodeItems(local.get("equipment"), itemNames);
                if (local.has("active_prayers")) activePrayers = strings(local.get("active_prayers"));
                if (local.has("combat_stats"))
                {
                    if (local.get("combat_stats").isJsonNull()) combatState = null;
                    else combatState = decodeCombatState(local, combatState);
                }
            }
            JsonObject actorOperations = encoded.getAsJsonObject("actors");
            for (com.google.gson.JsonElement removed : actorOperations.getAsJsonArray("remove"))
            {
                actors.remove(removed.getAsString());
            }
            for (com.google.gson.JsonElement upsert : actorOperations.getAsJsonArray("upsert"))
            {
                JsonObject delta = upsert.getAsJsonObject();
                String key = delta.get("key").getAsString();
                ActorSnapshot old = actors.get(key);
                if (old == null) requireCompleteActor(delta);
                actors.put(key, applyActor(delta, old, itemNames));
            }
            JsonObject sceneOperations = encoded.getAsJsonObject("scene");
            List<SceneTileKey> sceneRemovals = new java.util.ArrayList<>();
            for (com.google.gson.JsonElement removed : sceneOperations.getAsJsonArray("remove"))
            {
                JsonObject key = removed.getAsJsonObject();
                sceneRemovals.add(new SceneTileKey(key.get("view_key").getAsString(),
                    key.get("plane").getAsInt(), key.get("x").getAsInt(), key.get("y").getAsInt()));
                scene.remove(tileKey(key));
            }
            for (com.google.gson.JsonElement upsert : sceneOperations.getAsJsonArray("upsert"))
            {
                SceneTileSnapshot tile = decodeTile(upsert.getAsJsonObject());
                scene.put(tile.mapKey(), tile);
            }
            List<String> projectileRemovals = new java.util.ArrayList<>();
            List<ProjectileSnapshot> projectileUpserts = new java.util.ArrayList<>();
            JsonObject projectileOperations = object(encoded, "projectiles");
            if (projectileOperations != null)
            {
                for (com.google.gson.JsonElement removed : projectileOperations.getAsJsonArray("remove"))
                {
                    String key = removed.getAsString();
                    projectileRemovals.add(key);
                    projectiles.remove(key);
                }
                for (com.google.gson.JsonElement upsert : projectileOperations.getAsJsonArray("upsert"))
                {
                    ProjectileSnapshot projectile = decodeProjectile(upsert.getAsJsonObject());
                    projectileUpserts.add(projectile);
                    projectiles.put(projectile.key, projectile);
                }
            }
            List<String> groundItemRemovals = new java.util.ArrayList<>();
            List<GroundItemSnapshot> groundItemUpserts = new java.util.ArrayList<>();
            JsonObject groundItemOperations = object(encoded, "ground_items");
            if (groundItemOperations != null)
            {
                for (com.google.gson.JsonElement removed : groundItemOperations.getAsJsonArray("remove"))
                {
                    String key = removed.getAsString();
                    groundItemRemovals.add(key);
                    groundItems.remove(key);
                }
                for (com.google.gson.JsonElement upsert : groundItemOperations.getAsJsonArray("upsert"))
                {
                    JsonObject value = upsert.getAsJsonObject();
                    int itemId = value.get("item_id").getAsInt();
                    GroundItemSnapshot item = new GroundItemSnapshot(value.get("key").getAsString(),
                        itemId, itemNames.get(itemId), value.get("quantity").getAsInt(),
                        value.get("view_key").getAsString(), value.get("plane").getAsInt(),
                        value.get("x").getAsInt(), value.get("y").getAsInt(),
                        value.get("observed_cycle").getAsInt());
                    groundItemUpserts.add(item);
                    groundItems.put(item.key, item);
                }
            }
            JsonObject sync = encoded.getAsJsonObject("sync");
            List<ContainerSnapshot> containers = decodeContainers(encoded.get("container_changes"), itemNames);
            List<RecordedEvent> events = decodeEvents(encoded.getAsJsonArray("events"));
            ticks.add(new RecordedTick(index, nullableIntValue(sync, "game_cycle", -1, -1),
                nullableIntValue(sync, "client_tick", -1, -1),
                Instant.parse(sync.get("observed_at").getAsString()).toEpochMilli(),
                sync.get("elapsed_millis").getAsLong(), world, viewKey, templateChunks,
                plane, baseX, baseY, instanced, hitpoints, baseHitpoints, prayer, basePrayer,
                new java.util.ArrayList<>(actors.values()), inventory, equipment, containers, events,
                new java.util.ArrayList<>(scene.values()), sceneRemovals, activePrayers,
                projectileUpserts, projectileRemovals, combatState,
                groundItemUpserts, groundItemRemovals));
        }
        JsonObject producer = root.getAsJsonObject("producer");
        return CombatRecording.restored(root.get("recording_id").getAsString(),
            producer.get("plugin_version").getAsString(), producer.get("runelite_version").getAsString(),
            nullableInteger(producer, "game_revision", null),
            Instant.parse(root.get("started_at").getAsString()).toEpochMilli(),
            Instant.parse(root.get("ended_at").getAsString()).toEpochMilli(),
            root.get("name").getAsString(), ticks, npcDefinitions, objectDefinitions);
    }

    private static void requireCompleteActor(JsonObject actor)
    {
        for (String property : new String[]{
            "kind", "display_name", "is_local_player", "npc_id", "view_key", "world_x", "world_y",
            "scene_x", "scene_y", "local_x", "local_y", "plane", "size", "orientation",
            "animation_id", "pose_animation_id", "health_ratio", "health_scale", "target_key", "dead",
            "visible_equipment", "overhead_icon"
        })
        {
            if (!actor.has(property))
            {
                throw new IllegalArgumentException("Incomplete first actor appearance: " + property);
            }
        }
    }

    private static ActorSnapshot applyActor(JsonObject delta, ActorSnapshot old, Map<Integer, String> names)
    {
        String key = delta.get("key").getAsString();
        String kind = nullableString(delta, "kind", old == null ? null : old.kind);
        if (kind != null) kind = kind.toUpperCase(Locale.ROOT);
        String label = nullableString(delta, "display_name", old == null ? null : old.label);
        boolean local = nullableBooleanValue(delta, "is_local_player", old != null && old.isLocalPlayer, false);
        int npcId = nullableIntValue(delta, "npc_id", old == null ? -1 : old.npcId, -1);
        String actorView = nullableString(delta, "view_key", old == null ? null : old.viewKey);
        int viewId = delta.has("view_key")
            ? actorView == null ? 0 : actorView.hashCode()
            : old == null ? 0 : old.worldViewId;
        List<ItemSnapshot> visible = old == null ? null : old.visibleEquipment;
        if (delta.has("visible_equipment")) visible = decodeItems(delta.get("visible_equipment"), names);
        MovementAnimations movement = old == null ? null : old.movementAnimations;
        if (delta.has("movement_animations")) movement = decodeMovement(object(delta, "movement_animations"));
        return new ActorSnapshot(key, kind, label, local, npcId,
            nullableIntValue(delta, "world_x", old == null ? 0 : old.worldX, 0),
            nullableIntValue(delta, "world_y", old == null ? 0 : old.worldY, 0),
            nullableIntValue(delta, "scene_x", old == null ? 0 : old.sceneX, 0),
            nullableIntValue(delta, "scene_y", old == null ? 0 : old.sceneY, 0),
            nullableIntValue(delta, "local_x", old == null ? 0 : old.localX, 0),
            nullableIntValue(delta, "local_y", old == null ? 0 : old.localY, 0),
            nullableIntValue(delta, "plane", old == null ? 0 : old.plane, 0), viewId, actorView,
            nullableIntValue(delta, "size", old == null ? 1 : old.size, 1),
            nullableIntValue(delta, "orientation", old == null ? 0 : old.orientation, 0),
            nullableIntValue(delta, "animation_id", old == null ? -1 : old.animation, -1),
            nullableIntValue(delta, "pose_animation_id", old == null ? -1 : old.poseAnimation, -1),
            nullableIntValue(delta, "health_ratio", old == null ? -1 : old.healthRatio, -1),
            nullableIntValue(delta, "health_scale", old == null ? -1 : old.healthScale, -1),
            nullableString(delta, "target_key", old == null ? null : old.targetKey),
            nullableBooleanValue(delta, "dead", old != null && old.dead, false), visible,
            nullableString(delta, "overhead_icon", old == null ? null : old.overheadIcon),
            nullableInteger(delta, "combat_level", old == null ? null : old.combatLevel), movement);
    }

    private static MovementAnimations decodeMovement(JsonObject value)
    {
        if (value == null) return null;
        return new MovementAnimations(nullableIntValue(value, "idle", -1, -1),
            nullableIntValue(value, "idle_rotate_left", -1, -1),
            nullableIntValue(value, "idle_rotate_right", -1, -1),
            nullableIntValue(value, "walk", -1, -1),
            nullableIntValue(value, "walk_rotate_left", -1, -1),
            nullableIntValue(value, "walk_rotate_right", -1, -1),
            nullableIntValue(value, "walk_rotate_180", -1, -1),
            nullableIntValue(value, "run", -1, -1));
    }

    private static SceneTileSnapshot decodeTile(JsonObject value)
    {
        JsonArray encodedObjects = value.getAsJsonArray("game_object_ids");
        int[] objects = new int[encodedObjects.size()];
        for (int index = 0; index < objects.length; index++) objects[index] = encodedObjects.get(index).getAsInt();
        String view = value.get("view_key").getAsString();
        return new SceneTileSnapshot(view.hashCode(), view, value.get("plane").getAsInt(),
            value.get("x").getAsInt(), value.get("y").getAsInt(), value.get("height").getAsInt(),
            nullableIntValue(value, "terrain_rgb", -1, -1), value.get("collision_flags").getAsInt(),
            nullableIntValue(value, "wall_object_id", -1, -1),
            nullableIntValue(value, "ground_object_id", -1, -1),
            nullableIntValue(value, "decorative_object_id", -1, -1), objects);
    }

    private static ObjectObservation decodeObjectObservation(JsonObject value)
    {
        if (!value.has("object_category")) return null;
        return new ObjectObservation(nullableString(value, "object_category", null),
            nullableInteger(value, "effective_definition_id", null),
            nullableInteger(value, "object_orientation", null),
            nullableInteger(value, "object_configuration", null),
            nullableInteger(value, "size_x", null), nullableInteger(value, "size_y", null));
    }

    private static LocalCombatState decodeCombatState(JsonObject local, LocalCombatState previous)
    {
        JsonObject stats = local.getAsJsonObject("combat_stats");
        JsonObject attack = stats.getAsJsonObject("attack");
        JsonObject strength = stats.getAsJsonObject("strength");
        JsonObject defence = stats.getAsJsonObject("defence");
        JsonObject ranged = stats.getAsJsonObject("ranged");
        JsonObject magic = stats.getAsJsonObject("magic");
        return new LocalCombatState(attack.get("current").getAsInt(), attack.get("base").getAsInt(),
            strength.get("current").getAsInt(), strength.get("base").getAsInt(),
            defence.get("current").getAsInt(), defence.get("base").getAsInt(),
            ranged.get("current").getAsInt(), ranged.get("base").getAsInt(),
            magic.get("current").getAsInt(), magic.get("base").getAsInt(),
            nullableIntValue(local, "run_energy", previous == null ? -1 : previous.runEnergyHundredths, -1),
            nullableIntValue(local, "special_attack_energy", previous == null ? -1 : previous.specialAttackEnergyTenths, -1),
            nullableBooleanValue(local, "special_attack_enabled",
                previous != null && previous.specialAttackEnabled, false));
    }

    private static ProjectileSnapshot decodeProjectile(JsonObject value)
    {
        JsonObject source = object(value, "source_point");
        JsonObject target = object(value, "target_point");
        return new ProjectileSnapshot(value.get("key").getAsString(), value.get("definition_id").getAsInt(),
            nullableString(value, "source_actor_key", null), nullableString(value, "target_actor_key", null),
            source != null ? nullableString(source, "view_key", null)
                : target != null ? nullableString(target, "view_key", null) : null,
            source == null ? null : source.get("plane").getAsInt(),
            source == null ? null : source.get("x").getAsInt(),
            source == null ? null : source.get("y").getAsInt(),
            target == null ? null : target.get("plane").getAsInt(),
            target == null ? null : target.get("x").getAsInt(),
            target == null ? null : target.get("y").getAsInt(),
            value.get("start_cycle").getAsInt(), value.get("end_cycle").getAsInt(),
            value.get("remaining_cycles").getAsInt(), value.get("start_height").getAsInt(),
            value.get("end_height").getAsInt(), value.get("slope").getAsInt(),
            value.get("orientation").getAsInt());
    }

    private static List<ContainerSnapshot> decodeContainers(com.google.gson.JsonElement element,
        Map<Integer, String> names)
    {
        List<ContainerSnapshot> result = new java.util.ArrayList<>();
        if (element == null || element.isJsonNull()) return result;
        for (com.google.gson.JsonElement encoded : element.getAsJsonArray())
        {
            JsonObject value = encoded.getAsJsonObject();
            result.add(new ContainerSnapshot(value.get("container").getAsString().toUpperCase(Locale.ROOT),
                nullableIntValue(value, "game_cycle", -1, -1), decodeItems(value.get("items"), names)));
        }
        return result;
    }

    private static List<RecordedEvent> decodeEvents(JsonArray encoded)
    {
        List<RecordedEvent> result = new java.util.ArrayList<>();
        for (com.google.gson.JsonElement item : encoded)
        {
            JsonObject value = item.getAsJsonObject();
            JsonObject location = object(value, "location");
            result.add(new RecordedEvent(value.get("event_id").getAsString(),
                value.get("type").getAsString().toUpperCase(Locale.ROOT),
                nullableIntValue(value, "game_cycle", -1, -1), nullableInteger(value, "sequence", null),
                nullableString(value, "actor_key", null), nullableString(value, "target_key", null),
                nullableInteger(value, "definition_id", null), nullableInteger(value, "amount", null),
                nullableInteger(value, "from_definition_id", null),
                nullableInteger(value, "to_definition_id", null),
                nullableString(value, "projectile_key", null),
                location == null ? null : nullableString(location, "view_key", null),
                location == null ? null : nullableInteger(location, "plane", null),
                location == null ? -1 : location.get("x").getAsInt(),
                location == null ? -1 : location.get("y").getAsInt(),
                location == null ? null : location.get("coordinate_space").getAsString(),
                nullableString(value, "detail", null), value.get("evidence").getAsString(),
                nullableString(value, "rule_id", null), strings(value.get("evidence_event_ids")),
                nullableString(value, "action_kind", null), nullableString(value, "menu_option", null),
                nullableString(value, "menu_target", null), nullableString(value, "menu_action", null),
                nullableInteger(value, "item_id", null), nullableInteger(value, "widget_id", null),
                nullableInteger(value, "object_id", null), decodeObjectObservation(value)));
        }
        return result;
    }

    private static Map<Integer, NpcDefinitionMetadata> npcDefinitions(JsonObject dictionaries)
    {
        Map<Integer, NpcDefinitionMetadata> result = new LinkedHashMap<>();
        JsonObject values = object(dictionaries, "npcs");
        if (values == null) return result;
        for (Map.Entry<String, com.google.gson.JsonElement> entry : values.entrySet())
        {
            JsonObject value = entry.getValue().getAsJsonObject();
            result.put(Integer.parseInt(entry.getKey()), new NpcDefinitionMetadata(
                nullableString(value, "name", null), nullableInteger(value, "combat_level", null),
                value.get("size").getAsInt()));
        }
        return result;
    }

    private static Map<Integer, ObjectDefinitionMetadata> objectDefinitions(JsonObject dictionaries)
    {
        Map<Integer, ObjectDefinitionMetadata> result = new LinkedHashMap<>();
        JsonObject values = object(dictionaries, "objects");
        if (values == null) return result;
        for (Map.Entry<String, com.google.gson.JsonElement> entry : values.entrySet())
        {
            JsonObject value = entry.getValue().getAsJsonObject();
            result.put(Integer.parseInt(entry.getKey()), new ObjectDefinitionMetadata(
                nullableString(value, "name", null), nullableInteger(value, "effective_definition_id", null),
                value.get("size_x").getAsInt(), value.get("size_y").getAsInt(),
                nullableInteger(value, "map_icon_id", null), nullableInteger(value, "map_scene_id", null)));
        }
        return result;
    }

    private static Map<Integer, String> itemNames(JsonObject dictionaries)
    {
        Map<Integer, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : dictionaries.getAsJsonObject("items").entrySet())
        {
            result.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsString());
        }
        return result;
    }

    private static List<ItemSnapshot> decodeItems(com.google.gson.JsonElement element, Map<Integer, String> names)
    {
        if (element == null || element.isJsonNull()) return null;
        List<ItemSnapshot> result = new java.util.ArrayList<>();
        for (com.google.gson.JsonElement encoded : element.getAsJsonArray())
        {
            JsonObject value = encoded.getAsJsonObject();
            int id = value.get("item_id").getAsInt();
            result.add(new ItemSnapshot(value.get("slot").getAsInt(), id,
                value.get("quantity").getAsInt(), names.get(id)));
        }
        return result;
    }

    private static List<String> strings(com.google.gson.JsonElement element)
    {
        if (element == null || element.isJsonNull()) return null;
        List<String> result = new java.util.ArrayList<>();
        for (com.google.gson.JsonElement item : element.getAsJsonArray()) result.add(item.getAsString());
        return result;
    }

    private static int[][][] decodeChunks(JsonArray planes)
    {
        int[][][] result = new int[planes.size()][][];
        for (int plane = 0; plane < planes.size(); plane++)
        {
            JsonArray columns = planes.get(plane).getAsJsonArray();
            result[plane] = new int[columns.size()][];
            for (int x = 0; x < columns.size(); x++)
            {
                JsonArray rows = columns.get(x).getAsJsonArray();
                result[plane][x] = new int[rows.size()];
                for (int y = 0; y < rows.size(); y++) result[plane][x][y] = rows.get(y).getAsInt();
            }
        }
        return result;
    }

    private static String tileKey(JsonObject value)
    {
        return value.get("view_key").getAsString() + ":" + value.get("plane").getAsInt()
            + ":" + value.get("x").getAsInt() + ":" + value.get("y").getAsInt();
    }

    private static JsonObject object(JsonObject parent, String name)
    {
        return !parent.has(name) || parent.get(name).isJsonNull() ? null : parent.getAsJsonObject(name);
    }

    private static int integer(JsonObject object, String name, int fallback)
    {
        return object.has(name) ? object.get(name).getAsInt() : fallback;
    }

    private static Integer nullableInteger(JsonObject object, String name, Integer previous)
    {
        if (!object.has(name)) return previous;
        return object.get(name).isJsonNull() ? null : object.get(name).getAsInt();
    }

    private static int nullableIntValue(JsonObject object, String name, int previous, int cleared)
    {
        if (!object.has(name)) return previous;
        return object.get(name).isJsonNull() ? cleared : object.get(name).getAsInt();
    }

    private static String nullableString(JsonObject object, String name, String previous)
    {
        if (!object.has(name)) return previous;
        return object.get(name).isJsonNull() ? null : object.get(name).getAsString();
    }

    private static boolean nullableBooleanValue(JsonObject object, String name, boolean previous, boolean cleared)
    {
        if (!object.has(name)) return previous;
        return object.get(name).isJsonNull() ? cleared : object.get(name).getAsBoolean();
    }

    private static String timestamp(long epochMillis)
    {
        return Instant.ofEpochMilli(epochMillis).toString();
    }

    private static void addAvailableInteger(JsonObject object, String name, int value)
    {
        if (value < 0) object.add(name, JsonNull.INSTANCE);
        else object.addProperty(name, value);
    }

    private static void addNullableInteger(JsonObject object, String name, Integer value)
    {
        if (value == null) object.add(name, JsonNull.INSTANCE);
        else object.addProperty(name, value);
    }

    private static void addNullable(JsonObject object, String name, String value)
    {
        if (value == null) object.add(name, JsonNull.INSTANCE);
        else object.addProperty(name, value);
    }
}
