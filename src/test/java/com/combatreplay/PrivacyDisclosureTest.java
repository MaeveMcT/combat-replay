package com.combatreplay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrivacyDisclosureTest
{
    @Test
    public void recordingUiDisclosesExactNamesAndPrivateUpload()
    {
        String disclosure = CombatReplayPanel.PRIVACY_DISCLOSURE;

        assertTrue(disclosure.contains("exact names of all visible players"));
        assertTrue(disclosure.contains("uploaded privately"));
        assertTrue(disclosure.contains("client observations"));
    }

    @Test
    public void uploadsAndExtraDiagnosticsRequireExplicitSetup()
    {
        CombatReplayConfig config = new CombatReplayConfig() { };

        assertFalse(config.uploadEnabled());
        assertFalse(config.cra201Diagnostics());
        assertTrue(config.webAddress().isEmpty());
        assertTrue(config.deviceToken().isEmpty());
    }
}
