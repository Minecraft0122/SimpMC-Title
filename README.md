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
- Hovering a chat title shows every configured title.
- Built-in default player title and a newcomer title that expires seven days after first join.
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

The bundled `config.yml` includes the `default_player` and `new_player_7d` presets. Newcomers automatically wear the newcomer title during their first seven days, unless they manually clear or change it. The title is reclaimed at expiry, including while online. Chat falls back to the player title after clearing unless `simpmctitle.title.hide` is granted; the player title is hidden from Tab by default. Upgrades add missing settings while preserving existing values and comments. Custom titles without an explicit color use `&7`. Group definitions and UUID membership are stored in `groups.yml`; each player can belong to one group, whose shared title takes priority over their personal title. For TAB or TAB-Bridge, see [TAB_BRIDGE.md](TAB_BRIDGE.md).

## 预设称号与聊天悬停

| 称号 ID | 显示文本 | 使用规则 |
| --- | --- | --- |
| `default_player` | `&7[玩家]` | 所有人可用；聊天的保底称号，Tab 默认隐藏。 |
| `new_player_7d` | `&7[&f初来乍到&7]` | 新人首次进服自动佩戴，从首次进服起计算 7 × 24 小时；到期自动回收。 |

`/smctitle` 打开菜单。新人无需手动选择“初来乍到”，但可用菜单或 `/smctitle unequip` 取消，也可更换其他称号。取消和手动选择在重连、重启后保留；自动佩戴只初始化一次，其他称号到期也不会强行换回新人称号。可用 `/smctitle equip default_player` 选择玩家称号，或在期限内用 `/smctitle equip new_player_7d` 恢复新人称号。已有个人称号和称号组优先级保留。首次进服时间保存为 `players.yml` 的 `first-joined-at`，自动选择标记为 `newcomer-default-initialized`；更新不会重新赠送 7 天。

普通玩家取消个人称号后，聊天仍显示 `[玩家]`，Tab 和头顶不因此增加保底称号。只有授予 `simpmctitle.title.hide` 才允许手动取消后聊天无称号；此权限默认 `false`，OP 和 `simpmctitle.admin` 本身不自动豁免。权限插件若授予了包含此节点的通配权限，也视为授权。撤回权限后下一条聊天恢复保底。称号组仍优先展示。使用 LuckPerms 时可执行 `/lp user <玩家> permission set simpmctitle.title.hide true` 授权。

首次升级会补充两个缺失的预设，并把旧版空的 `display.default-title` 设置为 `default_player`；已有非空默认称号、称号定义、其他设置及注释均保留。迁移由 `presets.initialized` 标记，之后可将默认称号设空以关闭个人默认，但这不会绕过聊天保底。1.5.0 仅补充缺失的 Tab 模式、隐藏开关及取消提示，不覆盖已有设置。无需删除配置或玩家数据。

鼠标放在聊天消息的称号文字上，会显示所有通过内容校验并加载的配置称号（包括尚未解锁的称号）。称号与玩家名默认相隔一个空格，悬停及称号样式不会扩散到玩家名或消息。此功能及聊天保底由插件的 Paper 聊天渲染器提供，需启用 `display.chat.enabled`。普通玩家的格式若漏写 `%title%`，渲染器会自动补在开头；如果其他聊天插件取消消息或替换渲染器，需要相应集成。

## TAB / TAB-Bridge

`display.tab.hide-player-title: true` 默认隐藏 `default_player`，新人及其他个人/组称号仍展示。`display.tab.mode: auto` 检测后端的 TAB 或 TAB-Bridge 后交由它们消费占位符；无上述插件时直接设置 Tab 名字。代理端 TAB 需要后端同时安装 PlaceholderAPI 和 TAB-Bridge，并在代理端 TAB 配置称号占位符。完整步骤、配置示例和排查命令见 [TAB_BRIDGE.md](TAB_BRIDGE.md)。

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
- `%simpmctitle_tab_title%`：遵循 Tab 隐藏规则的称号，不含分隔空格。
- `%simpmctitle_tab_prefix%`：Tab 称号 + 分隔空格 + 样式重置；无称号时返回空。
- `%simpmctitle_tab_name%`：带样式重置的玩家显示名，不包含称号。
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
