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
	String OVERLAY_ADJUSTMENTS_SECTION = "overlayAdjustments";
	String SIDE_PANEL_SECTION = "sidePanel";
	String GENERAL_SECTION = "general";
	String HIGHLIGHT_SECTION = "highlight";

	@ConfigSection(
		name = "Overlay",
		description = "Overlay hotkeys, style, and behavior",
		position = 0
	)
	String overlaySection = OVERLAY_SECTION;

	@ConfigSection(
		name = "Overlay Adjustments",
		description = "Overlay position, sizing, and colors",
		position = 1
	)
	String overlayAdjustmentsSection = OVERLAY_ADJUSTMENTS_SECTION;

	@ConfigSection(
		name = "Side Panel",
		description = "RuneLite side panel controls",
		position = 2
	)
	String sidePanelSection = SIDE_PANEL_SECTION;

	@ConfigSection(
		name = "General",
		description = "Shared Area Loot behavior",
		position = 3
	)
	String generalSection = GENERAL_SECTION;

	@ConfigSection(
		name = "Highlight Colors",
		description = "Selected item highlight colors",
		position = 4
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
		return new Keybind(KeyEvent.VK_V, 0);
	}

	@ConfigItem(
		keyName = "rememberOverlayMode",
		name = "Remember overlay mode",
		description = "Restore the overlay mode after logging back in",
		position = 2,
		section = OVERLAY_SECTION
	)
	default boolean rememberOverlayMode()
	{
		return false;
	}

	@ConfigItem(
		keyName = "animateOverlay",
		name = "Animate overlay",
		description = "Fade the overlay list in and out when it appears or hides",
		position = 3,
		section = OVERLAY_SECTION
	)
	default boolean animateOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayStyle",
		name = "Overlay style",
		description = "Choose whether the overlay shows a list or icon grid",
		position = 4,
		section = OVERLAY_SECTION
	)
	default AreaLootOverlayStyle overlayStyle()
	{
		return AreaLootOverlayStyle.LIST;
	}

	@Range(
		min = 1,
		max = 25
	)
	@ConfigItem(
		keyName = "maxOverlayItems",
		name = "List max items",
		description = "Maximum number of rows shown in the Area Loot overlay",
		position = 5,
		section = OVERLAY_SECTION
	)
	default int maxOverlayItems()
	{
		return 12;
	}

	@Range(
		min = 100,
		max = 500
	)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "overlayWidth",
		name = "List width",
		description = "Overlay list width",
		position = 6,
		section = OVERLAY_SECTION
	)
	default int overlayWidth()
	{
		return 140;
	}

	@Range(
		min = 1,
		max = 10
	)
	@ConfigItem(
		keyName = "gridColumns",
		name = "Grid columns",
		description = "Number of item columns shown in grid overlay style",
		position = 7,
		section = OVERLAY_SECTION
	)
	default int gridColumns()
	{
		return 5;
	}

	@Range(
		min = 1,
		max = 10
	)
	@ConfigItem(
		keyName = "gridRows",
		name = "Grid rows",
		description = "Number of item rows shown in grid overlay style",
		position = 8,
		section = OVERLAY_SECTION
	)
	default int gridRows()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "gridIconSize",
		name = "Grid icon size",
		description = "Choose the item icon size used by grid overlay style",
		position = 9,
		section = OVERLAY_SECTION
	)
	default AreaLootGridIconSize gridIconSize()
	{
		return AreaLootGridIconSize.DEFAULT;
	}

	@ConfigItem(
		keyName = "gridAutoAdjust",
		name = "Grid auto adjust",
		description = "Shrink the grid overlay to fit the number of visible items",
		position = 10,
		section = OVERLAY_SECTION
	)
	default boolean gridAutoAdjust()
	{
		return true;
	}

	@Range(
		min = 0,
		max = 2000
	)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "overlayX",
		name = "X position",
		description = "Default overlay X position before moving it in overlay edit mode",
		position = 0,
		section = OVERLAY_ADJUSTMENTS_SECTION
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
		description = "Default overlay Y position before moving it in overlay edit mode",
		position = 1,
		section = OVERLAY_ADJUSTMENTS_SECTION
	)
	default int overlayY()
	{
		return 80;
	}

	@Alpha
	@ConfigItem(
		keyName = "overlayBackgroundColor",
		name = "Background",
		description = "Overlay background color",
		position = 2,
		section = OVERLAY_ADJUSTMENTS_SECTION
	)
	default Color overlayBackgroundColor()
	{
		return new Color(30, 30, 30, 190);
	}

	@Alpha
	@ConfigItem(
		keyName = "overlayBorderColor",
		name = "Border",
		description = "Overlay border color",
		position = 3,
		section = OVERLAY_ADJUSTMENTS_SECTION
	)
	default Color overlayBorderColor()
	{
		return new Color(23, 23, 23);
	}

	@ConfigItem(
		keyName = "overlayHeaderColor",
		name = "Header text",
		description = "Overlay header text color",
		position = 4,
		section = OVERLAY_ADJUSTMENTS_SECTION
	)
	default Color overlayHeaderColor()
	{
		return new Color(220, 138, 0);
	}

	@ConfigItem(
		keyName = "overlayTextColor",
		name = "Item text",
		description = "Overlay item text color",
		position = 5,
		section = OVERLAY_ADJUSTMENTS_SECTION
	)
	default Color overlayTextColor()
	{
		return new Color(198, 198, 198);
	}

	@ConfigItem(
		keyName = "overlaySecondaryTextColor",
		name = "Status text",
		description = "Overlay status and empty message text color, such as No nearby loot and auto mode messages",
		position = 6,
		section = OVERLAY_ADJUSTMENTS_SECTION
	)
	default Color overlaySecondaryTextColor()
	{
		return new Color(165, 165, 165);
	}

	@ConfigItem(
		keyName = "geValueTextColor",
		name = "GE value text",
		description = "GE value text color in the overlay list and side panel",
		position = 7,
		section = OVERLAY_ADJUSTMENTS_SECTION
	)
	default Color geValueTextColor()
	{
		return new Color(210, 190, 35);
	}

	@ConfigItem(
		keyName = "tileDistanceTextColor",
		name = "Tile distance text",
		description = "Tile distance text color in the overlay list and side panel",
		position = 8,
		section = OVERLAY_ADJUSTMENTS_SECTION
	)
	default Color tileDistanceTextColor()
	{
		return new Color(165, 165, 165);
	}

	@Alpha
	@ConfigItem(
		keyName = "overlaySelectedRowColor",
		name = "Selected row",
		description = "Overlay list selected row color",
		position = 9,
		section = OVERLAY_ADJUSTMENTS_SECTION
	)
	default Color overlaySelectedRowColor()
	{
		return new Color(0, 200, 255, 65);
	}

	@ConfigItem(
		keyName = "sidePanelEnabled",
		name = "Enable side panel",
		description = "Show Area Loot in the RuneLite side panel",
		position = 0,
		section = SIDE_PANEL_SECTION
	)
	default boolean sidePanelEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sidePanelHotkey",
		name = "Side panel hotkey",
		description = "Opens the Area Loot side panel",
		position = 1,
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

	@ConfigItem(
		keyName = "tileDistanceMode",
		name = "Show tile distance",
		description = "Choose whether and how each loot item's distance is shown",
		position = 1,
		section = GENERAL_SECTION
	)
	default AreaLootDistanceMode tileDistanceMode()
	{
		return AreaLootDistanceMode.SHORT;
	}

	@ConfigItem(
		keyName = "showGeValue",
		name = "Show GE value",
		description = "Show each loot item's total Grand Exchange value",
		position = 2,
		section = GENERAL_SECTION
	)
	default boolean showGeValue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sortMode",
		name = "Sort list by",
		description = "Choose how Area Loot sorts nearby ground items",
		position = 3,
		section = GENERAL_SECTION
	)
	default AreaLootSortMode sortMode()
	{
		return AreaLootSortMode.NEAREST;
	}

	@ConfigItem(
		keyName = "minimumGeValue",
		name = "Minimum GE value",
		description = "Only show drops worth at least this much GP. Supports values like 1000, 10k, or 1m",
		position = 4,
		section = GENERAL_SECTION
	)
	default String minimumGeValue()
	{
		return "0";
	}

	@ConfigItem(
		keyName = "blockedItems",
		name = "Blocked items",
		description = "Comma-separated item names or wildcard patterns to hide, such as Ashes, Bones, Burnt *",
		position = 5,
		section = GENERAL_SECTION
	)
	default String blockedItems()
	{
		return "";
	}

	@ConfigItem(
		keyName = "blockedItems",
		name = "",
		description = ""
	)
	void setBlockedItems(String blockedItems);

	@ConfigItem(
		keyName = "shiftRightClickBlockItems",
		name = "Shift+right-click block/unblock",
		description = "Add a Shift+right-click menu option on ground items to add or remove them from the blocked item list",
		position = 6,
		section = GENERAL_SECTION
	)
	default boolean shiftRightClickBlockItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "drawHighlightLine",
		name = "Draw highlight line",
		description = "Draw a line from your player to the highlighted loot item",
		position = 7,
		section = GENERAL_SECTION
	)
	default boolean drawHighlightLine()
	{
		return true;
	}

	@ConfigItem(
		keyName = "onlyShowHighlightedItemMenu",
		name = "Only show highlighted item",
		description = "When right-clicking the highlighted item's tile, hide other ground items from that menu",
		position = 8,
		section = GENERAL_SECTION
	)
	default boolean onlyShowHighlightedItemMenu()
	{
		return false;
	}

	@Range(
		min = 1,
		max = 30
	)
	@ConfigItem(
		keyName = "lootRadius",
		name = "Loot radius",
		description = "Maximum tile distance from your player to show in the Area Loot (1-30)",
		position = 9,
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
		keyName = "matchLineColor",
		name = "Line matches tile outline",
		description = "Use the tile outline color for the locator line instead of the separate line color",
		position = 2,
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
		position = 3,
		section = HIGHLIGHT_SECTION
	)
	default Color highlightLineColor()
	{
		return new Color(0, 200, 255, 220);
	}
}
