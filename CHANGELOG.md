# Changelog

All notable user-facing changes to Area Loot are documented here.

## 1.5.5 - 22-Jul-2026

### Added

- Added an option to group identical nearby drops into a single overlay row while still highlighting every matching drop when selected, replacing the old same-tile selection option.
- Added a single `Area Loot` submenu for shift-right-click item filtering, replacing the separate block and whitelist top-level entries.
- Added a `Condensed item names` option in list overlay settings, with a width threshold so short entries stay on one line while long names can wrap onto two lines.
- Added a `Condensed footer item names` option in list overlay settings so long selected item names can wrap in the list footer without widening the overlay.
- Added a `Condensed footer item names` option in grid overlay settings so long selected item names can wrap in the grid footer instead of widening the overlay.

## 1.5.4 - 16-Jul-2026

### Added

- Added an option in Menu Settings to disable update notifications in the chatbox.

### Fixed

- Fixed plugin startup after a hub update so the plugin can be enabled without restarting RuneLite.
- Fixed footer spacing so loot count and total GE footer lines no longer leave blank space when those footer options are disabled or hidden by the current item count.

## 1.5.3 - 15-Jul-2026

### Fixed

- Fixed install/uninstall behavior.

## 1.5.2 - 08-Jul-2026

### Added

- Added an option to show the selected loot item's name over the highlighted tile, with its own overlay text color setting.
- Added a selected-item footer mode in the overlay, with Short and Long display options.
- Added separate selected-item label/name colors and total GE label/value colors in the overlay color settings.
- Added a grid fill direction option so grid overlays can populate horizontally or vertically.

### Fixed

- Fixed compact list mode so tile distance text stays right-aligned when item names are hidden.

## 1.5.1 - 07-Jul-2026

### Added

- Added selected-item minimap markers with separate dot and line toggles, plus independent color settings for each.
- Added a dedicated `Minimap Settings` section for the selected-item minimap markers.

### Changed

- Removed the `Line matches tile outline` option and now use the explicit line color setting directly.

### Fixed

- Fixed list overlay sizing so hiding item names still lets the box shrink and stacks the total loot summary on narrow layouts.
- Fixed compact list mode so tile distance text stays right-aligned when item names are hidden.

## 1.5.0 - 01-Jul-2026

### Added

- Added a `Keep overlay above game` setting so the overlay can render above in-game actors and scene elements.
- Added a `Show item delay` setting to wait up to 10 seconds before newly dropped items appear in the overlay.
- Added chat confirmations for Shift+right-click block, unblock, whitelist, and unwhitelist actions.
- Added README configuration screenshots.

### Changed

- Changed `List max items` default from 12 to 10.
- Changed `Show total loot count` to be enabled by default.
- Changed `Show total GE value` to default to long format.
- Changed default hotkeys to `Ctrl+X` for overlay toggle and `Ctrl+Z` for auto show/hide.
- Improved grid overlay sizing so cells stay stable as item GE values change.
- Improved grid auto-adjust so the overlay changes by grid rows/columns instead of fluctuating with GP text width.
- Improved overlay footer layout so total loot and total GE stack on narrow grid overlays.
- Hid total GE footer text when only one item is visible.
- Added temporary overlay title status text for toggle and auto show/hide mode changes, with smoother fade behavior.
- Changed config sections other than Overlay to start closed by default.
- Improved whitelist and blocklist config tooltips with exact-match and wildcard examples.
- Cleaned up Overlay List Settings labels and config section ordering.

### Fixed

- Fixed temporary auto-enabled title text popping back in after it faded out.
- Fixed GE and other text-entry fields triggering Area Loot hotkeys while typing.
- Fixed overlay visibility on login and click-to-enter screens by only rendering when the game viewport is visible.
- Fixed layer switching so disabling `Keep overlay above game` restores the original overlay layer behavior.

## 1.4.1 - 30-Jun-2026

### Added

- Added total ground-item count and total GE value to the side panel.
- Added a side panel item limit setting.

### Changed

- Reorganized config settings into clearer sections.
- Moved menu-related options into Menu Settings.
- Renamed overlay adjustment sections to better separate colors and behavior.

### Fixed

- Fixed typing conflicts with single-letter hotkeys when using press-enter-to-chat.

## 1.4.0 - 29-Jun-2026

### Added

- Added whitelist item support, including wildcard matching.
- Added overlay footer indicators for total visible loot count and total visible GE value.
- Added a minimum list width option to reduce width changes as item names change.
- Added one-time in-game update notices.

### Changed

- Changed overlay movement to use RuneLite's Alt-drag overlay positioning.
- Removed the old X/Y coordinate positioning workflow.
- Improved README image layout.

## 1.3.0 - 28-Jun-2026

### Added

- Added selected-item menu pinning for Take options.
- Added selected Examine grouping within the right-click menu.
- Added overlay footer indicators for loot count and total GE value.

## 1.2.0 - 27-Jun-2026

### Added

- Added grid overlay mode.
- Added grid auto-adjust to reduce empty space when fewer items are visible.
- Added grid icon size options.
- Added selected item fill/outline style.
- Added list icon size options.
- Added overlay title toggle.
- Added README grid overlay screenshot.

### Changed

- Improved grid sizing so longer GE value text does not randomly expand the overlay once the configured column count is reached.
- Reworked and reorganized config sections for better readability.
- Made remembered overlay mode enabled by default.

### Fixed

- Fixed update notification color.

## 1.1.0 - 24-Jun-2026

### Added

- Added remember overlay mode so the selected overlay mode can persist through logout/login.
- Added Shift+right-click block/unblock actions for ground items.
- Added a permissive license file.

## 1.0.0 - 24-Jun-2026

### Added

- Initial Area Loot plugin release.
- Added nearby ground-loot overlay and RuneLite side panel.
- Added click-to-highlight behavior for item tiles.
- Added clear-highlight behavior.
- Added item icons in the overlay.
- Added optional line from the player to the highlighted item.
- Added GE value display.
- Added tile distance display options.
- Added side panel toggle.
- Added sorting by nearest item or GE value.
- Added blocked item list.
- Added wildcard support for blocked items, such as `Burnt *`.
- Added right-click menu filtering for highlighted items.
- Added fade in/out behavior for the overlay.
- Added initial README, plugin icon, and example screenshot.

### Changed

- Reorganized and clarified config wording.
- Made the side panel match overlay clear behavior.
- Changed side panel default to off.
- Improved GE value and tile-distance alignment.
- Adjusted default auto show/hide and fade settings.

### Fixed

- Fixed tile highlight selection so duplicate items on the same tile are handled more accurately.
- Fixed highlight line endpoint so it goes to the tile edge instead of the tile center.
