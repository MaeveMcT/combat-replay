package com.combatreplay;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;

/**
 * Temporary, owner-local instrumentation for CRA-201. This intentionally does not
 * extend replay v1 or upload diagnostics. It records only an allowlist of Gauntlet
 * varbits and coarse client observation availability.
 */
@Singleton
final class GauntletSignalDiagnostics
{
	private static final int MAX_OBSERVATIONS = 10_000;
	private static final int RESYNC_TICKS = 10;
	private static final Map<Integer, String> SIGNALS = signals();

	private final Client client;
	private final Gson gson;
	private JsonObject report;
	private JsonArray observations;
	private final Map<Integer, Integer> previousValues = new LinkedHashMap<>();
	private int sequence;
	private int ticksSinceResync;
	private Boolean previousWorldViewAvailable;
	private boolean truncated;

	@Inject
	GauntletSignalDiagnostics(Client client, Gson gson)
	{
		this.client = client;
		this.gson = gson.newBuilder().serializeNulls().setPrettyPrinting().create();
	}

	void start(String recordingId)
	{
		report = new JsonObject();
		report.addProperty("diagnostic", "CRA-201");
		report.addProperty("version", 1);
		report.addProperty("recording_id", recordingId);
		report.addProperty("started_at", Instant.now().toString());
		observations = new JsonArray();
		report.add("observations", observations);
		previousValues.clear();
		sequence = 0;
		ticksSinceResync = 0;
		previousWorldViewAvailable = null;
		truncated = false;
		recordGameState("initial", client.getGameState());
		recordCoverage("initial", true);
		sampleAll("initial", true);
	}

	boolean isActive()
	{
		return report != null;
	}

	void onVarbitChanged(VarbitChanged event)
	{
		if (!isActive()) return;
		String signal = SIGNALS.get(event.getVarbitId());
		if (signal != null)
		{
			int sampledValue = client.getVarbitValue(event.getVarbitId());
			recordSignal("callback", signal, event.getVarbitId(), sampledValue, event.getValue());
			previousValues.put(event.getVarbitId(), sampledValue);
		}
		sampleAll("callback_poll", false);
	}

	void onGameTick()
	{
		if (!isActive()) return;
		recordCoverage("game_tick", false);
		ticksSinceResync++;
		boolean resync = ticksSinceResync >= RESYNC_TICKS;
		sampleAll(resync ? "resync" : "game_tick_poll", resync);
		if (resync) ticksSinceResync = 0;
	}

	void onGameStateChanged(GameState state)
	{
		if (!isActive()) return;
		recordGameState("callback", state);
		recordCoverage("game_state", false);
		sampleAll("game_state_poll", false);
	}

	Snapshot stop()
	{
		if (!isActive()) return null;
		recordCoverage("final", true);
		sampleAll("final", true);
		report.addProperty("ended_at", Instant.now().toString());
		report.addProperty("truncated", truncated);
		Snapshot snapshot = new Snapshot(gson, report.deepCopy());
		report = null;
		observations = null;
		previousValues.clear();
		return snapshot;
	}

	private void sampleAll(String source, boolean includeEqual)
	{
		for (Map.Entry<Integer, String> signal : SIGNALS.entrySet())
		{
			int value = client.getVarbitValue(signal.getKey());
			Integer previous = previousValues.put(signal.getKey(), value);
			if (includeEqual || previous == null || previous.intValue() != value)
			{
				recordSignal(source, signal.getValue(), signal.getKey(), value, null);
			}
		}
	}

	private void recordSignal(String source, String signal, int varbitId, int value, Integer callbackValue)
	{
		JsonObject observation = base("activity_signal", source);
		observation.addProperty("signal", signal);
		observation.addProperty("varbit_id", varbitId);
		observation.addProperty("value", value);
		if (callbackValue != null) observation.addProperty("callback_value", callbackValue);
		add(observation);
	}

	private void recordGameState(String source, GameState state)
	{
		JsonObject observation = base("game_state", source);
		observation.addProperty("state", state == null ? "UNKNOWN" : state.name());
		add(observation);
	}

	private void recordCoverage(String source, boolean includeEqual)
	{
		boolean available = client.getTopLevelWorldView() != null;
		if (!includeEqual && previousWorldViewAvailable != null
			&& previousWorldViewAvailable.booleanValue() == available) return;
		previousWorldViewAvailable = available;
		JsonObject observation = base("coverage", source);
		observation.addProperty("world_view_available", available);
		add(observation);
	}

	private JsonObject base(String kind, String source)
	{
		JsonObject observation = new JsonObject();
		observation.addProperty("sequence", sequence++);
		observation.addProperty("kind", kind);
		observation.addProperty("source", source);
		observation.addProperty("game_cycle", client.getGameCycle());
		observation.addProperty("client_tick", client.getTickCount());
		observation.addProperty("observed_at", Instant.now().toString());
		return observation;
	}

	private void add(JsonObject observation)
	{
		if (observations.size() < MAX_OBSERVATIONS)
		{
			observations.add(observation);
		}
		else
		{
			truncated = true;
		}
	}

	private static Map<Integer, String> signals()
	{
		Map<Integer, String> signals = new LinkedHashMap<>();
		signals.put(VarbitID.PLAYER_IN_GAUNTLET, "PLAYER_IN_GAUNTLET");
		signals.put(VarbitID.GAUNTLET_BOSS_STARTED, "GAUNTLET_BOSS_STARTED");
		signals.put(VarbitID.GAUNTLET_CORRUPTED, "GAUNTLET_CORRUPTED");
		signals.put(VarbitID.GAUNTLET_REWARD_AVAILABLE, "GAUNTLET_REWARD_AVAILABLE");
		signals.put(VarbitID.GAUNTLET_START, "GAUNTLET_START");
		return signals;
	}

	static final class Snapshot
	{
		private final Gson gson;
		private final JsonObject json;

		private Snapshot(Gson gson, JsonObject json)
		{
			this.gson = gson;
			this.json = json;
		}

		JsonObject json()
		{
			return json;
		}

		Path save(Path recordingDirectory) throws IOException
		{
			Path directory = recordingDirectory.resolve(".cra-201");
			Files.createDirectories(directory);
			String recordingId = json.get("recording_id").getAsString();
			Path destination = directory.resolve(recordingId + ".json");
			Path temporary = Files.createTempFile(directory, "cra-201-", ".tmp");
			try
			{
				try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8))
				{
					gson.toJson(json, writer);
				}
				try
				{
					Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
				}
				catch (IOException exception)
				{
					Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			finally
			{
				Files.deleteIfExists(temporary);
			}
			return destination;
		}
	}
}
