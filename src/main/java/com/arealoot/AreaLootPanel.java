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
	private final JLabel summary = new JLabel();

	AreaLootPanel(AreaLootPlugin plugin, AreaLootConfig config, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;

		header.setForeground(ColorScheme.TEXT_COLOR);
		header.setHorizontalAlignment(SwingConstants.CENTER);
		summary.setForeground(ColorScheme.TEXT_COLOR);
		summary.setHorizontalAlignment(SwingConstants.CENTER);
		add(header);
	}

	void rebuild(List<AreaLootItem> items)
	{
		removeAll();
		int itemCount = Math.min(items.size(), Math.max(1, config.sidePanelMaxItems()));
		header.setText(items.isEmpty() ? "No nearby loot" : "Nearby loot (" + items.size() + ")");
		add(header);

		for (int i = 0; i < itemCount; i++)
		{
			add(createRow(items.get(i)));
		}

		if (items.size() > itemCount && !config.showLootCount())
		{
			JLabel more = new JLabel("+" + (items.size() - itemCount) + " more");
			more.setForeground(ColorScheme.TEXT_COLOR);
			more.setHorizontalAlignment(SwingConstants.CENTER);
			add(more);
		}
		String summaryText = getSummaryText(items);
		if (!summaryText.isEmpty())
		{
			summary.setText(summaryText);
			add(summary);
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
		if (config.listIconSize() != AreaLootConfig.ListIconSize.NONE)
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

	private String formatGeValue(long value)
	{
		return AreaLootValueFormatter.formatGeValue(value);
	}

	private String getSummaryText(List<AreaLootItem> items)
	{
		if (items.isEmpty() || (!config.showLootCount() && config.totalGeValueMode() == AreaLootConfig.TotalGeValueMode.NONE))
		{
			return "";
		}

		String lootCountText = config.showLootCount()
			? "<font color='" + toHtmlColor(config.lootCountTextColor()) + "'>" + items.size() + (items.size() == 1 ? " item" : " items") + "</font>"
			: "";
		String totalGeValueText = getTotalGeValueText(items);
		if (lootCountText.isEmpty() && totalGeValueText.isEmpty())
		{
			return "";
		}
		if (!lootCountText.isEmpty() && !totalGeValueText.isEmpty())
		{
			return "<html>" + lootCountText + "&nbsp;|&nbsp;" + totalGeValueText + "</html>";
		}
		return "<html>" + lootCountText + totalGeValueText + "</html>";
	}

	private String getTotalGeValueText(List<AreaLootItem> items)
	{
		if (items.size() <= 1)
		{
			return "";
		}

		switch (config.totalGeValueMode())
		{
			case LONG:
				return "<font color='" + toHtmlColor(config.totalGeValueTextColor()) + "'>Total: " + formatGeValue(getTotalGeValue(items)) + "</font>";
			case SHORT:
				return "<font color='" + toHtmlColor(config.totalGeValueTextColor()) + "'>" + formatGeValue(getTotalGeValue(items)) + "</font>";
			case NONE:
			default:
				return "";
		}
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
