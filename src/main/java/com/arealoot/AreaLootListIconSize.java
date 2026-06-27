package com.arealoot;

public enum AreaLootListIconSize
{
	DEFAULT("Default", 18),
	MEDIUM("Medium", 22),
	LARGE("Large", 26);

	private final String name;
	private final int pixels;

	AreaLootListIconSize(String name, int pixels)
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
