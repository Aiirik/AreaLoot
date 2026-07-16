package com.arealoot;

import com.google.inject.Provides;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
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
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
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
	private volatile List<AreaLootItem> nearbyLoot = Collections.emptyList();
	private final List<SimpleEntry<Rectangle, AreaLootItem>> overlayRows = new ArrayList<>();
	private boolean sidePanelRegistered;
	private boolean sidePanelActive;
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
		panel = new AreaLootPanel(this, config, itemManager);
		navButton = NavigationButton.builder()
			.tooltip("Area Loot")
			.icon(createIcon())
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
		sidePanelActive = false;
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
			AreaLootUpdateNotice.announceIfNeeded(configManager, chatMessageManager);
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
		else if ("sortMode".equals(key) || "minimumGeValue".equals(key) || "overlayItemDelay".equals(key) || BLOCKED_ITEMS_KEY.equals(key)
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
		switch (key)
		{
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

		client.createMenuEntry(-1)
			.setParam0(event.getActionParam0())
			.setParam1(event.getActionParam1())
			.setIdentifier(event.getIdentifier())
			.setItemId(event.getItemId())
			.setTarget(event.getTarget())
			.setOption(blockedByName ? "Unblock in Area Loot" : "Block in Area Loot")
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

		client.createMenuEntry(-1)
			.setParam0(event.getActionParam0())
			.setParam1(event.getActionParam1())
			.setIdentifier(event.getIdentifier())
			.setItemId(event.getItemId())
			.setTarget(event.getTarget())
			.setOption(whitelistedByName ? "Unwhitelist in Area Loot" : "Whitelist in Area Loot")
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
		return selectedLocation != null;
	}

	boolean isSelectedLoot(AreaLootItem item)
	{
		return isSelectedItem(item);
	}

	AreaLootItem getSelectedLootItem()
	{
		return selectedLootItem;
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
		if (selectedLocation == null)
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
		return manualOverlayEnabled || autoOverlayEnabled || sidePanelActive || selectedLocation != null;
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
			&& (config.groupSameTileSelection() || item.getStackId() == selectedStackId);
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

		entry.setTarget(ColorUtil.prependColorTag(Text.removeTags(entry.getTarget()), config.highlightMenuTextColor()));
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

		if (location.equals(selectedLocation) && selectedItemId == item.getId())
		{
			selectedLocation = null;
			selectedLootItem = null;
			selectedItemId = -1;
			selectedStackId = -1;
		}

		return spawnedAtMillis;
	}

	private BufferedImage createIcon()
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
