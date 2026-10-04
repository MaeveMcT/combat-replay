package com.combatreplay;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.client.ui.ClientToolbar;
import org.junit.Test;

public class CombatReplayLifecycleTest
{
    @Test
    public void recordingLimitStopsAndSavesInsteadOfDroppingFurtherTicks() throws Exception
    {
        CombatReplayPlugin plugin = new CombatReplayPlugin();
        CombatRecorder recorder = mock(CombatRecorder.class);
        GauntletSignalDiagnostics diagnostics = mock(GauntletSignalDiagnostics.class);
        CombatReplayPanel panel = mock(CombatReplayPanel.class);
        RecordingStore store = mock(RecordingStore.class);
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        CombatRecording recording = recordingWithOneTick();
        when(recorder.isRecording()).thenReturn(true);
        when(recorder.tickCount()).thenReturn(CombatRecorder.MAX_TICKS);
        when(recorder.stop()).thenReturn(recording);
        when(store.save(recording)).thenReturn(TestFilepaths.filepath(Paths.get("recording.json")));
        org.mockito.Mockito.doAnswer(invocation ->
        {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
        inject(plugin, "recorder", recorder);
        inject(plugin, "gauntletDiagnostics", diagnostics);
        inject(plugin, "panel", panel);
        inject(plugin, "store", store);
        inject(plugin, "executor", executor);

        plugin.onGameTick(null);

        verify(recorder).stop();
        verify(store).save(recording);
        verify(panel).recordingStopped(recording, "30-minute limit reached; saved recording.json", true);
    }

    @Test
    public void shutdownStopsAndSavesAnActiveManualRecording() throws Exception
    {
        CombatReplayPlugin plugin = new CombatReplayPlugin();
        CombatRecorder recorder = mock(CombatRecorder.class);
        GauntletSignalDiagnostics diagnostics = mock(GauntletSignalDiagnostics.class);
        CombatReplayPanel panel = mock(CombatReplayPanel.class);
        RecordingStore store = mock(RecordingStore.class);
        ClientToolbar toolbar = mock(ClientToolbar.class);
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        CombatRecording recording = recordingWithOneTick();
        when(recorder.isRecording()).thenReturn(true);
        when(recorder.stop()).thenReturn(recording);
        when(store.save(recording)).thenReturn(TestFilepaths.filepath(Paths.get("recording.json")));
        org.mockito.Mockito.doAnswer(invocation ->
        {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));

        inject(plugin, "recorder", recorder);
        inject(plugin, "gauntletDiagnostics", diagnostics);
        inject(plugin, "panel", panel);
        inject(plugin, "store", store);
        inject(plugin, "clientToolbar", toolbar);
        inject(plugin, "executor", executor);

        plugin.shutDown();

        verify(diagnostics).stop();
        verify(recorder).stop();
        verify(store).save(recording);
        verify(panel).reset();
    }

    private static CombatRecording recordingWithOneTick()
    {
        CombatRecording recording = new CombatRecording(1_000L);
        recording.add(new RecordedTick(0, 100, 3200, 3200, false,
            90, 99, 70, 70, Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
        return recording.completed(1_600L);
    }

    private static void inject(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
