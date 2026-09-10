package com.lootfinder;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;

@Getter
final class LootSource
{
	private final String name;
	private final String type;
	private final Instant last;
	private final Set<Integer> itemIds;

	LootSource(String type, String name, Instant last, Set<Integer> itemIds)
	{
		this.type = type;
		this.name = name;
		this.last = last;
		this.itemIds = Collections.unmodifiableSet(new HashSet<>(itemIds));
	}
}
