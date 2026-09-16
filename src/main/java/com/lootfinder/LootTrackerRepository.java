package com.lootfinder;

import com.google.gson.Gson;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Objects;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.loottracker.LootTrackerConfig;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.game.ItemStack;
import net.runelite.client.util.Text;

@Slf4j
final class LootTrackerRepository
{
	@Inject private ConfigManager configManager;
	@Inject private Gson gson;
	private String liveProfile;
	private final Map<List<String>, LootSource> liveSources = new HashMap<>();

	void clearLive()
	{
		liveSources.clear();
		liveProfile = null;
	}

	private void useProfile(String profile)
	{
		if (!Objects.equals(profile, liveProfile))
		{
			clearLive();
			liveProfile = profile;
		}
	}

	void record(String profile, LootReceived event)
	{
		useProfile(profile);
		if (profile == null || event.getItems().isEmpty()) return;
		Set<Integer> ids = new HashSet<>();
		for (ItemStack item : event.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0) ids.add(item.getId());
		}
		if (ids.isEmpty()) return;
		List<String> key = List.of(event.getType().name(), event.getName());
		LootSource previous = liveSources.get(key);
		if (previous != null) ids.addAll(previous.getItemIds());
		liveSources.put(key, new LootSource(event.getType().name(), event.getName(), Instant.now(), ids));
	}

	void removeLive(String profile, String configKey)
	{
		if (!Objects.equals(profile, liveProfile)) return;
		liveSources.entrySet().removeIf(entry -> configKey.equals("drops_" + entry.getKey().get(0) + "_" + entry.getKey().get(1)));
	}

	List<LootSource> recent(int limit)
	{
		String profile = configManager.getRSProfileKey();
		useProfile(profile);
		if (profile == null) return Collections.emptyList();
		Set<String> ignored = new HashSet<>(Text.fromCSV(
			configManager.getConfig(LootTrackerConfig.class).getIgnoredEvents()));
		List<LootSource> saved = new ArrayList<>();
		for (String key : configManager.getRSProfileConfigurationKeys("loottracker", profile, "drops_"))
		{
			try
			{
				LootTrackerRecord record = gson.fromJson(configManager.getConfiguration("loottracker", profile, key), LootTrackerRecord.class);
				LootSource source = convert(record);
				if (source != null)
				{
					saved.add(source);
				}
			}
			catch (RuntimeException ex)
			{
				log.debug("Skipping invalid loot record {}", key, ex);
			}
		}
		return recent(profile, saved, ignored, limit);
	}

	List<LootSource> recent(String profile, List<LootSource> saved, Set<String> ignored, int limit)
	{
		useProfile(profile);
		if (profile == null) return Collections.emptyList();
		Map<List<String>, LootSource> sources = new HashMap<>();
		for (LootSource source : saved)
		{
			sources.merge(List.of(source.getType(), source.getName()), source, (a, b) -> a.getLast().isAfter(b.getLast()) ? a : b);
		}
		for (Map.Entry<List<String>, LootSource> entry : liveSources.entrySet())
		{
			LootSource live = entry.getValue();
			Set<Integer> ids = new HashSet<>(live.getItemIds());
			LootSource stored = sources.get(entry.getKey());
			if (stored != null) ids.addAll(stored.getItemIds());
			// Saved timestamps reflect batch-save time; live timestamps reflect loot order.
			sources.put(entry.getKey(), new LootSource(live.getType(), live.getName(), live.getLast(), ids));
		}
		List<LootSource> result = new ArrayList<>(sources.values());
		result.removeIf(source -> ignored.contains(source.getName()));
		result.sort(Comparator.comparing(LootSource::getLast).reversed().thenComparing(LootSource::getName).thenComparing(LootSource::getType));
		return new ArrayList<>(result.subList(0, Math.min(Math.max(limit, 0), result.size())));
	}

	static LootSource convert(LootTrackerRecord record)
	{
		if (record == null) return null;
		if (record.type == null || record.type.trim().isEmpty()
			|| record.name == null || record.name.trim().isEmpty() || record.last == null
			|| record.drops == null || record.drops.length % 2 != 0)
		{
			throw new IllegalArgumentException("Incomplete loot record");
		}
		Set<Integer> ids = new HashSet<>();
		for (int i = 0; i < record.drops.length; i += 2)
		{
			if (record.drops[i] > 0 && record.drops[i + 1] > 0) ids.add(record.drops[i]);
		}
		return new LootSource(record.type, record.name, record.last, ids);
	}

	static List<LootSource> favorites(List<LootSource> sources, String names)
	{
		Map<String, List<LootSource>> byName = new HashMap<>();
		for (LootSource source : sources)
		{
			byName.computeIfAbsent(source.getName().toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(source);
		}
		Set<String> requested = new LinkedHashSet<>();
		if (names != null)
		{
			for (String name : names.split(",")) requested.add(name.trim().toLowerCase(Locale.ROOT));
		}
		List<LootSource> result = new ArrayList<>();
		for (String name : requested)
		{
			List<LootSource> matching = byName.get(name);
			if (matching != null) result.addAll(matching);
		}
		return result;
	}
}


