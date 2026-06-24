package com.arealoot;

public enum AreaLootDistanceMode
{
	NONE("None"),
	SHORT("Short form"),
	LONG("Long form");

	private final String name;

	AreaLootDistanceMode(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
