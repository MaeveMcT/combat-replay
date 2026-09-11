package com.combatreplay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Projectile;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class CombatRecorderV1Test
{
    @Test
    public void capturesExactVisibleNamesLocalIdentityAndSynchronization()
    {
        Client client = mock(Client.class);
        WorldView worldView = mock(WorldView.class);
        Scene scene = mock(Scene.class);
        Player local = player("Alice", worldView, 3200);
        Player teammate = player("Bob", worldView, 3201);
        when(client.getTopLevelWorldView()).thenReturn(worldView);
        when(client.getLocalPlayer()).thenReturn(local);
        when(client.getGameCycle()).thenReturn(1234);
        when(client.getTickCount()).thenReturn(77);
        when(client.getWorld()).thenReturn(330);
        when(client.getRevision()).thenReturn(230);
        IndexedObjectSet<Player> players = mock(IndexedObjectSet.class);
        when(players.iterator()).thenReturn(java.util.Arrays.asList(local, teammate).iterator());
        IndexedObjectSet<NPC> npcs = mock(IndexedObjectSet.class);
        when(npcs.iterator()).thenReturn(Collections.<NPC>emptyList().iterator());
        doReturn(players).when(worldView).players();
        doReturn(npcs).when(worldView).npcs();
        when(worldView.getBaseX()).thenReturn(3190);
        when(worldView.getBaseY()).thenReturn(3190);
        when(worldView.getPlane()).thenReturn(0);
        when(worldView.getId()).thenReturn(4);
        int[][][] instanceChunks = {{{12345}}};
        when(worldView.isInstance()).thenReturn(true);
        when(worldView.getInstanceTemplateChunks()).thenReturn(instanceChunks);
        when(worldView.getScene()).thenReturn(scene);
        when(scene.getTiles()).thenReturn(new Tile[4][0][0]);

        CombatRecorder recorder = new CombatRecorder(client);
        recorder.start();
        recorder.captureTick();
        CombatRecording recording = recorder.snapshot();

        assertEquals("Alice", recording.ticks.get(0).actors.get(0).label);
        assertTrue(recording.ticks.get(0).actors.get(0).isLocalPlayer);
        assertEquals("Bob", recording.ticks.get(0).actors.get(1).label);
        assertFalse(recording.ticks.get(0).actors.get(1).isLocalPlayer);
        assertEquals(77, recording.ticks.get(0).clientTick);
        assertEquals(Integer.valueOf(330), recording.ticks.get(0).world);
        assertEquals("view-" + (31 * 4 + java.util.Arrays.deepHashCode(instanceChunks)),
            recording.ticks.get(0).viewKey);
        assertEquals(12345, recording.ticks.get(0).instanceTemplateChunks[0][0][0]);
        assertEquals(Integer.valueOf(230), recording.gameRevision);
    }

    @Test
    public void recordsOnlyEffectiveUpgradedPrayerWhenRuneLiteReportsBoth()
    {
        RecorderFixture fixture = new RecorderFixture();
        fixture.withActors(Collections.singletonList(fixture.local), Collections.emptyList());
        when(fixture.client.isPrayerActive(Prayer.EAGLE_EYE)).thenReturn(true);
        when(fixture.client.isPrayerActive(Prayer.DEADEYE)).thenReturn(true);
        when(fixture.client.isPrayerActive(Prayer.MYSTIC_MIGHT)).thenReturn(true);
        when(fixture.client.isPrayerActive(Prayer.MYSTIC_VIGOUR)).thenReturn(true);

        fixture.recorder.start();
        fixture.recorder.captureTick();
        RecordedTick tick = fixture.recorder.stop().ticks.get(0);

        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("DEADEYE", "MYSTIC_VIGOUR")),
            new java.util.HashSet<>(tick.activePrayers));
        java.util.Set<String> activated = new java.util.HashSet<>();
        for (RecordedEvent event : tick.events)
            if ("PRAYER_CHANGE".equals(event.type) && Integer.valueOf(1).equals(event.value))
                activated.add(event.detail);
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("DEADEYE", "MYSTIC_VIGOUR")), activated);
    }

    @Test
    public void capturesInitialChangedAndPeriodicActivitySignalObservations()
    {
        RecorderFixture fixture = new RecorderFixture();
        fixture.withActors(Collections.singletonList(fixture.local), Collections.emptyList());
        when(fixture.client.getVarbitValue(VarbitID.PLAYER_IN_GAUNTLET)).thenReturn(0);
        when(fixture.client.getVarbitValue(VarbitID.GAUNTLET_BOSS_STARTED)).thenReturn(0, 1, 1, 1);
        when(fixture.client.getVarbitValue(VarbitID.GAUNTLET_CORRUPTED)).thenReturn(0);

        fixture.recorder.start();
        fixture.recorder.captureActivitySignal(VarbitID.GAUNTLET_BOSS_STARTED);
        fixture.recorder.captureActivitySignal(VarbitID.GAUNTLET_BOSS_STARTED);
        for (int tick = 0; tick < 10; tick++) fixture.recorder.captureTick();
        CombatRecording recording = fixture.recorder.stop();

        assertTrue(recording.capabilities.contains("activity_signals"));
        java.util.List<RecordedEvent> signals = new java.util.ArrayList<>();
        for (RecordedTick tick : recording.ticks)
            for (RecordedEvent event : tick.events)
                if (event.activitySignal != null) signals.add(event);
        assertEquals(7, signals.size());
        assertEquals("initial", signals.get(0).activitySignal.observation);
        assertEquals(Integer.valueOf(0), signals.get(0).activitySignal.value);
        assertEquals("change", signals.get(3).activitySignal.observation);
        assertEquals(Integer.valueOf(1), signals.get(3).activitySignal.value);
        assertEquals("resync", signals.get(4).activitySignal.observation);
        assertEquals(Integer.valueOf(0), signals.get(4).activitySignal.value);
    }

    @Test
    public void recordsWorldViewCoverageLossAndRecoveryWithoutTreatingLoadingAsLoss()
    {
        RecorderFixture fixture = new RecorderFixture();
        fixture.withActors(Collections.singletonList(fixture.local), Collections.emptyList());

        fixture.recorder.start();
        fixture.recorder.captureTick();
        fixture.recorder.captureObservationCoverage();
        when(fixture.client.getTopLevelWorldView()).thenReturn(null);
        fixture.recorder.captureObservationCoverage();
        when(fixture.client.getTopLevelWorldView()).thenReturn(fixture.worldView);
        fixture.recorder.captureObservationCoverage();
        fixture.recorder.captureTick();
        CombatRecording recording = fixture.recorder.stop();

        assertTrue(recording.capabilities.contains("observation_coverage"));
        java.util.List<RecordedEvent> coverage = new java.util.ArrayList<>();
        for (RecordedTick tick : recording.ticks)
            for (RecordedEvent event : tick.events)
                if (event.observationCoverage != null) coverage.add(event);
        assertEquals(3, coverage.size());
        assertTrue(coverage.get(0).observationCoverage.available);
        assertEquals("initial", coverage.get(0).observationCoverage.observation);
        assertFalse(coverage.get(1).observationCoverage.available);
        assertEquals("change", coverage.get(1).observationCoverage.observation);
        assertTrue(coverage.get(2).observationCoverage.available);

        com.google.gson.JsonArray encodedEvents = ReplayV1Format.encode(recording)
            .getAsJsonArray("ticks").get(1).getAsJsonObject().getAsJsonArray("events");
        com.google.gson.JsonObject encodedLoss = null;
        for (com.google.gson.JsonElement encodedEvent : encodedEvents)
        {
            com.google.gson.JsonObject value = encodedEvent.getAsJsonObject();
            if ("observation_coverage".equals(value.get("type").getAsString())
                && !value.getAsJsonObject("observation_coverage").get("available").getAsBoolean())
                encodedLoss = value;
        }
        assertNotNull(encodedLoss);
    }

    @Test
    public void preservesLifecycleEvidenceOnManualTailStopWithoutLeakingIntoTheNextRecording()
    {
        RecorderFixture fixture = new RecorderFixture();
        fixture.withActors(Collections.singletonList(fixture.local), Collections.emptyList());
        when(fixture.client.getVarbitValue(VarbitID.PLAYER_IN_GAUNTLET)).thenReturn(0, 1, 0);

        fixture.recorder.start();
        fixture.recorder.captureTick();
        fixture.recorder.captureActivitySignal(VarbitID.PLAYER_IN_GAUNTLET);
        when(fixture.client.getTopLevelWorldView()).thenReturn(null);
        fixture.recorder.captureObservationCoverage();
        CombatRecording stopped = fixture.recorder.stop();

        assertEquals(1, stopped.ticks.size());
        assertTrue(stopped.ticks.get(0).events.stream().anyMatch(event -> event.activitySignal != null
            && "change".equals(event.activitySignal.observation)
            && Integer.valueOf(1).equals(event.activitySignal.value)));
        assertTrue(stopped.ticks.get(0).events.stream().anyMatch(event -> event.observationCoverage != null
            && !event.observationCoverage.available));

        when(fixture.client.getTopLevelWorldView()).thenReturn(fixture.worldView);
        fixture.recorder.start();
        fixture.recorder.captureTick();
        CombatRecording restarted = fixture.recorder.stop();
        assertFalse(restarted.ticks.get(0).events.stream().anyMatch(event -> event.observationCoverage != null
            && !event.observationCoverage.available));
    }

    @Test
    public void boundsRecordedEventsIncludingManualTailEvidence()
    {
        RecorderFixture fixture = new RecorderFixture();
        fixture.withActors(Collections.singletonList(fixture.local), Collections.emptyList());
        fixture.recorder.start();
        fixture.recorder.captureTick();
        for (int index = 0; index < RecordedTick.MAX_EVENTS + 100; index++)
            fixture.recorder.addEvent("ANIMATION", fixture.local, null, index, 0, null, null);

        CombatRecording stopped = fixture.recorder.stop();

        assertEquals(RecordedTick.MAX_EVENTS, stopped.ticks.get(0).events.size());
        ReplayV1SemanticValidator.validate(ReplayV1Format.encode(stopped));
    }

    @Test
    public void assignsUniqueStableIdsToCapturedAndDerivedEvents()
    {
        RecorderFixture fixture = new RecorderFixture();
        NPC victim = npc("Hunllef", fixture.worldView, 3201);
        fixture.withActors(Collections.singletonList(fixture.local), Collections.singletonList(victim));
        when(fixture.client.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS)).thenReturn(90, 80);

        fixture.recorder.start();
        fixture.recorder.captureTick();
        fixture.recorder.addEvent("HITSPLAT", victim, null, 16, 10, null, "mine");
        fixture.recorder.addEvent("DEATH", victim, null, 0, 0, null, null);
        fixture.recorder.captureTick();
        CombatRecording recording = fixture.recorder.stop();

        java.util.List<String> ids = new java.util.ArrayList<>();
        for (RecordedTick tick : recording.ticks)
            for (RecordedEvent event : tick.events) ids.add(event.eventId);
        assertFalse(ids.contains(null));
        assertEquals(ids.size(), new java.util.HashSet<>(ids).size());

        com.google.gson.JsonObject encoded = ReplayV1Format.encode(recording);
        ReplayV1SemanticValidator.validate(encoded);
        com.google.gson.JsonObject attribution = encoded.getAsJsonArray("ticks").get(1).getAsJsonObject()
            .getAsJsonArray("events").get(2).getAsJsonObject();
        assertEquals("kill_attribution", attribution.get("type").getAsString());
        assertEquals("inferred", attribution.get("evidence").getAsString());
        assertEquals("terminal_hitsplat_local_v1", attribution.get("rule_id").getAsString());
        assertEquals(2, attribution.getAsJsonArray("evidence_event_ids").size());
    }

    @Test
    public void emitsLocalKillAttributionWithSupportingEvidence()
    {
        RecorderFixture fixture = new RecorderFixture();
        NPC victim = npc("Hunllef", fixture.worldView, 3201);
        fixture.withActors(Collections.singletonList(fixture.local), Collections.singletonList(victim));

        fixture.recorder.start();
        fixture.recorder.captureTick();
        fixture.recorder.addEvent("HITSPLAT", victim, null, 16, 24, null, "mine");
        fixture.recorder.addEvent("DEATH", victim, null, 0, 0, null, null);

        RecordedEvent attribution = fixture.recorder.stop().ticks.get(0).events.stream()
            .filter(event -> "KILL_ATTRIBUTION".equals(event.type)).findFirst().orElse(null);

        assertNotNull(attribution);
        assertEquals(fixture.recorder.keyFor(fixture.local), attribution.actorKey);
        assertEquals(fixture.recorder.keyFor(victim), attribution.targetKey);
        assertEquals("inferred", attribution.evidence);
        assertEquals("terminal_hitsplat_local_v1", attribution.ruleId);
        assertEquals(2, attribution.evidenceEventIds.size());
    }

    @Test
    public void emitsRemoteKillAttributionForUniqueMatchingProjectile()
    {
        RecorderFixture fixture = new RecorderFixture();
        Player attacker = player("Bob", fixture.worldView, 3201);
        fixture.withActors(java.util.Arrays.asList(fixture.local, attacker), Collections.emptyList());

        fixture.recorder.start();
        fixture.recorder.captureTick();
        fixture.recorder.addEvent("PROJECTILE", attacker, fixture.local, 123, 0, null, null);
        fixture.recorder.addEvent("HITSPLAT", fixture.local, null, 16, 18, null, "other");
        fixture.recorder.addEvent("DEATH", fixture.local, null, 0, 0, null, null);

        RecordedEvent attribution = fixture.recorder.stop().ticks.get(0).events.stream()
            .filter(event -> "KILL_ATTRIBUTION".equals(event.type)).findFirst().orElse(null);

        assertNotNull(attribution);
        assertEquals(fixture.recorder.keyFor(attacker), attribution.actorKey);
        assertEquals(fixture.recorder.keyFor(fixture.local), attribution.targetKey);
        assertEquals("terminal_hitsplat_projectile_v1", attribution.ruleId);
        assertEquals(3, attribution.evidenceEventIds.size());
    }

    @Test
    public void linksOneEventToRepeatedProjectileStateUpdates()
    {
        RecorderFixture fixture = new RecorderFixture();
        fixture.withActors(Collections.singletonList(fixture.local), Collections.emptyList());
        Projectile projectile = mock(Projectile.class);
        when(projectile.getId()).thenReturn(1712);
        when(projectile.getTargetActor()).thenReturn(fixture.local);
        when(projectile.getSourcePoint()).thenReturn(new WorldPoint(3215, 3215, 0));
        when(projectile.getTargetPoint()).thenReturn(new WorldPoint(3210, 3210, 0));
        when(projectile.getStartCycle()).thenReturn(1200);
        when(projectile.getEndCycle()).thenReturn(1300);
        when(projectile.getRemainingCycles()).thenReturn(60, 50);

        fixture.recorder.start();
        fixture.recorder.captureProjectile(projectile, new LocalPoint(1280, 1280));
        fixture.recorder.captureProjectile(projectile, new LocalPoint(1408, 1280));
        fixture.recorder.captureTick();
        RecordedTick tick = fixture.recorder.stop().ticks.get(0);

        assertEquals(1, tick.projectileUpserts.size());
        assertEquals(50, tick.projectileUpserts.get(0).remainingCycles);
        RecordedEvent projectileEvent = tick.events.stream()
            .filter(event -> "PROJECTILE".equals(event.type)).findFirst().get();
        assertEquals(1L, tick.events.stream().filter(event -> "PROJECTILE".equals(event.type)).count());
        assertEquals(tick.projectileUpserts.get(0).key, projectileEvent.projectileKey);
    }

    @Test
    public void capturesBothRecordedNpcTransformDefinitions()
    {
        RecorderFixture fixture = new RecorderFixture();
        fixture.withActors(Collections.singletonList(fixture.local), Collections.emptyList());
        NPC npc = npc("Hunllef", fixture.worldView, 3201);
        NPCComposition oldComposition = mock(NPCComposition.class);
        NPCComposition newComposition = mock(NPCComposition.class);
        when(oldComposition.getId()).thenReturn(9037);
        when(oldComposition.getName()).thenReturn("Corrupted Hunllef");
        when(oldComposition.getCombatLevel()).thenReturn(674);
        when(oldComposition.getSize()).thenReturn(1);
        when(newComposition.getId()).thenReturn(9036);
        when(newComposition.getName()).thenReturn("Corrupted Hunllef (tornado phase)");
        when(newComposition.getCombatLevel()).thenReturn(674);
        when(newComposition.getSize()).thenReturn(1);
        when(npc.getTransformedComposition()).thenReturn(newComposition);

        fixture.recorder.start();
        fixture.recorder.captureTick();
        fixture.recorder.captureNpcTransform(npc, oldComposition);
        CombatRecording recording = fixture.recorder.stop();
        RecordedEvent transform = recording.ticks.get(0).events.stream()
            .filter(event -> "NPC_CHANGED".equals(event.type)).findFirst().get();

        assertEquals("Corrupted Hunllef", recording.npcDefinitions.get(9037).name);
        assertEquals("Corrupted Hunllef (tornado phase)", recording.npcDefinitions.get(9036).name);
        assertEquals(Integer.valueOf(9037), transform.fromDefinitionId);
        assertEquals(Integer.valueOf(9036), transform.toDefinitionId);
    }

    private static Player player(String name, WorldView worldView, int worldX)
    {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getWorldView()).thenReturn(worldView);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(worldX, 3200, 0));
        when(player.getLocalLocation()).thenReturn(new LocalPoint(1280 + (worldX - 3200) * 128, 1280));
        when(player.getHealthRatio()).thenReturn(-1);
        when(player.getHealthScale()).thenReturn(-1);
        when(player.getAnimation()).thenReturn(-1);
        when(player.getPoseAnimation()).thenReturn(-1);
        return player;
    }

    private static NPC npc(String name, WorldView worldView, int worldX)
    {
        NPC npc = mock(NPC.class);
        NPCComposition composition = mock(NPCComposition.class);
        when(composition.getName()).thenReturn(name);
        when(composition.getSize()).thenReturn(1);
        when(npc.getTransformedComposition()).thenReturn(composition);
        when(npc.getName()).thenReturn(name);
        when(npc.getWorldView()).thenReturn(worldView);
        when(npc.getWorldLocation()).thenReturn(new WorldPoint(worldX, 3200, 0));
        when(npc.getLocalLocation()).thenReturn(new LocalPoint(1280 + (worldX - 3200) * 128, 1280));
        when(npc.getHealthRatio()).thenReturn(-1);
        when(npc.getHealthScale()).thenReturn(-1);
        when(npc.getAnimation()).thenReturn(-1);
        when(npc.getPoseAnimation()).thenReturn(-1);
        return npc;
    }

    private static final class RecorderFixture
    {
        private final Client client = mock(Client.class);
        private final WorldView worldView = mock(WorldView.class);
        private final Scene scene = mock(Scene.class);
        private final Player local = player("Alice", worldView, 3200);
        private final CombatRecorder recorder = new CombatRecorder(client);

        private RecorderFixture()
        {
            when(client.getTopLevelWorldView()).thenReturn(worldView);
            when(client.getLocalPlayer()).thenReturn(local);
            when(client.getGameCycle()).thenReturn(1234);
            when(client.getTickCount()).thenReturn(77);
            when(worldView.getBaseX()).thenReturn(3190);
            when(worldView.getBaseY()).thenReturn(3190);
            when(worldView.getPlane()).thenReturn(0);
            when(worldView.getScene()).thenReturn(scene);
            when(scene.getTiles()).thenReturn(new Tile[4][0][0]);
        }

        private void withActors(java.util.List<Player> playerActors, java.util.List<NPC> npcActors)
        {
            IndexedObjectSet<Player> players = mock(IndexedObjectSet.class);
            when(players.iterator()).thenAnswer(ignored -> playerActors.iterator());
            IndexedObjectSet<NPC> npcs = mock(IndexedObjectSet.class);
            when(npcs.iterator()).thenAnswer(ignored -> npcActors.iterator());
            doReturn(players).when(worldView).players();
            doReturn(npcs).when(worldView).npcs();
        }
    }
}
