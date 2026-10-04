package com.combatreplay;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import net.runelite.client.util.Filepath;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class RecordingStore
{
	private final Gson gson;
	private final Filepath directory;

	@Inject
	RecordingStore(Gson gson, Filepath directory)
	{
		this.gson = gson.newBuilder().serializeNulls().create();
		this.directory = directory;
	}

	Filepath directory()
	{
		return directory;
	}

	Filepath save(CombatRecording recording) throws IOException
	{
		directory().createDirectories();
		String stem = safeFileName(recording.name == null ? "Combat replay" : recording.name);
		Filepath destination = uniquePath(directory(), stem);
		write(recording, destination);
		return destination;
	}

	List<StoredRecording> list() throws IOException
	{
		directory().createDirectories();
		List<StoredRecording> recordings = new ArrayList<>();
		try (Stream<Filepath> paths = directory().walk(1))
		{
			paths.filter(path -> path.isFile() && path.getFileName().endsWith(".json"))
				.sorted(Comparator.comparingLong(this::modified).reversed())
				.forEach(path -> {
					try
					{
						recordings.add(new StoredRecording(path, recordingId(path)));
					}
					catch (IOException | RuntimeException ignored)
					{
						// Keep a corrupt or incompatible recording from hiding the rest of the library.
					}
				});
		}
		return recordings;
	}

	CombatRecording load(Filepath path) throws IOException
	{
		try (Reader reader = path.openBufferedReader())
		{
			JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
			return ReplayV1Format.decode(root);
		}
	}

	StoredRecording rename(StoredRecording stored, String name) throws IOException
	{
		Filepath destination = uniquePath(directory(), safeFileName(name));
		// A local rename must not change the upload bytes for this recording ID.
		stored.path.moveTo(destination);
		return new StoredRecording(destination, stored.recordingId);
	}

	void delete(StoredRecording recording) throws IOException
	{
		recording.path.deleteIfExists();
	}

	private String recordingId(Filepath path) throws IOException
	{
		String id = null;
		int version = -1;
		try (JsonReader reader = new JsonReader(path.openBufferedReader()))
		{
			reader.beginObject();
			while (reader.hasNext())
			{
				switch (reader.nextName())
				{
					case "recording_id": id = reader.nextString(); break;
					case "format_version": version = reader.nextInt(); break;
					default: reader.skipValue();
				}
					if (id != null && version == CombatRecording.FORMAT_VERSION) break;
			}
		}
		if (version != CombatRecording.FORMAT_VERSION || id == null
			|| !id.matches("[0-9a-fA-F-]{36}"))
		{
			throw new IOException("Unsupported recording metadata");
		}
		return id;
	}

	private void write(CombatRecording recording, Filepath destination) throws IOException
	{
		Filepath temporary = directory().createTempFile("combat-replay-", ".tmp");
		try
		{
			try (Writer writer = temporary.openBufferedWriter())
			{
				ReplayV1Format.write(recording, gson, writer);
			}
			try
			{
				temporary.moveTo(destination, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (IOException exception)
			{
				temporary.moveTo(destination, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		finally
		{
			temporary.deleteIfExists();
		}
	}

	private long modified(Filepath path)
	{
		try
		{
			return path.getLastModifiedTime().toMillis();
		}
		catch (IOException ignored)
		{
			return 0L;
		}
	}

	private static String safeFileName(String value)
	{
		String safe = value.replaceAll("[\\x00-\\x1f\\\\/:*?\"<>|~]", "-").replaceAll("\\s+", " ").trim();
		if (safe.isEmpty())
		{
			safe = "Combat replay";
		}
		return safe.length() > 100 ? safe.substring(0, 100).trim() : safe;
	}

	private static Filepath uniquePath(Filepath directory, String stem)
	{
		Filepath candidate;
		try
		{
			candidate = directory.joinSegment(stem + ".json");
		}
		catch (IllegalArgumentException exception)
		{
			// Prefix Windows device names (including names with extensions).
			stem = "Replay-" + stem;
			candidate = directory.joinSegment(stem + ".json");
		}
		for (int suffix = 2; candidate.exists(); suffix++)
		{
			candidate = directory.joinSegment(stem + "-" + suffix + ".json");
		}
		return candidate;
	}
}
