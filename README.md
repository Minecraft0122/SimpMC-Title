# SimpMC-Title

SimpMC-Title is a Paper plugin for managing player titles, nicknames, display colors and a configurable title shop.

## Features

- Configurable titles with permissions, descriptions and icons.
- PlayerTitle-inspired title warehouse, shop and color GUIs with framed content areas, grouped status lore, balance previews and pagination.
- Title ownership stored in `players.yml`.
- Player title purchases using server gold through Vault or the built-in currency.
- Configurable gold-to-internal-currency exchange rate, minimum exchange amount and renewal for timed titles.
- JSON Lines audit records for purchases, exchanges, profile changes and administrator operations.
- Title content policy with maximum length, allowed characters, banned words and banned regular expressions.
- Nickname content with length and character validation.
- Legacy colors, RGB colors and two-color gradients.
- Chat, tab list and head display synchronization.
- Shared title groups whose members display the same title.
- Admin commands for granting, taking and forcing titles.
- Optional PlaceholderAPI expansion.
- Public Java API exposed through Bukkit's `ServicesManager` for title, profile, economy and title-group integrations.

## Commands

- `/smtitle` or `/smtitle menu` opens the title menu
- `/smtitle shop`
- `/smtitle buy <id> [gold|internal]`
- `/smtitle balance`
- `/smtitle exchange <amount|all>`
- `/smtitle colors`
- `/smtitle list`
- `/smtitle equip <id>`
- `/smtitle unequip`
- `/smtitle give <player> <title text> [days]` to grant a configured title or add and grant a custom title
- `/smtitle take <player> <id>`
- `/smtitle set <player> <id>`
- `/smtitle set <player> <title text> [days]` for a validated custom title; `0` or omitted means permanent
- `/smtitle reload`
- `/smtitle group create <group>`
- `/smtitle group title <group> <title text>`
- `/smtitle group add <group> <online player>`
- `/smtitle group remove <group> <online player>`
- `/smtitle group delete <group>`
- `/smtitle group list`

Other plugins can integrate with the public API documented in [API.md](API.md). The plugin registers `SimpMCTitleAPI` as a Bukkit service.

The bundled `config.yml` starts without preset titles. Custom titles without an explicit color use `&7`. Group definitions and UUID membership are stored in `groups.yml`; each player can belong to one group, and the group's shared title takes priority over their personal title while assigned. Title changes synchronize to chat, the player list (Tab), and head display.

## Economy

The built-in currency is stored per player in `plugins/SimpMC-Title/players.yml` under `internal-balance`. Configure its display name, initial balance and whether internal-currency purchases are enabled in `config.yml`.

Gold purchases and gold-to-internal exchange use Vault through the Bukkit service provider. Vault itself and a Vault-compatible economy plugin are optional; the internal currency continues to work without them.

Each title can define a `purchase` section:

```yaml
purchase:
  enabled: true
  days: 30       # 0 means permanent
  gold: 15000.0
  internal: 150
```

Set either price to a non-positive value to disable that payment method for the title. Permission or administrator access bypasses payment, while an owned title can be renewed when `economy.purchase.allow-renewal` is enabled.

## Audit and content policy

When `audit.enabled` is true, actions are appended to `logs/actions.log` as one JSON object per line. The logger rotates the file after the configured size. The `/smtitle audit [keyword]` command shows recent matching records to authorized administrators.

The `content` section validates configured title display text after color codes are removed. Invalid titles are skipped during startup or reload, so a misconfigured or disallowed title cannot be offered in the menus. The same policy can be extended to future free-form title inputs without changing the storage format.

## PlaceholderAPI

- `%simpmctitle_title%`
- `%simpmctitle_title_plain%`
- `%simpmctitle_nickname%`
- `%simpmctitle_display_name%`
- `%simpmctitle_name_color%`
- `%simpmctitle_currency%` / `%simpmctitle_currency_balance%`
- `%simpmctitle_currency_name%`
- `%simpmctitle_gold%` / `%simpmctitle_gold_balance%`
- `%simpmctitle_title_count%` / `%simpmctitle_owned_count%`
- `%simpmctitle_has_title_<id>%`
- `%simpmctitle_economy%`

The project targets Paper 1.20.4 and Java 17, and declares Folia support. Player display updates use the entity scheduler and chat formatting reads a thread-safe display snapshot. The generated JAR does not bundle Paper, PlaceholderAPI, Vault or an economy provider. Install those on the server when using the corresponding features.

Nickname, color and resetname commands are disabled by default; set `nickname.enabled: true` to enable them. The player commands use `simpmctitle.use`, `simpmctitle.shop`, `simpmctitle.purchase`, `simpmctitle.balance`, `simpmctitle.exchange` and (when enabled) `simpmctitle.nickname`. Administrator commands use `simpmctitle.admin` or the narrower `simpmctitle.reload` and `simpmctitle.audit` permissions.

When an administrator sets a custom title, the plugin appends its definition under `titles` in `config.yml`, preserving the existing file contents. The optional final `days` argument sets the active title expiration; `0` or omitting it means permanent.
