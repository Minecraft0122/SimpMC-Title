# TAB / TAB-Bridge 接入（1.5.0）

## 安装位置

- 单服：后端安装 SimpMC-Title、PlaceholderAPI、TAB。
- 群组服：代理端安装 TAB；每个需要显示称号的后端安装 SimpMC-Title、PlaceholderAPI、TAB-Bridge。TAB-Bridge 的版本需与代理端 TAB 协议匹配。

官方说明中，TAB-Bridge 负责把后端 PlaceholderAPI 数据转发到代理端，Bridge 本身没有配置文件，展示格式由 TAB 控制。SimpMC-Title 使用此转发机制，不依赖 Bridge 的内部实现。插件只自动补充自己的 config.yml，不会替换 TAB 的 groups.yml、users.yml 或代理端配置。

## SimpMC-Title 配置

```yaml
display:
  tab:
    enabled: true
    mode: auto
    hide-player-title: true
    format: "%title%%name%"
```

合并到原有配置，勿重复创建 display 区块。缺失的 mode 和 hide-player-title 会在启动或重载时自动补充。

- `auto`：发现后端 TAB 或 TAB-Bridge 后只提供占位符，避免与 TAB 反复覆盖列表名；否则使用原生列表名。
- `placeholders`：强制由外部插件消费占位符。
- `native`：强制调用后端列表名 API。通常只用于没有 TAB 接管的服务器。
- `hide-player-title: true`：隐藏 default_player 预设，包含更改过显示文字的该预设；新人、其他称号及称号组仍显示。
- `enabled: false`：Tab 称号占位符返回空；不影响聊天或头顶。

TAB 模式下，最终格式由 TAB 配置决定，SimpMC-Title 的 display.tab.format 只用于 native 路径。

## TAB 配置

在运行 TAB 的那一端修改配置：群组服为代理端，单服为后端。

TAB 的 config.yml 启用列表名字格式，并设置刷新频率（毫秒）：

```yaml
tablist-name-formatting:
  enabled: true
placeholder-refresh-intervals:
  "%simpmctitle_tab_prefix%": 500
  "%simpmctitle_tab_name%": 500
```

合并到已有区块，保留其他刷新设置；同时检查自己的 disable-condition 没有禁用目标玩家。

TAB 的 groups.yml 设置默认显示格式：

```yaml
_DEFAULT_:
  tabprefix: "%simpmctitle_tab_prefix%"
  customtabname: "%simpmctitle_tab_name%"
```

不要重复创建已有的 `_DEFAULT_`。如果已有 tabprefix（例如权限组前缀），按所需顺序把 `%simpmctitle_tab_prefix%` 加入原字符串。用户或具体分组有更高优先级的 tabprefix/customtabname 时，也需要在对应条目使用这些占位符。保留原有 tabsuffix，除非你本来就希望移除它。

tab_prefix 已包含一个默认分隔空格和样式重置，额外添加空格会产生双空格；tab_name 自带重置，称号的加粗不会传到玩家名。不要改用 `%simpmctitle_title%`，它没有 Tab 的玩家预设隐藏规则。

修改后重载 TAB；代理端通常使用 `/btab reload`，后端使用 `/tab reload`。新装或更新 JAR 建议重启对应服务端。

## 占位符与排查

| 占位符 | 输出 |
| --- | --- |
| `%simpmctitle_tab_title%` | Tab 称号，不带分隔空格；玩家预设默认空 |
| `%simpmctitle_tab_prefix%` | Tab 称号、分隔空格和重置；隐藏时完全为空 |
| `%simpmctitle_tab_name%` | 玩家显示名，先重置样式，不含称号 |

占位符读取已计算的线程安全在线快照。切换/取消称号、到期及重连会刷新快照，TAB 按配置的周期获取变化。离线或首次展示刷新前返回空值。

1. 在后端运行 `/papi parse me %simpmctitle_tab_prefix%`。新人应返回初来乍到前缀；玩家预设应为空。可再检查 `%simpmctitle_tab_name%` 确认扩展已加载。
2. 在代理端运行 `/btab dump <玩家>`，检查 Bridge 连接为 connected。未连接时检查后端安装与 TAB/Bridge 版本匹配。
3. 检查 TAB 的 groups.yml / users.yml 覆盖关系，以及最终格式是否包含上述占位符。
4. 默认模式启动日志会说明占位符接入要求；缺少 PlaceholderAPI 会明确警告。

自动化测试覆盖 Bridge 检测、不覆盖外部列表名、占位符内容、玩家预设隐藏、取消/到期及样式分隔。尚未在真实代理端与后端联机环境完成验证。

## 官方依据

- [TAB-Bridge 安装、转发及连接检查](https://github.com/NEZNAMY/TAB/wiki/TAB-Bridge)
- [TAB PlaceholderAPI 与异步刷新说明](https://github.com/NEZNAMY/TAB/wiki/Placeholders)
- [TAB 列表名字格式及 groups.yml / users.yml 优先级](https://github.com/NEZNAMY/TAB/wiki/Feature-guide:-Tablist-name-formatting)
