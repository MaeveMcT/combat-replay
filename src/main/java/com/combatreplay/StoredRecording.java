package com.combatreplay;

import java.nio.file.Path;

final class StoredRecording
{
	final Path path;
	final String recordingId;

	StoredRecording(Path path, String recordingId)
	{
		this.path = path;
		this.recordingId = recordingId;
	}

	@Override
	public String toString()
	{
		String filename = path.getFileName().toString();
		return filename.endsWith(".json") ? filename.substring(0, filename.length() - 5) : filename;
	}
}
