package com.combatreplay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
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
}
