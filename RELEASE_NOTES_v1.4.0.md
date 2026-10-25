# SimpMC-Title 1.4.0

## Added

- Added the `default_player` preset (`&7[玩家]`), displayed for everyone unless they explicitly unequip their title.
- Added `new_player_7d` (`&7[&f初来乍到&7]`), granted to new players and automatically reclaimed seven days after their first join.
- Added an Adventure chat renderer. Hovering over a displayed title lists every configured title with its configured colors.
- Existing `config.yml` files gain only missing preset definitions and the default title setting; other text and settings remain intact.

## Compatibility

- Targets Paper 1.20.4 and Java 17; continues to support Folia scheduling.
- The plugin API remains compatible with API version 1.

## Upgrade and validation

- Replace the old JAR and restart. Keep the existing configuration and player data.
- The first upgrade changes an empty default title to `default_player` once and records `presets.initialized`; customized nonempty defaults and preset definitions are preserved.
- Newcomer eligibility uses the stored first join timestamp, including after reconnects and restarts. Administrator permissions do not bypass its deadline.
- Regression tests cover YAML migration, comments, empty/inline sections, manual unequip, expiry, renewals, chat hover scope, spacing and player-name formatting.
- Automated checks do not replace a live Paper/Folia and Minecraft-client integration test.
