package com.arealoot;

import java.awt.Dimension;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

class AreaLootPanel extends PluginPanel
{
	private static final Dimension ROW_SIZE = new Dimension(PluginPanel.PANEL_WIDTH, 34);

	private final AreaLootPlugin plugin;
	private final JLabel header = new JLabel("Nearby loot");

	AreaLootPanel(AreaLootPlugin plugin)
	{
		this.plugin = plugin;

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
		JButton row = new JButton("<html><b>" + item.getName() + "</b>" + quantity
			+ "<br><span style='color:#a5a5a5'>" + item.getDistance() + " tiles away</span></html>");
		row.setHorizontalAlignment(SwingConstants.LEFT);
		row.setPreferredSize(ROW_SIZE);
		row.setMinimumSize(ROW_SIZE);
		row.setMaximumSize(ROW_SIZE);
		row.setFocusable(false);
		row.setForeground(ColorScheme.TEXT_COLOR);
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
			BorderFactory.createEmptyBorder(3, 6, 3, 6)
		));
		row.addActionListener(e -> plugin.selectLoot(item));
		return row;
	}
}
