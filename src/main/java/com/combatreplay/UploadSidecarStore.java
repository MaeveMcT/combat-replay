package com.combatreplay;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class UploadSidecarStore
{
    private final Gson gson;
    private final Path directory;

    UploadSidecarStore(Gson gson, Path recordingDirectory)
    {
        this.gson = gson;
        this.directory = recordingDirectory.resolve(".uploads");
    }

    synchronized void save(UploadSidecarState state) throws IOException
    {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "upload-", ".tmp");
        try
        {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8))
            {
                gson.toJson(state, writer);
            }
            try
            {
                Files.move(temporary, path(state.recordingId), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException exception)
            {
                Files.move(temporary, path(state.recordingId), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }

    synchronized UploadSidecarState load(String recordingId) throws IOException
    {
        Path source = path(recordingId);
        if (!Files.exists(source))
        {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8))
        {
            return gson.fromJson(reader, UploadSidecarState.class);
        }
    }

    private Path path(String recordingId)
    {
        if (!recordingId.matches("[0-9a-fA-F-]{36}"))
        {
            throw new IllegalArgumentException("Invalid recording ID");
        }
        return directory.resolve(recordingId + ".json");
    }
}
