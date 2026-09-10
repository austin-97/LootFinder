package com.lootfinder;

import java.util.function.BiFunction;

final class LootFinderConfigMigration
{
	@FunctionalInterface
	interface Writer
	{
		void set(String group, String key, String value);
	}

	private LootFinderConfigMigration() {}

	static void migrate(BiFunction<String, String, String> read, Writer write)
	{
		String group = LootFinderConfig.GROUP;
		if ("true".equals(read.apply(group, "legacyConfigMigrated"))) return;
		copy(read, write, "favoriteNpcs", "");
		copy(read, write, "recentSources", "5");
		write.set(group, "legacyConfigMigrated", "true");
	}

	private static void copy(BiFunction<String, String, String> read, Writer write, String key, String defaultValue)
	{
		String oldValue = read.apply("LootFinder", key);
		String current = read.apply(LootFinderConfig.GROUP, key);
		// Defaults may already have been populated when switching RuneLite profiles.
		if (oldValue != null && (current == null || current.equals(defaultValue)))
		{
			write.set(LootFinderConfig.GROUP, key, oldValue);
		}
	}
}
