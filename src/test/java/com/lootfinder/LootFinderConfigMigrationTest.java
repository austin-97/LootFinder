package com.lootfinder;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class LootFinderConfigMigrationTest
{
	private void migrate(Map<String, String> values)
	{
		LootFinderConfigMigration.migrate((group, key) -> values.get(group + "." + key),
			(group, key, value) -> values.put(group + "." + key, value));
	}

	@Test
	public void migratesFavoritesAndLimitOverDefaults()
	{
		Map<String, String> values = new HashMap<>();
		values.put("LootFinder.favoriteNpcs", "Zulrah, Barrows");
		values.put("LootFinder.recentSources", "20");
		values.put("loot-finder.favoriteNpcs", "");
		values.put("loot-finder.recentSources", "5");
		migrate(values);
		assertEquals("Zulrah, Barrows", values.get("loot-finder.favoriteNpcs"));
		assertEquals("20", values.get("loot-finder.recentSources"));
		values.put("loot-finder.favoriteNpcs", "");
		values.put("loot-finder.recentSources", "5");
		migrate(values);
		assertEquals("", values.get("loot-finder.favoriteNpcs"));
		assertEquals("5", values.get("loot-finder.recentSources"));
	}

	@Test
	public void preservesExistingNonDefaultSettings()
	{
		Map<String, String> values = new HashMap<>();
		values.put("LootFinder.favoriteNpcs", "Zulrah");
		values.put("loot-finder.favoriteNpcs", "Barrows");
		migrate(values);
		assertEquals("Barrows", values.get("loot-finder.favoriteNpcs"));
	}
}
