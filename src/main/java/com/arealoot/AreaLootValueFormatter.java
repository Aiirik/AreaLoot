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

	static String formatLongGeValue(long value)
	{
		String digits = Long.toString(value);
		StringBuilder formatted = new StringBuilder(digits.length() + (digits.length() - 1) / 3 + 2);
		for (int i = 0; i < digits.length(); i++)
		{
			if (i > 0 && (digits.length() - i) % 3 == 0)
			{
				formatted.append(',');
			}
			formatted.append(digits.charAt(i));
		}
		return formatted.append("gp").toString();
	}
}
