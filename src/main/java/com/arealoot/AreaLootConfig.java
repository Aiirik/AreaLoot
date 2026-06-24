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
	String OVERLAY_SECTION = "overlay";
	String SIDE_PANEL_SECTION = "sidePanel";
	String GENERAL_SECTION = "general";
	String HIGHLIGHT_SECTION = "highlight";

	@ConfigSection(
		name = "Overlay List",
		description = "Overlay list controls, sizing, and colors",
		position = 0
	)
	String overlaySection = OVERLAY_SECTION;

	@ConfigSection(
		name = "Side Panel",
		description = "RuneLite side panel controls",
		position = 1
	)
	String sidePanelSection = SIDE_PANEL_SECTION;

	@ConfigSection(
		name = "General",
		description = "Shared Area Loot behavior",
		position = 2
	)
	String generalSection = GENERAL_SECTION;

	@ConfigSection(
		name = "Highlight",
		description = "Selected item highlight options",
		position = 3
	)
	String highlightSection = HIGHLIGHT_SECTION;

	@ConfigItem(
		keyName = "toggleHotkey",
		name = "Overlay toggle hotkey",
		description = "Toggles the Area Loot overlay list",
		position = 0,
		section = OVERLAY_SECTION
	)
	default Keybind toggleHotkey()
	{
		return new Keybind(KeyEvent.VK_B, 0);
	}

	@ConfigItem(
		keyName = "autoShowHotkey",
		name = "Auto show/hide hotkey",
		description = "Toggles auto show/hide mode for the overlay list",
		position = 1,
		section = OVERLAY_SECTION
	)
	default Keybind autoShowHotkey()
	{
		return Keybind.NOT_SET;
	}

	@Range(
		min = 1,
		max = 25
	)
	@ConfigItem(
		keyName = "maxOverlayItems",
		name = "Max items",
		description = "Maximum number of rows shown in the Area Loot overlay",
		position = 2,
		section = OVERLAY_SECTION
	)
	default int maxOverlayItems()
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
		position = 3,
		section = OVERLAY_SECTION
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
		position = 4,
		section = OVERLAY_SECTION
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
		position = 5,
		section = OVERLAY_SECTION
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
		position = 6,
		section = OVERLAY_SECTION
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
		position = 7,
		section = OVERLAY_SECTION
	)
	default Color overlayBorderColor()
	{
		return new Color(23, 23, 23);
	}

	@ConfigItem(
		keyName = "overlayHeaderColor",
		name = "Header text",
		description = "Overlay list header text color",
		position = 8,
		section = OVERLAY_SECTION
	)
	default Color overlayHeaderColor()
	{
		return new Color(220, 138, 0);
	}

	@ConfigItem(
		keyName = "overlayTextColor",
		name = "Item text",
		description = "Overlay list item text color",
		position = 9,
		section = OVERLAY_SECTION
	)
	default Color overlayTextColor()
	{
		return new Color(198, 198, 198);
	}

	@ConfigItem(
		keyName = "overlaySecondaryTextColor",
		name = "Secondary text",
		description = "Overlay list distance and empty-state text color",
		position = 10,
		section = OVERLAY_SECTION
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
		position = 11,
		section = OVERLAY_SECTION
	)
	default Color overlaySelectedRowColor()
	{
		return new Color(0, 200, 255, 65);
	}

	@ConfigItem(
		keyName = "sidePanelHotkey",
		name = "Side panel hotkey",
		description = "Opens the Area Loot side panel",
		position = 0,
		section = SIDE_PANEL_SECTION
	)
	default Keybind sidePanelHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "showItemIcons",
		name = "Show item icons",
		description = "Show item icons next to item names in the side panel and overlay list",
		position = 0,
		section = GENERAL_SECTION
	)
	default boolean showItemIcons()
	{
		return true;
	}

	@Range(
		min = 1,
		max = 30
	)
	@ConfigItem(
		keyName = "lootRadius",
		name = "Loot radius",
		description = "Maximum tile distance from your player to show in the Area Loot panel",
		position = 1,
		section = GENERAL_SECTION
	)
	default int lootRadius()
	{
		return 12;
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
		name = "Line matches tile outline",
		description = "Use the tile outline color for the locator line instead of the separate line color",
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
