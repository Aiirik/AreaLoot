package com.arealoot;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.AsyncBufferedImage;

class AreaLootOverlay extends Overlay
{
	private static final int HEADER_HEIGHT = 22;
	private static final int ROW_HEIGHT = 24;
	private static final int GRID_CELL_GAP = 2;
	private static final int GRID_OUTER_PADDING = 3;
	private static final int GRID_CELL_HORIZONTAL_PADDING = 3;
	private static final int GRID_CELL_VERTICAL_PADDING = 2;
	private static final int GRID_TEXT_LINE_HEIGHT = 12;
	private static final int FOOTER_LINE_HEIGHT = 12;
	private static final int FOOTER_TOP_GAP = 4;
	private static final int CONDENSED_NAME_LINE_GAP = 1;
	private static final int CONDENSED_NAME_EXTRA_PADDING = 2;
	private static final int CONDENSED_ROW_GAP = 3;
	private static final String GRID_STABLE_QUANTITY_TEXT = "x999";
	private static final String[] GRID_STABLE_GE_TEXTS = {"999gp", "999k", "999m"};
	private static final String GRID_STABLE_DISTANCE_SHORT_TEXT = "30t";
	private static final String GRID_STABLE_DISTANCE_LONG_TEXT = "30 Tiles";
	private static final String TRUNCATION_SUFFIX = "...";
	private static final int PADDING = 6;
	private static final int METADATA_GAP = 12;
	private static final double LINE_EDGE_GAP = 3.0;
	private static final long FADE_DURATION_MILLIS = 220L;

	private final Client client;
	private final AreaLootPlugin plugin;
	private final AreaLootConfig config;
	private final ItemManager itemManager;
	private List<AreaLootItem> lastVisibleItems = new ArrayList<>();
	private String lastHeaderText = "Area Loot";
	private String lastEmptyText = "No nearby loot";
	private boolean lastVisibleListRendered;
	private boolean wasShowing;
	private boolean keepOverlayAboveGame;
	private long fadeInStartedAtMillis;
	private long fadeStartedAtMillis;

