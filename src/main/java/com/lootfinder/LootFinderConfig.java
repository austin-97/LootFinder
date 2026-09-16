package com.lootfinder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(LootFinderConfig.GROUP)
public interface LootFinderConfig extends Config
{
    String GROUP = "loot-finder";

    @ConfigItem(keyName = "menuLocation", name = "Menu location", description = "Use a separate icon or use existing bank buttons with a right click")
    default LootMenuLocation menuLocation()
    {
        return LootMenuLocation.SEPARATE_ICON;
    }

    // Retain the stored key so existing favorites remain saved.
    @ConfigItem(keyName = "favoriteNpcs", name = "Favorite loot sources", description = "Examples: Corrupted Hunllef, Seed pack")
    default String favoriteSources()
    {
        return "";
    }


    @Range(min = 1, max = 20)
    @ConfigItem(keyName = "recentSources", name = "Recent loot sources", description = "Number of recent Loot Tracker sources in the bank menu")
    default int recentSources()
    {
        return 5;
    }
}
