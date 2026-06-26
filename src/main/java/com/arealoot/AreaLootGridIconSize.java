package com.arealoot;

public enum AreaLootGridIconSize
{
	SMALL("Small", 22),
	DEFAULT("Default", 24),
	LARGE("Large", 32);

	private final String name;
	private final int pixels;

	AreaLootGridIconSize(String name, int pixels)
	{
		this.name = name;
		this.pixels = pixels;
	}

	int getPixels()
	{
		return pixels;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
