package com.arealoot;

final class AreaLootValueFormatter
{
	private AreaLootValueFormatter()
	{
	}

	static String formatGeValue(long value)
	{
		if (value >= 10_000_000)
		{
			return (value / 1_000_000) + "m";
		}
		if (value >= 1_000_000)
		{
			return String.format("%.1fm", value / 1_000_000.0);
		}
		if (value >= 10_000)
		{
			return (value / 1_000) + "k";
		}
		if (value >= 1_000)
		{
			return String.format("%.1fk", value / 1_000.0);
		}
		return value + "gp";
	}
}
