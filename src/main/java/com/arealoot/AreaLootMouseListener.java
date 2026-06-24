package com.arealoot;

import java.awt.event.MouseEvent;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.client.input.MouseAdapter;

class AreaLootMouseListener extends MouseAdapter
{
	@Inject
	private AreaLootPlugin plugin;

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		if (!plugin.shouldShowOverlayList() || !SwingUtilities.isLeftMouseButton(event))
		{
			return event;
		}

		AreaLootItem item = plugin.getOverlayItemAt(event.getPoint());
		if (item == null)
		{
			return event;
		}

		plugin.selectLoot(item);
		event.consume();
		return event;
	}
}
