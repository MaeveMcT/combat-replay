package com.combatreplay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldView;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GauntletSignalDiagnosticsTest
{
	@Test
	public void diagnosticsAreOptIn()
	{
		CombatReplayConfig config = new CombatReplayConfig() { };

		assertFalse(config.cra201Diagnostics());
	}

	private static final String RECORDING_ID = "123e4567-e89b-42d3-a456-426614174000";

	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	private final Map<Integer, Integer> values = new HashMap<>();
	private Client client;
	private GauntletSignalDiagnostics diagnostics;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getTopLevelWorldView()).thenReturn(mock(WorldView.class));
		when(client.getGameCycle()).thenReturn(100);
		when(client.getTickCount()).thenReturn(10);
		when(client.getVarbitValue(anyInt())).thenAnswer(invocation ->
			values.getOrDefault(invocation.getArgument(0), 0));
		diagnostics = new GauntletSignalDiagnostics(client, new Gson());
	}

	@Test
	public void recordsOnlyAllowlistedSignalsAndRetainsRedundantCallbacks()
	{
		diagnostics.start(RECORDING_ID);
		values.put(VarbitID.GAUNTLET_BOSS_STARTED, 1);
		VarbitChanged callback = new VarbitChanged();
		callback.setVarbitId(VarbitID.GAUNTLET_BOSS_STARTED);
		callback.setVarpId(1234);
		callback.setValue(1);
		diagnostics.onVarbitChanged(callback);
		diagnostics.onVarbitChanged(callback);

		values.put(VarbitID.PLAYER_IN_GAUNTLET, 1);
		VarbitChanged unrelated = new VarbitChanged();
		unrelated.setVarbitId(999_999);
		unrelated.setVarpId(5678);
		unrelated.setValue(42);
		diagnostics.onVarbitChanged(unrelated);

		JsonArray observations = diagnostics.stop().json().getAsJsonArray("observations");
		Set<Integer> allowed = Set.of(VarbitID.PLAYER_IN_GAUNTLET,
			VarbitID.GAUNTLET_BOSS_STARTED, VarbitID.GAUNTLET_CORRUPTED,
			VarbitID.GAUNTLET_REWARD_AVAILABLE, VarbitID.GAUNTLET_START);
		int callbackCount = 0;
		boolean polledMembershipChange = false;
		for (int index = 0; index < observations.size(); index++)
		{
			JsonObject observation = observations.get(index).getAsJsonObject();
			assertFalse(observation.has("varp_id"));
			if (!"activity_signal".equals(observation.get("kind").getAsString())) continue;
			assertTrue(allowed.contains(observation.get("varbit_id").getAsInt()));
			if ("callback".equals(observation.get("source").getAsString())) callbackCount++;
			if ("callback_poll".equals(observation.get("source").getAsString())
				&& "PLAYER_IN_GAUNTLET".equals(observation.get("signal").getAsString())
				&& observation.get("value").getAsInt() == 1) polledMembershipChange = true;
		}
		assertEquals(2, callbackCount);
		assertTrue(polledMembershipChange);
	}

	@Test
	public void recordsPeriodicResyncAndCoverageLoss()
	{
		diagnostics.start(RECORDING_ID);
		for (int tick = 0; tick < 10; tick++) diagnostics.onGameTick();
		when(client.getTopLevelWorldView()).thenReturn(null);
		diagnostics.onGameStateChanged(GameState.LOADING);

		JsonArray observations = diagnostics.stop().json().getAsJsonArray("observations");
		Set<String> sources = new HashSet<>();
		boolean loading = false;
		boolean unavailable = false;
		for (int index = 0; index < observations.size(); index++)
		{
			JsonObject observation = observations.get(index).getAsJsonObject();
			sources.add(observation.get("source").getAsString());
			loading |= "game_state".equals(observation.get("kind").getAsString())
				&& "LOADING".equals(observation.get("state").getAsString());
			unavailable |= "coverage".equals(observation.get("kind").getAsString())
				&& !observation.get("world_view_available").getAsBoolean();
		}
		assertTrue(sources.contains("resync"));
		assertTrue(loading);
		assertTrue(unavailable);
	}

	@Test
	public void boundsDiagnosticObservations()
	{
		diagnostics.start(RECORDING_ID);
		VarbitChanged callback = new VarbitChanged();
		callback.setVarbitId(VarbitID.GAUNTLET_BOSS_STARTED);
		for (int index = 0; index < 10_100; index++) diagnostics.onVarbitChanged(callback);

		JsonObject report = diagnostics.stop().json();
		assertEquals(10_000, report.getAsJsonArray("observations").size());
		assertTrue(report.get("truncated").getAsBoolean());
	}

	@Test
	public void savesDiagnosticsOutsideTheReplayLibrary()
		throws Exception
	{
		diagnostics.start(RECORDING_ID);
		Path root = temporary.newFolder().toPath();
		Path saved = diagnostics.stop().save(root);

		assertEquals(root.resolve(".cra-201").resolve(RECORDING_ID + ".json"), saved);
		assertTrue(Files.exists(saved));
		JsonObject json = new Gson().fromJson(Files.readString(saved), JsonObject.class);
		assertEquals("CRA-201", json.get("diagnostic").getAsString());
		assertEquals(RECORDING_ID, json.get("recording_id").getAsString());
	}
}
