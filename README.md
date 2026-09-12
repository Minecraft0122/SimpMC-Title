# SimpMC-Title

SimpMC-Title is a Paper plugin for managing player titles, nicknames, display colors and a configurable title shop.

## Features

- Configurable titles with permissions, descriptions and icons.
- Title selection GUI and title ownership stored in `players.yml`.
- Player title purchases using server gold through Vault or the built-in currency.
- Configurable gold-to-internal-currency exchange rate, minimum exchange amount and renewal for timed titles.
- JSON Lines audit records for purchases, exchanges, profile changes and administrator operations.
- Title content policy with maximum length, allowed characters, banned words and banned regular expressions.
- Nickname content with length and character validation.
- Legacy colors, RGB colors and two-color gradients.
- Chat, tab list and head display synchronization.
- Admin commands for granting, taking and forcing titles.
- Optional PlaceholderAPI expansion.

## Commands

- `/smtitle menu`
- `/smtitle shop`
- `/smtitle buy <id> [gold|internal]`
- `/smtitle balance`
- `/smtitle exchange <amount|all>`
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

The project targets Paper 1.20.4 and Java 17. The generated JAR does not bundle Paper, PlaceholderAPI, Vault or an economy provider. Install those on the server when using the corresponding features.

The player commands use `simpmctitle.use`, `simpmctitle.shop`, `simpmctitle.purchase`, `simpmctitle.balance`, `simpmctitle.exchange` and `simpmctitle.nickname`. Administrator commands use `simpmctitle.admin` or the narrower `simpmctitle.reload` and `simpmctitle.audit` permissions.
