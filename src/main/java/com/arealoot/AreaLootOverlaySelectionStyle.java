package com.arealoot;

public enum AreaLootOverlaySelectionStyle
{
	FILL("Fill"),
	OUTLINE("Outline");

	private final String name;

	AreaLootOverlaySelectionStyle(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
