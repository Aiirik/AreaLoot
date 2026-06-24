package com.arealoot;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup("area-loot")
public interface AreaLootConfig extends Config
{
	@ConfigItem(
		keyName = "toggleHotkey",
		name = "Toggle hotkey",
		description = "Opens the Area Loot panel"
	)
	default Keybind toggleHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "openSidePanel",
		name = "Open side panel",
		description = "Open the RuneLite side panel when the Area Loot hotkey is pressed"
	)
	default boolean openSidePanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOverlayList",
		name = "Show overlay list",
		description = "Show a clickable loot list overlay when the Area Loot hotkey is pressed"
	)
	default boolean showOverlayList()
	{
		return true;
	}

	@Range(
		min = 1,
		max = 25
	)
	@ConfigItem(
		keyName = "maxOverlayItems",
		name = "Max overlay items",
		description = "Maximum number of rows shown in the Area Loot overlay"
	)
	default int maxOverlayItems()
	{
		return 12;
	}

	@Range(
		min = 1,
		max = 30
	)
	@ConfigItem(
		keyName = "lootRadius",
		name = "Loot radius",
		description = "Maximum tile distance from your player to show in the Area Loot panel"
	)
	default int lootRadius()
	{
		return 12;
	}

	@ConfigItem(
		keyName = "highlightColor",
		name = "Highlight color",
		description = "Ground tile color for the selected loot item"
	)
	default Color highlightColor()
	{
		return new Color(0, 200, 255, 90);
	}
}
