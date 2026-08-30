package com.arealoot;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

class AreaLootThemePanel extends PluginPanel
{
	private static final String EMPTY_CARD = "empty";
	private static final String CREATE_CARD = "create";
	private static final String IMPORT_CARD = "import";
	private static final Color STATUS_ERROR = new Color(220, 96, 96);
	private static final Color STATUS_SUCCESS = new Color(128, 220, 128);
	private static final Color ACTIVE_ROW_COLOR = new Color(54, 74, 54);
	private static final Color FIELD_TEXT_COLOR = new Color(235, 235, 235);
	private static final Color FIELD_BACKGROUND_COLOR = new Color(35, 35, 35);

	private final AreaLootPlugin plugin;
	private final JPanel actionPanel = new JPanel(new BorderLayout());
	private final JPanel themeList = new JPanel(new GridBagLayout());
	private final JTextField createThemeName = new JTextField();
	private final JTextField importThemeName = new JTextField();
	private final JTextArea importText = new JTextArea();
	private final JLabel status = new JLabel(" ");

	AreaLootThemePanel(AreaLootPlugin plugin)
	{
		super(false);
		this.plugin = plugin;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		styleTextField(createThemeName);
		styleTextField(importThemeName);
		styleTextArea(importText);

		JPanel content = new JPanel(new BorderLayout(0, 10));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.add(createHeaderPanel(), BorderLayout.NORTH);
		content.add(createBodyPanel(), BorderLayout.CENTER);

		JScrollPane scrollPane = new JScrollPane(content);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(scrollPane, BorderLayout.CENTER);
		rebuild();
	}

	void rebuild()
	{
		themeList.removeAll();

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(0, 0, 8, 0);

		Map<String, String> themes = plugin.getNamedColorThemes();
		if (themes.isEmpty())
		{
			JLabel empty = new JLabel("No custom themes");
			empty.setForeground(Color.LIGHT_GRAY);
			themeList.add(empty, constraints);
		}
		else
		{
			for (String name : themes.keySet())
			{
				themeList.add(createThemeRow(name), constraints);
				constraints.gridy++;
			}
		}

		themeList.revalidate();
		themeList.repaint();
	}

	private JPanel createHeaderPanel()
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Area Loot Themes");
		title.setForeground(Color.WHITE);
		panel.add(title, BorderLayout.WEST);

