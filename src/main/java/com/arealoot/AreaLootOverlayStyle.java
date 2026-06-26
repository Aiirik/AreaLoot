package com.arealoot;

public enum AreaLootOverlayStyle
{
	LIST("List"),
	GRID("Grid");

	private final String name;

	AreaLootOverlayStyle(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
