package com.combatreplay;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import net.runelite.client.util.Filepath;
import java.nio.file.StandardCopyOption;

final class UploadSidecarStore
{
    private final Gson gson;
    private final Filepath directory;

    UploadSidecarStore(Gson gson, Filepath recordingDirectory)
    {
        this.gson = gson;
        this.directory = recordingDirectory.joinSegment(".uploads");
    }

    synchronized void save(UploadSidecarState state) throws IOException
    {
        directory.createDirectories();
        Filepath temporary = directory.createTempFile("upload-", ".tmp");
        try
        {
            try (Writer writer = temporary.openBufferedWriter())
            {
                gson.toJson(state, writer);
            }
            try
            {
                temporary.moveTo(path(state.recordingId), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException exception)
            {
                temporary.moveTo(path(state.recordingId), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            temporary.deleteIfExists();
        }
    }

    synchronized UploadSidecarState load(String recordingId) throws IOException
    {
        Filepath source = path(recordingId);
        if (!source.exists())
        {
            return null;
        }
        try (Reader reader = source.openBufferedReader())
        {
            return gson.fromJson(reader, UploadSidecarState.class);
        }
    }

    private Filepath path(String recordingId)
    {
        if (!recordingId.matches("[0-9a-fA-F-]{36}"))
        {
            throw new IllegalArgumentException("Invalid recording ID");
        }
        return directory.joinSegment(recordingId + ".json");
    }
}