		return panel;
	}

	private JPanel createBodyPanel()
	{
		JPanel body = new JPanel(new GridBagLayout());
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);

		actionPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		setActionCard(EMPTY_CARD);
		themeList.setBackground(ColorScheme.DARK_GRAY_COLOR);

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(0, 0, 6, 0);
		body.add(actionPanel, constraints);

		constraints.gridy = 1;
		body.add(createThemeListPanel(), constraints);

		constraints.gridy = 2;
		constraints.insets = new Insets(0, 0, 0, 0);
		body.add(status, constraints);

		constraints.gridy = 3;
		constraints.weighty = 1;
		constraints.fill = GridBagConstraints.BOTH;
		JPanel spacer = new JPanel();
		spacer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.add(spacer, constraints);
		return body;
	}

	private JPanel createEmptyCard()
	{
		JPanel panel = createSectionPanel();
		panel.setLayout(new GridBagLayout());

		JButton useCurrent = new JButton("Use current theme as base");
		useCurrent.setToolTipText("Create a named color theme from the current Area Loot colors");
		useCurrent.addActionListener(e -> setActionCard(CREATE_CARD));

		JButton importTheme = new JButton("Import theme");
		importTheme.setToolTipText("Create a named color theme from pasted Area Loot theme text");
		importTheme.addActionListener(e -> setActionCard(IMPORT_CARD));

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(0, 0, 6, 0);
		panel.add(useCurrent, constraints);

		constraints.gridy = 1;
		constraints.insets = new Insets(0, 0, 0, 0);
		panel.add(importTheme, constraints);
		return panel;
	}

	private JPanel createCurrentThemeCard()
	{
		JPanel panel = createSectionPanel();
		panel.setLayout(new BorderLayout(0, 6));
		panel.add(sectionLabel("Use current theme as base"), BorderLayout.NORTH);

		JPanel fields = new JPanel(new BorderLayout(0, 4));
		fields.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		fields.add(sectionLabel("Name"), BorderLayout.NORTH);
		fields.add(createThemeName, BorderLayout.CENTER);

		panel.add(fields, BorderLayout.CENTER);
		panel.add(createFormButtons("Create theme", this::createFromCurrent), BorderLayout.SOUTH);
		return panel;
	}

	private JPanel createImportThemeCard()
	{
		JPanel panel = createSectionPanel();
		panel.setLayout(new BorderLayout(0, 6));
		panel.add(sectionLabel("Import theme"), BorderLayout.NORTH);

		JPanel fields = new JPanel(new BorderLayout(0, 6));
		fields.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JPanel namePanel = new JPanel(new BorderLayout(0, 4));
		namePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		namePanel.add(sectionLabel("Name"), BorderLayout.NORTH);
		namePanel.add(importThemeName, BorderLayout.CENTER);
		fields.add(namePanel, BorderLayout.NORTH);

		importText.setRows(5);
		importText.setLineWrap(true);
		importText.setWrapStyleWord(true);

		JPanel textPanel = new JPanel(new BorderLayout(0, 4));
		textPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		textPanel.add(sectionLabel("Theme text"), BorderLayout.NORTH);
		textPanel.add(new JScrollPane(importText), BorderLayout.CENTER);
		fields.add(textPanel, BorderLayout.CENTER);

		panel.add(fields, BorderLayout.CENTER);
		panel.add(createFormButtons("Import theme", this::createFromImport), BorderLayout.SOUTH);
		return panel;
	}

	private JPanel createThemeListPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.add(sectionLabel("Custom themes"), BorderLayout.NORTH);
		panel.add(themeList, BorderLayout.NORTH);
		return panel;
	}

	private JPanel createThemeRow(String name)
	{
		boolean active = name.equals(plugin.getActiveThemeName());
		JPanel row = createSectionPanel(active);
		row.setLayout(new BorderLayout(0, 6));

		JLabel title = new JLabel(active ? "Active: " + name : name);
		title.setForeground(Color.WHITE);
		row.add(title, BorderLayout.NORTH);

		JPanel buttons = new JPanel(new GridBagLayout());
		buttons.setBackground(row.getBackground());
		addThemeButton(buttons, "Apply", "Use this saved theme for the current Area Loot colors", 0, 0, () -> applyTheme(name));
		addThemeButton(buttons, "Overwrite", "Replace this saved theme with the current config colors", 1, 0, () -> confirmOverwriteTheme(name));
		addThemeButton(buttons, "Export", "Copy share text for this saved theme", 0, 1, () -> exportTheme(name));
		addThemeButton(buttons, "Delete", "Remove this saved theme", 1, 1, () -> deleteTheme(name));

		row.add(buttons, BorderLayout.CENTER);
		return row;
	}

	private JPanel createFormButtons(String confirmText, Runnable confirmAction)
	{
		JPanel buttons = new JPanel(new GridBagLayout());
		buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JButton confirm = new JButton(confirmText);
		confirm.addActionListener(e -> confirmAction.run());

		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(e -> setActionCard(EMPTY_CARD));

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(0, 0, 0, 4);
		buttons.add(confirm, constraints);

		constraints.gridx = 1;
		constraints.insets = new Insets(0, 4, 0, 0);
		buttons.add(cancel, constraints);
		return buttons;
	}

	private void addThemeButton(JPanel panel, String text, String tooltip, int x, int y, Runnable action)
	{
		JButton button = new JButton(text);
		button.setToolTipText(tooltip);
		button.addActionListener(e -> action.run());

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = x;
		constraints.gridy = y;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(y == 0 ? 0 : 4, x == 0 ? 0 : 3, 0, x == 1 ? 0 : 3);
		panel.add(button, constraints);
	}

	private JPanel createSectionPanel()
	{
		return createSectionPanel(false);
	}

	private JPanel createSectionPanel(boolean active)
	{
		JPanel panel = new JPanel();
		panel.setBackground(active ? ACTIVE_ROW_COLOR : ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(active
			? BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(STATUS_SUCCESS),
				BorderFactory.createEmptyBorder(7, 7, 7, 7))
			: BorderFactory.createEmptyBorder(8, 8, 8, 8));
		return panel;
	}

	private JLabel sectionLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(FIELD_TEXT_COLOR);
		return label;
	}

	private void styleTextField(JTextField field)
	{
		field.setForeground(FIELD_TEXT_COLOR);
		field.setCaretColor(FIELD_TEXT_COLOR);
		field.setBackground(FIELD_BACKGROUND_COLOR);
		field.setSelectedTextColor(Color.WHITE);
		field.setSelectionColor(ColorScheme.MEDIUM_GRAY_COLOR);
	}

	private void styleTextArea(JTextArea textArea)
	{
		textArea.setForeground(FIELD_TEXT_COLOR);
		textArea.setCaretColor(FIELD_TEXT_COLOR);
		textArea.setBackground(FIELD_BACKGROUND_COLOR);
		textArea.setSelectedTextColor(Color.WHITE);
		textArea.setSelectionColor(ColorScheme.MEDIUM_GRAY_COLOR);
	}

	private void createFromCurrent()
	{
		try
		{
			plugin.createThemeFromCurrentColors(createThemeName.getText());
			createThemeName.setText("");
			setStatus("Theme saved", STATUS_SUCCESS);
			setActionCard(EMPTY_CARD);
			rebuild();
		}
		catch (RuntimeException ex)
		{
			setStatus(ex.getMessage(), STATUS_ERROR);
		}
	}

	private void createFromImport()
	{
		try
		{
			plugin.importNamedColorTheme(importThemeName.getText(), importText.getText());
			importThemeName.setText("");
			importText.setText("");
			setStatus("Theme imported", STATUS_SUCCESS);
			setActionCard(EMPTY_CARD);
			rebuild();
		}
		catch (RuntimeException ex)
		{
			setStatus(ex.getMessage(), STATUS_ERROR);
		}
	}

	private void applyTheme(String name)
	{
		try
		{
			plugin.applyNamedColorTheme(name);
			setStatus("Applied " + name, STATUS_SUCCESS);
		}
		catch (RuntimeException ex)
		{
			setStatus(ex.getMessage(), STATUS_ERROR);
		}
	}

	private void updateTheme(String name)
	{
		try
		{
			plugin.updateNamedColorThemeFromCurrent(name);
			setStatus("Updated " + name, STATUS_SUCCESS);
			rebuild();
		}
		catch (RuntimeException ex)
		{
			setStatus(ex.getMessage(), STATUS_ERROR);
		}
	}

	private void exportTheme(String name)
	{
		try
		{
			plugin.copyNamedColorThemeToClipboard(name);
			setStatus("Copied " + name, STATUS_SUCCESS);
		}
		catch (RuntimeException ex)
		{
			setStatus(ex.getMessage(), STATUS_ERROR);
		}
	}

	private void deleteTheme(String name)
	{
		if (!confirm("Delete theme?", "Delete \"" + name + "\"?"))
		{
			return;
		}

		plugin.deleteNamedColorTheme(name);
		setStatus("Deleted " + name, STATUS_SUCCESS);
		setActionCard(EMPTY_CARD);
		rebuild();
	}

	private void confirmOverwriteTheme(String name)
	{
		if (confirm("Overwrite theme?", "Replace \"" + name + "\" with the current config colors?"))
		{
			updateTheme(name);
		}
	}

	private boolean confirm(String title, String message)
	{
		return JOptionPane.showConfirmDialog(
			this,
			message,
			title,
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
	}

	private void setActionCard(String card)
	{
		actionPanel.removeAll();
		switch (card)
		{
			case CREATE_CARD:
				actionPanel.add(createCurrentThemeCard(), BorderLayout.CENTER);
				break;
			case IMPORT_CARD:
				actionPanel.add(createImportThemeCard(), BorderLayout.CENTER);
				break;
			case EMPTY_CARD:
			default:
				actionPanel.add(createEmptyCard(), BorderLayout.CENTER);
				break;
		}

		actionPanel.revalidate();
		actionPanel.repaint();
	}

	private void setStatus(String message, Color color)
	{
		status.setText(message == null || message.isEmpty() ? " " : message);
		status.setForeground(color);
	}
}
