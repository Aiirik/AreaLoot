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
	String OVERLAY_LIST_SECTION = "overlayList";
	String OVERLAY_GRID_SECTION = "overlayGrid";
	String OVERLAY_ADJUSTMENTS_SECTION = "overlayAdjustments";
	String SIDE_PANEL_SECTION = "sidePanel";
	String MENU_SECTION = "menu";
	String MINIMAP_SECTION = "minimap";
	String FILTER_LISTS_SECTION = "filterLists";
	String GENERAL_SECTION = "general";
	String HIGHLIGHT_SECTION = "highlight";

	enum MenuHighlightMode
	{
		NONE("None"),
		TAKE("Take"),
		EXAMINE("Examine"),
		TAKE_AND_EXAMINE("Both");

		private final String name;

		MenuHighlightMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum OverlayStyle
	{
		LIST("List"),
		GRID("Grid");

		private final String name;

		OverlayStyle(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum OverlayItemDelay
	{
		NONE("0 seconds", 0),
		ONE("1 second", 1),
		TWO("2 seconds", 2),
		THREE("3 seconds", 3),
		FOUR("4 seconds", 4),
		FIVE("5 seconds", 5),
		SIX("6 seconds", 6),
		SEVEN("7 seconds", 7),
		EIGHT("8 seconds", 8),
		NINE("9 seconds", 9),
		TEN("10 seconds", 10);

		private final String name;
		private final int seconds;

		OverlayItemDelay(String name, int seconds)
		{
			this.name = name;
			this.seconds = seconds;
		}

		int getSeconds()
		{
			return seconds;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum ListIconSize
	{
		NONE("None", 0),
		DEFAULT("Small", 18),
		MEDIUM("Default", 22),
		LARGE("Large", 26);

		private final String name;
		private final int pixels;

		ListIconSize(String name, int pixels)
		{
			this.name = name;
			this.pixels = pixels;
		}

		int getPixels()
		{
			return pixels;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum GridIconSize
	{
		SMALL("Small", 22),
		DEFAULT("Default", 24),
		LARGE("Large", 32);

		private final String name;
		private final int pixels;

		GridIconSize(String name, int pixels)
		{
			this.name = name;
			this.pixels = pixels;
		}

		int getPixels()
		{
			return pixels;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum GridFillDirection
	{
		HORIZONTAL("Horizontal"),
		VERTICAL("Vertical");

		private final String name;

		GridFillDirection(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum OverlaySelectionStyle
	{
		FILL("Fill"),
		OUTLINE("Outline");

		private final String name;

		OverlaySelectionStyle(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum DistanceMode
	{
		NONE("None"),
		SHORT("Short form"),
		LONG("Long form");

		private final String name;

		DistanceMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum SortMode
	{
		NEAREST("Nearest"),
		GE_HIGH_TO_LOW("GE price High-low");

		private final String name;

		SortMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum TotalGeValueMode
	{
		NONE("None"),
		SHORT("Short"),
		LONG("Long");

		private final String name;

		TotalGeValueMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum SelectedItemFooterMode
	{
		OFF("Off"),
		SHORT("Short"),
		LONG("Long");

		private final String name;

		SelectedItemFooterMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	@ConfigSection(
		name = "General",
		description = "Core loot behavior and hotkeys",
		position = 0
	)
	String generalSection = GENERAL_SECTION;

	@ConfigSection(
		name = "Overlay",
		description = "Overlay display and behavior",
		position = 1,
		closedByDefault = true
	)
	String overlaySection = OVERLAY_SECTION;

	@ConfigSection(
		name = "Whitelist / Blocklist",
		description = "Item whitelist and blocklist controls",
		position = 7,
		closedByDefault = true
	)
	String filterListsSection = FILTER_LISTS_SECTION;

	@ConfigSection(
		name = "Overlay List Settings",
		description = "List overlay controls",
		position = 2,
		closedByDefault = true
	)
	String overlayListSection = OVERLAY_LIST_SECTION;

	@ConfigSection(
		name = "Overlay Grid Settings",
		description = "Grid overlay controls",
		position = 3,
		closedByDefault = true
	)
	String overlayGridSection = OVERLAY_GRID_SECTION;

	@ConfigSection(
		name = "Menu Settings",
		description = "Right-click menu controls",
		position = 4,
		closedByDefault = true
	)
	String menuSection = MENU_SECTION;

	@ConfigSection(
		name = "Minimap Settings",
		description = "Minimap markers for the selected loot item",
		position = 5,
		closedByDefault = true
	)
	String minimapSection = MINIMAP_SECTION;

	@ConfigSection(
		name = "Side Panel",
		description = "RuneLite side panel controls",
		position = 6,
		closedByDefault = true
	)
	String sidePanelSection = SIDE_PANEL_SECTION;

	@ConfigSection(
		name = "Overlay Colors",
		description = "Overlay text, background, and selection colors",
		position = 8,
		closedByDefault = true
	)
	String overlayAdjustmentsSection = OVERLAY_ADJUSTMENTS_SECTION;

	@ConfigSection(
		name = "Highlight Colors",
		description = "Selected item tile and line colors",
		position = 9,
		closedByDefault = true
	)
	String highlightSection = HIGHLIGHT_SECTION;

	@ConfigItem(
		keyName = "toggleHotkey",
		name = "Overlay toggle hotkey",
		description = "Toggles the Area Loot overlay list",
		position = 0,
		section = GENERAL_SECTION
	)
	default Keybind toggleHotkey()
	{
		return new Keybind(KeyEvent.VK_X, KeyEvent.CTRL_DOWN_MASK);
	}

	@ConfigItem(
		keyName = "autoShowHotkey",
		name = "Auto show/hide hotkey",
		description = "Toggles auto show/hide mode for the overlay list",
		position = 1,
		section = GENERAL_SECTION
	)
	default Keybind autoShowHotkey()
	{
		return new Keybind(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK);
	}

	@ConfigItem(
		keyName = "rememberOverlayMode",
		name = "Remember overlay mode",
		description = "Restore the overlay mode after logging back in",
		position = 2,
		section = GENERAL_SECTION
	)
	default boolean rememberOverlayMode()
	{
		return true;
	}

	@ConfigItem(
		keyName = "animateOverlay",
		name = "Fade in/out overlay",
		description = "Fade the overlay list in and out when it appears or hides",
		position = 4,
		section = GENERAL_SECTION
	)
	default boolean animateOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayStyle",
		name = "Overlay style",
		description = "Choose whether the overlay shows a list or icon grid",
		position = 0,
		section = OVERLAY_SECTION
	)
	default OverlayStyle overlayStyle()
	{
		return OverlayStyle.LIST;
	}

	@ConfigItem(
		keyName = "keepOverlayAboveGame",
		name = "Keep overlay above game",
		description = "Draw the Area Loot overlay above in-game actors and scene elements",
		position = 3,
		section = GENERAL_SECTION
	)
	default boolean keepOverlayAboveGame()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayItemDelay",
		name = "Show item delay",
		description = "<html>Select a delay to show newly dropped items in Area Loot <br> This helps when drops are constantly picked up instantly and prevents overlay spam</html>",
		position = 3,
		section = OVERLAY_SECTION
	)
	default OverlayItemDelay overlayItemDelay()
	{
		return OverlayItemDelay.NONE;
	}

	@Range(
		min = 1,
		max = 25
	)
	@ConfigItem(
		keyName = "maxOverlayItems",
		name = "List max items",
		description = "Maximum number of rows shown in the Area Loot overlay",
		position = 0,
		section = OVERLAY_LIST_SECTION
	)
	default int maxOverlayItems()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "listIconSize",
		name = "List icon size",
		description = "Choose whether item icons are hidden or shown with the selected size",
		position = 1,
		section = OVERLAY_LIST_SECTION
	)
	default ListIconSize listIconSize()
	{
		return ListIconSize.MEDIUM;
	}

	@ConfigItem(
		keyName = "showItemNamesInListMode",
		name = "List item names",
		description = "Show item names in the overlay list",
		position = 2,
		section = OVERLAY_LIST_SECTION
	)
	default boolean showItemNamesInListMode()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useListMinimumWidth",
		name = "List minimum width",
		description = "Keep list mode at least the configured width while still allowing it to expand for longer content",
		position = 3,
		section = OVERLAY_LIST_SECTION
	)
	default boolean useListMinimumWidth()
	{
		return false;
	}

	@Range(
		min = 80,
		max = 500
	)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "listMinimumWidth",
		name = "List minimum width",
		description = "Minimum list mode width when enabled",
		position = 4,
		section = OVERLAY_LIST_SECTION
	)
	default int listMinimumWidth()
	{
		return 160;
	}

	@Range(
		min = 1,
		max = 10
	)
	@ConfigItem(
		keyName = "gridColumns",
		name = "Grid columns",
		description = "Number of item columns shown in grid overlay style",
		position = 0,
		section = OVERLAY_GRID_SECTION
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
		position = 1,
		section = OVERLAY_GRID_SECTION
	)
	default int gridRows()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "gridIconSize",
		name = "Grid icon size",
		description = "Choose the item icon size used by grid overlay style",
		position = 2,
		section = OVERLAY_GRID_SECTION
	)
	default GridIconSize gridIconSize()
	{
		return GridIconSize.DEFAULT;
	}

	@ConfigItem(
		keyName = "gridAutoAdjust",
		name = "Grid auto adjust",
		description = "Shrink the grid below the configured size when fewer items are visible; width stabilizes once it reaches the configured column count",
		position = 3,
		section = OVERLAY_GRID_SECTION
	)
	default boolean gridAutoAdjust()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gridFillDirection",
		name = "Grid fill direction",
		description = "Choose whether grid items fill left-to-right or top-to-bottom",
		position = 4,
		section = OVERLAY_GRID_SECTION
	)
	default GridFillDirection gridFillDirection()
	{
		return GridFillDirection.HORIZONTAL;
	}

	@ConfigItem(
		keyName = "overlaySelectionStyle",
		name = "Selected item style",
		description = "Choose how the selected item is shown in the overlay",
		position = 14,
		section = OVERLAY_SECTION
	)
	default OverlaySelectionStyle overlaySelectionStyle()
	{
		return OverlaySelectionStyle.FILL;
	}

	@ConfigItem(
		keyName = "showSelectedItemName",
		name = "Selected item name on tile",
		description = "Show the selected loot item's name over the highlighted tile",
		position = 7,
		section = GENERAL_SECTION
	)
	default boolean showSelectedItemName()
	{
		return false;
	}

	@ConfigItem(
		keyName = "disableUpdateNotifications",
		name = "Disable update notifications",
		description = "Hide the chatbox message shown when Area Loot updates",
		position = 8,
		section = GENERAL_SECTION
	)
	default boolean disableUpdateNotifications()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showSelectedItemNameInOverlay",
		name = "Selected item name",
		description = "Choose how the selected loot item is shown in the overlay footer",
		position = 6,
		section = OVERLAY_SECTION
	)
	default SelectedItemFooterMode showSelectedItemNameInOverlay()
	{
		return SelectedItemFooterMode.OFF;
	}

	@Alpha
	@ConfigItem(
		keyName = "overlayBackgroundColor",
		name = "Background",
		description = "Overlay background color",
		position = 0,
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
		position = 1,
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
		position = 2,
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
		position = 3,
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
		position = 4,
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
		position = 5,
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
		position = 6,
		section = OVERLAY_ADJUSTMENTS_SECTION
	)
	default Color tileDistanceTextColor()
	{
		return new Color(165, 165, 165);
	}

	@ConfigItem(
		keyName = "lootCountTextColor",
		name = "Loot count text",
		description = "Loot count footer text color",
		position = 7,
		section = OVERLAY_ADJUSTMENTS_SECTION
	)
	default Color lootCountTextColor()
	{
		return new Color(165, 165, 165);
	}

	@ConfigItem(
		keyName = "totalGeValueLabelTextColor",
		name = "Total GE label",
		description = "Total GE label footer text color",
		position = 8,
		section = OVERLAY_ADJUSTMENTS_SECTION
	)
	default Color totalGeValueLabelTextColor()
	{
		return new Color(165, 165, 165);
	}

	@ConfigItem(
		keyName = "totalGeValueTextColor",
		name = "Total GE text",
		description = "Total GE value footer text color",
		position = 9,
		section = OVERLAY_ADJUSTMENTS_SECTION
	)
	default Color totalGeValueTextColor()
	{
		return new Color(210, 190, 35);
	}

	@ConfigItem(
		keyName = "selectedItemNameLabelTextColor",
		name = "Selected item label",
		description = "Selected item label text color in the overlay footer",
		position = 10,
		section = OVERLAY_ADJUSTMENTS_SECTION
	)
	default Color selectedItemNameLabelTextColor()
	{
		return new Color(165, 165, 165);
	}

	@ConfigItem(
		keyName = "selectedItemNameTextColor",
		name = "Selected item name",
		description = "Selected loot item name text color",
		position = 11,
		section = OVERLAY_ADJUSTMENTS_SECTION
	)
	default Color selectedItemNameTextColor()
	{
		return Color.WHITE;
	}

	@Alpha
	@ConfigItem(
		keyName = "overlaySelectedRowColor",
		name = "Selected item",
		description = "Selected item color in the list or grid overlay",
		position = 12,
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

	@Range(
		min = 1,
		max = 100
	)
	@ConfigItem(
		keyName = "sidePanelMaxItems",
		name = "Side panel max items",
		description = "Maximum number of items shown in the side panel",
		position = 2,
		section = SIDE_PANEL_SECTION
	)
	default int sidePanelMaxItems()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "showOverlayTitle",
		name = "Show overlay title",
		description = "Show Area Loot or Area Loot (auto) at the top of the overlay",
		position = 5,
		section = OVERLAY_SECTION
	)
	default boolean showOverlayTitle()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tileDistanceMode",
		name = "Show tile distance",
		description = "Choose whether and how each loot item's distance is shown",
		position = 8,
		section = OVERLAY_SECTION
	)
	default DistanceMode tileDistanceMode()
	{
		return DistanceMode.SHORT;
	}

	@ConfigItem(
		keyName = "showLootCount",
		name = "Show total loot count",
		description = "Show the number of visible loot items below the overlay",
		position = 9,
		section = OVERLAY_SECTION
	)
	default boolean showLootCount()
	{
		return true;
	}

	@ConfigItem(
		keyName = "totalGeValueMode",
		name = "Show total GE value",
		description = "Choose how the total GE value of visible loot items is shown below the overlay",
		position = 10,
		section = OVERLAY_SECTION
	)
	default TotalGeValueMode totalGeValueMode()
	{
		return TotalGeValueMode.LONG;
	}

	@ConfigItem(
		keyName = "showGeValue",
		name = "Show item GE value",
		description = "Show each loot item's total Grand Exchange value",
		position = 7,
		section = OVERLAY_SECTION
	)
	default boolean showGeValue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sortMode",
		name = "Sort loot by",
		description = "Choose how Area Loot sorts nearby ground items",
		position = 2,
		section = OVERLAY_SECTION
	)
	default SortMode sortMode()
	{
		return SortMode.NEAREST;
	}

	@ConfigItem(
		keyName = "minimumGeValue",
		name = "Minimum GE value",
		description = "Only show drops worth at least this much GP. Supports values like 1000, 10k, or 1m",
		position = 11,
		section = OVERLAY_SECTION
	)
	default String minimumGeValue()
	{
		return "0";
	}

	@ConfigItem(
		keyName = "whitelistedItems",
		name = "Whitelisted items",
		description = "<html>Comma-separated item names to always show.<br>Exact: Rune sword<br>Wildcards: Rune *, * sword, Burnt *<br>Shift+right-click to block/unblock or whitelist/unwhitelist items.</html>",
		position = 0,
		section = FILTER_LISTS_SECTION
	)
	default String whitelistedItems()
	{
		return "";
	}

	@ConfigItem(
		keyName = "whitelistedItems",
		name = "",
		description = ""
	)
	void setWhitelistedItems(String whitelistedItems);

	@ConfigItem(
		keyName = "blockedItems",
		name = "Blocked items",
		description = "<html>Comma-separated item names to hide.<br>Exact: Ashes<br>Wildcards: Rune *, * sword, Burnt *<br>Shift+right-click to block/unblock or whitelist/unwhitelist items.</html>",
		position = 1,
		section = FILTER_LISTS_SECTION
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
		name = "Shift+right-click block/whitelist",
		description = "Add Shift+right-click menu options on ground items to block/unblock or whitelist/unwhitelist them",
		position = 0,
		section = MENU_SECTION
	)
	default boolean shiftRightClickBlockItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pinSelectedItem",
		name = "Pin selected item menu option",
		description = "Move the selected loot item's Take option to the top of the right-click menu",
		position = 1,
		section = MENU_SECTION
	)
	default boolean pinSelectedItem()
	{
		return true;
	}

	@ConfigItem(
		keyName = "drawHighlightLine",
		name = "Draw highlight line",
		description = "Draw a line from your player to the highlighted loot item",
		position = 6,
		section = GENERAL_SECTION
	)
	default boolean drawHighlightLine()
	{
		return true;
	}

	@ConfigItem(
		keyName = "drawMinimapDot",
		name = "Draw minimap dot",
		description = "Show a dot on the minimap for the selected loot item",
		position = 0,
		section = MINIMAP_SECTION
	)
	default boolean drawMinimapDot()
	{
		return false;
	}

	@ConfigItem(
		keyName = "drawMinimapLine",
		name = "Draw minimap line",
		description = "Show a line on the minimap from your player to the selected loot item",
		position = 1,
		section = MINIMAP_SECTION
	)
	default boolean drawMinimapLine()
	{
		return false;
	}

	@ConfigItem(
		keyName = "groupSameItemOverlay",
		name = "Group same items on overlay",
		description = "Group identical nearby drops into one overlay row and highlight them together when selected",
		position = 4,
		section = OVERLAY_SECTION
	)
	default boolean groupSameItemOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "onlyShowHighlightedItemMenu",
		name = "Only show highlighted item",
		description = "When right-clicking the highlighted item's tile, hide other ground items from that menu",
		position = 2,
		section = MENU_SECTION
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
		position = 5,
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

	@Alpha
	@ConfigItem(
		keyName = "highlightLineColor",
		name = "Line color",
		description = "Line color for the selected loot item",
		position = 2,
		section = HIGHLIGHT_SECTION
	)
	default Color highlightLineColor()
	{
		return highlightOutlineColor();
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightMinimapDotColor",
		name = "Minimap dot color",
		description = "Dot color for the selected loot item's minimap marker",
		position = 3,
		section = HIGHLIGHT_SECTION
	)
	default Color highlightMinimapDotColor()
	{
		return highlightOutlineColor();
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightMinimapLineColor",
		name = "Minimap line color",
		description = "Line color for the selected loot item's minimap marker",
		position = 4,
		section = HIGHLIGHT_SECTION
	)
	default Color highlightMinimapLineColor()
	{
		return highlightOutlineColor();
	}

	@ConfigItem(
		keyName = "highlightMenuTextMode",
		name = "Highlight menu text",
		description = "Choose which right-click menu entries for the selected loot item use the menu text color",
		position = 3,
		section = MENU_SECTION
	)
	default MenuHighlightMode highlightMenuTextMode()
	{
		return MenuHighlightMode.TAKE;
	}

	@ConfigItem(
		keyName = "highlightMenuTextColor",
		name = "Menu text color",
		description = "Right-click menu text color for the selected loot item",
		position = 5,
		section = HIGHLIGHT_SECTION
	)
	default Color highlightMenuTextColor()
	{
		return new Color(0, 200, 255);
	}
}
