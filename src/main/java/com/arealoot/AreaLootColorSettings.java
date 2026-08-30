package com.arealoot;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;

final class AreaLootColorSettings
{
	static final String[] THEME_COLOR_KEYS = {
		"overlayBackgroundColor",
		"overlayBorderColor",
		"overlayHeaderColor",
		"overlayTextColor",
		"overlaySecondaryTextColor",
		"geValueTextColor",
		"tileDistanceTextColor",
		"lootCountTextColor",
		"totalGeValueLabelTextColor",
		"totalGeValueTextColor",
		"selectedItemNameLabelTextColor",
		"selectedItemNameTextColor",
		"overlaySelectedRowColor",
		"highlightColor",
		"highlightOutlineColor",
		"highlightLineColor",
		"highlightMinimapDotColor",
		"highlightMinimapLineColor",
		"highlightMenuTextColor"
	};

	private static final String EXPORT_PLUGIN = "area-loot";
	private static final String PLAYER_EXAMINE_PLUGIN = "player-examine";
	private static final int EXPORT_VERSION = 1;

	private AreaLootColorSettings()
	{
	}

	static String exportToJson(String themeName, Map<String, String> colors, Gson gson)
	{
		JsonObject root = new JsonObject();
		JsonObject colorObject = new JsonObject();
		root.addProperty("plugin", EXPORT_PLUGIN);
		root.addProperty("version", EXPORT_VERSION);
		root.addProperty("theme", themeName);
		root.add("colors", colorObject);

		for (Map.Entry<String, String> entry : colors.entrySet())
		{
			if (isThemeColorKey(entry.getKey()) && entry.getValue() != null)
			{
				colorObject.addProperty(entry.getKey(), entry.getValue());
			}
		}

		return gson.toJson(root);
	}

	static Map<String, String> importFromJson(String json)
	{
		JsonObject root = parseThemeRoot(json);
		JsonElement plugin = root.get("plugin");
		if (plugin == null)
		{
			throw new IllegalArgumentException("Text does not contain Area Loot theme JSON.");
		}

		JsonObject colors = root.getAsJsonObject("colors");
		if (colors == null)
		{
			throw new IllegalArgumentException("Text does not contain theme colors.");
		}

		String pluginName = plugin.getAsString();
		if (PLAYER_EXAMINE_PLUGIN.equals(pluginName))
		{
			return importPlayerExamineColors(colors);
		}
		if (!EXPORT_PLUGIN.equals(pluginName))
		{
			throw new IllegalArgumentException("Text does not contain Area Loot colors.");
		}

		Map<String, String> importedColors = new LinkedHashMap<>();
		for (String key : colors.keySet())
		{
			if (!isThemeColorKey(key))
			{
				continue;
			}

			JsonElement value = colors.get(key);
			if (value != null && !value.isJsonNull())
			{
				importedColors.put(key, value.getAsString());
			}
		}

		return importedColors;
	}

	private static JsonObject parseThemeRoot(String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			throw new IllegalArgumentException("Theme text is required.");
		}

		try
		{
			JsonElement element = new JsonParser().parse(json.trim());
			if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString())
			{
				element = new JsonParser().parse(element.getAsString());
			}
			if (!element.isJsonObject())
			{
				throw new IllegalArgumentException("Theme text must be a JSON object.");
			}

			JsonObject root = element.getAsJsonObject();
			if (root.has("plugin"))
			{
				return root;
			}

			for (String name : root.keySet())
			{
				JsonElement value = root.get(name);
				if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())
				{
					try
					{
						JsonElement nested = new JsonParser().parse(value.getAsString());
						if (nested.isJsonObject() && nested.getAsJsonObject().has("plugin"))
						{
							return nested.getAsJsonObject();
						}
					}
					catch (RuntimeException ignored)
					{
						// Keep looking; stored custom-theme maps contain JSON strings, ordinary objects may not.
					}
				}
			}

			return root;
		}
		catch (IllegalArgumentException ex)
		{
			throw ex;
		}
		catch (RuntimeException ex)
		{
			throw new IllegalArgumentException("Theme text is not valid JSON.");
		}
	}

	private static Map<String, String> importPlayerExamineColors(JsonObject colors)
	{
		Map<String, String> importedColors = new LinkedHashMap<>();
		putTranslatedColor(importedColors, colors, "overlayBackgroundColor", "overlayBackgroundColor");
		putTranslatedColor(importedColors, colors, "overlayBorderColor", "overlayBorderColor");
		putTranslatedColor(importedColors, colors, "overlayHeaderTextColor", "overlayHeaderColor");
		putTranslatedColor(importedColors, colors, "overlaySubTextColor", "overlayTextColor");
		putTranslatedColor(importedColors, colors, "overlaySubTextColor", "overlaySecondaryTextColor");
		putTranslatedColor(importedColors, colors, "totalGeTextColor", "geValueTextColor");
		putTranslatedColor(importedColors, colors, "totalGeTextColor", "totalGeValueTextColor");
		putTranslatedColor(importedColors, colors, "overlaySubTextColor", "tileDistanceTextColor");
		putTranslatedColor(importedColors, colors, "overlaySubTextColor", "lootCountTextColor");
		putTranslatedColor(importedColors, colors, "overlaySubTextColor", "totalGeValueLabelTextColor");
		putTranslatedColor(importedColors, colors, "overlaySubTextColor", "selectedItemNameLabelTextColor");
		putTranslatedColor(importedColors, colors, "overlayHeaderTextColor", "selectedItemNameTextColor");
		putTranslatedColor(importedColors, colors, "valueHighlightColor", "overlaySelectedRowColor");
		putTranslatedColor(importedColors, colors, "valueHighlightColor", "highlightColor");
		putTranslatedColor(importedColors, colors, "valueHighlightColor", "highlightOutlineColor");
		putTranslatedColor(importedColors, colors, "valueHighlightColor", "highlightLineColor");
		putTranslatedColor(importedColors, colors, "valueHighlightColor", "highlightMinimapDotColor");
		putTranslatedColor(importedColors, colors, "valueHighlightColor", "highlightMinimapLineColor");
		putTranslatedColor(importedColors, colors, "valueHighlightColor", "highlightMenuTextColor");
		return importedColors;
	}

	private static void putTranslatedColor(Map<String, String> importedColors, JsonObject colors, String fromKey, String toKey)
	{
		JsonElement value = colors.get(fromKey);
		if (value != null && !value.isJsonNull() && isThemeColorKey(toKey))
		{
			importedColors.put(toKey, value.getAsString());
		}
	}

	static boolean isThemeColorKey(String key)
	{
		for (String themeColorKey : THEME_COLOR_KEYS)
		{
			if (themeColorKey.equals(key))
			{
				return true;
			}
		}

		return false;
	}
}
