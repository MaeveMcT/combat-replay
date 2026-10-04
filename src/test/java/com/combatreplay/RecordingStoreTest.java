package com.combatreplay;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import net.runelite.client.util.Filepath;
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
		Filepath directory = Filepath.Unchecked.getRooted(temporary.newFolder("recordings").toPath());
		RecordingStore store = new RecordingStore(new Gson(), directory);
		CombatRecording source = recording().completed(2_000L);

		Filepath path = store.save(source);
		CombatRecording loaded = store.load(path);
		try (Reader reader = path.openBufferedReader())
		{
			assertEquals(ReplayV1Format.encode(source), new JsonParser().parse(reader));
		}

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

		try (Reader reader = path.openBufferedReader())
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

	@Test
	public void renamePreservesUploadBytesAndListingDoesNotDecodeTicks() throws Exception
	{
		Filepath directory = Filepath.Unchecked.getRooted(temporary.newFolder("library").toPath());
		RecordingStore store = new RecordingStore(new Gson(), directory);
		Filepath path = store.save(recording().completed(2_000L));
		byte[] original = readBytes(path);
		StoredRecording stored = store.list().get(0);
		StoredRecording renamed = store.rename(stored, "Local title");

		assertFalse(path.exists());
		assertEquals("Local title", renamed.toString());
		assertEquals(stored.recordingId, renamed.recordingId);
		assertTrue(java.util.Arrays.equals(original, readBytes(renamed.path)));
		assertEquals(stored.recordingId, store.list().get(0).recordingId);
		assertEquals(stored.recordingId, store.load(renamed.path).recordingId);
	}

	@Test
	public void listingReadsOnlyMetadataWithoutDecodingTicks() throws Exception
	{
		Filepath directory = Filepath.Unchecked.getRooted(temporary.newFolder("metadata").toPath());
		String id = "123e4567-e89b-42d3-a456-426614174000";
		directory.joinSegment("Test.json").write(
			"{\"format_version\":1,\"recording_id\":\"" + id + "\",\"ticks\":[not parsed]}");
		RecordingStore store = new RecordingStore(new Gson(), directory);
		assertEquals(id, store.list().get(0).recordingId);
	}

	@Test
	public void sanitizesFilepathNamesAndKeepsUniqueRecordings() throws Exception
	{
		Filepath directory = Filepath.Unchecked.getRooted(temporary.newFolder("names").toPath());
		RecordingStore store = new RecordingStore(new Gson(), directory);
		StoredRecording stored = new StoredRecording(store.save(recording().completed(2_000L)), "id");
		StoredRecording reserved = store.rename(stored, "CON.txt");
		assertEquals("Replay-CON.txt.json", reserved.path.getFileName());
		StoredRecording sanitized = store.rename(reserved, "../bad\\\\name~\u0001");
		assertTrue(sanitized.path.startsWith(directory));
		assertEquals("..-bad--name--.json", sanitized.path.getFileName());
		Filepath first = store.save(recording().completed(2_000L));
		Filepath second = store.save(recording().completed(2_000L));
		assertFalse(first.equals(second));
		store.delete(sanitized);
		assertFalse(sanitized.path.exists());
		assertEquals(2, store.list().size());
		try (java.util.stream.Stream<Filepath> paths = directory.walk(1))
		{
			assertFalse(paths.anyMatch(path -> path.getFileName().endsWith(".tmp")));
		}
	}

	private static byte[] readBytes(Filepath path) throws Exception
	{
		try (java.io.InputStream input = path.openInputStream())
		{
			return input.readAllBytes();
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
