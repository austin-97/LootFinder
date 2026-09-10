package com.lootfinder;

import java.time.Instant;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class LootTrackerRepositoryTest
{
	@Test
	public void favoritesMatchNamesAndPreserveConfiguredOrder()
	{
		LootSource hunllef = LootTrackerRepository.convert(record(995, 20));
		LootSource zulrah = new LootSource("NPC", "Zulrah", Instant.EPOCH, Set.of(995));
		assertEquals(java.util.List.of(zulrah, hunllef), LootTrackerRepository.favorites(
			java.util.List.of(hunllef, zulrah), " zulRAH, Corrupted Hunllef, ZULRAH, missing, ,"));
		assertTrue(LootTrackerRepository.favorites(java.util.List.of(hunllef), "").isEmpty());
	}

	private LootTrackerRecord record(int... drops)
	{
		LootTrackerRecord record = new LootTrackerRecord();
		record.type = "NPC";
		record.name = "Corrupted Hunllef";
		record.last = Instant.ofEpochMilli(1789004691735L);
		record.drops = drops;
		return record;
	}

	@Test
	public void extractsIdsNotQuantities()
	{
		LootSource source = LootTrackerRepository.convert(record(3203, 55, 561, 3968, 23962, 1935, 995, 4028432));
		assertEquals(Set.of(3203, 561, 23962, 995), source.getItemIds());
		assertEquals(1789004691735L, source.getLast().toEpochMilli());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsUnpairedDrop()
	{
		LootTrackerRepository.convert(record(995, 100, 561));
	}

	@Test
	public void acceptsAllLootTypes()
	{
		for (String type : java.util.List.of("NPC", "PLAYER", "EVENT", "PICKPOCKET", "FUTURE_TYPE"))
		{
			LootTrackerRecord record = record(995, 100);
			record.type = type;
			assertEquals(type, LootTrackerRepository.convert(record).getType());
		}
	}

	@Test
	public void favoritesKeepSameNameAcrossTypes()
	{
		LootSource npc = new LootSource("NPC", "Guard", Instant.EPOCH, Set.of(995));
		LootSource pickpocket = new LootSource("PICKPOCKET", "Guard", Instant.EPOCH, Set.of(561));
		assertEquals(java.util.List.of(npc, pickpocket), LootTrackerRepository.favorites(
			java.util.List.of(npc, pickpocket), "Guard"));
	}

	@Test
	public void deduplicatesAndSkipsEmptyStacks()
	{
		assertEquals(Set.of(995), LootTrackerRepository.convert(record(995, 20, 995, 10, 561, 0)).getItemIds());
	}
}
