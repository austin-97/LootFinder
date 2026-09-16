package com.lootfinder;

import java.time.Instant;
import java.util.Set;
import java.util.List;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;
import static org.junit.Assert.*;

public class LootTrackerRepositoryTest
{
	private LootReceived loot(String name, int itemId)
	{
		return new LootReceived(name, 1, LootRecordType.NPC, List.of(new ItemStack(itemId, 1)), 1, null);
	}

	@Test
	public void liveLootAppearsBeforeItIsSavedAndMergesItemHistory()
	{
		LootTrackerRepository repository = new LootTrackerRepository();
		LootSource stored = new LootSource("NPC", "Guard", Instant.EPOCH, Set.of(995));
		LootSource other = new LootSource("NPC", "Cow", Instant.EPOCH.plusSeconds(1), Set.of(1739));
		repository.record("account-a", loot("Guard", 561));
		repository.record("account-a", loot("Guard", 562));
		List<LootSource> recent = repository.recent("account-a", List.of(stored, other), Set.of(), 1);
		assertEquals(1, recent.size());
		assertEquals("Guard", recent.get(0).getName());
		assertEquals(Set.of(995, 561, 562), recent.get(0).getItemIds());
		assertTrue(repository.recent("account-a", List.of(stored), Set.of("Guard"), 5).isEmpty());
	}

	@Test
	public void batchSaveDoesNotReplaceLiveLootTimeOrDuplicateSource()
	{
		LootTrackerRepository repository = new LootTrackerRepository();
		repository.record("account-a", loot("Guard", 561));
		LootSource live = repository.recent("account-a", List.of(), Set.of(), 5).get(0);
		LootSource saved = new LootSource("NPC", "Guard", live.getLast().plusSeconds(60), Set.of(995, 561));
		List<LootSource> recent = repository.recent("account-a", List.of(saved), Set.of(), 5);
		assertEquals(1, recent.size());
		assertEquals(live.getLast(), recent.get(0).getLast());
		assertEquals(Set.of(995, 561), recent.get(0).getItemIds());
	}

	@Test
	public void liveLootDoesNotLeakBetweenAccounts()
	{
		LootTrackerRepository repository = new LootTrackerRepository();
		repository.record("account-a", loot("Guard", 561));
		assertTrue(repository.recent("account-b", List.of(), Set.of(), 5).isEmpty());
		repository.record("account-b", loot("Cow", 1739));
		assertEquals("Cow", repository.recent("account-b", List.of(), Set.of(), 5).get(0).getName());
		assertTrue(repository.recent("account-a", List.of(), Set.of(), 5).isEmpty());
	}

	@Test
	public void deletingRecordRemovesLiveLootOnlyForMatchingProfile()
	{
		LootTrackerRepository repository = new LootTrackerRepository();
		repository.record("account-a", loot("Guard", 561));
		repository.removeLive("account-b", "drops_NPC_Guard");
		assertEquals(1, repository.recent("account-a", List.of(), Set.of(), 5).size());
		repository.removeLive("account-a", "drops_NPC_Guard");
		assertTrue(repository.recent("account-a", List.of(), Set.of(), 5).isEmpty());
	}

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
