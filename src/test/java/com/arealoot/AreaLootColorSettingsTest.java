package com.arealoot;

import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class AreaLootColorSettingsTest
{
	@Test
	public void exportAndImportRoundTripsThemeColors()
	{
		Map<String, String> colors = new LinkedHashMap<>();
		colors.put("overlayBackgroundColor", "-11053225");
		colors.put("highlightMenuTextColor", "-16724737");
		colors.put("unknownColor", "-1");

		String json = AreaLootColorSettings.exportToJson("Test", colors, new Gson());
		Map<String, String> imported = AreaLootColorSettings.importFromJson(json);

		Assert.assertEquals(1, imported.size());
		Assert.assertEquals("-11053225", imported.get("overlayBackgroundColor"));
		Assert.assertFalse(imported.containsKey("highlightMenuTextColor"));
		Assert.assertFalse(imported.containsKey("unknownColor"));
	}

	@Test
	public void importTranslatesPlayerExamineThemeColors()
	{
		Map<String, String> imported = AreaLootColorSettings.importFromJson(
			"{\"plugin\":\"player-examine\",\"colors\":{\"overlayHeaderTextColor\":\"-1\",\"totalGeTextColor\":\"-2\",\"valueHighlightColor\":\"-3\"}}");

		Assert.assertEquals("-1", imported.get("overlayHeaderColor"));
		Assert.assertEquals("-2", imported.get("geValueTextColor"));
		Assert.assertEquals("-3", imported.get("overlaySelectedRowColor"));
		Assert.assertFalse(imported.containsKey("highlightMenuTextColor"));
	}

	@Test
	public void importAcceptsQuotedThemeJson()
	{
		Map<String, String> imported = AreaLootColorSettings.importFromJson(
			"\"{\\\"plugin\\\":\\\"area-loot\\\",\\\"colors\\\":{\\\"overlayTextColor\\\":\\\"-4\\\"}}\"");

		Assert.assertEquals("-4", imported.get("overlayTextColor"));
	}

	@Test
	public void importAcceptsStoredThemeMap()
	{
		Map<String, String> imported = AreaLootColorSettings.importFromJson(
			"{\"My theme\":\"{\\\"plugin\\\":\\\"area-loot\\\",\\\"colors\\\":{\\\"overlayBorderColor\\\":\\\"-5\\\"}}\"}");

		Assert.assertEquals("-5", imported.get("overlayBorderColor"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void importRejectsUnknownPlugins()
	{
		AreaLootColorSettings.importFromJson("{\"plugin\":\"unknown\",\"colors\":{}}");
	}
}
