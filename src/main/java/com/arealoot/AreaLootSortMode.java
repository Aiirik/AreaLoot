package com.arealoot;

public enum AreaLootSortMode
{
	NEAREST("Nearest"),
	GE_HIGH_TO_LOW("GE price High-low");

	private final String name;

	AreaLootSortMode(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
