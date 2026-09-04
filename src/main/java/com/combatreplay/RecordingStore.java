package com.combatreplay;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.RuneLite;

@Singleton
final class RecordingStore
{
	private final Gson gson;
	private final Path directory;

	@Inject
	RecordingStore(Gson gson)
	{
		this(gson, RuneLite.RUNELITE_DIR.toPath().resolve("combat-replay"));
	}

	RecordingStore(Gson gson, Path directory)
	{
		this.gson = gson.newBuilder().serializeNulls().create();
		this.directory = directory;
	}

	Path directory()
	{
		return directory;
	}

	Path save(CombatRecording recording) throws IOException
	{
		Files.createDirectories(directory());
		String stem = safeFileName(recording.name == null ? "Combat replay" : recording.name);
		Path destination = uniquePath(directory(), stem);
		write(recording, destination);
		return destination;
	}

	List<StoredRecording> list() throws IOException
	{
		Files.createDirectories(directory());
		List<StoredRecording> recordings = new ArrayList<>();
		try (Stream<Path> paths = Files.list(directory()))
		{
			paths.filter(path -> path.getFileName().toString().endsWith(".json"))
				.sorted(Comparator.comparingLong(this::modified).reversed())
				.forEach(path -> {
					try
					{
						recordings.add(new StoredRecording(path, load(path)));
					}
					catch (IOException | RuntimeException ignored)
					{
						// Keep a corrupt or incompatible recording from hiding the rest of the library.
					}
				});
		}
		return recordings;
	}

	CombatRecording load(Path path) throws IOException
	{
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
		{
			JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
			return ReplayV1Format.decode(root);
		}
	}

	StoredRecording rename(StoredRecording stored, String name) throws IOException
	{
		CombatRecording renamed = stored.recording.renamed(name.trim());
		Path destination = uniquePath(directory(), safeFileName(name));
		write(renamed, destination);
		if (!destination.equals(stored.path))
		{
			Files.deleteIfExists(stored.path);
		}
		return new StoredRecording(destination, renamed);
	}

	void delete(StoredRecording recording) throws IOException
	{
		Files.deleteIfExists(recording.path);
	}

	private void write(CombatRecording recording, Path destination) throws IOException
	{
		Path temporary = Files.createTempFile(directory(), "combat-replay-", ".tmp");
		try
		{
			try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8))
			{
				gson.toJson(ReplayV1Format.encode(recording), writer);
			}
			try
			{
				Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
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
	}

	private long modified(Path path)
	{
		try
		{
			return Files.getLastModifiedTime(path).toMillis();
		}
		catch (IOException ignored)
		{
			return 0L;
		}
	}

	private static String safeFileName(String value)
	{
		String safe = value.replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s+", " ").trim();
		if (safe.isEmpty())
		{
			safe = "Combat replay";
		}
		return safe.length() > 100 ? safe.substring(0, 100).trim() : safe;
	}

	private static Path uniquePath(Path directory, String stem)
	{
		Path candidate = directory.resolve(stem + ".json");
		for (int suffix = 2; Files.exists(candidate); suffix++)
		{
			candidate = directory.resolve(stem + "-" + suffix + ".json");
		}
		return candidate;
	}
}
