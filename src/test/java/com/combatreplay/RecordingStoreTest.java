package com.combatreplay;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecordingStoreTest
{
	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	@Test
	public void versionOneRecordingRoundTripsToCompleteTicks() throws Exception
	{
		Path directory = temporary.newFolder("recordings").toPath();
		RecordingStore store = new RecordingStore(new Gson(), directory);
		CombatRecording source = recording().completed(2_000L);

		Path path = store.save(source);
		CombatRecording loaded = store.load(path);

		assertEquals(source.recordingId, loaded.recordingId);
		assertEquals(2, loaded.ticks.size());
		assertEquals("You", loaded.ticks.get(1).actors.get(0).label);
		assertEquals(1344, loaded.ticks.get(1).actors.get(0).localX);
		assertEquals("Protect from Melee", loaded.ticks.get(1).actors.get(0).overheadIcon);
		assertEquals(1, loaded.ticks.get(1).actors.get(0).visibleEquipment.size());
		assertEquals("Abyssal whip", loaded.ticks.get(1).actors.get(0).visibleEquipment.get(0).name);
		assertEquals("Shark", loaded.ticks.get(1).inventory.get(0).name);
		assertEquals(4151, loaded.ticks.get(1).equipment.get(0).itemId);
		assertEquals(1, loaded.ticks.get(0).sceneTiles.size());

		try (Reader reader = Files.newBufferedReader(path))
		{
			JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
			assertEquals(1, root.get("format_version").getAsInt());
			assertTrue(root.has("recording_id"));
			assertFalse("legacy compact envelope must not be emitted", root.has("v"));
			JsonArray ticks = root.getAsJsonArray("ticks");
			assertTrue(ticks.get(0).getAsJsonObject().get("keyframe").getAsBoolean());
			assertTrue(ticks.get(0).getAsJsonObject().has("actors"));
			assertTrue(ticks.get(0).getAsJsonObject().has("scene"));
		}
	}

	private static CombatRecording recording()
	{
		CombatRecording recording = new CombatRecording(1_000L);
		ItemSnapshot food = new ItemSnapshot(0, 385, 4, "Shark");
		ItemSnapshot weapon = new ItemSnapshot(3, 4151, 1, "Abyssal whip");
		recording.add(tick(0, actor(1280), food, weapon,
			Collections.singletonList(new SceneTileSnapshot(0, 0, 3200, 3201,
				-20, 0x334422, 4, -1, -1, -1, new int[0]))));
		recording.add(tick(1, actor(1344), food, weapon, Collections.emptyList()));
		return recording;
	}

	private static RecordedTick tick(int number, ActorSnapshot actor, ItemSnapshot food,
		ItemSnapshot weapon, java.util.List<SceneTileSnapshot> scene)
	{
		return new RecordedTick(number, 100 + number * 30, 3190, 3190, false,
			90, 99, 70, 70, Collections.singletonList(actor),
			Collections.singletonList(food), Collections.singletonList(weapon),
			Collections.emptyList(), Collections.emptyList(), scene, Collections.emptyList());
	}

	private static ActorSnapshot actor(int localX)
	{
		return new ActorSnapshot("player-1", "PLAYER", "You", -1,
			3200, 3200, localX >> 7, 10, localX, 1344, 0, 0, 1,
			0, -1, -1, -1, -1, null, false,
			Collections.singletonList(new ItemSnapshot(3, 4151, 1, "Abyssal whip")),
			"Protect from Melee");
	}
}
