package com.combatreplay;

import java.nio.file.Path;

final class StoredRecording
{
	final Path path;
	final CombatRecording recording;

	StoredRecording(Path path, CombatRecording recording)
	{
		this.path = path;
		this.recording = recording;
	}

	@Override
	public String toString()
	{
		if (recording.name != null && !recording.name.trim().isEmpty())
		{
			return recording.name;
		}
		return path.getFileName().toString();
	}
}
