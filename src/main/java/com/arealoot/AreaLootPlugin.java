package com.arealoot;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Area Loot",
	description = "Shows nearby ground loot in a panel and highlights selected item locations",
	tags = {"area", "ground", "highlight", "loot", "panel"}
)
public class AreaLootPlugin extends Plugin
{
	private static final long AUTO_STATUS_ENABLED_MILLIS = 1200L;
	private static final long AUTO_STATUS_DISABLED_MILLIS = 1000L;
	private static final long OVERLAY_STATUS_FADE_MILLIS = 450L;
	private static final String CONFIG_GROUP = "area-loot";
	private static final String BLOCKED_ITEMS_KEY = "blockedItems";
	private static final String WHITELISTED_ITEMS_KEY = "whitelistedItems";
	private static final String REMEMBERED_MANUAL_OVERLAY_KEY = "rememberedManualOverlayEnabled";
	private static final String REMEMBERED_AUTO_OVERLAY_KEY = "rememberedAutoOverlayEnabled";
	private static final String CUSTOM_THEMES_KEY = "customColorThemes";
	private static final String ACTIVE_THEME_KEY = "activeTheme";
	private static final String CUSTOM_PRESET_PREFIX = "customPreset.";

	private final Map<WorldPoint, List<TrackedGroundItem>> groundItems = new HashMap<>();
	private final Map<Integer, String> itemNameCache = new HashMap<>();
	private final Map<Integer, Integer> itemPriceCache = new HashMap<>();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private AreaLootConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private Gson gson;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private AreaLootOverlay overlay;

	@Inject
	private AreaLootMinimapOverlay minimapOverlay;

	@Inject
	private AreaLootMouseListener mouseListener;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	private AreaLootPanel panel;
	private NavigationButton navButton;
	private AreaLootThemePanel themePanel;
	private NavigationButton themeNavButton;
	private volatile List<AreaLootItem> nearbyLoot = Collections.emptyList();
	private final List<SimpleEntry<Rectangle, AreaLootItem>> overlayRows = new ArrayList<>();
	private boolean sidePanelRegistered;
	private boolean sidePanelActive;
	private boolean applyingNamedTheme;
	private boolean applyingCustomColorStartingPoint;
	private boolean lootDirty;
	private long nextDelayedLootRefreshMillis;

	@Getter
	private volatile WorldPoint selectedLocation;
	private volatile AreaLootItem selectedLootItem;
	private WorldPoint lastPlayerLocation;
	private int selectedItemId = -1;
	private int selectedStackId = -1;
	private volatile boolean manualOverlayEnabled;
	private volatile boolean autoOverlayEnabled;
	private volatile long overlayStatusUntilMillis;
	private volatile String overlayStatusMode = "";
	private volatile String overlayStatusText = "";
	private volatile boolean overlayFadeOutActive;