	@Inject
	AreaLootOverlay(Client client, AreaLootPlugin plugin, AreaLootConfig config, ItemManager itemManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		keepOverlayAboveGame = !config.keepOverlayAboveGame();
		applyConfiguredLayer();
		setMovable(true);
		setSnappable(true);
		setResettable(true);
		setResizable(true);
		setMinimumSize(0);
		setPreferredSize(new Dimension(0, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (client.getGameState() != GameState.LOGGED_IN || !isGameInterfaceVisible())
		{
			clearRenderState();
			return null;
		}

		Dimension listDimension = renderLootList(graphics);
		java.awt.Point origin = getBounds().getLocation();
		graphics.translate(-origin.x, -origin.y);
		renderSelectedTile(graphics);
		graphics.translate(origin.x, origin.y);
		return listDimension;
	}

	private boolean isGameInterfaceVisible()
	{
		return isVisible(InterfaceID.ToplevelOsrsStretch.VIEWPORT)
			|| isVisible(InterfaceID.ToplevelPreEoc.VIEWPORT)
			|| isVisible(InterfaceID.Toplevel.VIEWPORT)
			|| isVisible(InterfaceID.ToplevelOsm.VIEWPORT);
	}

	private boolean isVisible(int componentId)
	{
		Widget widget = client.getWidget(componentId);
		return widget != null && !widget.isHidden();
	}

	private void clearRenderState()
	{
		wasShowing = false;
		lastVisibleListRendered = false;
		fadeInStartedAtMillis = 0;
		fadeStartedAtMillis = 0;
		plugin.finishOverlayFadeOut();
		plugin.setOverlayRows(new ArrayList<>());
	}

	boolean applyConfiguredLayer()
	{
		boolean configuredOverlayAboveGame = config.keepOverlayAboveGame();
		if (configuredOverlayAboveGame == keepOverlayAboveGame)
		{
			return false;
		}

		keepOverlayAboveGame = configuredOverlayAboveGame;
		setLayer(keepOverlayAboveGame ? OverlayLayer.ALWAYS_ON_TOP : OverlayLayer.ABOVE_SCENE);
		return true;
	}

	private Dimension renderLootList(Graphics2D graphics)
	{
		boolean shouldShow = plugin.shouldShowOverlayList() && !plugin.isOverlayFadeOutActive();
		if (!shouldShow && (!config.animateOverlay() || !lastVisibleListRendered))
		{
			clearRenderState();
			return null;
		}

		long now = System.currentTimeMillis();
		List<AreaLootItem> items = getOverlayLootItems(plugin.getNearbyLootSnapshot());
		String headerText;
		String emptyText;
		float alpha = 1.0f;
		boolean fading = false;
		boolean fadingOut = false;

		if (shouldShow)
		{
			lastVisibleItems = new ArrayList<>(items);
			lastHeaderText = getHeaderText();
			lastEmptyText = getEmptyText();
			headerText = lastHeaderText;
			emptyText = lastEmptyText;
			lastVisibleListRendered = true;
			fadeStartedAtMillis = 0;
			if (!wasShowing)
			{
				fadeInStartedAtMillis = now;
			}
			wasShowing = true;

			if (config.animateOverlay() && fadeInStartedAtMillis > 0)
			{
				long elapsed = now - fadeInStartedAtMillis;
				if (elapsed < FADE_DURATION_MILLIS)
				{
					alpha = (float) elapsed / FADE_DURATION_MILLIS;
					fading = true;
				}
				else
				{
					fadeInStartedAtMillis = 0;
				}
			}
		}
		else
		{
			wasShowing = false;
			fadeInStartedAtMillis = 0;
			if (!config.animateOverlay())
			{
				lastVisibleItems = new ArrayList<>();
				lastVisibleListRendered = false;
				plugin.finishOverlayFadeOut();
				plugin.setOverlayRows(new ArrayList<>());
				return null;
			}

			if (fadeStartedAtMillis == 0)
			{
				fadeStartedAtMillis = now;
			}

			long elapsed = now - fadeStartedAtMillis;
			if (elapsed >= FADE_DURATION_MILLIS)
			{
				lastVisibleItems = new ArrayList<>();
				lastVisibleListRendered = false;
				plugin.finishOverlayFadeOut();
				plugin.setOverlayRows(new ArrayList<>());
				return null;
			}

			alpha = 1.0f - ((float) elapsed / FADE_DURATION_MILLIS);
			items = lastVisibleItems;
			headerText = lastHeaderText;
			emptyText = lastEmptyText;
			fading = true;
			fadingOut = true;
		}

		if (config.overlayStyle() == AreaLootConfig.OverlayStyle.GRID)
		{
			return renderLootGrid(graphics, items, headerText, emptyText, fading, fadingOut, alpha);
		}

		java.awt.Point origin = getBounds().getLocation();
		int listX = 0;
		int listY = 0;
		int rowCount = Math.min(items.size(), config.maxOverlayItems());
		FontMetrics metrics = graphics.getFontMetrics();
		boolean showHeader = shouldShowOverlayTitle(headerText);
		boolean showItemIcons = showListItemIcons();
		boolean showItemNames = config.showItemNamesInListMode();
		int listIconSize = config.listIconSize().getPixels();
		int distanceWidth = getMaxDistanceWidth(metrics, items, rowCount);
		boolean condenseItemNames = shouldCondenseListItemNames() && showItemNames;
		List<ListRowLayout> rowLayouts = buildListRowLayouts(
			metrics,
			items,
			rowCount,
			distanceWidth,
			listIconSize,
			showItemIcons,
			showItemNames,
			condenseItemNames,
			config.condenseListItemNamesWidth());
		int rowHeight = getListRowHeight(metrics, listIconSize, rowLayouts);
		int listWidth = getListWidth(metrics, rowLayouts, items.size(), rowCount, headerText, emptyText, showHeader);
		int headerHeight = getOverlayHeaderHeight(headerText);
		int footerLineCount = 0;
		int condensedRowGapCount = getCondensedRowGapCount(rowLayouts);
		int height = headerHeight + Math.max(1, rowCount) * rowHeight + (condensedRowGapCount * CONDENSED_ROW_GAP) + PADDING;
		int footerWidth = 0;
		boolean showFooter = !items.isEmpty()
			&& (hasVisibleLootSummary(items) || shouldShowMoreItemsLine(items, rowCount) || shouldShowSelectedItemFooter());
		if (showFooter)
		{
			footerLineCount = getFooterLineCount(metrics, items, rowCount, listWidth - (PADDING * 2));
			footerWidth = getFooterWidth(metrics, items, rowCount, footerLineCount);
			height += getFooterTopGap(footerLineCount) + (footerLineCount * FOOTER_LINE_HEIGHT);
		}
		listWidth = Math.max(listWidth, footerWidth + (PADDING * 2));
		List<SimpleEntry<Rectangle, AreaLootItem>> rowBounds = new ArrayList<>();
		Composite originalComposite = graphics.getComposite();
		if (fading)
		{
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		}

		graphics.setColor(config.overlayBackgroundColor());
		graphics.fillRoundRect(listX, listY, listWidth, height, 6, 6);
		graphics.setColor(config.overlayBorderColor());
		graphics.drawRoundRect(listX, listY, listWidth, height, 6, 6);

		if (showHeader)
		{
			drawHeaderText(graphics, metrics, headerText, listX + PADDING, listY + 15);
		}

		int rowStartY = listY + headerHeight;
		if (items.isEmpty())
		{
			graphics.setColor(config.overlaySecondaryTextColor());
			graphics.drawString(emptyText, listX + PADDING, rowStartY + 15);
			plugin.setOverlayRows(fading ? new ArrayList<>() : rowBounds);
			graphics.setComposite(originalComposite);
			return new Dimension(listWidth, height);
		}

		int rowY = rowStartY;
		for (int i = 0; i < rowCount; i++)
		{
			AreaLootItem item = items.get(i);
			ListRowLayout layout = rowLayouts.get(i);
			int y = rowY;
			Rectangle localRow = new Rectangle(listX, y, listWidth, rowHeight);
			Rectangle clickRow = new Rectangle(origin.x + listX, origin.y + y, listWidth, rowHeight);
			if (!fadingOut)
			{
				rowBounds.add(new SimpleEntry<>(clickRow, item));
			}

			if (plugin.isSelectedOverlayLoot(item))
			{
				renderSelectedOverlayEntry(graphics, localRow, false);
			}

			int textX = listX + PADDING;
			if (showItemIcons)
			{
				AsyncBufferedImage image = itemManager.getImage(item.getId(), item.getQuantity(), false);
				if (image != null)
				{
					int iconY = y + ((rowHeight - listIconSize) / 2);
					graphics.drawImage(image, listX + PADDING, iconY, listIconSize, listIconSize, null);
				}
				textX += listIconSize + 6;
			}

			String distanceText = formatDistance(item);
			int textTop = y + getListTextTopPadding(rowHeight, layout.getTextBlockHeight(metrics));
			int textBaseline = textTop + metrics.getAscent();
			if (showItemNames)
			{
				graphics.setColor(config.overlayTextColor());
				graphics.drawString(layout.getFirstLine(), textX, textBaseline);
				if (layout.hasSecondLine())
				{
					graphics.drawString(layout.getSecondLine(), textX, textBaseline + metrics.getHeight() + CONDENSED_NAME_LINE_GAP);
				}

				int metadataRight = listX + listWidth - PADDING;
				if (distanceWidth > 0)
				{
					metadataRight -= distanceWidth + METADATA_GAP;
				}
				int metadataBaseline = getListMetadataBaseline(textTop, layout, metrics);
				if (config.showGeValue())
				{
					String valueText = formatGeValue(item);
					graphics.setColor(config.geValueTextColor());
					graphics.drawString(valueText, metadataRight - metrics.stringWidth(valueText), metadataBaseline);
				}
				if (!distanceText.isEmpty())
				{
					graphics.setColor(config.tileDistanceTextColor());
					graphics.drawString(distanceText, listX + listWidth - PADDING - metrics.stringWidth(distanceText), metadataBaseline);
				}
			}
			else
			{
				int metadataX = textX;
				int metadataBaseline = getListMetadataBaseline(textTop, layout, metrics);
				if (config.showGeValue())
				{
					String valueText = formatGeValue(item);
					graphics.setColor(config.geValueTextColor());
					int valueWidth = metrics.stringWidth(valueText);
					int distanceGap = !distanceText.isEmpty() ? METADATA_GAP : 0;
					graphics.drawString(valueText, metadataX, metadataBaseline);
					metadataX += valueWidth + distanceGap;
				}
				if (!distanceText.isEmpty())
				{
					graphics.setColor(config.tileDistanceTextColor());
					graphics.drawString(distanceText, listX + listWidth - PADDING - metrics.stringWidth(distanceText), metadataBaseline);
				}
			}

			rowY += rowHeight;
			if (layout.hasSecondLine() && i < rowCount - 1)
			{
				rowY += CONDENSED_ROW_GAP;
			}
		}

		drawFooterLines(graphics, metrics, items, rowCount, listX + PADDING,
			rowY + 10 + getFooterTopGap(footerLineCount),
			listWidth - (PADDING * 2));

		plugin.setOverlayRows(rowBounds);
		graphics.setComposite(originalComposite);
		return new Dimension(listWidth, height);
	}

	private Dimension renderLootGrid(
		Graphics2D graphics,
		List<AreaLootItem> items,
		String headerText,
		String emptyText,
		boolean fading,
		boolean fadingOut,
		float alpha)
	{
		java.awt.Point origin = getBounds().getLocation();
		int gridX = 0;
		int gridY = 0;
		int configuredColumns = Math.max(1, config.gridColumns());
		int configuredRows = Math.max(1, config.gridRows());
		int maxItems = configuredColumns * configuredRows;
		int itemCount = Math.min(items.size(), maxItems);
		int columns = configuredColumns;
		int visibleRows = configuredRows;
		boolean verticalFill = config.gridFillDirection() == AreaLootConfig.GridFillDirection.VERTICAL;
		if (config.gridAutoAdjust())
		{
			if (verticalFill)
			{
				visibleRows = Math.max(1, Math.min(configuredRows, Math.max(1, itemCount)));
				columns = Math.max(1, (int) Math.ceil(itemCount / (double) visibleRows));
			}
			else
			{
				columns = Math.max(1, Math.min(configuredColumns, Math.max(1, itemCount)));
				visibleRows = Math.max(1, (int) Math.ceil(itemCount / (double) columns));
			}
		}
		FontMetrics metrics = graphics.getFontMetrics();
		int gridIconSize = config.gridIconSize().getPixels();
		int metadataLines = getGridMetadataLineCount(items);
		int cellWidth = getStableGridCellWidth(metrics, gridIconSize, items);
		int cellHeight = (GRID_CELL_VERTICAL_PADDING * 2) + gridIconSize + (metadataLines * GRID_TEXT_LINE_HEIGHT);
		int gridWidth = (columns * cellWidth) + ((columns - 1) * GRID_CELL_GAP);
		int overlayWidth = gridWidth + (GRID_OUTER_PADDING * 2);
		boolean showHeader = shouldShowOverlayTitle(headerText);
		if (showHeader)
		{
			overlayWidth = Math.max(overlayWidth, metrics.stringWidth(headerText) + (GRID_OUTER_PADDING * 2));
		}
		if (items.isEmpty())
		{
			overlayWidth = Math.max(overlayWidth, metrics.stringWidth(emptyText) + (GRID_OUTER_PADDING * 2));
		}
		int headerHeight = getOverlayHeaderHeight(headerText);
		int footerLineCount = getFooterLineCount(metrics, items, itemCount, overlayWidth - (GRID_OUTER_PADDING * 2));
		int footerWidth = getFooterWidth(metrics, items, itemCount, footerLineCount);
		overlayWidth = Math.max(overlayWidth, footerWidth + (GRID_OUTER_PADDING * 2));
		footerLineCount = getFooterLineCount(metrics, items, itemCount, overlayWidth - (GRID_OUTER_PADDING * 2));
		int height = headerHeight + (visibleRows * cellHeight) + GRID_OUTER_PADDING
			+ getFooterTopGap(footerLineCount) + (footerLineCount * FOOTER_LINE_HEIGHT);

		List<SimpleEntry<Rectangle, AreaLootItem>> cellBounds = new ArrayList<>();
		Composite originalComposite = graphics.getComposite();
		if (fading)
		{
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		}

		graphics.setColor(config.overlayBackgroundColor());
		graphics.fillRoundRect(gridX, gridY, overlayWidth, height, 6, 6);
		graphics.setColor(config.overlayBorderColor());
		graphics.drawRoundRect(gridX, gridY, overlayWidth, height, 6, 6);

		if (showHeader)
		{
			drawHeaderText(graphics, metrics, headerText, gridX + GRID_OUTER_PADDING, gridY + 15);
		}

		int gridStartX = gridX + GRID_OUTER_PADDING;
		int gridStartY = gridY + headerHeight;
		if (items.isEmpty())
		{
			graphics.setColor(config.overlaySecondaryTextColor());
			graphics.drawString(emptyText, gridStartX, gridStartY + 15);
			plugin.setOverlayRows(fading ? new ArrayList<>() : cellBounds);
			graphics.setComposite(originalComposite);
			return new Dimension(overlayWidth, height);
		}

		for (int i = 0; i < itemCount; i++)
		{
			AreaLootItem item = items.get(i);
			int column = verticalFill ? i / visibleRows : i % columns;
			int row = verticalFill ? i % visibleRows : i / columns;
			int x = gridStartX + (column * (cellWidth + GRID_CELL_GAP));
			int y = gridStartY + (row * cellHeight);
			Rectangle localCell = new Rectangle(x, y, cellWidth, cellHeight);
			Rectangle clickCell = new Rectangle(origin.x + x, origin.y + y, cellWidth, cellHeight);
			if (!fadingOut)
			{
				cellBounds.add(new SimpleEntry<>(clickCell, item));
			}

			if (plugin.isSelectedOverlayLoot(item))
			{
				renderSelectedOverlayEntry(graphics, localCell, true);
			}

			AsyncBufferedImage image = itemManager.getImage(item.getId(), item.getQuantity(), false);
			if (image != null)
			{
				int iconX = x + ((cellWidth - gridIconSize) / 2);
				graphics.drawImage(image, iconX, y + GRID_CELL_VERTICAL_PADDING, gridIconSize, gridIconSize, null);
			}

			int textY = y + GRID_CELL_VERTICAL_PADDING + gridIconSize + 10;
			if (item.getQuantity() > 1)
			{
				graphics.setColor(config.overlayTextColor());
				drawCenteredGridText(graphics, metrics, "x" + item.getQuantity(), x, textY, cellWidth);
				textY += GRID_TEXT_LINE_HEIGHT;
			}
			if (config.showGeValue())
			{
				String valueText = formatGeValue(item);
				graphics.setColor(config.geValueTextColor());
				drawCenteredGridText(graphics, metrics, valueText, x, textY, cellWidth);
				textY += GRID_TEXT_LINE_HEIGHT;
			}

			String distanceText = formatDistance(item);
			if (!distanceText.isEmpty())
			{
				graphics.setColor(config.tileDistanceTextColor());
				drawCenteredGridText(graphics, metrics, distanceText, x, textY, cellWidth);
			}
		}

		drawFooterLines(graphics, metrics, items, itemCount, gridX + GRID_OUTER_PADDING,
			gridStartY + (visibleRows * cellHeight) + 10 + getFooterTopGap(footerLineCount),
			overlayWidth - (GRID_OUTER_PADDING * 2));

		plugin.setOverlayRows(cellBounds);
		graphics.setComposite(originalComposite);
		return new Dimension(overlayWidth, height);
	}

	private int getGridMetadataLineCount(List<AreaLootItem> items)
	{
		int lines = 0;
		if (shouldShowGridQuantityLine(items))
		{
			lines++;
		}
		if (config.showGeValue())
		{
			lines++;
		}
		if (config.tileDistanceMode() != AreaLootConfig.DistanceMode.NONE)
		{
			lines++;
		}
		return lines;
	}

	private int getStableGridCellWidth(FontMetrics metrics, int gridIconSize, List<AreaLootItem> items)
	{
		int width = gridIconSize + (GRID_CELL_HORIZONTAL_PADDING * 2);
		if (shouldShowGridQuantityLine(items))
		{
			width = Math.max(width, metrics.stringWidth(GRID_STABLE_QUANTITY_TEXT) + (GRID_CELL_HORIZONTAL_PADDING * 2));
		}
		if (config.showGeValue())
		{
			for (String geText : GRID_STABLE_GE_TEXTS)
			{
				width = Math.max(width, metrics.stringWidth(geText) + (GRID_CELL_HORIZONTAL_PADDING * 2));
			}
		}
		if (config.tileDistanceMode() == AreaLootConfig.DistanceMode.SHORT)
		{
			width = Math.max(width, metrics.stringWidth(GRID_STABLE_DISTANCE_SHORT_TEXT) + (GRID_CELL_HORIZONTAL_PADDING * 2));
		}
		else if (config.tileDistanceMode() == AreaLootConfig.DistanceMode.LONG)
		{
			width = Math.max(width, metrics.stringWidth(GRID_STABLE_DISTANCE_LONG_TEXT) + (GRID_CELL_HORIZONTAL_PADDING * 2));
		}
		return width;
	}

	private boolean shouldShowGridQuantityLine(List<AreaLootItem> items)
	{
		if (!config.groupSameItemOverlay())
		{
			return false;
		}

		for (AreaLootItem item : items)
		{
			if (item.getQuantity() > 1)
			{
				return true;
			}
		}
		return false;
	}

	private void drawCenteredGridText(Graphics2D graphics, FontMetrics metrics, String text, int x, int y, int cellWidth)
	{
		String visibleText = fitTextToWidth(metrics, text, cellWidth - (GRID_CELL_HORIZONTAL_PADDING * 2));
		int textX = x + ((cellWidth - metrics.stringWidth(visibleText)) / 2);
		graphics.drawString(visibleText, Math.max(x + GRID_CELL_HORIZONTAL_PADDING, textX), y);
	}

	private String fitTextToWidth(FontMetrics metrics, String text, int maxWidth)
	{
		if (metrics.stringWidth(text) <= maxWidth)
		{
			return text;
		}

		int suffixWidth = metrics.stringWidth(TRUNCATION_SUFFIX);
		if (suffixWidth >= maxWidth)
		{
			return "";
		}

		for (int i = text.length() - 1; i > 0; i--)
		{
			String shortened = text.substring(0, i) + TRUNCATION_SUFFIX;
			if (metrics.stringWidth(shortened) <= maxWidth)
			{
				return shortened;
			}
		}

		return "";
	}

	private void renderSelectedOverlayEntry(Graphics2D graphics, Rectangle bounds, boolean rounded)
	{
		graphics.setColor(config.overlaySelectedRowColor());
		if (config.overlaySelectionStyle() == AreaLootConfig.OverlaySelectionStyle.OUTLINE)
		{
			Stroke originalStroke = graphics.getStroke();
			graphics.setStroke(new BasicStroke(2));
			if (rounded)
			{
				graphics.drawRoundRect(bounds.x + 1, bounds.y + 1, bounds.width - 3, bounds.height - 3, 4, 4);
			}
			else
			{
				graphics.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 3, bounds.height - 3);
			}
			graphics.setStroke(originalStroke);
			return;
		}

		if (rounded)
		{
			graphics.fillRoundRect(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2, 4, 4);
		}
		else
		{
			graphics.fillRect(bounds.x + 1, bounds.y, bounds.width - 2, bounds.height);
		}
	}

	private String getHeaderText()
	{
		String headerText = "Area Loot";
		if (plugin.shouldShowOverlayStatus())
		{
			headerText += " (" + plugin.getOverlayStatusMode() + ") - " + plugin.getOverlayStatusText();
		}
		return headerText;
	}

	private String getEmptyText()
	{
		return "No nearby loot";
	}

	private void drawHeaderText(Graphics2D graphics, FontMetrics metrics, String headerText, int x, int y)
	{
		String baseText = "Area Loot";
		graphics.setColor(config.overlayHeaderColor());
		if (isExpiredEnabledStatusHeader(headerText))
		{
			if (config.showOverlayTitle())
			{
				graphics.drawString(baseText, x, y);
			}
			return;
		}

		if (!plugin.shouldShowOverlayStatus() || !headerText.startsWith(baseText))
		{
			graphics.drawString(headerText, x, y);
			return;
		}

		String statusText = headerText.substring(baseText.length());
		if (!"Enabled".equals(plugin.getOverlayStatusText()))
		{
			graphics.drawString(headerText, x, y);
			return;
		}

		float statusAlpha = plugin.getOverlayStatusAlpha();
		if (config.showOverlayTitle())
		{
			graphics.drawString(baseText, x, y);
			drawHeaderTextWithAlpha(graphics, statusText, x + metrics.stringWidth(baseText), y, statusAlpha);
			return;
		}

		drawHeaderTextWithAlpha(graphics, baseText + statusText, x, y, statusAlpha);
	}

	private void drawHeaderTextWithAlpha(Graphics2D graphics, String text, int x, int y, float alpha)
	{
		Composite originalComposite = graphics.getComposite();
		float compositeAlpha = alpha;
		if (originalComposite instanceof AlphaComposite)
		{
			compositeAlpha *= ((AlphaComposite) originalComposite).getAlpha();
		}

		graphics.setComposite(AlphaComposite.getInstance(
			AlphaComposite.SRC_OVER,
			Math.max(0.0f, Math.min(1.0f, compositeAlpha))
		));
		graphics.drawString(text, x, y);
		graphics.setComposite(originalComposite);
	}

	private boolean isExpiredEnabledStatusHeader(String headerText)
	{
		return !plugin.shouldShowOverlayStatus() && headerText.endsWith(" - Enabled");
	}

	private void renderSelectedTile(Graphics2D graphics)
	{
		List<AreaLootItem> selectedItems = plugin.getSelectedLootItems();
		if (selectedItems.isEmpty())
		{
			return;
		}

		Color fill = config.highlightColor();
		Color outlineColor = config.highlightOutlineColor();
		Color lineColor = getHighlightLineColor();
		AreaLootItem selectedItem = selectedItems.get(0);
		Set<WorldPoint> renderedLocations = new HashSet<>();
		for (AreaLootItem item : selectedItems)
		{
			if (!renderedLocations.add(item.getLocation()))
			{
				continue;
			}

			LocalPoint localPoint = LocalPoint.fromWorld(client, item.getLocation());
			if (localPoint == null)
			{
				continue;
			}

			Polygon tile = Perspective.getCanvasTilePoly(client, localPoint);
			if (tile == null)
			{
				continue;
			}

			if (config.drawHighlightLine())
			{
				renderHighlightLine(graphics, localPoint, lineColor);
			}

			graphics.setColor(fill);
			graphics.fill(tile);
			graphics.setColor(outlineColor);
			graphics.setStroke(new BasicStroke(2));
			graphics.draw(tile);
		}

		if (config.showSelectedItemName())
		{
			LocalPoint localPoint = LocalPoint.fromWorld(client, selectedItem.getLocation());
			if (localPoint != null)
			{
				Point textLocation = Perspective.getCanvasTextLocation(client, graphics, localPoint, selectedItem.getName(), 0);
				if (textLocation != null)
				{
					graphics.setColor(config.selectedItemNameTextColor());
					graphics.drawString(selectedItem.getName(), textLocation.getX(), textLocation.getY());
				}
			}
		}
	}

	private List<AreaLootItem> getOverlayLootItems(List<AreaLootItem> items)
	{
		if (!config.groupSameItemOverlay() || items.size() < 2)
		{
			return items;
		}

		Map<Integer, LootGroup> groupedItems = new LinkedHashMap<>();
		for (AreaLootItem item : items)
		{
			LootGroup group = groupedItems.get(item.getId());
			if (group == null)
			{
				groupedItems.put(item.getId(), new LootGroup(item));
			}
			else
			{
				group.add(item);
			}
		}

		List<AreaLootItem> groupedLoot = new ArrayList<>(groupedItems.size());
		for (LootGroup group : groupedItems.values())
		{
			groupedLoot.add(group.toItem());
		}

		sortLoot(groupedLoot);
		return groupedLoot;
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

	private Color getHighlightLineColor()
	{
		return config.highlightLineColor();
	}

	private int getListWidth(
		FontMetrics metrics,
		List<ListRowLayout> rowLayouts,
		int totalItemCount,
		int displayedItemCount,
		String headerText,
		String emptyText,
		boolean showHeader)
	{
		int width = getConfiguredListMinimumWidth();
		if (showHeader)
		{
			width = Math.max(width, metrics.stringWidth(headerText) + (PADDING * 2));
		}
		if (rowLayouts.isEmpty())
		{
			return Math.max(width, metrics.stringWidth(emptyText) + (PADDING * 2));
		}

		for (ListRowLayout rowLayout : rowLayouts)
		{
			width = Math.max(width, rowLayout.getRowWidth());
		}

		if (shouldShowSelectedItemFooter())
		{
			width = Math.max(width, getSelectedItemFooterLayout(metrics, Integer.MAX_VALUE).getWidth() + (PADDING * 2));
		}

		if (totalItemCount > displayedItemCount)
		{
			width = Math.max(width, metrics.stringWidth("+" + (totalItemCount - displayedItemCount) + " more") + (PADDING * 2));
		}
		return width;
	}

	private int getConfiguredListMinimumWidth()
	{
		return config.useListMinimumWidth() ? Math.max(0, config.listMinimumWidth()) : 0;
	}

	private int getFooterLineCount(List<AreaLootItem> items, int displayedCount)
	{
		return getFooterLineCount(null, items, displayedCount, Integer.MAX_VALUE);
	}

	private int getFooterLineCount(FontMetrics metrics, List<AreaLootItem> items, int displayedCount, int maxWidth)
	{
		if (items.isEmpty())
		{
			return 0;
		}

		int lineCount = getSelectedItemFooterLineCount(metrics, maxWidth);
		lineCount += getLootSummaryLineCount(metrics, items, maxWidth);
		if (shouldShowMoreItemsLine(items, displayedCount))
		{
			lineCount++;
		}
		return lineCount;
	}

	private int getLootSummaryLineCount(FontMetrics metrics, List<AreaLootItem> items, int maxWidth)
	{
		if (!hasVisibleLootSummary(items))
		{
			return 0;
		}

		String lootCountText = getLootCountText(items);
		String totalGeValueText = getTotalGeValueValueText(items);
		if (lootCountText.isEmpty() || totalGeValueText.isEmpty())
		{
			return 1;
		}

		return shouldStackLootSummary(metrics, items, maxWidth) ? 2 : 1;
	}

	private int getFooterTopGap(int footerLineCount)
	{
		return footerLineCount > 0 ? FOOTER_TOP_GAP : 0;
	}

	private int getFooterWidth(FontMetrics metrics, List<AreaLootItem> items, int displayedCount, int footerLineCount)
	{
		if (items.isEmpty())
		{
			return 0;
		}

		int width = 0;
		boolean showMoreItemsLine = shouldShowMoreItemsLine(items, displayedCount);
		int summaryLineCount = footerLineCount - (showMoreItemsLine ? 1 : 0);
		if (shouldShowSelectedItemFooter())
		{
			SelectedItemFooterLayout footerLayout = getSelectedItemFooterLayout(metrics, Integer.MAX_VALUE);
			width = Math.max(width, footerLayout.getWidth());
			summaryLineCount = Math.max(0, summaryLineCount - footerLayout.getLineCount());
		}
		if (hasVisibleLootSummary(items))
		{
			if (summaryLineCount > 1)
			{
				width = Math.max(width, metrics.stringWidth(getLootCountText(items)));
				width = Math.max(width, getTotalGeValueWidth(metrics, items));
			}
			else
			{
				width = Math.max(width, getLootSummaryWidth(metrics, items));
			}
		}
		if (showMoreItemsLine)
		{
			width = Math.max(width, metrics.stringWidth(getMoreItemsText(items, displayedCount)));
		}
		return width;
	}

	private int getLootSummaryWidth(FontMetrics metrics, List<AreaLootItem> items)
	{
		int width = 0;
		String lootCountText = getLootCountText(items);
		String totalGeValueText = getTotalGeValueValueText(items);
		if (!lootCountText.isEmpty())
		{
			width += metrics.stringWidth(lootCountText);
		}
		if (!lootCountText.isEmpty() && !totalGeValueText.isEmpty())
		{
			width += metrics.stringWidth(" | ");
		}
		if (!totalGeValueText.isEmpty())
		{
			width += getTotalGeValueWidth(metrics, items);
		}
		return width;
	}

	private void drawFooterLines(Graphics2D graphics, FontMetrics metrics, List<AreaLootItem> items, int displayedCount, int x, int y)
	{
		drawFooterLines(graphics, metrics, items, displayedCount, x, y, Integer.MAX_VALUE);
	}

	private void drawFooterLines(
		Graphics2D graphics,
		FontMetrics metrics,
		List<AreaLootItem> items,
		int displayedCount,
		int x,
		int y,
		int maxWidth)
	{
		if (items.isEmpty())
		{
			return;
		}

		if (shouldShowSelectedItemFooter())
		{
			SelectedItemFooterLayout footerLayout = getSelectedItemFooterLayout(metrics, maxWidth);
			drawSelectedItemFooter(graphics, metrics, footerLayout, x, y, maxWidth);
			y += footerLayout.getLineCount() * FOOTER_LINE_HEIGHT;
		}
		if (hasVisibleLootSummary(items))
		{
			if (shouldStackLootSummary(metrics, items, maxWidth))
			{
				drawStackedLootSummary(graphics, metrics, items, x, y, maxWidth);
				y += getLootSummaryLineCount(metrics, items, maxWidth) * FOOTER_LINE_HEIGHT;
			}
			else
			{
				drawLootSummary(graphics, metrics, items, x, y, maxWidth);
				y += FOOTER_LINE_HEIGHT;
			}
		}
		if (shouldShowMoreItemsLine(items, displayedCount))
		{
			graphics.setColor(config.overlaySecondaryTextColor());
			graphics.drawString(fitTextToWidth(metrics, getMoreItemsText(items, displayedCount), maxWidth), x, y);
		}
	}

	private void drawSelectedItemFooter(
		Graphics2D graphics,
		FontMetrics metrics,
		SelectedItemFooterLayout footerLayout,
		int x,
		int y,
		int maxWidth)
	{
		if (footerLayout.isEmpty())
		{
			return;
		}

		if (footerLayout.hasSecondLine())
		{
			String labelText = footerLayout.getLabelText();
			String firstLine = footerLayout.getFirstLine();
			if (labelText.isEmpty())
			{
				graphics.setColor(config.selectedItemNameTextColor());
				graphics.drawString(fitTextToWidth(metrics, firstLine, maxWidth), x, y);
			}
			else
			{
				int labelWidth = metrics.stringWidth(labelText);
				String fittedLabelText = fitTextToWidth(metrics, labelText, maxWidth);
				int fittedLabelWidth = metrics.stringWidth(fittedLabelText);
				String firstValueLine = firstLine.length() > labelText.length() ? firstLine.substring(labelText.length()) : "";
				String fittedFirstValueLine = fitTextToWidth(metrics, firstValueLine, Math.max(0, maxWidth - labelWidth));
				graphics.setColor(config.selectedItemNameLabelTextColor());
				graphics.drawString(fittedLabelText, x, y);
				graphics.setColor(config.selectedItemNameTextColor());
				graphics.drawString(fittedFirstValueLine, x + fittedLabelWidth, y);
			}
			graphics.setColor(config.selectedItemNameTextColor());
			graphics.drawString(fitTextToWidth(metrics, footerLayout.getSecondLine(), maxWidth), x, y + FOOTER_LINE_HEIGHT);
			return;
		}

		if (footerLayout.getLabelText().isEmpty())
		{
			graphics.setColor(config.selectedItemNameTextColor());
			graphics.drawString(fitTextToWidth(metrics, footerLayout.getFirstLine(), maxWidth), x, y);
			return;
		}

		String labelText = footerLayout.getLabelText();
		int labelWidth = metrics.stringWidth(labelText);
		int valueWidth = Math.max(0, maxWidth - labelWidth);
		String fittedLabelText = fitTextToWidth(metrics, labelText, maxWidth);
		if (fittedLabelText.isEmpty())
		{
			graphics.setColor(config.selectedItemNameTextColor());
			graphics.drawString(fitTextToWidth(metrics, footerLayout.getFirstLine(), maxWidth), x, y);
			return;
		}

		int fittedLabelWidth = metrics.stringWidth(fittedLabelText);
		String fittedValueText = fitTextToWidth(metrics, footerLayout.getFirstLine().substring(labelText.length()), valueWidth);
		graphics.setColor(config.selectedItemNameLabelTextColor());
		graphics.drawString(fittedLabelText, x, y);
		graphics.setColor(config.selectedItemNameTextColor());
		graphics.drawString(fittedValueText, x + fittedLabelWidth, y);
	}

	private boolean shouldShowMoreItemsLine(List<AreaLootItem> items, int displayedCount)
	{
		return !config.showLootCount() && items.size() > displayedCount;
	}

	private boolean shouldShowSelectedItemFooter()
	{
		return config.showSelectedItemNameInOverlay() != AreaLootConfig.SelectedItemFooterMode.OFF
			&& plugin.getSelectedLootItem() != null;
	}

	private int getSelectedItemFooterLineCount(FontMetrics metrics, int maxWidth)
	{
		if (!shouldShowSelectedItemFooter())
		{
			return 0;
		}

		return getSelectedItemFooterLayout(metrics, maxWidth).getLineCount();
	}

	private String getSelectedItemFooterLabelText()
	{
		return config.showSelectedItemNameInOverlay() == AreaLootConfig.SelectedItemFooterMode.LONG ? "Selected: " : "";
	}

	private String getSelectedItemFooterValueText()
	{
		AreaLootItem selectedItem = plugin.getSelectedLootItem();
		return selectedItem == null ? "" : selectedItem.getName();
	}

	private String getSelectedItemFooterText()
	{
		String valueText = getSelectedItemFooterValueText();
		if (valueText.isEmpty())
		{
			return "";
		}

		String labelText = getSelectedItemFooterLabelText();
		return labelText + valueText;
	}

	private SelectedItemFooterLayout getSelectedItemFooterLayout(FontMetrics metrics, int maxWidth)
	{
		String labelText = getSelectedItemFooterLabelText();
		String valueText = getSelectedItemFooterValueText();
		if (valueText.isEmpty())
		{
			return SelectedItemFooterLayout.EMPTY;
		}

		if (!shouldCondenseSelectedItemFooter() || valueText.length() < getSelectedItemFooterLengthThreshold())
		{
			String text = labelText + valueText;
			return new SelectedItemFooterLayout(labelText, text, "", metrics.stringWidth(text));
		}

		String[] wrappedValue = wrapListItemName(metrics, valueText);
		if (wrappedValue[1].isEmpty())
		{
			String text = labelText + valueText;
			return new SelectedItemFooterLayout(labelText, text, "", metrics.stringWidth(text));
		}

		String firstLine = labelText + wrappedValue[0];
		String secondLine = wrappedValue[1];
		int width = Math.max(metrics.stringWidth(firstLine), metrics.stringWidth(secondLine));
		if (maxWidth != Integer.MAX_VALUE)
		{
			width = Math.min(width, maxWidth);
		}
		return new SelectedItemFooterLayout(labelText, firstLine, secondLine, width);
	}

	private boolean shouldCondenseSelectedItemFooter()
	{
		switch (config.overlayStyle())
		{
			case GRID:
				return config.condenseGridFooterItemNames();
			case LIST:
				return config.condenseListFooterItemNames();
			default:
				return false;
		}
	}

	private int getSelectedItemFooterLengthThreshold()
	{
		switch (config.overlayStyle())
		{
			case GRID:
				return config.condenseGridFooterItemNamesLength();
			case LIST:
				return config.condenseListFooterItemNamesLength();
			default:
				return Integer.MAX_VALUE;
		}
	}

	private void drawLootSummary(Graphics2D graphics, FontMetrics metrics, List<AreaLootItem> items, int x, int y)
	{
		drawLootSummary(graphics, metrics, items, x, y, Integer.MAX_VALUE);
	}

	private void drawLootSummary(Graphics2D graphics, FontMetrics metrics, List<AreaLootItem> items, int x, int y, int maxWidth)
	{
		String lootCountText = getLootCountText(items);
		String totalGeValueLabelText = getTotalGeValueLabelText(items);
		String totalGeValueText = getTotalGeValueValueText(items);
		int drawX = x;
		if (!lootCountText.isEmpty())
		{
			lootCountText = fitTextToWidth(metrics, lootCountText, maxWidth);
			graphics.setColor(config.lootCountTextColor());
			graphics.drawString(lootCountText, drawX, y);
			drawX += metrics.stringWidth(lootCountText);
		}
		if (!lootCountText.isEmpty() && !totalGeValueText.isEmpty())
		{
			if (drawX - x + metrics.stringWidth(" | ") >= maxWidth)
			{
				return;
			}

			graphics.setColor(config.overlaySecondaryTextColor());
			graphics.drawString(" | ", drawX, y);
			drawX += metrics.stringWidth(" | ");
		}
		if (!totalGeValueText.isEmpty())
		{
			int remainingWidth = Math.max(0, maxWidth - (drawX - x));
			String fittedLabelText = fitTextToWidth(metrics, totalGeValueLabelText, remainingWidth);
			if (fittedLabelText.isEmpty())
			{
				graphics.setColor(config.totalGeValueTextColor());
				graphics.drawString(fitTextToWidth(metrics, totalGeValueText, remainingWidth), drawX, y);
				return;
			}

			int labelWidth = metrics.stringWidth(fittedLabelText);
			String fittedValueText = fitTextToWidth(metrics, totalGeValueText, Math.max(0, remainingWidth - labelWidth));
			graphics.setColor(config.totalGeValueLabelTextColor());
			graphics.drawString(fittedLabelText, drawX, y);
			graphics.setColor(config.totalGeValueTextColor());
			graphics.drawString(fittedValueText, drawX + labelWidth, y);
		}
	}

	private void drawStackedLootSummary(Graphics2D graphics, FontMetrics metrics, List<AreaLootItem> items, int x, int y, int maxWidth)
	{
		String lootCountText = getLootCountText(items);
		String totalGeValueLabelText = getTotalGeValueLabelText(items);
		String totalGeValueText = getTotalGeValueValueText(items);
		if (!lootCountText.isEmpty())
		{
			graphics.setColor(config.lootCountTextColor());
			graphics.drawString(fitTextToWidth(metrics, lootCountText, maxWidth), x, y);
			y += FOOTER_LINE_HEIGHT;
		}
		if (!totalGeValueText.isEmpty())
		{
			String fittedLabelText = fitTextToWidth(metrics, totalGeValueLabelText, maxWidth);
			if (fittedLabelText.isEmpty())
			{
				graphics.setColor(config.totalGeValueTextColor());
				graphics.drawString(fitTextToWidth(metrics, totalGeValueText, maxWidth), x, y);
				return;
			}

			int labelWidth = metrics.stringWidth(fittedLabelText);
			String fittedValueText = fitTextToWidth(metrics, totalGeValueText, Math.max(0, maxWidth - labelWidth));
			graphics.setColor(config.totalGeValueLabelTextColor());
			graphics.drawString(fittedLabelText, x, y);
			graphics.setColor(config.totalGeValueTextColor());
			graphics.drawString(fittedValueText, x + labelWidth, y);
		}
	}

	private boolean shouldStackLootSummary(FontMetrics metrics, List<AreaLootItem> items, int maxWidth)
	{
		return metrics != null
			&& maxWidth != Integer.MAX_VALUE
			&& !getLootCountText(items).isEmpty()
			&& !getTotalGeValueValueText(items).isEmpty()
			&& getLootSummaryWidth(metrics, items) > maxWidth;
	}

	private boolean hasVisibleLootSummary(List<AreaLootItem> items)
	{
		return !getLootCountText(items).isEmpty() || !getTotalGeValueValueText(items).isEmpty();
	}

	private String getLootCountText(List<AreaLootItem> items)
	{
		if (!config.showLootCount())
		{
			return "";
		}
		return items.size() + (items.size() == 1 ? " item" : " items");
	}

	private String getTotalGeValueLabelText(List<AreaLootItem> items)
	{
		if (items.size() <= 1 || config.totalGeValueMode() == AreaLootConfig.TotalGeValueMode.NONE)
		{
			return "";
		}

		return "Total: ";
	}

	private String getTotalGeValueValueText(List<AreaLootItem> items)
	{
		if (items.size() <= 1)
		{
			return "";
		}

		switch (config.totalGeValueMode())
		{
			case LONG:
			case SHORT:
				return formatGeValue(getTotalGeValue(items));
			case NONE:
			default:
				return "";
		}
	}

	private String getMoreItemsText(List<AreaLootItem> items, int displayedCount)
	{
		return "+" + (items.size() - displayedCount) + " more";
	}

	private long getTotalGeValue(List<AreaLootItem> items)
	{
		long total = 0;
		for (AreaLootItem item : items)
		{
			total += item.getGeValue();
		}
		return total;
	}

	private int getTotalGeValueWidth(FontMetrics metrics, List<AreaLootItem> items)
	{
		String labelText = getTotalGeValueLabelText(items);
		String valueText = getTotalGeValueValueText(items);
		if (valueText.isEmpty())
		{
			return 0;
		}

		return metrics.stringWidth(labelText) + metrics.stringWidth(valueText);
	}

	private int getOverlayHeaderHeight(String headerText)
	{
		return shouldShowOverlayTitle(headerText) ? HEADER_HEIGHT : PADDING;
	}

	private boolean shouldShowOverlayTitle(String headerText)
	{
		if (isExpiredEnabledStatusHeader(headerText))
		{
			return config.showOverlayTitle();
		}

		return config.showOverlayTitle() || !"Area Loot".equals(headerText);
	}

	private String formatGeValue(AreaLootItem item)
	{
		return AreaLootValueFormatter.formatGeValue(item.getGeValue());
	}

	private String formatGeValue(long value)
	{
		return AreaLootValueFormatter.formatGeValue(value);
	}

	private int getMetadataWidth(FontMetrics metrics, AreaLootItem item, int distanceWidth)
	{
		int width = 0;
		if (config.showGeValue())
		{
			width += metrics.stringWidth(formatGeValue(item));
		}
		if (distanceWidth > 0)
		{
			if (width > 0)
			{
				width += METADATA_GAP;
			}
			width += distanceWidth;
		}
		return width;
	}

	private int getCompactMetadataWidth(FontMetrics metrics, AreaLootItem item)
	{
		int width = 0;
		if (config.showGeValue())
		{
			width += metrics.stringWidth(formatGeValue(item));
		}
		String distanceText = formatDistance(item);
		if (!distanceText.isEmpty())
		{
			if (width > 0)
			{
				width += METADATA_GAP;
			}
			width += metrics.stringWidth(distanceText);
		}
		return width;
	}

	private int getListRowHeight(FontMetrics metrics, int listIconSize, List<ListRowLayout> rowLayouts)
	{
		int rowHeight = Math.max(ROW_HEIGHT, listIconSize + 2);
		for (ListRowLayout rowLayout : rowLayouts)
		{
			rowHeight = Math.max(rowHeight, rowLayout.getRowHeight(metrics));
		}
		return rowHeight;
	}

	private int getListTextTopPadding(int rowHeight, int textBlockHeight)
	{
		return Math.max(0, (rowHeight - textBlockHeight) / 2);
	}

	private int getCondensedRowGapCount(List<ListRowLayout> rowLayouts)
	{
		int gapCount = 0;
		for (int i = 0; i < rowLayouts.size() - 1; i++)
		{
			if (rowLayouts.get(i).hasSecondLine())
			{
				gapCount++;
			}
		}
		return gapCount;
	}

	private int getListMetadataBaseline(int textTop, ListRowLayout layout, FontMetrics metrics)
	{
		if (!layout.hasSecondLine())
		{
			return textTop + metrics.getAscent();
		}

		return textTop + metrics.getAscent() + ((metrics.getHeight() + CONDENSED_NAME_LINE_GAP) / 2);
	}

	private List<ListRowLayout> buildListRowLayouts(
		FontMetrics metrics,
		List<AreaLootItem> items,
		int rowCount,
		int distanceWidth,
		int listIconSize,
		boolean showItemIcons,
		boolean showItemNames,
		boolean condenseItemNames,
		int condenseWidthThreshold)
	{
		List<ListRowLayout> rowLayouts = new ArrayList<>();
		for (int i = 0; i < rowCount; i++)
		{
			rowLayouts.add(createListRowLayout(
				metrics,
				items.get(i),
				distanceWidth,
				listIconSize,
				showItemIcons,
				showItemNames,
				condenseItemNames,
				condenseWidthThreshold));
		}
		return rowLayouts;
	}

	private ListRowLayout createListRowLayout(
		FontMetrics metrics,
		AreaLootItem item,
		int distanceWidth,
		int listIconSize,
		boolean showItemIcons,
		boolean showItemNames,
		boolean condenseItemNames,
		int condenseWidthThreshold)
	{
		String quantity = item.getQuantity() > 1 ? " x" + item.getQuantity() : "";
		String text = item.getName() + quantity;
		String firstLine = text;
		String secondLine = "";
		if (shouldWrapListItemName(metrics, text, condenseItemNames, condenseWidthThreshold))
		{
			String[] wrappedLines = wrapListItemName(metrics, text);
			firstLine = wrappedLines[0];
			secondLine = wrappedLines[1];
		}

		int rowWidth = PADDING * 2;
		if (showItemIcons)
		{
			rowWidth += listIconSize + 6;
		}

		if (showItemNames)
		{
			int metadataWidth = getMetadataWidth(metrics, item, distanceWidth);
			int gapWidth = metadataWidth > 0 ? METADATA_GAP : 0;
			int firstLineWidth = metrics.stringWidth(firstLine);
			int secondLineWidth = metrics.stringWidth(secondLine);
			if (secondLine.isEmpty())
			{
				rowWidth += firstLineWidth + metadataWidth + gapWidth;
			}
			else
			{
				int nameBlockWidth = Math.max(firstLineWidth, secondLineWidth);
				rowWidth += nameBlockWidth + metadataWidth + gapWidth;
			}
		}
		else
		{
			rowWidth += getCompactMetadataWidth(metrics, item);
		}

		return new ListRowLayout(firstLine, secondLine, rowWidth);
	}

	private boolean shouldWrapListItemName(FontMetrics metrics, String text, boolean condenseItemNames, int condenseWidthThreshold)
	{
		if (!condenseItemNames)
		{
			return false;
		}

		String trimmed = text == null ? "" : text.trim();
		if (trimmed.isEmpty())
		{
			return false;
		}

		if (trimmed.length() < condenseWidthThreshold)
		{
			return false;
		}

		int fullWidth = metrics.stringWidth(trimmed);
		String[] wrappedLines = wrapListItemName(metrics, trimmed);
		return wrappedLines[1] != null && !wrappedLines[1].isEmpty()
			&& Math.max(metrics.stringWidth(wrappedLines[0]), metrics.stringWidth(wrappedLines[1])) < fullWidth;
	}

	private String[] wrapListItemName(FontMetrics metrics, String text)
	{
		String trimmed = text == null ? "" : text.trim();
		if (trimmed.isEmpty())
		{
			return new String[] {"", ""};
		}

		String[] words = trimmed.split("\\s+");
		if (words.length < 2)
		{
			return new String[] {trimmed, ""};
		}

		int fullWidth = metrics.stringWidth(trimmed);
		int bestSplit = -1;
		int bestWidth = fullWidth;
		for (int splitIndex = 1; splitIndex < words.length; splitIndex++)
		{
			String firstLine = joinWords(words, 0, splitIndex);
			String secondLine = joinWords(words, splitIndex, words.length);
			int splitWidth = Math.max(metrics.stringWidth(firstLine), metrics.stringWidth(secondLine));
			if (splitWidth < bestWidth)
			{
				bestWidth = splitWidth;
				bestSplit = splitIndex;
			}
		}

		if (bestSplit == -1)
		{
			return new String[] {trimmed, ""};
		}

		return new String[] {joinWords(words, 0, bestSplit), joinWords(words, bestSplit, words.length)};
	}

	private String joinWords(String[] words, int from, int to)
	{
		StringBuilder builder = new StringBuilder();
		for (int i = from; i < to; i++)
		{
			if (builder.length() > 0)
			{
				builder.append(' ');
			}
			builder.append(words[i]);
		}
		return builder.toString();
	}

	private boolean shouldCondenseListItemNames()
	{
		return config.condenseListItemNames();
	}

	private boolean showListItemIcons()
	{
		return config.listIconSize() != AreaLootConfig.ListIconSize.NONE;
	}

	private static final class ListRowLayout
	{
		private final String firstLine;
		private final String secondLine;
		private final int rowWidth;

		private ListRowLayout(String firstLine, String secondLine, int rowWidth)
		{
			this.firstLine = firstLine;
			this.secondLine = secondLine;
			this.rowWidth = rowWidth;
		}

		private String getFirstLine()
		{
			return firstLine;
		}

		private String getSecondLine()
		{
			return secondLine;
		}

		private boolean hasSecondLine()
		{
			return !secondLine.isEmpty();
		}

		private int getRowWidth()
		{
			return rowWidth;
		}

		private int getTextBlockHeight(FontMetrics metrics)
		{
			return hasSecondLine()
				? (metrics.getHeight() * 2) + CONDENSED_NAME_LINE_GAP
				: metrics.getHeight();
		}

		private int getRowHeight(FontMetrics metrics)
		{
			return getTextBlockHeight(metrics) + CONDENSED_NAME_EXTRA_PADDING;
		}
	}

	private static final class SelectedItemFooterLayout
	{
		private static final SelectedItemFooterLayout EMPTY = new SelectedItemFooterLayout("", "", "", 0);

		private final String labelText;
		private final String firstLine;
		private final String secondLine;
		private final int width;

		private SelectedItemFooterLayout(String labelText, String firstLine, String secondLine, int width)
		{
			this.labelText = labelText;
			this.firstLine = firstLine;
			this.secondLine = secondLine;
			this.width = width;
		}

		private String getLabelText()
		{
			return labelText;
		}

		private String getFirstLine()
		{
			return firstLine;
		}

		private String getSecondLine()
		{
			return secondLine;
		}

		private boolean hasSecondLine()
		{
			return !secondLine.isEmpty();
		}

		private boolean isEmpty()
		{
			return firstLine.isEmpty() && secondLine.isEmpty();
		}

		private int getLineCount()
		{
			return hasSecondLine() ? 2 : 1;
		}

		private int getWidth()
		{
			return width;
		}
	}

	private int getMaxDistanceWidth(FontMetrics metrics, List<AreaLootItem> items, int rowCount)
	{
		if (config.tileDistanceMode() == AreaLootConfig.DistanceMode.NONE)
		{
			return 0;
		}

		int width = 0;
		for (int i = 0; i < rowCount; i++)
		{
			width = Math.max(width, metrics.stringWidth(formatDistance(items.get(i))));
		}
		return width;
	}

	private String formatDistance(AreaLootItem item)
	{
		switch (config.tileDistanceMode())
		{
			case LONG:
				return item.getDistance() + (item.getDistance() <= 1 ? " Tile" : " Tiles");
			case SHORT:
				return item.getDistance() + "t";
			case NONE:
			default:
				return "";
		}
	}

	private void renderHighlightLine(Graphics2D graphics, LocalPoint itemPoint, Color color)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}

		Polygon playerTile = Perspective.getCanvasTilePoly(client, player.getLocalLocation());
		Polygon itemTile = Perspective.getCanvasTilePoly(client, itemPoint);
		Point playerCanvasPoint = Perspective.localToCanvas(client, player.getLocalLocation(), client.getPlane(), 0);
		Point itemCanvasPoint = Perspective.localToCanvas(client, itemPoint, client.getPlane(), 0);
		if (playerCanvasPoint == null || itemCanvasPoint == null)
		{
			return;
		}

		java.awt.Point playerCenter = new java.awt.Point(playerCanvasPoint.getX(), playerCanvasPoint.getY());
		java.awt.Point itemCenter = new java.awt.Point(itemCanvasPoint.getX(), itemCanvasPoint.getY());
		java.awt.Point lineStart = moveToward(playerCenter, getTileEdgePoint(playerTile, playerCenter, itemCenter), LINE_EDGE_GAP);
		java.awt.Point lineEnd = moveToward(itemCenter, getTileEdgePoint(itemTile, itemCenter, playerCenter), LINE_EDGE_GAP);

		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(2));
		graphics.drawLine(lineStart.x, lineStart.y, lineEnd.x, lineEnd.y);
	}

	private java.awt.Point getTileEdgePoint(Polygon tile, java.awt.Point fromCenter, java.awt.Point toward)
	{
		if (tile == null || tile.npoints < 3)
		{
			return fromCenter;
		}

		java.awt.Point bestPoint = null;
		double bestT = Double.MAX_VALUE;

		for (int i = 0; i < tile.npoints; i++)
		{
			int next = (i + 1) % tile.npoints;
			LineIntersection intersection = getLineIntersection(
				fromCenter.x,
				fromCenter.y,
				toward.x,
				toward.y,
				tile.xpoints[i],
				tile.ypoints[i],
				tile.xpoints[next],
				tile.ypoints[next]
			);

			if (intersection != null && intersection.t > 0 && intersection.t < bestT)
			{
				bestT = intersection.t;
				bestPoint = intersection.point;
			}
		}

		return bestPoint == null ? fromCenter : bestPoint;
	}

	private LineIntersection getLineIntersection(
		double x1,
		double y1,
		double x2,
		double y2,
		double x3,
		double y3,
		double x4,
		double y4)
	{
		double denominator = ((x1 - x2) * (y3 - y4)) - ((y1 - y2) * (x3 - x4));
		if (Math.abs(denominator) < 0.0001)
		{
			return null;
		}

		double t = (((x1 - x3) * (y3 - y4)) - ((y1 - y3) * (x3 - x4))) / denominator;
		double u = (((x1 - x3) * (y1 - y2)) - ((y1 - y3) * (x1 - x2))) / denominator;
		if (u < 0 || u > 1)
		{
			return null;
		}

		java.awt.Point point = new java.awt.Point(
			(int) Math.round(x1 + (t * (x2 - x1))),
			(int) Math.round(y1 + (t * (y2 - y1)))
		);
		return new LineIntersection(point, t);
	}

	private java.awt.Point moveToward(java.awt.Point from, java.awt.Point to, double pixels)
	{
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double distance = Math.hypot(dx, dy);
		if (distance <= pixels || distance == 0)
		{
			return to;
		}

		return new java.awt.Point(
			(int) Math.round(to.x + ((dx / distance) * pixels)),
			(int) Math.round(to.y + ((dy / distance) * pixels))
		);
	}

	private static final class LootGroup
	{
		private AreaLootItem representative;
		private int totalQuantity;
		private long totalGeValue;

		private LootGroup(AreaLootItem item)
		{
			representative = item;
			totalQuantity = item.getQuantity();
			totalGeValue = item.getGeValue();
		}

		private void add(AreaLootItem item)
		{
			totalQuantity += item.getQuantity();
			totalGeValue += item.getGeValue();
			if (item.getDistance() < representative.getDistance())
			{
				representative = item;
			}
		}

		private AreaLootItem toItem()
		{
			return new AreaLootItem(
				representative.getId(),
				representative.getStackId(),
				totalQuantity,
				representative.getName(),
				representative.getLocation(),
				representative.getDistance(),
				totalGeValue
			);
		}
	}

	private static class LineIntersection
	{
		private final java.awt.Point point;
		private final double t;

		private LineIntersection(java.awt.Point point, double t)
		{
			this.point = point;
			this.t = t;
		}
	}
}
