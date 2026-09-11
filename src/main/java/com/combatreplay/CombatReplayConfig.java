package com.combatreplay;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("combatreplay")
public interface CombatReplayConfig extends Config
{
    @ConfigItem(
        keyName = "uploadEnabled",
        name = "Upload recordings",
        description = "Privately upload recordings after they are saved"
    )
    default boolean uploadEnabled()
    {
        return false;
    }

    @ConfigItem(
        keyName = "webAddress",
        name = "Web address",
        description = "Combat Replay web service address"
    )
    default String webAddress()
    {
        return "http://localhost:3000";
    }

    @ConfigItem(
        keyName = "cra201Diagnostics",
        name = "CRA-201 diagnostics",
        description = "Save owner-local Gauntlet signal diagnostics beside recordings; these files are never uploaded"
    )
    default boolean cra201Diagnostics()
    {
        return true;
    }

    @ConfigItem(
        keyName = "deviceToken",
        name = "Device token",
        description = "Token issued by device pairing",
        hidden = true,
        secret = true
    )
    default String deviceToken()
    {
        return "";
    }
}
