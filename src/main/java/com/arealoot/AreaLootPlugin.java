package com.arealoot;

import com.google.inject.Provides;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
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
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
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
import net.runelite.client.util.HotkeyListener;

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
	private static final String CONFIG_GROUP = "area-loot";
	private static final String BLOCKED_ITEMS_KEY = "blockedItems";
	private static final String REMEMBERED_MANUAL_OVERLAY_KEY = "rememberedManualOverlayEnabled";
	private static final String REMEMBERED_AUTO_OVERLAY_KEY = "rememberedAutoOverlayEnabled";

	private final Map<WorldPoint, List<TileItem>> groundItems = new HashMap<>();
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
	private AreaLootOverlay overlay;

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

	@Getter
	private volatile WorldPoint selectedLocation;
	private WorldPoint lastPlayerLocation;
	private int selectedItemId = -1;
	private volatile boolean manualOverlayEnabled;
	private volatile boolean autoOverlayEnabled;
	private volatile long overlayStatusUntilMillis;
	private volatile String overlayStatusText = "";
	private volatile boolean overlayFadeOutActive;

	private final HotkeyListener overlayHotkeyListener = new HotkeyListener(() -> config.toggleHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			toggleOverlay();
		}
	};

	private final HotkeyListener sidePanelHotkeyListener = new HotkeyListener(() -> config.sidePanelHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			openSidePanel();
		}
	};

	private final HotkeyListener autoShowHotkeyListener = new HotkeyListener(() -> config.autoShowHotkey())
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
		restoreOverlayMode();
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
		keyManager.registerKeyListener(overlayHotkeyListener);
		keyManager.registerKeyListener(autoShowHotkeyListener);
		mouseManager.registerMouseListener(mouseListener);
		updateSidePanelRegistration();
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
		groundItems.clear();
		itemNameCache.clear();
		itemPriceCache.clear();
		nearbyLoot = Collections.emptyList();
		clearOverlayRows();
		selectedLocation = null;
		lastPlayerLocation = null;
		selectedItemId = -1;
		manualOverlayEnabled = false;
		autoOverlayEnabled = false;
		lootDirty = false;
		overlayStatusUntilMillis = 0;
		overlayStatusText = "";
		overlayFadeOutActive = false;
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		WorldPoint location = event.getTile().getWorldLocation();
		groundItems.computeIfAbsent(location, ignored -> new ArrayList<>()).add(event.getItem());
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
		removeItem(event.getTile(), event.getItem());
		groundItems.computeIfAbsent(event.getTile().getWorldLocation(), ignored -> new ArrayList<>()).add(event.getItem());
		lootDirty = true;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!shouldMaintainLootSnapshot())
		{
			return;
		}

		if (lootDirty || hasPlayerMoved())
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
			lastPlayerLocation = null;
			selectedItemId = -1;
			manualOverlayEnabled = false;
			autoOverlayEnabled = false;
			sidePanelActive = false;
			lootDirty = false;
			overlayStatusUntilMillis = 0;
			overlayStatusText = "";
			overlayFadeOutActive = false;
			rebuildPanel(Collections.emptyList());
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			restoreOverlayMode();
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
		if ("overlayX".equals(key) || "overlayY".equals(key) || "overlayWidth".equals(key))
		{
			overlay.applyConfiguredListBounds();
		}
		else if ("sidePanelEnabled".equals(key))
		{
			updateSidePanelRegistration();
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
		else if ("sortMode".equals(key) || "minimumGeValue".equals(key) || BLOCKED_ITEMS_KEY.equals(key) || "lootRadius".equals(key))
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
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!config.onlyShowHighlightedItemMenu() || selectedLocation == null)
		{
			return;
		}

		WorldView worldView = client.getTopLevelWorldView();
		int selectedSceneX = selectedLocation.getX() - worldView.getBaseX();
		int selectedSceneY = selectedLocation.getY() - worldView.getBaseY();
		MenuEntry[] menuEntries = event.getMenuEntries();
		MenuEntry[] filteredEntries = Arrays.stream(menuEntries)
			.filter(entry -> shouldKeepMenuEntry(entry, selectedSceneX, selectedSceneY))
			.toArray(MenuEntry[]::new);

		if (filteredEntries.length != menuEntries.length)
		{
			client.getMenu().setMenuEntries(filteredEntries);
		}
	}

	void selectLoot(AreaLootItem item)
	{
		selectedLocation = item.getLocation();
		selectedItemId = item.getId();
		rebuildPanel(nearbyLoot);
	}

	void clearSelectedLoot()
	{
		selectedLocation = null;
		selectedItemId = -1;
		rebuildPanel(nearbyLoot);
	}

	boolean hasSelectedLoot()
	{
		return selectedLocation != null;
	}

	boolean isSelectedLoot(AreaLootItem item)
	{
		return selectedLocation != null && item.getId() == selectedItemId && item.getLocation().equals(selectedLocation);
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

	String getOverlayStatusText()
	{
		return overlayStatusText;
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
			boolean wasShowing = shouldShowOverlayList();
			manualOverlayEnabled = !manualOverlayEnabled;
			if (manualOverlayEnabled)
			{
				autoOverlayEnabled = false;
				overlayStatusUntilMillis = 0;
				overlayStatusText = "";
				overlayFadeOutActive = false;
			}
			else if (wasShowing && config.animateOverlay())
			{
				overlayFadeOutActive = true;
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
				overlayStatusText = "Area Loot (auto) Enabled";
				overlayStatusUntilMillis = now + AUTO_STATUS_ENABLED_MILLIS;
				overlayFadeOutActive = false;
			}
			else
			{
				overlayStatusText = "Area Loot (auto) Disabled";
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
		if (selectedLocation != null && items.stream().noneMatch(this::isSelectedItem))
		{
			selectedLocation = null;
			selectedItemId = -1;
		}

		nearbyLoot = Collections.unmodifiableList(items);
		rebuildPanel(nearbyLoot);
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
		return item.getId() == selectedItemId && item.getLocation().equals(selectedLocation);
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

		return entry.getItemId() == selectedItemId || entry.getIdentifier() == selectedItemId;
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
		List<AreaLootItem> items = new ArrayList<>();

		for (Map.Entry<WorldPoint, List<TileItem>> entry : groundItems.entrySet())
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

			for (TileItem tileItem : entry.getValue())
			{
				String itemName = getItemName(tileItem.getId());
				if (isBlockedItem(itemName, blockedItems))
				{
					continue;
				}

				long geValue = (long) getItemPrice(tileItem.getId()) * tileItem.getQuantity();
				if (geValue < minimumGeValue)
				{
					continue;
				}

				items.add(new AreaLootItem(
					tileItem.getId(),
					tileItem.getQuantity(),
					itemName,
					location,
					distance,
					geValue
				));
			}
		}

		sortLoot(items);
		return items;
	}

	private void sortLoot(List<AreaLootItem> items)
	{
		if (config.sortMode() == AreaLootSortMode.GE_HIGH_TO_LOW)
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
		String value = config.blockedItems();
		if (value == null || value.trim().isEmpty())
		{
			return Collections.emptySet();
		}

		Set<String> blockedItems = new HashSet<>();
		for (String itemName : parseBlockedItemList(value))
		{
			String normalized = normalizeItemName(itemName);
			if (!normalized.isEmpty())
			{
				blockedItems.add(normalized);
			}
		}
		return blockedItems;
	}

	private void addBlockedItem(String itemName)
	{
		String normalizedItemName = normalizeItemName(itemName);
		if (normalizedItemName.isEmpty())
		{
			return;
		}

		List<String> blockedItems = getConfiguredBlockedItemList();

		for (String blockedItem : blockedItems)
		{
			if (normalizeItemName(blockedItem).equals(normalizedItemName))
			{
				return;
			}
		}

		blockedItems.add(itemName);
		String updatedBlockedItems = String.join(", ", blockedItems);
		configManager.setConfiguration(CONFIG_GROUP, BLOCKED_ITEMS_KEY, updatedBlockedItems);

		String storedBlockedItems = configManager.getConfiguration(CONFIG_GROUP, BLOCKED_ITEMS_KEY);
		if (!updatedBlockedItems.equals(storedBlockedItems))
		{
			log.debug("Area Loot failed to update blocked items. Expected '{}', stored '{}'", updatedBlockedItems, storedBlockedItems);
			return;
		}

		lootDirty = true;
		SwingUtilities.invokeLater(() -> updateOpenBlockedItemsConfigText(updatedBlockedItems));
		if (shouldMaintainLootSnapshot())
		{
			refreshLootSnapshot();
		}
	}

	private void removeBlockedItem(String itemName)
	{
		String normalizedItemName = normalizeItemName(itemName);
		if (normalizedItemName.isEmpty())
		{
			return;
		}

		List<String> blockedItems = getConfiguredBlockedItemList();
		boolean removed = blockedItems.removeIf(blockedItem -> normalizeItemName(blockedItem).equals(normalizedItemName));
		if (!removed)
		{
			return;
		}

		String updatedBlockedItems = String.join(", ", blockedItems);
		configManager.setConfiguration(CONFIG_GROUP, BLOCKED_ITEMS_KEY, updatedBlockedItems);

		String storedBlockedItems = configManager.getConfiguration(CONFIG_GROUP, BLOCKED_ITEMS_KEY);
		if (!updatedBlockedItems.equals(storedBlockedItems))
		{
			log.debug("Area Loot failed to update blocked items. Expected '{}', stored '{}'", updatedBlockedItems, storedBlockedItems);
			return;
		}

		lootDirty = true;
		SwingUtilities.invokeLater(() -> updateOpenBlockedItemsConfigText(updatedBlockedItems));
		if (shouldMaintainLootSnapshot())
		{
			refreshLootSnapshot();
		}
	}

	private boolean isBlockedByExactName(String itemName)
	{
		String normalizedItemName = normalizeItemName(itemName);
		for (String blockedItem : getConfiguredBlockedItemList())
		{
			if (normalizeItemName(blockedItem).equals(normalizedItemName))
			{
				return true;
			}
		}
		return false;
	}

	private List<String> getConfiguredBlockedItemList()
	{
		String value = configManager.getConfiguration(CONFIG_GROUP, BLOCKED_ITEMS_KEY);
		if (value == null)
		{
			value = config.blockedItems();
		}
		return new ArrayList<>(parseBlockedItemList(value));
	}

	private void updateOpenBlockedItemsConfigText(String blockedItems)
	{
		for (Window window : Window.getWindows())
		{
			updateBlockedItemsConfigText(window, blockedItems);
		}
	}

	private boolean updateBlockedItemsConfigText(Component component, String blockedItems)
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
			if (child instanceof JLabel && "Blocked items".equals(((JLabel) child).getText()))
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
			textComponent.setText(blockedItems);
			return true;
		}

		for (Component child : container.getComponents())
		{
			if (updateBlockedItemsConfigText(child, blockedItems))
			{
				return true;
			}
		}
		return false;
	}

	private List<String> parseBlockedItemList(String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			return Collections.emptyList();
		}

		List<String> blockedItems = new ArrayList<>();
		for (String blockedItem : value.split(","))
		{
			String trimmed = blockedItem.trim();
			if (!trimmed.isEmpty())
			{
				blockedItems.add(trimmed);
			}
		}
		return blockedItems;
	}

	private String normalizeItemName(String itemName)
	{
		return itemName == null ? "" : itemName.trim().toLowerCase();
	}

	private boolean isBlockedItem(String itemName, Set<String> blockedItems)
	{
		String normalizedName = normalizeItemName(itemName);
		for (String blockedItem : blockedItems)
		{
			if (matchesBlockedItem(normalizedName, blockedItem))
			{
				return true;
			}
		}
		return false;
	}

	private boolean matchesBlockedItem(String itemName, String blockedItem)
	{
		if (!blockedItem.contains("*"))
		{
			return itemName.equals(blockedItem);
		}

		String[] parts = blockedItem.split("\\*", -1);
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

	private void removeItem(Tile tile, TileItem item)
	{
		WorldPoint location = tile.getWorldLocation();
		List<TileItem> items = groundItems.get(location);
		if (items == null)
		{
			return;
		}

		for (Iterator<TileItem> iterator = items.iterator(); iterator.hasNext(); )
		{
			TileItem current = iterator.next();
			if (current == item || current.getId() == item.getId())
			{
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
			selectedItemId = -1;
		}
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
}
