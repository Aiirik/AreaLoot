package com.arealoot;

import lombok.Value;
import net.runelite.api.coords.WorldPoint;

@Value
class AreaLootItem
{
	private final int id;
	private final int quantity;
	private final String name;
	private final WorldPoint location;
	private final int distance;
	private final long geValue;
}
