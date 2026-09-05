# SimpMC-Title

SimpMC-Title is a Paper plugin for managing player titles, nicknames and display colors.

## Features

- Configurable titles with permissions, descriptions and icons.
- Title selection GUI and title ownership stored in `players.yml`.
- Nickname content with length and character validation.
- Legacy colors, RGB colors and two-color gradients.
- Chat, tab list and head display synchronization.
- Admin commands for granting, taking and forcing titles.
- Optional PlaceholderAPI expansion.

## Commands

- `/smtitle menu`
- `/smtitle colors`
- `/smtitle list`
- `/smtitle equip <id>`
- `/smtitle unequip`
- `/smtitle nickname <content>`
- `/smtitle color <red|#55ccff|<gradient:#55ccff:#b36bff>>`
- `/smtitle resetname`
- `/smtitle give <player> <id> [days]`
- `/smtitle take <player> <id>`
- `/smtitle set <player> <id>`
- `/smtitle reload`

## PlaceholderAPI

- `%simpmctitle_title%`
- `%simpmctitle_title_plain%`
- `%simpmctitle_nickname%`
- `%simpmctitle_display_name%`
- `%simpmctitle_name_color%`

The project targets Paper 1.20.4 and Java 17. The generated JAR does not bundle Paper or PlaceholderAPI; install those on the server when using the corresponding features.
