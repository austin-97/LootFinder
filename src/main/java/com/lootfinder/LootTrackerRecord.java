package com.lootfinder;

import java.time.Instant;

final class LootTrackerRecord
{
	String type;
	String name;
	Instant last;
	int[] drops;
}
