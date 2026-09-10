package com.lootfinder;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.banktags.BankTag;

final class LootBankTag implements BankTag
{
	@Inject private ItemManager itemManager;
	private Set<Integer> items = Collections.emptySet();

	void select(LootSource source)
	{
		Set<Integer> next = new HashSet<>();
		for (int id : source.getItemIds()) next.add(itemManager.canonicalize(id));
		items = next;
	}

	void clear()
	{
		items = Collections.emptySet();
	}

	@Override
	public boolean contains(int itemId)
	{
		return itemId > 0 && items.contains(itemManager.canonicalize(itemId));
	}
}
