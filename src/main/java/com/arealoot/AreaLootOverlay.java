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
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
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
	private static final String GRID_STABLE_GE_TEXT = "999gp";
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
		setLayer(OverlayLayer.ABOVE_SCENE);
		setResizable(true);
		setMinimumSize(0);
		applyConfiguredListBounds();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Dimension listDimension = renderLootList(graphics);
		java.awt.Point origin = getBounds().getLocation();
		graphics.translate(-origin.x, -origin.y);
		renderSelectedTile(graphics);
		graphics.translate(origin.x, origin.y);
		return listDimension;
	}

	void applyConfiguredListBounds()
	{
		setPreferredLocation(new java.awt.Point(config.overlayX(), config.overlayY()));
		setPreferredSize(new Dimension(0, 0));
	}

	private Dimension renderLootList(Graphics2D graphics)
	{
		boolean shouldShow = plugin.shouldShowOverlayList() && !plugin.isOverlayFadeOutActive();
		if (!shouldShow && (!config.animateOverlay() || !lastVisibleListRendered))
		{
			wasShowing = false;
			lastVisibleListRendered = false;
			fadeInStartedAtMillis = 0;
			fadeStartedAtMillis = 0;
			plugin.finishOverlayFadeOut();
			plugin.setOverlayRows(new ArrayList<>());
			return null;
		}

		long now = System.currentTimeMillis();
		List<AreaLootItem> items = plugin.getNearbyLootSnapshot();
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
		int listWidth = getListWidth(metrics, items, rowCount, headerText, emptyText);
		int distanceWidth = getMaxDistanceWidth(metrics, items, rowCount);
		int rowHeight = getListRowHeight();
		int listIconSize = config.listIconSize().getPixels();
		int textBaselineOffset = ((rowHeight - metrics.getHeight()) / 2) + metrics.getAscent();
		int headerHeight = getOverlayHeaderHeight();
		int footerLineCount = getFooterLineCount(items, rowCount);
		int height = headerHeight + Math.max(1, rowCount) * rowHeight + PADDING + (footerLineCount * FOOTER_LINE_HEIGHT);
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

		if (config.showOverlayTitle())
		{
			graphics.setColor(config.overlayHeaderColor());
			graphics.drawString(headerText, listX + PADDING, listY + 15);
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

		for (int i = 0; i < rowCount; i++)
		{
			AreaLootItem item = items.get(i);
			int y = rowStartY + (i * rowHeight);
			Rectangle localRow = new Rectangle(listX, y, listWidth, rowHeight);
			Rectangle clickRow = new Rectangle(origin.x + listX, origin.y + y, listWidth, rowHeight);
			if (!fadingOut)
			{
				rowBounds.add(new SimpleEntry<>(clickRow, item));
			}

			if (plugin.isSelectedLoot(item))
			{
				renderSelectedOverlayEntry(graphics, localRow, false);
			}

			String quantity = item.getQuantity() > 1 ? " x" + item.getQuantity() : "";
			String text = item.getName() + quantity;
			int textX = listX + PADDING;
			if (config.showItemIcons())
			{
				AsyncBufferedImage image = itemManager.getImage(item.getId(), item.getQuantity(), false);
				if (image != null)
				{
					int iconY = y + ((rowHeight - listIconSize) / 2);
					graphics.drawImage(image, listX + PADDING, iconY, listIconSize, listIconSize, null);
				}
				textX += listIconSize + 6;
			}

			if (config.showItemNamesInListMode())
			{
				graphics.setColor(config.overlayTextColor());
				graphics.drawString(text, textX, y + textBaselineOffset);
			}
			int metadataRight = listX + listWidth - PADDING;
			if (distanceWidth > 0)
			{
				metadataRight -= distanceWidth + METADATA_GAP;
			}
			if (config.showGeValue())
			{
				String valueText = formatGeValue(item);
				graphics.setColor(config.geValueTextColor());
				graphics.drawString(valueText, metadataRight - metrics.stringWidth(valueText), y + textBaselineOffset);
			}
			String distanceText = formatDistance(item);
			if (!distanceText.isEmpty())
			{
				graphics.setColor(config.tileDistanceTextColor());
				graphics.drawString(distanceText, listX + listWidth - PADDING - metrics.stringWidth(distanceText), y + textBaselineOffset);
			}
		}

		drawFooterLines(graphics, metrics, items, rowCount, listX + PADDING, rowStartY + (rowCount * rowHeight) + 10);

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
		if (config.gridAutoAdjust())
		{
			columns = Math.max(1, Math.min(configuredColumns, Math.max(1, itemCount)));
			visibleRows = Math.max(1, (int) Math.ceil(itemCount / (double) columns));
		}
		FontMetrics metrics = graphics.getFontMetrics();
		int gridIconSize = config.gridIconSize().getPixels();
		int metadataLines = getGridMetadataLineCount();
		int cellWidth = getGridCellWidth(metrics, items, itemCount, gridIconSize, columns, configuredColumns);
		int cellHeight = (GRID_CELL_VERTICAL_PADDING * 2) + gridIconSize + (metadataLines * GRID_TEXT_LINE_HEIGHT);
		int gridWidth = (columns * cellWidth) + ((columns - 1) * GRID_CELL_GAP);
		int overlayWidth = gridWidth + (GRID_OUTER_PADDING * 2);
		if (config.showOverlayTitle())
		{
			overlayWidth = Math.max(overlayWidth, metrics.stringWidth(headerText) + (GRID_OUTER_PADDING * 2));
		}
		if (items.isEmpty())
		{
			overlayWidth = Math.max(overlayWidth, metrics.stringWidth(emptyText) + (GRID_OUTER_PADDING * 2));
		}
		int headerHeight = getOverlayHeaderHeight();
		int footerLineCount = getFooterLineCount(items, itemCount);
		overlayWidth = Math.max(overlayWidth, getFooterWidth(metrics, items, itemCount) + (GRID_OUTER_PADDING * 2));
		int height = headerHeight + (visibleRows * cellHeight) + GRID_OUTER_PADDING + (footerLineCount * FOOTER_LINE_HEIGHT);

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

		if (config.showOverlayTitle())
		{
			graphics.setColor(config.overlayHeaderColor());
			graphics.drawString(headerText, gridX + GRID_OUTER_PADDING, gridY + 15);
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
			int column = i % columns;
			int row = i / columns;
			int x = gridStartX + (column * (cellWidth + GRID_CELL_GAP));
			int y = gridStartY + (row * cellHeight);
			Rectangle localCell = new Rectangle(x, y, cellWidth, cellHeight);
			Rectangle clickCell = new Rectangle(origin.x + x, origin.y + y, cellWidth, cellHeight);
			if (!fadingOut)
			{
				cellBounds.add(new SimpleEntry<>(clickCell, item));
			}

			if (plugin.isSelectedLoot(item))
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

		drawFooterLines(graphics, metrics, items, itemCount, gridX + GRID_OUTER_PADDING, gridStartY + (visibleRows * cellHeight) + 10);

		plugin.setOverlayRows(cellBounds);
		graphics.setComposite(originalComposite);
		return new Dimension(overlayWidth, height);
	}

	private int getGridMetadataLineCount()
	{
		int lines = 0;
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

	private int getGridCellWidth(
		FontMetrics metrics,
		List<AreaLootItem> items,
		int itemCount,
		int gridIconSize,
		int columns,
		int configuredColumns)
	{
		if (config.gridAutoAdjust() && columns == configuredColumns)
		{
			return getStableGridCellWidth(metrics, gridIconSize);
		}

		int width = gridIconSize + (GRID_CELL_HORIZONTAL_PADDING * 2);
		for (int i = 0; i < itemCount; i++)
		{
			AreaLootItem item = items.get(i);
			if (config.showGeValue())
			{
				width = Math.max(width, metrics.stringWidth(formatGeValue(item)) + (GRID_CELL_HORIZONTAL_PADDING * 2));
			}

			String distanceText = formatDistance(item);
			if (!distanceText.isEmpty())
			{
				width = Math.max(width, metrics.stringWidth(distanceText) + (GRID_CELL_HORIZONTAL_PADDING * 2));
			}
		}
		return width;
	}

	private int getStableGridCellWidth(FontMetrics metrics, int gridIconSize)
	{
		int width = gridIconSize + (GRID_CELL_HORIZONTAL_PADDING * 2);
		if (config.showGeValue())
		{
			width = Math.max(width, metrics.stringWidth(GRID_STABLE_GE_TEXT) + (GRID_CELL_HORIZONTAL_PADDING * 2));
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
		return plugin.isOverlayAutoModeActive() || plugin.shouldShowOverlayStatus() ? "Area Loot (auto)" : "Area Loot";
	}

	private String getEmptyText()
	{
		return plugin.shouldShowOverlayStatus() ? plugin.getOverlayStatusText() : "No nearby loot";
	}

	private void renderSelectedTile(Graphics2D graphics)
	{
		WorldPoint selectedLocation = plugin.getSelectedLocation();
		if (selectedLocation == null)
		{
			return;
		}

		LocalPoint localPoint = LocalPoint.fromWorld(client, selectedLocation);
		if (localPoint == null)
		{
			return;
		}

		Polygon tile = Perspective.getCanvasTilePoly(client, localPoint);
		if (tile == null)
		{
			return;
		}

		Color fill = config.highlightColor();
		Color outlineColor = config.highlightOutlineColor();
		Color lineColor = getHighlightLineColor();
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

	private Color getHighlightLineColor()
	{
		return config.matchLineColor() ? config.highlightOutlineColor() : config.highlightLineColor();
	}

	private int getListWidth(FontMetrics metrics, List<AreaLootItem> items, int rowCount, String headerText, String emptyText)
	{
		int width = getConfiguredListMinimumWidth();
		int listIconSize = config.listIconSize().getPixels();
		if (config.showOverlayTitle())
		{
			width = Math.max(width, metrics.stringWidth(headerText) + (PADDING * 2));
		}
		if (items.isEmpty())
		{
			return Math.max(width, metrics.stringWidth(emptyText) + (PADDING * 2));
		}

		for (int i = 0; i < rowCount; i++)
		{
			AreaLootItem item = items.get(i);
			String quantity = item.getQuantity() > 1 ? " x" + item.getQuantity() : "";
			int rowWidth = PADDING * 2;
			if (config.showItemIcons())
			{
				rowWidth += listIconSize + 6;
			}
			if (config.showItemNamesInListMode())
			{
				rowWidth += metrics.stringWidth(item.getName() + quantity);
			}
			rowWidth += getMetadataWidth(metrics, item, getMaxDistanceWidth(metrics, items, rowCount));
			if ((config.showItemNamesInListMode() || config.showItemIcons())
				&& (config.showGeValue() || config.tileDistanceMode() != AreaLootConfig.DistanceMode.NONE))
			{
				rowWidth += METADATA_GAP;
			}
			width = Math.max(width, rowWidth);
		}

		if (items.size() > rowCount)
		{
			width = Math.max(width, metrics.stringWidth("+" + (items.size() - rowCount) + " more") + (PADDING * 2));
		}
		width = Math.max(width, getFooterWidth(metrics, items, rowCount) + (PADDING * 2));

		return width;
	}

	private int getConfiguredListMinimumWidth()
	{
		return config.useListMinimumWidth() ? Math.max(0, config.listMinimumWidth()) : 0;
	}

	private int getFooterLineCount(List<AreaLootItem> items, int displayedCount)
	{
		if (items.isEmpty())
		{
			return 0;
		}

		int lineCount = hasLootSummary() ? 1 : 0;
		if (items.size() > displayedCount)
		{
			lineCount++;
		}
		return lineCount;
	}

	private int getFooterWidth(FontMetrics metrics, List<AreaLootItem> items, int displayedCount)
	{
		if (items.isEmpty())
		{
			return 0;
		}

		int width = getLootSummaryWidth(metrics, items);
		if (items.size() > displayedCount)
		{
			width = Math.max(width, metrics.stringWidth(getMoreItemsText(items, displayedCount)));
		}
		return width;
	}

	private int getLootSummaryWidth(FontMetrics metrics, List<AreaLootItem> items)
	{
		int width = 0;
		String lootCountText = getLootCountText(items);
		String totalGeValueText = getTotalGeValueText(items);
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
			width += metrics.stringWidth(totalGeValueText);
		}
		return width;
	}

	private void drawFooterLines(Graphics2D graphics, FontMetrics metrics, List<AreaLootItem> items, int displayedCount, int x, int y)
	{
		if (items.isEmpty())
		{
			return;
		}

		if (hasLootSummary())
		{
			drawLootSummary(graphics, metrics, items, x, y);
			y += FOOTER_LINE_HEIGHT;
		}
		if (items.size() > displayedCount)
		{
			graphics.setColor(config.overlaySecondaryTextColor());
			graphics.drawString(getMoreItemsText(items, displayedCount), x, y);
		}
	}

	private void drawLootSummary(Graphics2D graphics, FontMetrics metrics, List<AreaLootItem> items, int x, int y)
	{
		String lootCountText = getLootCountText(items);
		String totalGeValueText = getTotalGeValueText(items);
		int drawX = x;
		if (!lootCountText.isEmpty())
		{
			graphics.setColor(config.lootCountTextColor());
			graphics.drawString(lootCountText, drawX, y);
			drawX += metrics.stringWidth(lootCountText);
		}
		if (!lootCountText.isEmpty() && !totalGeValueText.isEmpty())
		{
			graphics.setColor(config.overlaySecondaryTextColor());
			graphics.drawString(" | ", drawX, y);
			drawX += metrics.stringWidth(" | ");
		}
		if (!totalGeValueText.isEmpty())
		{
			graphics.setColor(config.totalGeValueTextColor());
			graphics.drawString(totalGeValueText, drawX, y);
		}
	}

	private boolean hasLootSummary()
	{
		return config.showLootCount() || config.totalGeValueMode() != AreaLootConfig.TotalGeValueMode.NONE;
	}

	private String getLootCountText(List<AreaLootItem> items)
	{
		if (!config.showLootCount())
		{
			return "";
		}
		return items.size() + (items.size() == 1 ? " item" : " items");
	}

	private String getTotalGeValueText(List<AreaLootItem> items)
	{
		switch (config.totalGeValueMode())
		{
			case LONG:
				return "Total: " + formatGeValue(getTotalGeValue(items));
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

	private int getOverlayHeaderHeight()
	{
		return config.showOverlayTitle() ? HEADER_HEIGHT : PADDING;
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

	private int getListRowHeight()
	{
		return Math.max(ROW_HEIGHT, config.listIconSize().getPixels() + 6);
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
