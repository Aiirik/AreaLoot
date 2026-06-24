package com.arealoot;

import java.awt.Color;
import java.awt.Dimension;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

class AreaLootPanel extends PluginPanel
{
	private static final Dimension ROW_SIZE = new Dimension(PluginPanel.PANEL_WIDTH, 38);

	private final AreaLootPlugin plugin;
	private final AreaLootConfig config;
	private final ItemManager itemManager;
	private final JLabel header = new JLabel("Nearby loot");

	AreaLootPanel(AreaLootPlugin plugin, AreaLootConfig config, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;

		header.setForeground(ColorScheme.TEXT_COLOR);
		header.setHorizontalAlignment(SwingConstants.CENTER);
		add(header);
	}

	void rebuild(List<AreaLootItem> items)
	{
		removeAll();
		header.setText(items.isEmpty() ? "No nearby loot" : "Nearby loot (" + items.size() + ")");
		add(header);

		for (AreaLootItem item : items)
		{
			add(createRow(item));
		}

		revalidate();
		repaint();
	}

	private JButton createRow(AreaLootItem item)
	{
		String quantity = item.getQuantity() > 1 ? " x" + item.getQuantity() : "";
		String geValueText = config.showGeValue()
			? "&nbsp;&nbsp;<font color='" + toHtmlColor(config.geValueTextColor()) + "'>" + formatGeValue(item) + "</font>"
			: "";
		String formattedDistance = formatDistance(item);
		String distanceText = formattedDistance.isEmpty()
			? ""
			: "<br><font color='" + toHtmlColor(config.tileDistanceTextColor()) + "'>" + formattedDistance + "</font>";
		JButton row = new JButton("<html><b>" + escapeHtml(item.getName()) + "</b>" + quantity + geValueText + distanceText + "</html>");
		row.setHorizontalAlignment(SwingConstants.LEFT);
		row.setPreferredSize(ROW_SIZE);
		row.setMinimumSize(ROW_SIZE);
		row.setMaximumSize(ROW_SIZE);
		row.setFocusable(false);
		row.setForeground(ColorScheme.TEXT_COLOR);
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setIconTextGap(6);
		boolean selected = plugin.isSelectedLoot(item);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(selected ? config.highlightOutlineColor() : ColorScheme.BORDER_COLOR, selected ? 2 : 1),
			BorderFactory.createEmptyBorder(3, 6, 3, 6)
		));
		if (config.showItemIcons())
		{
			AsyncBufferedImage image = itemManager.getImage(item.getId(), item.getQuantity(), false);
			if (image != null)
			{
				image.addTo(row);
			}
		}
		row.addActionListener(e ->
		{
			if (plugin.isSelectedLoot(item))
			{
				plugin.clearSelectedLoot();
			}
			else
			{
				plugin.selectLoot(item);
			}
		});
		return row;
	}

	private String formatGeValue(AreaLootItem item)
	{
		return AreaLootValueFormatter.formatGeValue(item.getGeValue());
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

	private String toHtmlColor(Color color)
	{
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	private String escapeHtml(String text)
	{
		return text
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;");
	}
}
