package com.arealoot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
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
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.AsyncBufferedImage;

class AreaLootOverlay extends Overlay
{
	private static final int LIST_X = 8;
	private static final int LIST_Y = 80;
	private static final int LIST_WIDTH = 230;
	private static final int HEADER_HEIGHT = 22;
	private static final int ROW_HEIGHT = 24;
	private static final int ICON_SIZE = 18;
	private static final int PADDING = 6;

	private final Client client;
	private final AreaLootPlugin plugin;
	private final AreaLootConfig config;
	private final ItemManager itemManager;

	@Inject
	AreaLootOverlay(Client client, AreaLootPlugin plugin, AreaLootConfig config, ItemManager itemManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		renderLootList(graphics);
		renderSelectedTile(graphics);
		return null;
	}

	private void renderLootList(Graphics2D graphics)
	{
		if (!plugin.isOverlayListVisible())
		{
			plugin.setOverlayRows(new ArrayList<>());
			return;
		}

		List<AreaLootItem> items = plugin.getNearbyLootSnapshot();
		int rowCount = Math.min(items.size(), config.maxOverlayItems());
		int height = HEADER_HEIGHT + Math.max(1, rowCount) * ROW_HEIGHT + PADDING;
		List<SimpleEntry<Rectangle, AreaLootItem>> rowBounds = new ArrayList<>();

		graphics.setColor(new Color(30, 30, 30, 190));
		graphics.fillRoundRect(LIST_X, LIST_Y, LIST_WIDTH, height, 6, 6);
		graphics.setColor(ColorScheme.BORDER_COLOR);
		graphics.drawRoundRect(LIST_X, LIST_Y, LIST_WIDTH, height, 6, 6);

		graphics.setColor(ColorScheme.BRAND_ORANGE);
		graphics.drawString("Area Loot", LIST_X + PADDING, LIST_Y + 15);

		if (items.isEmpty())
		{
			graphics.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			graphics.drawString("No nearby loot", LIST_X + PADDING, LIST_Y + HEADER_HEIGHT + 15);
			plugin.setOverlayRows(rowBounds);
			return;
		}

		FontMetrics metrics = graphics.getFontMetrics();
		for (int i = 0; i < rowCount; i++)
		{
			AreaLootItem item = items.get(i);
			int y = LIST_Y + HEADER_HEIGHT + (i * ROW_HEIGHT);
			Rectangle row = new Rectangle(LIST_X, y, LIST_WIDTH, ROW_HEIGHT);
			rowBounds.add(new SimpleEntry<>(row, item));

			if (item.getLocation().equals(plugin.getSelectedLocation()))
			{
				graphics.setColor(new Color(0, 200, 255, 65));
				graphics.fillRect(row.x + 1, row.y, row.width - 2, row.height);
			}

			String quantity = item.getQuantity() > 1 ? " x" + item.getQuantity() : "";
			String text = item.getName() + quantity;
			int textX = LIST_X + PADDING;
			if (config.showItemIcons())
			{
				AsyncBufferedImage image = itemManager.getImage(item.getId(), item.getQuantity(), false);
				if (image != null)
				{
					graphics.drawImage(image, LIST_X + PADDING, y + 3, ICON_SIZE, ICON_SIZE, null);
				}
				textX += ICON_SIZE + 6;
			}

			int maxNameWidth = LIST_X + LIST_WIDTH - 42 - textX;
			while (text.length() > 3 && metrics.stringWidth(text) > maxNameWidth)
			{
				text = text.substring(0, text.length() - 4) + "...";
			}

			graphics.setColor(ColorScheme.TEXT_COLOR);
			graphics.drawString(text, textX, y + 16);
			graphics.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			graphics.drawString(item.getDistance() + "t", LIST_X + LIST_WIDTH - 34, y + 16);
		}

		if (items.size() > rowCount)
		{
			graphics.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			graphics.drawString("+" + (items.size() - rowCount) + " more", LIST_X + PADDING, LIST_Y + height - 7);
		}

		plugin.setOverlayRows(rowBounds);
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
		Color border = new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 220);
		if (config.drawHighlightLine())
		{
			renderHighlightLine(graphics, localPoint, border);
		}

		graphics.setColor(fill);
		graphics.fill(tile);
		graphics.setColor(border);
		graphics.setStroke(new BasicStroke(2));
		graphics.draw(tile);
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
		java.awt.Point lineStart = getTileEdgePoint(playerTile, playerCenter, itemCenter);
		java.awt.Point lineEnd = getTileEdgePoint(itemTile, itemCenter, playerCenter);

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

		double bestDistance = Double.MAX_VALUE;
		java.awt.Point bestPoint = fromCenter;

		for (int i = 0; i < tile.npoints; i++)
		{
			int next = (i + 1) % tile.npoints;
			java.awt.Point intersection = getLineIntersection(
				fromCenter.x,
				fromCenter.y,
				toward.x,
				toward.y,
				tile.xpoints[i],
				tile.ypoints[i],
				tile.xpoints[next],
				tile.ypoints[next]
			);

			if (intersection == null)
			{
				continue;
			}

			double distance = intersection.distance(toward);
			if (distance < bestDistance)
			{
				bestDistance = distance;
				bestPoint = intersection;
			}
		}

		return bestPoint;
	}

	private java.awt.Point getLineIntersection(
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
		if (t < 0 || t > 1 || u < 0 || u > 1)
		{
			return null;
		}

		return new java.awt.Point(
			(int) Math.round(x1 + (t * (x2 - x1))),
			(int) Math.round(y1 + (t * (y2 - y1)))
		);
	}
}
