package com.combatreplay;

import net.runelite.client.util.Filepath;

final class StoredRecording
{
	final Filepath path;
	final String recordingId;

	StoredRecording(Filepath path, String recordingId)
	{
		this.path = path;
		this.recordingId = recordingId;
	}

	@Override
	public String toString()
	{
		String filename = path.getFileName();
		return filename.endsWith(".json") ? filename.substring(0, filename.length() - 5) : filename;
	}
}
