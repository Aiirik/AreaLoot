# Changelog

All notable user-facing changes to Area Loot are documented here.

## 1.4.2 - 2026-06-30

### Added

- Added a `Keep overlay above game` setting so the overlay can render above in-game actors and scene elements.
- Added chat confirmations for Shift+right-click block, unblock, whitelist, and unwhitelist actions.
- Added README configuration screenshots.

### Changed

- Changed default hotkeys to `Ctrl+X` for overlay toggle and `Ctrl+Z` for auto show/hide.
- Improved whitelist and blocklist config tooltips with exact-match and wildcard examples.
- Cleaned up Overlay List Settings labels and config section ordering.

### Fixed

- Fixed GE and other text-entry fields triggering Area Loot hotkeys while typing.
- Fixed overlay visibility on login and click-to-enter screens by only rendering when the game viewport is visible.
- Fixed layer switching so disabling `Keep overlay above game` restores the original overlay layer behavior.

## 1.4.1 - 2026-06-30

### Added

- Added total ground-item count and total GE value to the side panel.
- Added a side panel item limit setting.

### Changed

- Reorganized config settings into clearer sections.
- Moved menu-related options into Menu Settings.
- Renamed overlay adjustment sections to better separate colors and behavior.

### Fixed

- Fixed typing conflicts with single-letter hotkeys when using press-enter-to-chat.

## 1.4.0 - 2026-06-29

### Added

- Added whitelist item support, including wildcard matching.
- Added overlay footer indicators for total visible loot count and total visible GE value.
- Added a minimum list width option to reduce width changes as item names change.
- Added one-time in-game update notices.

### Changed

- Changed overlay movement to use RuneLite's Alt-drag overlay positioning.
- Removed the old X/Y coordinate positioning workflow.
- Improved README image layout.

## 1.3.0 - 2026-06-28

### Added

- Added selected-item menu pinning for Take options.
- Added selected Examine grouping within the right-click menu.
- Added overlay footer indicators for loot count and total GE value.

## 1.2.0 - 2026-06-27

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

## 1.1.0 - 2026-06-24

### Added

- Added remember overlay mode so the selected overlay mode can persist through logout/login.
- Added Shift+right-click block/unblock actions for ground items.
- Added a permissive license file.

## 1.0.0 - 2026-06-24

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
