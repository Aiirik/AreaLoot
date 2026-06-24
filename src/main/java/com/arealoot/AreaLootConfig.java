package com.arealoot;

import java.awt.Color;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup("area-loot")
public interface AreaLootConfig extends Config
{
	String GENERAL_SECTION = "general";
	String OVERLAY_LIST_SECTION = "overlayList";
	String HIGHLIGHT_SECTION = "highlight";

	@ConfigSection(
		name = "General",
		description = "Area Loot behavior",
		position = 0
	)
	String generalSection = GENERAL_SECTION;

	@ConfigSection(
		name = "Overlay List",
		description = "Overlay list position, sizing, and colors",
		position = 1
	)
	String overlayListSection = OVERLAY_LIST_SECTION;

	@ConfigSection(
		name = "Highlight",
		description = "Selected item highlight options",
		position = 2
	)
	String highlightSection = HIGHLIGHT_SECTION;

	@ConfigItem(
		keyName = "toggleHotkey",
		name = "Toggle hotkey",
		description = "Opens the Area Loot panel",
		position = 0,
		section = GENERAL_SECTION
	)
	default Keybind toggleHotkey()
	{
		return new Keybind(KeyEvent.VK_B, 0);
	}

	@ConfigItem(
		keyName = "showOverlayList",
		name = "Show overlay list",
		description = "Show a clickable loot list overlay when the Area Loot hotkey is pressed",
		position = 1,
		section = GENERAL_SECTION
	)
	default boolean showOverlayList()
	{
		return true;
	}

	@ConfigItem(
		keyName = "openSidePanel",
		name = "Open side panel",
		description = "Open the RuneLite side panel when the Area Loot hotkey is pressed",
		position = 2,
		section = GENERAL_SECTION
	)
	default boolean openSidePanel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showItemIcons",
		name = "Show item icons",
		description = "Show item icons next to item names in the side panel and overlay list",
		position = 3,
		section = GENERAL_SECTION
	)
	default boolean showItemIcons()
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
		description = "Maximum number of rows shown in the Area Loot overlay",
		position = 4,
		section = GENERAL_SECTION
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
		description = "Maximum tile distance from your player to show in the Area Loot panel",
		position = 5,
		section = GENERAL_SECTION
	)
	default int lootRadius()
	{
		return 12;
	}

	@Range(
		min = 0,
		max = 2000
	)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "overlayX",
		name = "X position",
		description = "Default overlay list X position before moving it in overlay edit mode",
		position = 0,
		section = OVERLAY_LIST_SECTION
	)
	default int overlayX()
	{
		return 8;
	}

	@Range(
		min = 0,
		max = 2000
	)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "overlayY",
		name = "Y position",
		description = "Default overlay list Y position before moving it in overlay edit mode",
		position = 1,
		section = OVERLAY_LIST_SECTION
	)
	default int overlayY()
	{
		return 80;
	}

	@Range(
		min = 100,
		max = 500
	)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "overlayWidth",
		name = "Width",
		description = "Overlay list width",
		position = 2,
		section = OVERLAY_LIST_SECTION
	)
	default int overlayWidth()
	{
		return 160;
	}

	@Alpha
	@ConfigItem(
		keyName = "overlayBackgroundColor",
		name = "Background",
		description = "Overlay list background color",
		position = 3,
		section = OVERLAY_LIST_SECTION
	)
	default Color overlayBackgroundColor()
	{
		return new Color(30, 30, 30, 190);
	}

	@Alpha
	@ConfigItem(
		keyName = "overlayBorderColor",
		name = "Border",
		description = "Overlay list border color",
		position = 4,
		section = OVERLAY_LIST_SECTION
	)
	default Color overlayBorderColor()
	{
		return new Color(23, 23, 23);
	}

	@ConfigItem(
		keyName = "overlayHeaderColor",
		name = "Header text",
		description = "Overlay list header text color",
		position = 5,
		section = OVERLAY_LIST_SECTION
	)
	default Color overlayHeaderColor()
	{
		return new Color(220, 138, 0);
	}

	@ConfigItem(
		keyName = "overlayTextColor",
		name = "Item text",
		description = "Overlay list item text color",
		position = 6,
		section = OVERLAY_LIST_SECTION
	)
	default Color overlayTextColor()
	{
		return new Color(198, 198, 198);
	}

	@ConfigItem(
		keyName = "overlaySecondaryTextColor",
		name = "Secondary text",
		description = "Overlay list distance and empty-state text color",
		position = 7,
		section = OVERLAY_LIST_SECTION
	)
	default Color overlaySecondaryTextColor()
	{
		return new Color(165, 165, 165);
	}

	@Alpha
	@ConfigItem(
		keyName = "overlaySelectedRowColor",
		name = "Selected row",
		description = "Overlay list selected row color",
		position = 8,
		section = OVERLAY_LIST_SECTION
	)
	default Color overlaySelectedRowColor()
	{
		return new Color(0, 200, 255, 65);
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightColor",
		name = "Tile fill color",
		description = "Fill color for the selected loot item's ground tile",
		position = 0,
		section = HIGHLIGHT_SECTION
	)
	default Color highlightColor()
	{
		return new Color(0x0F00C8FF, true);
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightOutlineColor",
		name = "Tile outline color",
		description = "Outline color for the selected loot item's ground tile",
		position = 1,
		section = HIGHLIGHT_SECTION
	)
	default Color highlightOutlineColor()
	{
		return new Color(0, 200, 255, 220);
	}

	@ConfigItem(
		keyName = "drawHighlightLine",
		name = "Draw highlight line",
		description = "Draw a line from your player to the highlighted loot item",
		position = 2,
		section = HIGHLIGHT_SECTION
	)
	default boolean drawHighlightLine()
	{
		return true;
	}

	@ConfigItem(
		keyName = "matchLineColor",
		name = "Match line color",
		description = "Use the tile outline color for the locator line",
		position = 3,
		section = HIGHLIGHT_SECTION
	)
	default boolean matchLineColor()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightLineColor",
		name = "Line color",
		description = "Line color for the selected loot item",
		position = 4,
		section = HIGHLIGHT_SECTION
	)
	default Color highlightLineColor()
	{
		return new Color(0, 200, 255, 220);
	}
}
