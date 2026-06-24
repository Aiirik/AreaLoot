package com.arealoot;

import com.google.inject.Provides;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
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

	private final Map<WorldPoint, List<TileItem>> groundItems = new HashMap<>();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private AreaLootConfig config;

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

	@Getter
	private volatile WorldPoint selectedLocation;
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
		panel = new AreaLootPanel(this, config, itemManager);
		navButton = NavigationButton.builder()
			.tooltip("Area Loot")
			.icon(createIcon())
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
		mouseManager.unregisterMouseListener(mouseListener);
		keyManager.unregisterKeyListener(autoShowHotkeyListener);
		keyManager.unregisterKeyListener(overlayHotkeyListener);
		overlayManager.remove(overlay);
		groundItems.clear();
		nearbyLoot = Collections.emptyList();
		clearOverlayRows();
		selectedLocation = null;
		selectedItemId = -1;
		manualOverlayEnabled = false;
		autoOverlayEnabled = false;
		overlayStatusUntilMillis = 0;
		overlayStatusText = "";
		overlayFadeOutActive = false;
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		WorldPoint location = event.getTile().getWorldLocation();
		groundItems.computeIfAbsent(location, ignored -> new ArrayList<>()).add(event.getItem());
		refreshLootSnapshot();
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		removeItem(event.getTile(), event.getItem());
		refreshLootSnapshot();
	}

	@Subscribe
	public void onItemQuantityChanged(ItemQuantityChanged event)
	{
		removeItem(event.getTile(), event.getItem());
		groundItems.computeIfAbsent(event.getTile().getWorldLocation(), ignored -> new ArrayList<>()).add(event.getItem());
		refreshLootSnapshot();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING || event.getGameState() == GameState.LOGIN_SCREEN)
		{
			groundItems.clear();
			nearbyLoot = Collections.emptyList();
			clearOverlayRows();
			selectedLocation = null;
			selectedItemId = -1;
			manualOverlayEnabled = false;
			autoOverlayEnabled = false;
			overlayStatusUntilMillis = 0;
			overlayStatusText = "";
			overlayFadeOutActive = false;
			rebuildPanel(Collections.emptyList());
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"area-loot".equals(event.getGroup()))
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
		});
	}

	private void openSidePanel()
	{
		if (!config.sidePanelEnabled())
		{
			return;
		}

		clientThread.invoke(() ->
		{
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
		}
	}

	private void refreshLootSnapshot()
	{
		List<AreaLootItem> items = getNearbyLoot();
		if (selectedLocation != null && items.stream().noneMatch(this::isSelectedItem))
		{
			selectedLocation = null;
			selectedItemId = -1;
		}

		nearbyLoot = Collections.unmodifiableList(items);
		rebuildPanel(nearbyLoot);
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
		if (panel != null)
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
				ItemComposition composition = itemManager.getItemComposition(tileItem.getId());
				long geValue = (long) itemManager.getItemPrice(tileItem.getId()) * tileItem.getQuantity();
				items.add(new AreaLootItem(
					tileItem.getId(),
					tileItem.getQuantity(),
					composition.getName(),
					location,
					distance,
					geValue
				));
			}
		}

		items.sort(Comparator
			.comparingInt(AreaLootItem::getDistance)
			.thenComparing(AreaLootItem::getName, String.CASE_INSENSITIVE_ORDER));
		return items;
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
		graphics.setColor(ColorScheme.BRAND_ORANGE);
		graphics.fillOval(3, 3, 10, 10);
		graphics.setColor(ColorScheme.TEXT_COLOR);
		graphics.drawOval(3, 3, 10, 10);
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
