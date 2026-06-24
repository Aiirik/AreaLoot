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
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.AsyncBufferedImage;

class AreaLootOverlay extends Overlay
{
	private static final int HEADER_HEIGHT = 22;
	private static final int ROW_HEIGHT = 24;
	private static final int ICON_SIZE = 18;
	private static final int PADDING = 6;
	private static final double LINE_EDGE_GAP = 3.0;

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
		setResizable(true);
		setMinimumSize(100);
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
		setPreferredSize(new Dimension(config.overlayWidth(), 0));
	}

	private Dimension renderLootList(Graphics2D graphics)
	{
		if (!plugin.isOverlayListVisible())
		{
			plugin.setOverlayRows(new ArrayList<>());
			return null;
		}

		List<AreaLootItem> items = plugin.getNearbyLootSnapshot();
		java.awt.Point origin = getBounds().getLocation();
		int listX = 0;
		int listY = 0;
		int listWidth = getListWidth();
		int rowCount = Math.min(items.size(), config.maxOverlayItems());
		int height = HEADER_HEIGHT + Math.max(1, rowCount) * ROW_HEIGHT + PADDING;
		List<SimpleEntry<Rectangle, AreaLootItem>> rowBounds = new ArrayList<>();

		graphics.setColor(config.overlayBackgroundColor());
		graphics.fillRoundRect(listX, listY, listWidth, height, 6, 6);
		graphics.setColor(config.overlayBorderColor());
		graphics.drawRoundRect(listX, listY, listWidth, height, 6, 6);

		graphics.setColor(config.overlayHeaderColor());
		graphics.drawString("Area Loot", listX + PADDING, listY + 15);

		if (items.isEmpty())
		{
			graphics.setColor(config.overlaySecondaryTextColor());
			graphics.drawString("No nearby loot", listX + PADDING, listY + HEADER_HEIGHT + 15);
			plugin.setOverlayRows(rowBounds);
			return new Dimension(listWidth, height);
		}

		FontMetrics metrics = graphics.getFontMetrics();
		for (int i = 0; i < rowCount; i++)
		{
			AreaLootItem item = items.get(i);
			int y = listY + HEADER_HEIGHT + (i * ROW_HEIGHT);
			Rectangle localRow = new Rectangle(listX, y, listWidth, ROW_HEIGHT);
			Rectangle clickRow = new Rectangle(origin.x + listX, origin.y + y, listWidth, ROW_HEIGHT);
			rowBounds.add(new SimpleEntry<>(clickRow, item));

			if (item.getLocation().equals(plugin.getSelectedLocation()))
			{
				graphics.setColor(config.overlaySelectedRowColor());
				graphics.fillRect(localRow.x + 1, localRow.y, localRow.width - 2, localRow.height);
			}

			String quantity = item.getQuantity() > 1 ? " x" + item.getQuantity() : "";
			String text = item.getName() + quantity;
			int textX = listX + PADDING;
			if (config.showItemIcons())
			{
				AsyncBufferedImage image = itemManager.getImage(item.getId(), item.getQuantity(), false);
				if (image != null)
				{
					graphics.drawImage(image, listX + PADDING, y + 3, ICON_SIZE, ICON_SIZE, null);
				}
				textX += ICON_SIZE + 6;
			}

			int maxNameWidth = listX + listWidth - 42 - textX;
			while (text.length() > 3 && metrics.stringWidth(text) > maxNameWidth)
			{
				text = text.substring(0, text.length() - 4) + "...";
			}

			graphics.setColor(config.overlayTextColor());
			graphics.drawString(text, textX, y + 16);
			graphics.setColor(config.overlaySecondaryTextColor());
			graphics.drawString(item.getDistance() + "t", listX + listWidth - 34, y + 16);
		}

		if (items.size() > rowCount)
		{
			graphics.setColor(config.overlaySecondaryTextColor());
			graphics.drawString("+" + (items.size() - rowCount) + " more", listX + PADDING, listY + height - 7);
		}

		plugin.setOverlayRows(rowBounds);
		return new Dimension(listWidth, height);
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

	private int getListWidth()
	{
		Dimension preferredSize = getPreferredSize();
		if (preferredSize != null && preferredSize.width > 0)
		{
			return Math.max(100, preferredSize.width);
		}

		return config.overlayWidth();
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
