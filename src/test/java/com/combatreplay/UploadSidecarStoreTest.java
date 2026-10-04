package com.combatreplay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UploadSidecarStoreTest
{
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void persistsUploadProgressByRecordingIdWithoutCredentials() throws Exception
    {
        Path recordings = temporary.newFolder("recordings").toPath();
        UploadSidecarStore store = new UploadSidecarStore(new Gson(), net.runelite.client.util.Filepath.Unchecked.getRooted(recordings));
        String recordingId = "123e4567-e89b-42d3-a456-426614174000";
        UploadSidecarState state = new UploadSidecarState(recordingId,
            recordings.resolve("local.json").toString());
        state.status = "retrying";
        state.attempts = 2;
        state.remoteReplayId = 42;
        state.failureCode = "transient_upload_failure";

        store.save(state);
        UploadSidecarState loaded = store.load(recordingId);

        assertEquals("retrying", loaded.status);
        assertEquals(2, loaded.attempts);
        assertEquals(42, loaded.remoteReplayId);
        String sidecar = Files.readString(recordings.resolve(".uploads").resolve(recordingId + ".json"),
            StandardCharsets.UTF_8);
        assertFalse(sidecar.contains("deviceToken"));
        assertFalse(sidecar.contains("Bearer"));
    }
}