	private final HotkeyListener overlayHotkeyListener = new NonTypingHotkeyListener(() -> config.toggleHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			toggleOverlay();
		}
	};

	private final HotkeyListener sidePanelHotkeyListener = new NonTypingHotkeyListener(() -> config.sidePanelHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			openSidePanel();
		}
	};

	private final HotkeyListener autoShowHotkeyListener = new NonTypingHotkeyListener(() -> config.autoShowHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			toggleAutoOverlay();
		}
	};

	@Override
	protected void startUp()
	{
		log.debug("Area Loot started");
		migrateLegacyThemePresetName();
		panel = new AreaLootPanel(this, config, itemManager);
		navButton = NavigationButton.builder()
			.tooltip("Area Loot")
			.icon(createLootIcon())
			.onClick(() -> clientThread.invoke(() ->
			{
				sidePanelActive = true;
				refreshLootSnapshot();
			}))
			.priority(5)
			.panel(panel)
			.build();

		overlayManager.add(overlay);
		overlayManager.add(minimapOverlay);
		keyManager.registerKeyListener(overlayHotkeyListener);
		keyManager.registerKeyListener(autoShowHotkeyListener);
		mouseManager.registerMouseListener(mouseListener);
		updateSidePanelRegistration();
		updateThemePanelNavigation();
		clientThread.invoke(this::restoreOverlayMode);
	}

	@Override
	protected void shutDown()
	{
		log.debug("Area Loot stopped");
		if (sidePanelRegistered)
		{
			keyManager.unregisterKeyListener(sidePanelHotkeyListener);
			clientToolbar.removeNavigation(navButton);
			sidePanelRegistered = false;
		}
		if (themeNavButton != null)
		{
			clientToolbar.removeNavigation(themeNavButton);
			themeNavButton = null;
			themePanel = null;
		}
		sidePanelActive = false;
		applyingNamedTheme = false;
		applyingCustomColorStartingPoint = false;
		mouseManager.unregisterMouseListener(mouseListener);
		keyManager.unregisterKeyListener(autoShowHotkeyListener);
		keyManager.unregisterKeyListener(overlayHotkeyListener);
		overlayManager.remove(overlay);
		overlayManager.remove(minimapOverlay);
		groundItems.clear();
		itemNameCache.clear();
		itemPriceCache.clear();
		nearbyLoot = Collections.emptyList();
		clearOverlayRows();
		selectedLocation = null;
		selectedLootItem = null;
		lastPlayerLocation = null;
		selectedItemId = -1;
		selectedStackId = -1;
		manualOverlayEnabled = false;
		autoOverlayEnabled = false;
		lootDirty = false;
		nextDelayedLootRefreshMillis = 0;
		overlayStatusUntilMillis = 0;
		overlayStatusMode = "";
		overlayStatusText = "";
		overlayFadeOutActive = false;
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		WorldPoint location = event.getTile().getWorldLocation();
		addItem(location, event.getItem(), System.currentTimeMillis());
		lootDirty = true;
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		removeItem(event.getTile(), event.getItem());
		lootDirty = true;
	}

	@Subscribe
	public void onItemQuantityChanged(ItemQuantityChanged event)
	{
		Long spawnedAtMillis = removeItem(event.getTile(), event.getItem());
		addItem(
			event.getTile().getWorldLocation(),
			event.getItem(),
			spawnedAtMillis == null ? System.currentTimeMillis() : spawnedAtMillis
		);
		lootDirty = true;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!shouldMaintainLootSnapshot())
		{
			return;
		}

		if (lootDirty || hasPlayerMoved() || hasDelayedLootReady())
		{
			lootDirty = false;
			refreshLootSnapshot();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING || event.getGameState() == GameState.LOGIN_SCREEN)
		{
			groundItems.clear();
			itemNameCache.clear();
			itemPriceCache.clear();
			nearbyLoot = Collections.emptyList();
			clearOverlayRows();
			selectedLocation = null;
			selectedLootItem = null;
			lastPlayerLocation = null;
			selectedItemId = -1;
			selectedStackId = -1;
			manualOverlayEnabled = false;
			autoOverlayEnabled = false;
			sidePanelActive = false;
			lootDirty = false;
			nextDelayedLootRefreshMillis = 0;
			overlayStatusUntilMillis = 0;
			overlayStatusMode = "";
			overlayStatusText = "";
			overlayFadeOutActive = false;
			rebuildPanel(Collections.emptyList());
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			restoreOverlayMode();
			AreaLootUpdateNotice.announceIfNeeded(configManager, chatMessageManager, config.disableUpdateNotifications());
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}

		String key = event.getKey();
		if ("sidePanelEnabled".equals(key))
		{
			updateSidePanelRegistration();
		}
		else if ("themeSharingPanelEnabled".equals(key))
		{
			updateThemePanelNavigation();
		}
		else if ("themePreset".equals(key))
		{
			if (!applyingNamedTheme && config.themePreset() != AreaLootConfig.ThemePreset.Custom)
			{
				clearActiveTheme();
			}
			rebuildPanel(nearbyLoot);
		}
		else if ("customColorStartingPoint".equals(key))
		{
			AreaLootConfig.CustomColorStartingPoint startingPoint = parseCustomColorStartingPoint(event.getNewValue());
			if (startingPoint != null)
			{
				copyThemeColorsToCustom(startingPoint);
				if (startingPoint != AreaLootConfig.CustomColorStartingPoint.SidePanelTheme)
				{
					clearActiveTheme();
				}
				rebuildPanel(nearbyLoot);
				eventBus.post(new ProfileChanged());
			}
		}
		else if ("keepOverlayAboveGame".equals(key))
		{
			if (overlay.applyConfiguredLayer())
			{
				overlayManager.remove(overlay);
				overlayManager.add(overlay);
			}
		}
		else if (isDisplayConfigKey(key))
		{
			if (!applyingNamedTheme && !applyingCustomColorStartingPoint && isThemeColorConfigKey(key))
			{
				configManager.setConfiguration(CONFIG_GROUP, "themePreset", AreaLootConfig.ThemePreset.Custom.name());
				if (config.customColorStartingPoint() == AreaLootConfig.CustomColorStartingPoint.SidePanelTheme)
				{
					clearActiveTheme();
				}
				saveCustomPresetColor(key);
			}
			rebuildPanel(nearbyLoot);
		}
		else if ("rememberOverlayMode".equals(key))
		{
			if (config.rememberOverlayMode())
			{
				saveOverlayMode();
			}
			else
			{
				clearSavedOverlayMode();
			}
		}
		else if ("sortMode".equals(key) || "minimumGeValue".equals(key) || "overlayItemDelay".equals(key) || "groupSameItemOverlay".equals(key) || BLOCKED_ITEMS_KEY.equals(key)
			|| WHITELISTED_ITEMS_KEY.equals(key) || "lootRadius".equals(key))
		{
			lootDirty = true;
			if (shouldMaintainLootSnapshot())
			{
				clientThread.invoke(() ->
				{
					lootDirty = false;
					refreshLootSnapshot();
				});
			}
		}
	}

	private boolean isDisplayConfigKey(String key)
	{
		if (isThemeColorConfigKey(key))
		{
			return true;
		}

		switch (key)
		{
			case "overlayTransparency":
			case "overlayTextTransparency":
			case "sidePanelMaxItems":
			case "listIconSize":
			case "showItemNamesInListMode":
			case "tileDistanceMode":
			case "showLootCount":
			case "totalGeValueMode":
			case "showGeValue":
			case "geValueTextColor":
			case "tileDistanceTextColor":
			case "lootCountTextColor":
			case "totalGeValueTextColor":
				return true;
			default:
				return false;
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.shiftRightClickBlockItems() || !client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}

		MenuAction type = MenuAction.of(event.getType());
		if (type != MenuAction.EXAMINE_ITEM_GROUND)
		{
			return;
		}

		String itemName = getItemName(event.getIdentifier());
		boolean blockedByName = isBlockedByExactName(itemName);
		boolean whitelistedByName = isWhitelistedByExactName(itemName);

		MenuEntry areaLootEntry = client.createMenuEntry(-1)
			.setParam0(event.getActionParam0())
			.setParam1(event.getActionParam1())
			.setIdentifier(event.getIdentifier())
			.setItemId(event.getItemId())
			.setTarget("")
			.setOption("Area Loot")
			.setType(MenuAction.RUNELITE_LOW_PRIORITY)
			.setDeprioritized(true);

		Menu submenu = areaLootEntry.createSubMenu();
		submenu.createMenuEntry(-1)
			.setParam0(event.getActionParam0())
			.setParam1(event.getActionParam1())
			.setIdentifier(event.getIdentifier())
			.setItemId(event.getItemId())
			.setTarget(event.getTarget())
			.setOption(blockedByName ? "Unblock" : "Block")
			.setType(MenuAction.RUNELITE)
			.onClick(entry ->
			{
				if (blockedByName)
				{
					removeBlockedItem(itemName);
				}
				else
				{
					addBlockedItem(itemName);
				}
			});

		submenu.createMenuEntry(-1)
			.setParam0(event.getActionParam0())
			.setParam1(event.getActionParam1())
			.setIdentifier(event.getIdentifier())
			.setItemId(event.getItemId())
			.setTarget(event.getTarget())
			.setOption(whitelistedByName ? "Unwhitelist" : "Whitelist")
			.setType(MenuAction.RUNELITE)
			.onClick(entry ->
			{
				if (whitelistedByName)
				{
					removeWhitelistedItem(itemName);
				}
				else
				{
					addWhitelistedItem(itemName);
				}
			});
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (selectedLocation == null)
		{
			return;
		}

		WorldView worldView = client.getTopLevelWorldView();
		int selectedSceneX = selectedLocation.getX() - worldView.getBaseX();
		int selectedSceneY = selectedLocation.getY() - worldView.getBaseY();
		MenuEntry[] menuEntries = event.getMenuEntries();
		for (MenuEntry entry : menuEntries)
		{
			colorSelectedMenuEntry(entry, selectedSceneX, selectedSceneY);
		}

		MenuEntry[] updatedEntries = menuEntries;
		if (config.onlyShowHighlightedItemMenu())
		{
			updatedEntries = Arrays.stream(updatedEntries)
				.filter(entry -> shouldKeepMenuEntry(entry, selectedSceneX, selectedSceneY))
				.toArray(MenuEntry[]::new);
		}

		if (config.pinSelectedItem())
		{
			updatedEntries = promoteSelectedMenuEntries(updatedEntries, selectedSceneX, selectedSceneY);
		}

		if (updatedEntries != menuEntries)
		{
			client.getMenu().setMenuEntries(updatedEntries);
		}
	}

	void selectLoot(AreaLootItem item)
	{
		selectedLocation = item.getLocation();
		selectedItemId = item.getId();
		selectedStackId = item.getStackId();
		selectedLootItem = item;
		rebuildPanel(nearbyLoot);
	}

	void clearSelectedLoot()
	{
		selectedLocation = null;
		selectedLootItem = null;
		selectedItemId = -1;
		selectedStackId = -1;
		rebuildPanel(nearbyLoot);
	}

	boolean hasSelectedLoot()
	{
		return selectedItemId != -1;
	}

	boolean isSelectedLoot(AreaLootItem item)
	{
		return isSelectedItem(item);
	}

	boolean isSelectedOverlayLoot(AreaLootItem item)
	{
		if (config.groupSameItemOverlay())
		{
			return selectedItemId != -1 && item.getId() == selectedItemId;
		}

		return isSelectedItem(item);
	}

	AreaLootItem getSelectedLootItem()
	{
		return selectedLootItem;
	}

	List<AreaLootItem> getSelectedLootItems()
	{
		if (selectedItemId == -1)
		{
			return Collections.emptyList();
		}

		List<AreaLootItem> selectedItems = new ArrayList<>();
		for (AreaLootItem item : nearbyLoot)
		{
			if (isSelectedOverlayLoot(item))
			{
				selectedItems.add(item);
			}
		}
		return selectedItems;
	}

	List<AreaLootItem> getNearbyLootSnapshot()
	{
		return nearbyLoot;
	}

	boolean shouldShowOverlayList()
	{
		if (shouldShowOverlayStatus())
		{
			return true;
		}

		return manualOverlayEnabled || (autoOverlayEnabled && !nearbyLoot.isEmpty()) || overlayFadeOutActive;
	}

	boolean isOverlayAutoModeActive()
	{
		return autoOverlayEnabled;
	}

	boolean shouldShowOverlayStatus()
	{
		return System.currentTimeMillis() < overlayStatusUntilMillis;
	}

	float getOverlayStatusAlpha()
	{
		long remainingMillis = overlayStatusUntilMillis - System.currentTimeMillis();
		if (remainingMillis <= 0)
		{
			return 0.0f;
		}

		return Math.min(1.0f, remainingMillis / (float) OVERLAY_STATUS_FADE_MILLIS);
	}

	String getOverlayStatusText()
	{
		return overlayStatusText;
	}

	String getOverlayStatusMode()
	{
		return overlayStatusMode;
	}

	boolean isOverlayFadeOutActive()
	{
		return overlayFadeOutActive;
	}

	void finishOverlayFadeOut()
	{
		overlayFadeOutActive = false;
		if (!shouldShowOverlayStatus())
		{
			overlayStatusMode = "";
			overlayStatusText = "";
		}
	}

	void setOverlayRows(List<SimpleEntry<Rectangle, AreaLootItem>> rows)
	{
		synchronized (overlayRows)
		{
			overlayRows.clear();
			overlayRows.addAll(rows);
		}
	}

	AreaLootItem getOverlayItemAt(Point point)
	{
		synchronized (overlayRows)
		{
			for (SimpleEntry<Rectangle, AreaLootItem> row : overlayRows)
			{
				if (row.getKey().contains(point))
				{
					return row.getValue();
				}
			}
		}

		return null;
	}

	private void toggleOverlay()
	{
		clientThread.invoke(() ->
		{
			refreshLootSnapshot();
			manualOverlayEnabled = !manualOverlayEnabled;
			long now = System.currentTimeMillis();
			if (manualOverlayEnabled)
			{
				autoOverlayEnabled = false;
				overlayStatusMode = "toggle";
				overlayStatusText = "Enabled";
				overlayStatusUntilMillis = now + AUTO_STATUS_ENABLED_MILLIS;
				overlayFadeOutActive = false;
			}
			else
			{
				overlayStatusMode = "toggle";
				overlayStatusText = "Disabled";
				overlayStatusUntilMillis = now + AUTO_STATUS_DISABLED_MILLIS;
				overlayFadeOutActive = false;
			}
			saveOverlayMode();
		});
	}

	private void toggleAutoOverlay()
	{
		clientThread.invoke(() ->
		{
			refreshLootSnapshot();
			autoOverlayEnabled = !autoOverlayEnabled;
			long now = System.currentTimeMillis();
			if (autoOverlayEnabled)
			{
				manualOverlayEnabled = false;
				overlayStatusMode = "auto";
				overlayStatusText = "Enabled";
				overlayStatusUntilMillis = now + AUTO_STATUS_ENABLED_MILLIS;
				overlayFadeOutActive = false;
			}
			else
			{
				overlayStatusMode = "auto";
				overlayStatusText = "Disabled";
				overlayStatusUntilMillis = now + AUTO_STATUS_DISABLED_MILLIS;
				overlayFadeOutActive = false;
			}
			saveOverlayMode();
		});
	}

	private void restoreOverlayMode()
	{
		if (!config.rememberOverlayMode())
		{
			return;
		}

		Boolean rememberedManualOverlay = configManager.getConfiguration(
			CONFIG_GROUP,
			REMEMBERED_MANUAL_OVERLAY_KEY,
			Boolean.class);
		Boolean rememberedAutoOverlay = configManager.getConfiguration(
			CONFIG_GROUP,
			REMEMBERED_AUTO_OVERLAY_KEY,
			Boolean.class);

		manualOverlayEnabled = Boolean.TRUE.equals(rememberedManualOverlay);
		autoOverlayEnabled = !manualOverlayEnabled && Boolean.TRUE.equals(rememberedAutoOverlay);
		overlayFadeOutActive = false;
		overlayStatusUntilMillis = 0;
		overlayStatusMode = "";
		overlayStatusText = "";
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			refreshLootSnapshot();
		}
	}

	private void saveOverlayMode()
	{
		if (!config.rememberOverlayMode())
		{
			return;
		}

		configManager.setConfiguration(CONFIG_GROUP, REMEMBERED_MANUAL_OVERLAY_KEY, Boolean.toString(manualOverlayEnabled));
		configManager.setConfiguration(CONFIG_GROUP, REMEMBERED_AUTO_OVERLAY_KEY, Boolean.toString(autoOverlayEnabled));
	}

	private void clearSavedOverlayMode()
	{
		configManager.unsetConfiguration(CONFIG_GROUP, REMEMBERED_MANUAL_OVERLAY_KEY);
		configManager.unsetConfiguration(CONFIG_GROUP, REMEMBERED_AUTO_OVERLAY_KEY);
	}

	private void openSidePanel()
	{
		if (!config.sidePanelEnabled())
		{
			return;
		}

		clientThread.invoke(() ->
		{
			sidePanelActive = true;
			refreshLootSnapshot();
			SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
		});
	}

	private void updateSidePanelRegistration()
	{
		if (config.sidePanelEnabled())
		{
			if (!sidePanelRegistered)
			{
				keyManager.registerKeyListener(sidePanelHotkeyListener);
				clientToolbar.addNavigation(navButton);
				sidePanelRegistered = true;
			}
		}
		else if (sidePanelRegistered)
		{
			keyManager.unregisterKeyListener(sidePanelHotkeyListener);
			clientToolbar.removeNavigation(navButton);
			sidePanelRegistered = false;
			sidePanelActive = false;
		}
	}

	private void updateThemePanelNavigation()
	{
		if (!config.themeSharingPanelEnabled())
		{
			if (themeNavButton != null)
			{
				clientToolbar.removeNavigation(themeNavButton);
				themeNavButton = null;
				themePanel = null;
			}
			return;
		}

		if (themeNavButton != null)
		{
			return;
		}

		themePanel = new AreaLootThemePanel(this);
		themeNavButton = NavigationButton.builder()
			.tooltip("Area Loot Themes")
			.icon(createThemeIcon())
			.panel(themePanel)
			.priority(6)
			.build();
		clientToolbar.addNavigation(themeNavButton);
	}

	private void migrateLegacyThemePresetName()
	{
		String savedPreset = configManager.getConfiguration(CONFIG_GROUP, "themePreset");
		if (savedPreset != null)
		{
			switch (savedPreset)
			{
				case "CUSTOM":
					configManager.setConfiguration(CONFIG_GROUP, "themePreset", AreaLootConfig.ThemePreset.Custom.name());
					break;
				case "DEFAULT":
				case "Default":
					configManager.setConfiguration(CONFIG_GROUP, "themePreset", AreaLootConfig.ThemePreset.Slate.name());
					break;
				case "CLASSIC":
					configManager.setConfiguration(CONFIG_GROUP, "themePreset", AreaLootConfig.ThemePreset.Classic.name());
					break;
				case "LIGHT_CLASSIC":
					configManager.setConfiguration(CONFIG_GROUP, "themePreset", AreaLootConfig.ThemePreset.LightClassic.name());
					break;
				case "LIGHT":
					configManager.setConfiguration(CONFIG_GROUP, "themePreset", AreaLootConfig.ThemePreset.Light.name());
					break;
				case "DARK":
					configManager.setConfiguration(CONFIG_GROUP, "themePreset", AreaLootConfig.ThemePreset.Dark.name());
					break;
				case "GOLD":
					configManager.setConfiguration(CONFIG_GROUP, "themePreset", AreaLootConfig.ThemePreset.Gold.name());
					break;
				case "ZAROS":
					configManager.setConfiguration(CONFIG_GROUP, "themePreset", AreaLootConfig.ThemePreset.Zaros.name());
					break;
				case "GUTHIX":
					configManager.setConfiguration(CONFIG_GROUP, "themePreset", AreaLootConfig.ThemePreset.Guthix.name());
					break;
				case "SARADOMIN":
					configManager.setConfiguration(CONFIG_GROUP, "themePreset", AreaLootConfig.ThemePreset.Saradomin.name());
					break;
				case "BLOOD":
					configManager.setConfiguration(CONFIG_GROUP, "themePreset", AreaLootConfig.ThemePreset.Blood.name());
					break;
				default:
					break;
			}
		}

		String savedStartingPoint = configManager.getConfiguration(CONFIG_GROUP, "customColorStartingPoint");
		if (savedStartingPoint != null)
		{
			switch (savedStartingPoint)
			{
				case "DEFAULT":
				case "Default":
					configManager.setConfiguration(CONFIG_GROUP, "customColorStartingPoint", AreaLootConfig.CustomColorStartingPoint.Slate.name());
					break;
				default:
					break;
			}
		}
	}

	Map<String, String> getNamedColorThemes()
	{
		Map<String, String> themes = new LinkedHashMap<>();
		String json = configManager.getConfiguration(CONFIG_GROUP, CUSTOM_THEMES_KEY);
		if (json == null || json.isEmpty())
		{
			return themes;
		}

		try
		{
			JsonObject root = new JsonParser().parse(json).getAsJsonObject();
			for (String name : root.keySet())
			{
				JsonElement value = root.get(name);
				if (value != null && !value.isJsonNull())
				{
					themes.put(name, value.getAsString());
				}
			}
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to read Area Loot custom themes", ex);
		}

		return themes;
	}

	String getActiveThemeName()
	{
		return configManager.getConfiguration(CONFIG_GROUP, ACTIVE_THEME_KEY);
	}

	String createThemeFromCurrentColors(String name)
	{
		String normalizedName = normalizeThemeName(name);
		String themeJson = exportCurrentColorTheme(normalizedName);
		saveNamedColorTheme(normalizedName, themeJson);
		queueThemeMessage("Saved Area Loot theme: " + normalizedName);
		return themeJson;
	}

	String importNamedColorTheme(String name, String json)
	{
		String normalizedName = normalizeThemeName(name);
		Map<String, String> colors = AreaLootColorSettings.importFromJson(json);
		String themeJson = AreaLootColorSettings.exportToJson(normalizedName, colors, gson);
		saveNamedColorTheme(normalizedName, themeJson);
		queueThemeMessage("Imported Area Loot theme: " + normalizedName);
		return themeJson;
	}

	void updateNamedColorThemeFromCurrent(String name)
	{
		String normalizedName = normalizeThemeName(name);
		saveNamedColorTheme(normalizedName, exportCurrentColorTheme(normalizedName));
		queueThemeMessage("Updated Area Loot theme: " + normalizedName);
	}

	int applyNamedColorTheme(String name)
	{
		String themeJson = getNamedColorThemes().get(name);
		if (themeJson == null)
		{
			throw new IllegalArgumentException("Unknown Area Loot theme: " + name);
		}

		int imported;
		applyingNamedTheme = true;
		try
		{
			imported = applyColorTheme(themeJson);
			configManager.setConfiguration(CONFIG_GROUP, ACTIVE_THEME_KEY, name);
			eventBus.post(new ProfileChanged());
		}
		finally
		{
			applyingNamedTheme = false;
		}

		rebuildPanel(nearbyLoot);
		if (themePanel != null)
		{
			themePanel.rebuild();
		}
		queueThemeMessage("Applied Area Loot theme: " + name);
		return imported;
	}

	String exportNamedColorTheme(String name)
	{
		String themeJson = getNamedColorThemes().get(name);
		if (themeJson == null)
		{
			throw new IllegalArgumentException("Unknown Area Loot theme: " + name);
		}

		return themeJson;
	}

	void copyNamedColorThemeToClipboard(String name)
	{
		String themeJson = exportNamedColorTheme(name);
		Toolkit.getDefaultToolkit()
			.getSystemClipboard()
			.setContents(new StringSelection(themeJson), null);
		queueThemeMessage("Copied Area Loot theme: " + name);
	}

	void deleteNamedColorTheme(String name)
	{
		Map<String, String> themes = getNamedColorThemes();
		if (themes.remove(name) != null)
		{
			saveNamedColorThemes(themes);
			if (name.equals(getActiveThemeName()))
			{
				clearActiveTheme();
			}
			queueThemeMessage("Deleted Area Loot theme: " + name);
		}
	}

	private String exportCurrentColorTheme(String themeName)
	{
		return AreaLootColorSettings.exportToJson(themeName, activeColorTheme(), gson);
	}

	private int applyColorTheme(String json)
	{
		Map<String, String> colors = AreaLootColorSettings.importFromJson(json);
		for (Map.Entry<String, String> entry : colors.entrySet())
		{
			configManager.setConfiguration(
				CONFIG_GROUP,
				customPresetKey(AreaLootConfig.CustomColorStartingPoint.SidePanelTheme, entry.getKey()),
				entry.getValue());
			setThemeColorConfig(entry.getKey(), entry.getValue());
		}
		configManager.setConfiguration(CONFIG_GROUP, "themePreset", AreaLootConfig.ThemePreset.Custom.name());
		configManager.setConfiguration(CONFIG_GROUP, "customColorStartingPoint", AreaLootConfig.CustomColorStartingPoint.SidePanelTheme.name());
		return colors.size();
	}

	private void copyThemeColorsToCustom(AreaLootConfig.CustomColorStartingPoint startingPoint)
	{
		Map<String, String> defaultColors = defaultThemeColors(startingPoint);
		if (defaultColors.isEmpty())
		{
			return;
		}

		applyingCustomColorStartingPoint = true;
		try
		{
			if (isBuiltInStartingPoint(startingPoint))
			{
				setThemeColors(defaultColors);
				saveThemeColorsToPreset(startingPoint, defaultColors);
			}
			else if (!customPresetExists(startingPoint))
			{
				setThemeColors(defaultColors);
				saveThemeColorsToPreset(startingPoint, defaultColors);
			}
			else
			{
				for (String keyName : AreaLootColorSettings.THEME_COLOR_KEYS)
				{
					String color = configManager.getConfiguration(CONFIG_GROUP, customPresetKey(startingPoint, keyName));
					if (color != null)
					{
						setThemeColorConfig(keyName, color);
					}
				}
			}
			configManager.setConfiguration(CONFIG_GROUP, "themePreset", AreaLootConfig.ThemePreset.Custom.name());
		}
		finally
		{
			applyingCustomColorStartingPoint = false;
		}
	}

	private void saveCustomPresetColor(String keyName)
	{
		AreaLootConfig.CustomColorStartingPoint startingPoint = config.customColorStartingPoint();
		if (startingPoint == null || !isCustomColorKey(keyName))
		{
			return;
		}

		if (!customPresetExists(startingPoint))
		{
			saveThemeColorsToPreset(startingPoint, defaultThemeColors(startingPoint));
		}

		String value = configManager.getConfiguration(CONFIG_GROUP, keyName);
		if (value != null)
		{
			configManager.setConfiguration(CONFIG_GROUP, customPresetKey(startingPoint, keyName), value);
		}
	}

	private Map<String, String> presetColorTheme(AreaLootConfig.ThemePreset preset)
	{
		Map<String, String> colors = new LinkedHashMap<>();
		if (preset == null || preset == AreaLootConfig.ThemePreset.Custom)
		{
			return colors;
		}

		switch (preset)
		{
			case Slate:
				putThemeColors(colors,
					new Color(30, 30, 30, 190), new Color(23, 23, 23),
					new Color(220, 138, 0), new Color(198, 198, 198), new Color(165, 165, 165),
					new Color(210, 190, 35), new Color(0, 200, 255));
				break;
			case Classic:
				putThemeColors(colors,
					new Color(31, 24, 17, 230), new Color(118, 94, 60, 255),
					new Color(235, 226, 193), new Color(235, 226, 193), new Color(200, 186, 140),
					new Color(235, 226, 193), new Color(255, 190, 64));
				break;
			case LightClassic:
				putThemeColors(colors,
					new Color(226, 214, 188, 235), new Color(116, 95, 60, 255),
					new Color(50, 38, 24), new Color(78, 62, 39), new Color(92, 74, 48),
					new Color(135, 111, 70), new Color(202, 139, 50));
				break;
			case Light:
				putThemeColors(colors,
					new Color(238, 241, 244, 210), new Color(136, 146, 156, 255),
					new Color(32, 38, 44), new Color(80, 88, 96), new Color(104, 112, 120),
					new Color(116, 131, 145), new Color(92, 166, 210));
				break;
			case Dark:
				putThemeColors(colors,
					new Color(13, 15, 18, 235), new Color(86, 96, 106, 255),
					new Color(222, 229, 235), new Color(185, 196, 205), new Color(146, 156, 166),
					new Color(128, 190, 220), new Color(92, 166, 210));
				break;
			case Gold:
				putThemeColors(colors,
					new Color(28, 22, 10, 235), new Color(172, 130, 38, 255),
					new Color(255, 232, 154), new Color(232, 201, 104), new Color(184, 146, 66),
					new Color(255, 218, 96), new Color(255, 190, 64));
				break;
			case Zaros:
				putThemeColors(colors,
					new Color(19, 17, 31, 235), new Color(101, 76, 160, 255),
					new Color(230, 219, 255), new Color(190, 176, 230), new Color(148, 128, 196),
					new Color(186, 134, 255), new Color(162, 105, 232));
				break;
			case Guthix:
				putThemeColors(colors,
					new Color(14, 27, 20, 235), new Color(70, 126, 81, 255),
					new Color(219, 239, 207), new Color(180, 218, 160), new Color(122, 174, 116),
					new Color(142, 226, 108), new Color(116, 210, 92));
				break;
			case Saradomin:
				putThemeColors(colors,
					new Color(13, 22, 36, 235), new Color(64, 111, 178, 255),
					new Color(222, 238, 255), new Color(170, 207, 245), new Color(112, 160, 212),
					new Color(116, 190, 255), new Color(92, 166, 245));
				break;
			case Blood:
				putThemeColors(colors,
					new Color(31, 12, 13, 235), new Color(151, 48, 48, 255),
					new Color(247, 220, 205), new Color(226, 153, 137), new Color(176, 86, 78),
					new Color(255, 92, 76), new Color(230, 72, 72));
				break;
			default:
				break;
		}

		return colors;
	}

	private static void putThemeColors(
		Map<String, String> colors,
		Color background,
		Color border,
		Color header,
		Color text,
		Color secondary,
		Color value,
		Color accent)
	{
		colors.put("overlayBackgroundColor", Integer.toString(background.getRGB()));
		colors.put("overlayBorderColor", Integer.toString(border.getRGB()));
		colors.put("overlayHeaderColor", Integer.toString(header.getRGB()));
		colors.put("overlayTextColor", Integer.toString(text.getRGB()));
		colors.put("overlaySecondaryTextColor", Integer.toString(secondary.getRGB()));
		colors.put("geValueTextColor", Integer.toString(value.getRGB()));
		colors.put("tileDistanceTextColor", Integer.toString(secondary.getRGB()));
		colors.put("lootCountTextColor", Integer.toString(secondary.getRGB()));
		colors.put("totalGeValueLabelTextColor", Integer.toString(secondary.getRGB()));
		colors.put("totalGeValueTextColor", Integer.toString(value.getRGB()));
		colors.put("selectedItemNameLabelTextColor", Integer.toString(secondary.getRGB()));
		colors.put("selectedItemNameTextColor", Integer.toString(header.getRGB()));
		colors.put("overlaySelectedRowColor", Integer.toString(withAlpha(accent, 65).getRGB()));
	}

	private static Color withAlpha(Color color, int alpha)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

	private Map<String, String> defaultThemeColors(AreaLootConfig.CustomColorStartingPoint startingPoint)
	{
		if (startingPoint == null)
		{
			return Collections.emptyMap();
		}

		if (isCustomStartingPoint(startingPoint))
		{
			return presetColorTheme(AreaLootConfig.ThemePreset.Classic);
		}

		return presetColorTheme(themePresetFromStartingPoint(startingPoint));
	}

	private void setThemeColors(Map<String, String> colors)
	{
		for (Map.Entry<String, String> entry : colors.entrySet())
		{
			setThemeColorConfig(entry.getKey(), entry.getValue());
		}
	}

	private void setThemeColorConfig(String keyName, String value)
	{
		configManager.setConfiguration(CONFIG_GROUP, keyName, value);
	}

	private void saveThemeColorsToPreset(AreaLootConfig.CustomColorStartingPoint startingPoint, Map<String, String> colors)
	{
		for (Map.Entry<String, String> entry : colors.entrySet())
		{
			configManager.setConfiguration(CONFIG_GROUP, customPresetKey(startingPoint, entry.getKey()), entry.getValue());
		}
	}

	private boolean customPresetExists(AreaLootConfig.CustomColorStartingPoint startingPoint)
	{
		return configManager.getConfiguration(CONFIG_GROUP, customPresetKey(startingPoint, "overlayBackgroundColor")) != null;
	}

	private static boolean isCustomStartingPoint(AreaLootConfig.CustomColorStartingPoint startingPoint)
	{
		return startingPoint == AreaLootConfig.CustomColorStartingPoint.Custom1
			|| startingPoint == AreaLootConfig.CustomColorStartingPoint.Custom2
			|| startingPoint == AreaLootConfig.CustomColorStartingPoint.Custom3
			|| startingPoint == AreaLootConfig.CustomColorStartingPoint.SidePanelTheme;
	}

	private static boolean isBuiltInStartingPoint(AreaLootConfig.CustomColorStartingPoint startingPoint)
	{
		return startingPoint != null && !isCustomStartingPoint(startingPoint);
	}

	private static boolean isCustomColorKey(String keyName)
	{
		return AreaLootColorSettings.isThemeColorKey(keyName);
	}

	private static String customPresetKey(AreaLootConfig.CustomColorStartingPoint startingPoint, String keyName)
	{
		return CUSTOM_PRESET_PREFIX + startingPoint.name() + "." + keyName;
	}

	private static AreaLootConfig.ThemePreset themePresetFromStartingPoint(AreaLootConfig.CustomColorStartingPoint startingPoint)
	{
		if (startingPoint == null)
		{
			return AreaLootConfig.ThemePreset.Custom;
		}

		return AreaLootConfig.ThemePreset.valueOf(startingPoint.name());
	}

	private static AreaLootConfig.CustomColorStartingPoint parseCustomColorStartingPoint(String value)
	{
		if (value == null)
		{
			return null;
		}

		try
		{
			return AreaLootConfig.CustomColorStartingPoint.valueOf(value);
		}
		catch (IllegalArgumentException ex)
		{
			return null;
		}
	}

	private Map<String, String> currentColorTheme()
	{
		Map<String, String> colors = new LinkedHashMap<>();
		for (String key : AreaLootColorSettings.THEME_COLOR_KEYS)
		{
			Color color = colorForThemeKey(key);
			if (color != null)
			{
				colors.put(key, Integer.toString(color.getRGB()));
			}
		}
		return colors;
	}

	private Map<String, String> activeColorTheme()
	{
		Map<String, String> presetColors = presetColorTheme(config.themePreset());
		return presetColors.isEmpty() ? currentColorTheme() : presetColors;
	}

	Color getThemeColor(String key)
	{
		Map<String, String> presetColors = presetColorTheme(config.themePreset());
		String color = presetColors.get(key);
		if (color != null)
		{
			try
			{
				return applyThemeTransparency(key, new Color(Integer.parseInt(color), true));
			}
			catch (NumberFormatException ex)
			{
				log.debug("Invalid Area Loot theme color for {}: {}", key, color);
			}
		}

		return applyThemeTransparency(key, colorForThemeKey(key));
	}

	private Color applyThemeTransparency(String key, Color color)
	{
		if (isOverlayTransparencyColorKey(key))
		{
			return applyOverlayTransparency(color);
		}
		if (isOverlayTextTransparencyColorKey(key))
		{
			return applyTextTransparency(color);
		}

		return color;
	}

	private Color applyOverlayTransparency(Color color)
	{
		if (color == null)
		{
			return null;
		}

		int extraTransparency = config.overlayTransparency();
		if (extraTransparency <= 0)
		{
			return color;
		}

		int alpha = color.getAlpha();
		int adjustedAlpha = (alpha * Math.max(0, 100 - extraTransparency)) / 100;
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), adjustedAlpha);
	}

	private Color applyTextTransparency(Color color)
	{
		if (color == null)
		{
			return null;
		}

		int extraTransparency = config.overlayTextTransparency();
		if (extraTransparency <= 0)
		{
			return color;
		}

		int alpha = color.getAlpha();
		int adjustedAlpha = (alpha * Math.max(0, 100 - extraTransparency)) / 100;
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), adjustedAlpha);
	}

	private static boolean isOverlayTransparencyColorKey(String key)
	{
		switch (key)
		{
			case "overlayBackgroundColor":
			case "overlayBorderColor":
			case "overlaySelectedRowColor":
				return true;
			default:
				return false;
		}
	}

	private static boolean isOverlayTextTransparencyColorKey(String key)
	{
		switch (key)
		{
			case "overlayHeaderColor":
			case "overlayTextColor":
			case "overlaySecondaryTextColor":
			case "geValueTextColor":
			case "tileDistanceTextColor":
			case "lootCountTextColor":
			case "totalGeValueLabelTextColor":
			case "totalGeValueTextColor":
			case "selectedItemNameLabelTextColor":
			case "selectedItemNameTextColor":
				return true;
			default:
				return false;
		}
	}

	private Color colorForThemeKey(String key)
	{
		switch (key)
		{
			case "overlayBackgroundColor":
				return config.overlayBackgroundColor();
			case "overlayBorderColor":
				return config.overlayBorderColor();
			case "overlayHeaderColor":
				return config.overlayHeaderColor();
			case "overlayTextColor":
				return config.overlayTextColor();
			case "overlaySecondaryTextColor":
				return config.overlaySecondaryTextColor();
			case "geValueTextColor":
				return config.geValueTextColor();
			case "tileDistanceTextColor":
				return config.tileDistanceTextColor();
			case "lootCountTextColor":
				return config.lootCountTextColor();
			case "totalGeValueLabelTextColor":
				return config.totalGeValueLabelTextColor();
			case "totalGeValueTextColor":
				return config.totalGeValueTextColor();
			case "selectedItemNameLabelTextColor":
				return config.selectedItemNameLabelTextColor();
			case "selectedItemNameTextColor":
				return config.selectedItemNameTextColor();
			case "overlaySelectedRowColor":
				return config.overlaySelectedRowColor();
			case "highlightColor":
				return config.highlightColor();
			case "highlightOutlineColor":
				return config.highlightOutlineColor();
			case "highlightLineColor":
				return config.highlightLineColor();
			case "highlightMinimapDotColor":
				return config.highlightMinimapDotColor();
			case "highlightMinimapLineColor":
				return config.highlightMinimapLineColor();
			case "highlightMenuTextColor":
				return config.highlightMenuTextColor();
			default:
				return null;
		}
	}

	private void clearActiveTheme()
	{
		configManager.unsetConfiguration(CONFIG_GROUP, ACTIVE_THEME_KEY);
		if (themePanel != null)
		{
			themePanel.rebuild();
		}
	}

	private void saveNamedColorTheme(String name, String themeJson)
	{
		Map<String, String> themes = getNamedColorThemes();
		themes.put(name, themeJson);
		saveNamedColorThemes(themes);
		if (themePanel != null)
		{
			themePanel.rebuild();
		}
	}

	private void saveNamedColorThemes(Map<String, String> themes)
	{
		JsonObject root = new JsonObject();
		for (Map.Entry<String, String> entry : themes.entrySet())
		{
			root.addProperty(entry.getKey(), entry.getValue());
		}

		configManager.setConfiguration(CONFIG_GROUP, CUSTOM_THEMES_KEY, gson.toJson(root));
	}

	private static String normalizeThemeName(String name)
	{
		String normalizedName = name == null ? "" : name.trim();
		if (normalizedName.isEmpty())
		{
			throw new IllegalArgumentException("Theme name is required.");
		}
		if (normalizedName.length() > 40)
		{
			throw new IllegalArgumentException("Theme name must be 40 characters or less.");
		}

		return normalizedName;
	}

	private static boolean isThemeColorConfigKey(String key)
	{
		return AreaLootColorSettings.isThemeColorKey(key);
	}

	private void queueThemeMessage(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(ColorUtil.wrapWithColorTag(message, config.themeNotificationTextColor()))
			.build());
	}

	private void refreshLootSnapshot()
	{
		lastPlayerLocation = getPlayerLocation();
		List<AreaLootItem> items = getNearbyLoot();
		refreshSelectedLootItem(items);

		nearbyLoot = Collections.unmodifiableList(items);
		rebuildPanel(nearbyLoot);
	}

	private void refreshSelectedLootItem(List<AreaLootItem> items)
	{
		if (selectedItemId == -1)
		{
			selectedLootItem = null;
			return;
		}

		if (config.groupSameItemOverlay())
		{
			for (AreaLootItem item : items)
			{
				if (item.getId() == selectedItemId)
				{
					selectedLocation = item.getLocation();
					selectedStackId = item.getStackId();
					selectedLootItem = item;
					return;
				}
			}
		}
		else if (selectedLocation == null)
		{
			selectedLootItem = null;
			return;
		}

		for (AreaLootItem item : items)
		{
			if (isSelectedItem(item))
			{
				selectedLootItem = item;
				return;
			}
		}

		selectedLocation = null;
		selectedLootItem = null;
		selectedItemId = -1;
		selectedStackId = -1;
	}

	private boolean shouldMaintainLootSnapshot()
	{
		return manualOverlayEnabled || autoOverlayEnabled || sidePanelActive || selectedItemId != -1;
	}

	private boolean hasPlayerMoved()
	{
		WorldPoint playerLocation = getPlayerLocation();
		return playerLocation != null && !playerLocation.equals(lastPlayerLocation);
	}

	private boolean hasDelayedLootReady()
	{
		return nextDelayedLootRefreshMillis > 0 && System.currentTimeMillis() >= nextDelayedLootRefreshMillis;
	}

	private WorldPoint getPlayerLocation()
	{
		Player player = client.getLocalPlayer();
		if (client.getGameState() != GameState.LOGGED_IN || player == null)
		{
			return null;
		}

		return player.getWorldLocation();
	}

	private boolean isSelectedItem(AreaLootItem item)
	{
		return item.getId() == selectedItemId
			&& item.getLocation().equals(selectedLocation)
			&& item.getStackId() == selectedStackId;
	}

	private boolean shouldKeepMenuEntry(MenuEntry entry, int selectedSceneX, int selectedSceneY)
	{
		if (!isGroundItemMenuEntry(entry))
		{
			return true;
		}

		if (entry.getParam0() != selectedSceneX || entry.getParam1() != selectedSceneY)
		{
			return true;
		}

		return isSelectedMenuItem(entry);
	}

	private void colorSelectedMenuEntry(MenuEntry entry, int selectedSceneX, int selectedSceneY)
	{
		if (config.highlightMenuTextMode() == AreaLootConfig.MenuHighlightMode.NONE)
		{
			return;
		}

		if (!isSelectedMenuHighlightEntry(entry, selectedSceneX, selectedSceneY))
		{
			return;
		}

		entry.setTarget(ColorUtil.prependColorTag(Text.removeTags(entry.getTarget()), getThemeColor("highlightMenuTextColor")));
	}

	private boolean isSelectedMenuHighlightEntry(MenuEntry entry, int selectedSceneX, int selectedSceneY)
	{
		return isHighlightedMenuEntry(entry)
			&& entry.getParam0() == selectedSceneX
			&& entry.getParam1() == selectedSceneY
			&& isSelectedMenuItem(entry);
	}

	private boolean isHighlightedMenuEntry(MenuEntry entry)
	{
		switch (config.highlightMenuTextMode())
		{
			case TAKE_AND_EXAMINE:
				return isGroundItemMenuEntry(entry);
			case EXAMINE:
				return isGroundItemExamineEntry(entry);
			case TAKE:
				return isTakeGroundItemMenuEntry(entry);
			case NONE:
			default:
				return false;
		}
	}

	private boolean isSelectedGroundItemMenuEntry(MenuEntry entry, int selectedSceneX, int selectedSceneY)
	{
		return isTakeGroundItemMenuEntry(entry)
			&& entry.getParam0() == selectedSceneX
			&& entry.getParam1() == selectedSceneY
			&& isSelectedMenuItem(entry);
	}

	private MenuEntry[] promoteSelectedMenuEntries(MenuEntry[] menuEntries, int selectedSceneX, int selectedSceneY)
	{
		List<MenuEntry> examineEntries = new ArrayList<>();
		List<MenuEntry> selectedExamineEntries = new ArrayList<>();
		List<MenuEntry> actionEntries = new ArrayList<>();
		List<MenuEntry> selectedActionEntries = new ArrayList<>();
		for (MenuEntry entry : menuEntries)
		{
			if (isSelectedGroundItemMenuEntry(entry, selectedSceneX, selectedSceneY))
			{
				selectedActionEntries.add(entry);
			}
			else if (isSelectedGroundItemExamineEntry(entry, selectedSceneX, selectedSceneY))
			{
				selectedExamineEntries.add(entry);
			}
			else if (isGroundItemExamineEntry(entry))
			{
				examineEntries.add(entry);
			}
			else
			{
				actionEntries.add(entry);
			}
		}

		if (selectedActionEntries.isEmpty() && selectedExamineEntries.isEmpty())
		{
			return menuEntries;
		}

		List<MenuEntry> promotedEntries = new ArrayList<>(menuEntries.length);
		promotedEntries.addAll(examineEntries);
		promotedEntries.addAll(selectedExamineEntries);
		promotedEntries.addAll(actionEntries);
		promotedEntries.addAll(selectedActionEntries);
		return promotedEntries.toArray(new MenuEntry[0]);
	}

	private boolean isGroundItemMenuEntry(MenuEntry entry)
	{
		switch (entry.getType())
		{
			case ITEM_USE_ON_GROUND_ITEM:
			case WIDGET_TARGET_ON_GROUND_ITEM:
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
			case EXAMINE_ITEM_GROUND:
				return true;
			default:
				return false;
		}
	}

	private boolean isTakeGroundItemMenuEntry(MenuEntry entry)
	{
		switch (entry.getType())
		{
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
				return true;
			default:
				return false;
		}
	}

	private boolean isSelectedGroundItemExamineEntry(MenuEntry entry, int selectedSceneX, int selectedSceneY)
	{
		return isGroundItemExamineEntry(entry)
			&& entry.getParam0() == selectedSceneX
			&& entry.getParam1() == selectedSceneY
			&& isSelectedMenuItem(entry);
	}

	private boolean isSelectedMenuItem(MenuEntry entry)
	{
		return entry.getItemId() == selectedItemId || entry.getIdentifier() == selectedItemId;
	}

	private boolean isGroundItemExamineEntry(MenuEntry entry)
	{
		return entry.getType() == MenuAction.EXAMINE_ITEM_GROUND;
	}

	private void rebuildPanel(List<AreaLootItem> items)
	{
		if (panel != null && sidePanelRegistered)
		{
			SwingUtilities.invokeLater(() -> panel.rebuild(items));
		}
	}

	private List<AreaLootItem> getNearbyLoot()
	{
		Player player = client.getLocalPlayer();
		if (client.getGameState() != GameState.LOGGED_IN || player == null)
		{
			return new ArrayList<>();
		}

		WorldPoint playerLocation = player.getWorldLocation();
		int radius = config.lootRadius();
		long minimumGeValue = parseMinimumGeValue();
		Set<String> blockedItems = parseBlockedItems();
		Set<String> whitelistedItems = parseWhitelistedItems();
		List<AreaLootItem> items = new ArrayList<>();
		long now = System.currentTimeMillis();
		long itemDelayMillis = getOverlayItemDelayMillis();
		long nextDelayedRefreshMillis = 0;

		for (Map.Entry<WorldPoint, List<TrackedGroundItem>> entry : groundItems.entrySet())
		{
			WorldPoint location = entry.getKey();
			if (location.getPlane() != playerLocation.getPlane())
			{
				continue;
			}

			int distance = playerLocation.distanceTo(location);
			if (distance > radius)
			{
				continue;
			}

			for (TrackedGroundItem trackedItem : entry.getValue())
			{
				if (itemDelayMillis > 0)
				{
					long showAtMillis = trackedItem.getSpawnedAtMillis() + itemDelayMillis;
					if (now < showAtMillis)
					{
						nextDelayedRefreshMillis = nextDelayedRefreshMillis == 0
							? showAtMillis
							: Math.min(nextDelayedRefreshMillis, showAtMillis);
						continue;
					}
				}

				TileItem tileItem = trackedItem.getItem();
				String itemName = getItemName(tileItem.getId());
				if (isConfiguredItem(itemName, blockedItems))
				{
					continue;
				}

				boolean whitelisted = isConfiguredItem(itemName, whitelistedItems);
				long geValue = (long) getItemPrice(tileItem.getId()) * tileItem.getQuantity();
				if (!whitelisted && geValue < minimumGeValue)
				{
					continue;
				}

				items.add(new AreaLootItem(
					tileItem.getId(),
					System.identityHashCode(tileItem),
					tileItem.getQuantity(),
					itemName,
					location,
					distance,
					geValue
				));
			}
		}

		nextDelayedLootRefreshMillis = nextDelayedRefreshMillis;
		sortLoot(items);
		return items;
	}

	private long getOverlayItemDelayMillis()
	{
		return config.overlayItemDelay().getSeconds() * 1000L;
	}

	private void sortLoot(List<AreaLootItem> items)
	{
		if (config.sortMode() == AreaLootConfig.SortMode.GE_HIGH_TO_LOW)
		{
			items.sort(Comparator
				.comparingLong(AreaLootItem::getGeValue).reversed()
				.thenComparingInt(AreaLootItem::getDistance)
				.thenComparing(AreaLootItem::getName, String.CASE_INSENSITIVE_ORDER));
			return;
		}

		items.sort(Comparator
			.comparingInt(AreaLootItem::getDistance)
			.thenComparing(AreaLootItem::getName, String.CASE_INSENSITIVE_ORDER));
	}

	private long parseMinimumGeValue()
	{
		String value = config.minimumGeValue();
		if (value == null)
		{
			return 0;
		}

		String normalized = value.trim().toLowerCase().replace(",", "").replace("_", "");
		if (normalized.isEmpty())
		{
			return 0;
		}

		long multiplier = 1;
		if (normalized.endsWith("k"))
		{
			multiplier = 1_000;
			normalized = normalized.substring(0, normalized.length() - 1).trim();
		}
		else if (normalized.endsWith("m"))
		{
			multiplier = 1_000_000;
			normalized = normalized.substring(0, normalized.length() - 1).trim();
		}

		try
		{
			double amount = Double.parseDouble(normalized);
			return Math.max(0, (long) (amount * multiplier));
		}
		catch (NumberFormatException ex)
		{
			log.debug("Invalid Area Loot minimum GE value: {}", value);
			return 0;
		}
	}

	private String getItemName(int itemId)
	{
		String name = itemNameCache.get(itemId);
		if (name == null)
		{
			ItemComposition composition = itemManager.getItemComposition(itemId);
			name = composition.getName();
			itemNameCache.put(itemId, name);
		}
		return name;
	}

	private int getItemPrice(int itemId)
	{
		Integer price = itemPriceCache.get(itemId);
		if (price == null)
		{
			price = itemManager.getItemPrice(itemId);
			itemPriceCache.put(itemId, price);
		}
		return price;
	}

	private Set<String> parseBlockedItems()
	{
		return parseConfiguredItems(config.blockedItems());
	}

	private Set<String> parseWhitelistedItems()
	{
		return parseConfiguredItems(config.whitelistedItems());
	}

	private boolean addBlockedItem(String itemName)
	{
		if (!addConfiguredItem(itemName, BLOCKED_ITEMS_KEY, config.blockedItems(), "Blocked items"))
		{
			return false;
		}

		sendListUpdateMessage("Blocked", itemName);
		return true;
	}

	private boolean removeBlockedItem(String itemName)
	{
		if (!removeConfiguredItem(itemName, BLOCKED_ITEMS_KEY, config.blockedItems(), "Blocked items"))
		{
			return false;
		}

		sendListUpdateMessage("Unblocked", itemName);
		return true;
	}

	private boolean addWhitelistedItem(String itemName)
	{
		if (!addConfiguredItem(itemName, WHITELISTED_ITEMS_KEY, config.whitelistedItems(), "Whitelisted items"))
		{
			return false;
		}

		sendListUpdateMessage("Whitelisted", itemName);
		return true;
	}

	private boolean removeWhitelistedItem(String itemName)
	{
		if (!removeConfiguredItem(itemName, WHITELISTED_ITEMS_KEY, config.whitelistedItems(), "Whitelisted items"))
		{
			return false;
		}

		sendListUpdateMessage("Unwhitelisted", itemName);
		return true;
	}

	private boolean isBlockedByExactName(String itemName)
	{
		return isConfiguredByExactName(itemName, getConfiguredItemList(BLOCKED_ITEMS_KEY, config.blockedItems()));
	}

	private boolean isWhitelistedByExactName(String itemName)
	{
		return isConfiguredByExactName(itemName, getConfiguredItemList(WHITELISTED_ITEMS_KEY, config.whitelistedItems()));
	}

	private List<String> getConfiguredBlockedItemList()
	{
		return getConfiguredItemList(BLOCKED_ITEMS_KEY, config.blockedItems());
	}

	private boolean addConfiguredItem(String itemName, String key, String defaultValue, String configLabel)
	{
		String normalizedItemName = normalizeItemName(itemName);
		if (normalizedItemName.isEmpty())
		{
			return false;
		}

		List<String> configuredItems = getConfiguredItemList(key, defaultValue);
		for (String configuredItem : configuredItems)
		{
			if (normalizeItemName(configuredItem).equals(normalizedItemName))
			{
				return false;
			}
		}

		configuredItems.add(itemName);
		return updateConfiguredItems(key, configLabel, String.join(", ", configuredItems));
	}

	private boolean removeConfiguredItem(String itemName, String key, String defaultValue, String configLabel)
	{
		String normalizedItemName = normalizeItemName(itemName);
		if (normalizedItemName.isEmpty())
		{
			return false;
		}

		List<String> configuredItems = getConfiguredItemList(key, defaultValue);
		boolean removed = configuredItems.removeIf(configuredItem -> normalizeItemName(configuredItem).equals(normalizedItemName));
		if (!removed)
		{
			return false;
		}

		return updateConfiguredItems(key, configLabel, String.join(", ", configuredItems));
	}

	private boolean updateConfiguredItems(String key, String configLabel, String updatedItems)
	{
		configManager.setConfiguration(CONFIG_GROUP, key, updatedItems);

		String storedItems = configManager.getConfiguration(CONFIG_GROUP, key);
		if (!updatedItems.equals(storedItems))
		{
			log.debug("Area Loot failed to update {}. Expected '{}', stored '{}'", configLabel, updatedItems, storedItems);
			return false;
		}

		lootDirty = true;
		SwingUtilities.invokeLater(() -> updateOpenConfigText(configLabel, updatedItems));
		if (shouldMaintainLootSnapshot())
		{
			refreshLootSnapshot();
		}
		return true;
	}

	private void sendListUpdateMessage(String action, String itemName)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage("Area Loot " + action + ": " + Text.escapeJagex(itemName))
			.build());
	}

	private boolean isConfiguredByExactName(String itemName, List<String> configuredItems)
	{
		String normalizedItemName = normalizeItemName(itemName);
		for (String configuredItem : configuredItems)
		{
			if (normalizeItemName(configuredItem).equals(normalizedItemName))
			{
				return true;
			}
		}
		return false;
	}

	private List<String> getConfiguredItemList(String key, String defaultValue)
	{
		String value = configManager.getConfiguration(CONFIG_GROUP, key);
		if (value == null)
		{
			value = defaultValue;
		}
		return new ArrayList<>(parseConfiguredItemList(value));
	}

	private void updateOpenConfigText(String configLabel, String value)
	{
		for (Window window : Window.getWindows())
		{
			updateConfigText(window, configLabel, value);
		}
	}

	private boolean updateConfigText(Component component, String configLabel, String value)
	{
		if (!(component instanceof Container))
		{
			return false;
		}

		Container container = (Container) component;
		boolean hasBlockedItemsLabel = false;
		JTextComponent textComponent = null;
		for (Component child : container.getComponents())
		{
			if (child instanceof JLabel && configLabel.equals(((JLabel) child).getText()))
			{
				hasBlockedItemsLabel = true;
			}
			else if (child instanceof JTextComponent)
			{
				textComponent = (JTextComponent) child;
			}
		}

		if (hasBlockedItemsLabel && textComponent != null)
		{
			textComponent.setText(value);
			return true;
		}

		for (Component child : container.getComponents())
		{
			if (updateConfigText(child, configLabel, value))
			{
				return true;
			}
		}
		return false;
	}

	private Set<String> parseConfiguredItems(String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			return Collections.emptySet();
		}

		Set<String> configuredItems = new HashSet<>();
		for (String itemName : parseConfiguredItemList(value))
		{
			String normalized = normalizeItemName(itemName);
			if (!normalized.isEmpty())
			{
				configuredItems.add(normalized);
			}
		}
		return configuredItems;
	}

	private List<String> parseConfiguredItemList(String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			return Collections.emptyList();
		}

		List<String> configuredItems = new ArrayList<>();
		for (String configuredItem : value.split(","))
		{
			String trimmed = configuredItem.trim();
			if (!trimmed.isEmpty())
			{
				configuredItems.add(trimmed);
			}
		}
		return configuredItems;
	}

	private String normalizeItemName(String itemName)
	{
		return itemName == null ? "" : itemName.trim().toLowerCase();
	}

	private boolean isConfiguredItem(String itemName, Set<String> configuredItems)
	{
		String normalizedName = normalizeItemName(itemName);
		for (String configuredItem : configuredItems)
		{
			if (matchesConfiguredItem(normalizedName, configuredItem))
			{
				return true;
			}
		}
		return false;
	}

	private boolean matchesConfiguredItem(String itemName, String configuredItem)
	{
		if (!configuredItem.contains("*"))
		{
			return itemName.equals(configuredItem);
		}

		String[] parts = configuredItem.split("\\*", -1);
		int index = 0;
		for (String part : parts)
		{
			if (part.isEmpty())
			{
				continue;
			}

			index = itemName.indexOf(part, index);
			if (index < 0)
			{
				return false;
			}
			index += part.length();
		}

		String firstPart = parts.length == 0 ? "" : parts[0];
		String lastPart = parts.length == 0 ? "" : parts[parts.length - 1];
		return (firstPart.isEmpty() || itemName.startsWith(firstPart))
			&& (lastPart.isEmpty() || itemName.endsWith(lastPart));
	}

	private boolean shouldIgnoreHotkeys()
	{
		if (client.getFocusedInputFieldWidget() != null)
		{
			return true;
		}

		if (client.getVarcIntValue(VarClientID.MESLAYERMODE) != InputType.NONE.getType()
			|| client.getVarcIntValue(VarClientID.WORLDMAP_SEARCHING) != 0)
		{
			return true;
		}

		Widget chatboxParent = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
		if (chatboxParent == null || chatboxParent.getOnKeyListener() == null)
		{
			return false;
		}

		Widget chatboxInput = client.getWidget(InterfaceID.Chatbox.INPUT);
		if (chatboxInput == null)
		{
			return true;
		}

		String chatboxText = Text.removeTags(chatboxInput.getText());
		return chatboxText == null || !chatboxText.contains("Press Enter to Chat");
	}

	private class NonTypingHotkeyListener extends HotkeyListener
	{
		private NonTypingHotkeyListener(java.util.function.Supplier<Keybind> keybind)
		{
			super(keybind);
		}

		@Override
		public void keyPressed(KeyEvent event)
		{
			if (shouldIgnoreHotkeys())
			{
				return;
			}

			super.keyPressed(event);
		}

		@Override
		public void keyTyped(KeyEvent event)
		{
			if (shouldIgnoreHotkeys())
			{
				return;
			}

			super.keyTyped(event);
		}
	}

	private void addItem(WorldPoint location, TileItem item, long spawnedAtMillis)
	{
		groundItems.computeIfAbsent(location, ignored -> new ArrayList<>())
			.add(new TrackedGroundItem(item, spawnedAtMillis));
	}

	private Long removeItem(Tile tile, TileItem item)
	{
		WorldPoint location = tile.getWorldLocation();
		List<TrackedGroundItem> items = groundItems.get(location);
		if (items == null)
		{
			return null;
		}

		Long spawnedAtMillis = null;
		for (Iterator<TrackedGroundItem> iterator = items.iterator(); iterator.hasNext(); )
		{
			TrackedGroundItem trackedItem = iterator.next();
			TileItem current = trackedItem.getItem();
			if (current == item || current.getId() == item.getId())
			{
				spawnedAtMillis = trackedItem.getSpawnedAtMillis();
				iterator.remove();
				break;
			}
		}

		if (items.isEmpty())
		{
			groundItems.remove(location);
		}

		if (!config.groupSameItemOverlay() && location.equals(selectedLocation) && selectedItemId == item.getId())
		{
			selectedLocation = null;
			selectedLootItem = null;
			selectedItemId = -1;
			selectedStackId = -1;
		}

		return spawnedAtMillis;
	}

	private BufferedImage createLootIcon()
	{
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = icon.createGraphics();
		graphics.setColor(new java.awt.Color(48, 38, 18));
		graphics.fillRoundRect(2, 7, 10, 7, 3, 3);
		graphics.setColor(new java.awt.Color(210, 190, 35));
		graphics.fillOval(3, 8, 8, 5);
		graphics.fillOval(4, 5, 8, 5);
		graphics.setColor(new java.awt.Color(125, 91, 20));
		graphics.drawOval(3, 8, 8, 5);
		graphics.drawOval(4, 5, 8, 5);
		graphics.setColor(new java.awt.Color(0, 200, 255));
		graphics.drawRect(10, 2, 4, 4);
		graphics.drawLine(12, 6, 12, 9);
		graphics.dispose();
		return icon;
	}

	private BufferedImage createThemeIcon()
	{
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = icon.createGraphics();
		graphics.setColor(new java.awt.Color(32, 32, 32));
		graphics.fillRect(3, 2, 10, 12);
		graphics.setColor(new java.awt.Color(18, 18, 18));
		graphics.drawRect(3, 2, 10, 12);

		graphics.setColor(new java.awt.Color(220, 138, 0));
		graphics.fillRect(5, 4, 6, 2);
		graphics.setColor(new java.awt.Color(0, 200, 255));
		graphics.fillRect(5, 7, 6, 2);
		graphics.setColor(new java.awt.Color(210, 190, 35));
		graphics.fillRect(5, 10, 6, 2);

		graphics.setColor(new java.awt.Color(235, 235, 235));
		graphics.drawLine(12, 3, 14, 3);
		graphics.drawLine(13, 4, 13, 12);
		graphics.drawLine(12, 13, 14, 13);
		graphics.dispose();
		return icon;
	}

	private void clearOverlayRows()
	{
		synchronized (overlayRows)
		{
			overlayRows.clear();
		}
	}

	@Provides
	AreaLootConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AreaLootConfig.class);
	}

	private static final class TrackedGroundItem
	{
		private final TileItem item;
		private final long spawnedAtMillis;

		private TrackedGroundItem(TileItem item, long spawnedAtMillis)
		{
			this.item = item;
			this.spawnedAtMillis = spawnedAtMillis;
		}

		private TileItem getItem()
		{
			return item;
		}

		private long getSpawnedAtMillis()
		{
			return spawnedAtMillis;
		}
	}
}
