package com.arealoot;

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

		if (plugin.hasSelectedLoot())
		{
			add(createClearButton());
		}

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
		JButton row = new JButton("<html><b>" + item.getName() + "</b>" + quantity
			+ "<br><span style='color:#a5a5a5'>" + item.getDistance() + " tiles away</span></html>");
		row.setHorizontalAlignment(SwingConstants.LEFT);
		row.setPreferredSize(ROW_SIZE);
		row.setMinimumSize(ROW_SIZE);
		row.setMaximumSize(ROW_SIZE);
		row.setFocusable(false);
		row.setForeground(ColorScheme.TEXT_COLOR);
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setIconTextGap(6);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
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
		row.addActionListener(e -> plugin.selectLoot(item));
		return row;
	}

	private JButton createClearButton()
	{
		JButton button = new JButton("Clear highlight");
		button.setHorizontalAlignment(SwingConstants.CENTER);
		button.setFocusable(false);
		button.setForeground(ColorScheme.TEXT_COLOR);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)
		));
		button.addActionListener(e -> plugin.clearSelectedLoot());
		return button;
	}
}
