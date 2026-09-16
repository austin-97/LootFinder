package com.lootfinder;

public enum LootMenuLocation
{
	SEPARATE_ICON("Separate icon"),
	BANK_SETTINGS("Bank wrench"),
	BANK_HELP("Bank help");

	private final String label;

	LootMenuLocation(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
