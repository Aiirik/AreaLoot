package com.arealoot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

class AreaLootMinimapOverlay extends Overlay
{
	private static final double MINIMAP_DOT_EDGE_OFFSET = 2.5;

	private final Client client;
	private final AreaLootPlugin plugin;
	private final AreaLootConfig config;

	@Inject
	AreaLootMinimapOverlay(Client client, AreaLootPlugin plugin, AreaLootConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		boolean drawDot = config.drawMinimapDot();
		boolean drawLine = config.drawMinimapLine();
		if (!drawDot && !drawLine)
		{
			return null;
		}

		WorldPoint selectedLocation = plugin.getSelectedLocation();
		if (selectedLocation == null)
		{
			return null;
		}

		LocalPoint localPoint = LocalPoint.fromWorld(client, selectedLocation);
		if (localPoint == null)
		{
			return null;
		}

		Point itemMinimapPoint = Perspective.localToMinimap(client, localPoint);
		if (itemMinimapPoint == null)
		{
			return null;
		}

		if (drawLine)
		{
			renderMinimapLine(graphics, itemMinimapPoint);
		}

		if (drawDot)
		{
			OverlayUtil.renderMinimapLocation(graphics, itemMinimapPoint, config.highlightMinimapDotColor());
		}
		return null;
	}

	private void renderMinimapLine(Graphics2D graphics, Point itemMinimapPoint)
	{
		if (client.getLocalPlayer() == null)
		{
			return;
		}

		Point playerMinimapPoint = Perspective.localToMinimap(client, client.getLocalPlayer().getLocalLocation());
		if (playerMinimapPoint == null)
		{
			return;
		}

		Stroke originalStroke = graphics.getStroke();
		graphics.setStroke(new BasicStroke(1f));
		graphics.setColor(config.highlightMinimapLineColor());
		Point lineEnd = moveToward(itemMinimapPoint, playerMinimapPoint, MINIMAP_DOT_EDGE_OFFSET);
		graphics.drawLine(playerMinimapPoint.getX(), playerMinimapPoint.getY(), lineEnd.getX(), lineEnd.getY());
		graphics.setStroke(originalStroke);
	}

	private Point moveToward(Point from, Point to, double pixels)
	{
		double dx = to.getX() - from.getX();
		double dy = to.getY() - from.getY();
		double distance = Math.hypot(dx, dy);
		if (distance <= pixels || distance == 0)
		{
			return to;
		}

		return new Point(
			(int) Math.round(from.getX() + ((dx / distance) * pixels)),
			(int) Math.round(from.getY() + ((dy / distance) * pixels))
		);
	}
}
