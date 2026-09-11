package com.combatreplay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReplayV1FormatTest
{
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void writesVersionOneEnvelopeWithStableRecordingIdentity() throws Exception
    {
        CombatRecording completed = completedRecording();
        RecordingStore store = new RecordingStore(new Gson(), temporary.newFolder().toPath());

        Path path = store.save(completed);
        JsonObject json;
        try (Reader reader = Files.newBufferedReader(path))
        {
            json = new JsonParser().parse(reader).getAsJsonObject();
        }

        assertEquals(1, json.get("format_version").getAsInt());
        assertEquals(completed.recordingId, json.get("recording_id").getAsString());
        assertNotNull(UUID.fromString(completed.recordingId));
        assertTrue(json.has("producer"));
        assertTrue(json.has("capture"));
        assertTrue(json.has("dictionaries"));
        assertTrue(json.getAsJsonArray("ticks").get(0).getAsJsonObject().get("keyframe").getAsBoolean());
    }

    @Test
    public void preservesExactPlayerNamesAndMarksLocalPlayerExplicitly()
    {
        ActorSnapshot local = player("player-1", "Alice", true);
        ActorSnapshot teammate = player("player-2", "Bob", false);
        CombatRecording recording = new CombatRecording(1_000L);
        recording.add(new RecordedTick(0, 100, 3200, 3200, false,
            90, 99, 70, 70, java.util.Arrays.asList(local, teammate), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));

        JsonArray actors = ReplayV1Format.encode(recording.completed(1_600L))
            .getAsJsonArray("ticks").get(0).getAsJsonObject()
            .getAsJsonObject("actors").getAsJsonArray("upsert");

        assertEquals("Alice", actors.get(0).getAsJsonObject().get("display_name").getAsString());
        assertTrue(actors.get(0).getAsJsonObject().get("is_local_player").getAsBoolean());
        assertEquals("Bob", actors.get(1).getAsJsonObject().get("display_name").getAsString());
        assertTrue(!actors.get(1).getAsJsonObject().get("is_local_player").getAsBoolean());
    }

    @Test
    public void roundTripsRecordingLocalDefinitionsAndNpcTransformEvidence()
    {
        CombatRecording recording = new CombatRecording(1_000L);
        recording.recordNpcDefinition(9037, new NpcDefinitionMetadata("Corrupted Hunllef", 674, 1));
        recording.recordNpcDefinition(9036, new NpcDefinitionMetadata("Corrupted Hunllef (tornado phase)", 674, 1));
        recording.recordObjectDefinition(36048,
            new ObjectDefinitionMetadata(null, 36049, 2, 1, null, 12));
        RecordedEvent transform = new RecordedEvent("event-1", "NPC_CHANGED", 100, null,
            "player-1", null, 9036, null, 9037, 9036, null, null, null, -1, -1, null,
            null, "observed", null, Collections.emptyList());
        RecordedEvent attempt = new RecordedEvent("event-2", "ACTION_ATTEMPT", 100, null,
            "player-1", null, null, null, null, null, null, null, null, -1, -1, null,
            null, "observed", null, Collections.emptyList(), "equip_item", "Wield",
            "Abyssal whip", "ITEM_SECOND_OPTION", 4151, 9764864, null, null);
        recording.add(new RecordedTick(0, 100, 3200, 3200, false,
            90, 99, 70, 70, Collections.singletonList(player("player-1", "Alice", true)),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            java.util.Arrays.asList(transform, attempt)));

        JsonObject encoded = ReplayV1Format.encode(recording.completed(1_600L));
        CombatRecording decoded = ReplayV1Format.decode(encoded);

        assertEquals("Corrupted Hunllef", decoded.npcDefinitions.get(9037).name);
        assertNull(decoded.objectDefinitions.get(36048).name);
        assertEquals(Integer.valueOf(36049), decoded.objectDefinitions.get(36048).effectiveDefinitionId);
        assertEquals(Integer.valueOf(9037), decoded.ticks.get(0).events.get(0).fromDefinitionId);
        assertEquals(Integer.valueOf(9036), decoded.ticks.get(0).events.get(0).toDefinitionId);
        assertEquals("equip_item", decoded.ticks.get(0).events.get(1).actionKind);
        assertEquals(Integer.valueOf(4151), decoded.ticks.get(0).events.get(1).itemId);
        assertTrue(encoded.getAsJsonObject("capture").getAsJsonArray("capabilities")
            .contains(new com.google.gson.JsonPrimitive("npc_definitions")));
    }

    @Test
    public void preservesDeclaredCapabilitiesAcrossDecodeAndEncode() throws Exception
    {
        JsonObject fixture = fixture();
        JsonArray declared = fixture.getAsJsonObject("capture").getAsJsonArray("capabilities");
        declared.add("future_activity_observations");

        JsonArray roundTripped = ReplayV1Format.encode(ReplayV1Format.decode(fixture))
            .getAsJsonObject("capture").getAsJsonArray("capabilities");

        assertEquals(declared, roundTripped);
    }

    @Test
    public void preservesNeutralActivitySignalObservationsAcrossDecodeAndEncode() throws Exception
    {
        JsonObject fixture = fixture();
        fixture.getAsJsonObject("capture").getAsJsonArray("capabilities").add("activity_signals");
        JsonObject signal = new JsonObject();
        signal.addProperty("varbit_id", 9177);
        signal.add("value", com.google.gson.JsonNull.INSTANCE);
        signal.addProperty("observation", "initial");
        JsonObject event = fixture.getAsJsonArray("ticks").get(0).getAsJsonObject()
            .getAsJsonArray("events").get(0).getAsJsonObject();
        event.addProperty("type", "activity_signal");
        event.add("activity_signal", signal);

        JsonObject roundTrippedEvent = ReplayV1Format.encode(ReplayV1Format.decode(fixture))
            .getAsJsonArray("ticks").get(0).getAsJsonObject()
            .getAsJsonArray("events").get(0).getAsJsonObject();

        assertEquals(signal, roundTrippedEvent.getAsJsonObject("activity_signal"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsActivitySignalObservationWithoutCapability() throws Exception
    {
        JsonObject fixture = fixture();
        JsonObject signal = new JsonObject();
        signal.addProperty("varbit_id", 9177);
        signal.addProperty("value", 0);
        signal.addProperty("observation", "initial");
        JsonObject event = fixture.getAsJsonArray("ticks").get(0).getAsJsonObject()
            .getAsJsonArray("events").get(0).getAsJsonObject();
        event.addProperty("type", "activity_signal");
        event.add("activity_signal", signal);

        ReplayV1Format.decode(fixture);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsActivitySignalOutsideCandidateAllowlist() throws Exception
    {
        JsonObject fixture;
        try (Reader reader = new java.io.InputStreamReader(
            getClass().getResourceAsStream("/replay-v1/valid/candidate-activity-signals.json")))
        {
            fixture = new JsonParser().parse(reader).getAsJsonObject();
        }
        fixture.getAsJsonArray("ticks").get(0).getAsJsonObject().getAsJsonArray("events")
            .get(2).getAsJsonObject().getAsJsonObject("activity_signal")
            .addProperty("varbit_id", 9179);

        ReplayV1Format.decode(fixture);
    }

    @Test
    public void decodesDeterministicProjectileLifecycle() throws Exception
    {
        JsonObject fixture;
        try (Reader reader = new java.io.InputStreamReader(
            getClass().getResourceAsStream("/replay-v1/valid/viewer-features.json")))
        {
            fixture = new JsonParser().parse(reader).getAsJsonObject();
        }

        CombatRecording recording = ReplayV1Format.decode(fixture);
        assertEquals(Integer.valueOf(126), recording.ticks.get(0).actors.get(0).combatLevel);
        assertEquals(808, recording.ticks.get(0).actors.get(0).movementAnimations.idle);
        assertEquals(808, recording.ticks.get(0).actors.get(0).movementAnimations.walk);
        assertEquals(117, recording.ticks.get(0).combatState.strengthCurrent);
        assertEquals(116, recording.ticks.get(1).combatState.strengthCurrent);
        assertEquals(116, recording.ticks.get(2).combatState.strengthCurrent);
        assertEquals(6100, recording.ticks.get(2).combatState.runEnergyHundredths);
        Map<String, ProjectileSnapshot> state = new LinkedHashMap<>();
        Map<String, GroundItemSnapshot> groundState = new LinkedHashMap<>();
        int[] expectedCounts = {1, 1, 0};
        for (int index = 0; index < recording.ticks.size(); index++)
        {
            RecordedTick tick = recording.ticks.get(index);
            for (String key : tick.projectileRemovals) state.remove(key);
            for (ProjectileSnapshot projectile : tick.projectileUpserts) state.put(projectile.key, projectile);
            assertEquals(expectedCounts[index], state.size());
            for (String key : tick.groundItemRemovals) groundState.remove(key);
            for (GroundItemSnapshot item : tick.groundItemUpserts) groundState.put(item.key, item);
            assertEquals(expectedCounts[index], groundState.size());
        }
    }

    @Test
    public void decodesGenericFightAnalysisFixture() throws Exception
    {
        JsonObject fixture;
        try (Reader reader = new java.io.InputStreamReader(
            getClass().getResourceAsStream("/replay-v1/valid/generic-fights.json")))
        {
            fixture = new JsonParser().parse(reader).getAsJsonObject();
        }

        CombatRecording recording = ReplayV1Format.decode(fixture);

        assertEquals(7, recording.ticks.size());
        assertEquals("ACTION_ATTEMPT", recording.ticks.get(0).events.get(0).type);
        assertEquals("mine", recording.ticks.get(4).events.get(0).detail);
        assertEquals(Integer.valueOf(200), recording.ticks.get(5).events.get(0).fromDefinitionId);
        assertEquals("mine", recording.ticks.get(6).events.get(0).detail);
        assertEquals("terminal_hitsplat_local_v1", recording.ticks.get(6).events.get(2).ruleId);
    }

    @Test
    public void roundTripsOrderedNeutralActivitySignalFixture() throws Exception
    {
        JsonObject fixture;
        try (Reader reader = new java.io.InputStreamReader(
            getClass().getResourceAsStream("/replay-v1/valid/candidate-activity-signals.json")))
        {
            fixture = new JsonParser().parse(reader).getAsJsonObject();
        }

        CombatRecording recording = ReplayV1Format.decode(fixture);
        ActivitySignalObservation initial = recording.ticks.get(0).events.stream()
            .filter(event -> event.activitySignal != null).findFirst().get().activitySignal;
        assertEquals(9177, initial.varbitId);
        assertNull(initial.value);
        assertEquals("initial", initial.observation);
        java.util.List<RecordedEvent> changes = new java.util.ArrayList<>();
        for (RecordedEvent event : recording.ticks.get(1).events)
            if (event.activitySignal != null) changes.add(event);
        assertEquals(Integer.valueOf(1), changes.get(0).activitySignal.value);
        assertEquals(Integer.valueOf(0), changes.get(1).activitySignal.value);
        assertEquals(Integer.valueOf(0), changes.get(2).activitySignal.value);
        assertFalse(recording.ticks.get(1).events.get(1).observationCoverage.available);
        assertTrue(recording.ticks.get(1).events.get(4).observationCoverage.available);
        assertEquals(125, recording.ticks.get(2).events.get(4).gameCycle);

        JsonObject roundTripped = ReplayV1Format.encode(recording);
        assertEquals(fixture.getAsJsonObject("capture").getAsJsonArray("capabilities"),
            roundTripped.getAsJsonObject("capture").getAsJsonArray("capabilities"));
        assertEquals(fixture.getAsJsonArray("ticks").get(1).getAsJsonObject().getAsJsonArray("events"),
            roundTripped.getAsJsonArray("ticks").get(1).getAsJsonObject().getAsJsonArray("events"));
    }

    @Test
    public void reconstructsNullClearsRemovalsReappearanceAndInstanceTransition() throws Exception
    {
        JsonObject fixture;
        try (Reader reader = new java.io.InputStreamReader(
            getClass().getResourceAsStream("/replay-v1/valid/deltas-and-instance.json")))
        {
            fixture = new JsonParser().parse(reader).getAsJsonObject();
        }

        CombatRecording recording = ReplayV1Format.decode(fixture);

        assertEquals(3, recording.ticks.size());
        assertEquals("Alice", recording.ticks.get(0).actors.get(0).label);
        assertEquals(1, recording.ticks.get(1).actors.size());
        assertEquals(-1, recording.ticks.get(1).prayer);
        assertEquals(null, recording.ticks.get(1).actors.get(0).targetKey);
        assertEquals(null, recording.ticks.get(1).actors.get(0).visibleEquipment);
        assertEquals("observed", recording.ticks.get(0).events.get(0).evidence);
        assertEquals("view-main", recording.ticks.get(0).events.get(0).viewKey);
        assertEquals("view-instance", recording.ticks.get(1).viewKey);
        assertEquals("view-instance", recording.ticks.get(1).actors.get(0).viewKey);
        assertEquals(12345, recording.ticks.get(1).instanceTemplateChunks[0][0][0]);
        assertEquals(1, recording.ticks.get(1).inventory.size());
        assertEquals(1, recording.ticks.get(1).sceneRemovals.size());
        assertTrue(recording.ticks.get(1).sceneTiles.isEmpty());
        assertEquals("Bob", recording.ticks.get(2).actors.get(1).label);
        assertTrue(!recording.ticks.get(2).actors.get(1).isLocalPlayer);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPartialFirstActorAppearance() throws Exception
    {
        JsonObject fixture;
        try (Reader reader = new java.io.InputStreamReader(
            getClass().getResourceAsStream("/replay-v1/valid/deltas-and-instance.json")))
        {
            fixture = new JsonParser().parse(reader).getAsJsonObject();
        }
        fixture.getAsJsonArray("ticks").get(0).getAsJsonObject()
            .getAsJsonObject("actors").getAsJsonArray("upsert").get(0).getAsJsonObject()
            .remove("kind");

        ReplayV1Format.decode(fixture);
    }

    @Test
    public void rejectsSemanticInvariantViolations() throws Exception
    {
        assertRejected(fixture -> fixture.getAsJsonArray("ticks").get(0).getAsJsonObject()
            .getAsJsonObject("actors").getAsJsonArray("upsert").add(
                fixture.getAsJsonArray("ticks").get(0).getAsJsonObject()
                    .getAsJsonObject("actors").getAsJsonArray("upsert").get(0).deepCopy()));
        assertRejected(fixture -> fixture.getAsJsonArray("ticks").get(0).getAsJsonObject()
            .getAsJsonObject("scene").getAsJsonArray("remove").add(
                fixture.getAsJsonArray("ticks").get(0).getAsJsonObject()
                    .getAsJsonObject("scene").getAsJsonArray("upsert").get(0).deepCopy()));
        assertRejected(fixture -> fixture.getAsJsonArray("ticks").get(0).getAsJsonObject()
            .getAsJsonArray("events").add(fixture.getAsJsonArray("ticks").get(0).getAsJsonObject()
                .getAsJsonArray("events").get(0).deepCopy()));
        assertRejected(fixture -> fixture.getAsJsonArray("ticks").get(0).getAsJsonObject()
            .getAsJsonArray("events").get(0).getAsJsonObject()
                .getAsJsonArray("evidence_event_ids").add("missing-event"));
        assertRejected(fixture -> fixture.getAsJsonArray("ticks").get(0).getAsJsonObject()
            .getAsJsonObject("sync").addProperty("elapsed_millis", 700));
        assertRejected(fixture -> fixture.getAsJsonObject("capture")
            .addProperty("local_actor_key", "player-2"));
        assertRejected(fixture -> fixture.getAsJsonObject("capture")
            .getAsJsonArray("capabilities").remove(2));
        assertRejected(fixture -> fixture.getAsJsonArray("ticks").get(1).getAsJsonObject()
            .getAsJsonObject("context").addProperty("instanced", false));
    }

    @Test
    public void preservesUnavailableAndZeroEventNumbers() throws Exception
    {
        JsonObject fixture = fixture();
        JsonObject event = fixture.getAsJsonArray("ticks").get(0).getAsJsonObject()
            .getAsJsonArray("events").get(0).getAsJsonObject();
        event.add("definition_id", com.google.gson.JsonNull.INSTANCE);
        event.addProperty("amount", 0);

        RecordedEvent decoded = ReplayV1Format.decode(fixture).ticks.get(0).events.get(0);

        assertNull(decoded.id);
        assertEquals(Integer.valueOf(0), decoded.value);
    }

    @Test
    public void emittedRecordingMatchesCanonicalSchema() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream schemaStream = getClass().getResourceAsStream("/replay-v1/replay-v1.schema.json"))
        {
            JsonSchema schema = factory.getSchema(schemaStream);
            Set<ValidationMessage> emittedErrors = schema.validate(
                mapper.readTree(ReplayV1Format.encode(completedRecording()).toString()));
            assertTrue(emittedErrors.toString(), emittedErrors.isEmpty());
            Set<ValidationMessage> fixtureErrors = schema.validate(mapper.readTree(
                getClass().getResourceAsStream("/replay-v1/valid/deltas-and-instance.json")));
            assertTrue(fixtureErrors.toString(), fixtureErrors.isEmpty());
            Set<ValidationMessage> enrichedErrors = schema.validate(mapper.readTree(
                getClass().getResourceAsStream("/replay-v1/valid/viewer-features.json")));
            assertTrue(enrichedErrors.toString(), enrichedErrors.isEmpty());
            Set<ValidationMessage> fightErrors = schema.validate(mapper.readTree(
                getClass().getResourceAsStream("/replay-v1/valid/generic-fights.json")));
            assertTrue(fightErrors.toString(), fightErrors.isEmpty());
            Set<ValidationMessage> activityErrors = schema.validate(mapper.readTree(
                getClass().getResourceAsStream("/replay-v1/valid/candidate-activity-signals.json")));
            assertTrue(activityErrors.toString(), activityErrors.isEmpty());
        }
    }

    private void assertRejected(Consumer<JsonObject> mutation) throws Exception
    {
        JsonObject fixture = fixture();
        mutation.accept(fixture);
        try
        {
            ReplayV1Format.decode(fixture);
            fail("Expected semantic validation to reject fixture");
        }
        catch (IllegalArgumentException expected)
        {
            // Expected.
        }
    }

    private JsonObject fixture() throws Exception
    {
        try (Reader reader = new java.io.InputStreamReader(
            getClass().getResourceAsStream("/replay-v1/valid/deltas-and-instance.json")))
        {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }

    private static ActorSnapshot player(String key, String name, boolean local)
    {
        return new ActorSnapshot(key, "PLAYER", name, local, -1,
            3200, 3200, 10, 10, 1280, 1280, 0, 0, 1, 0,
            -1, -1, -1, -1, null, false, Collections.emptyList(), null);
    }

    private static CombatRecording completedRecording()
    {
        CombatRecording recording = new CombatRecording(1_000L);
        recording.add(new RecordedTick(0, 100, 3200, 3200, false,
            90, 99, 70, 70, Collections.singletonList(player("player-1", "Alice", true)), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
        return recording.completed(1_600L);
    }
}
