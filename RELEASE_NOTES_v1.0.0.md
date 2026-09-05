# SimpMC-Title v1.0.0

SimpMC-Title 是一个面向 Paper 服务器的玩家展示管理插件，集中管理称号、昵称、名字颜色以及聊天、Tab 列表和头顶显示效果。

## 主要功能

- 配置多个称号，支持显示文本、描述、图标和权限限制。
- 称号选择 GUI，支持装备、取消装备和称号列表查看。
- 管理员可以发放、收回或强制设置玩家称号，也支持限时称号。
- 自定义昵称内容，并对长度和字符格式进行校验。
- 支持传统颜色代码、RGB 颜色和双颜色渐变。
- 将称号和昵称同步到聊天、Tab 列表及头顶显示。
- 提供名字颜色选择 GUI 和一键恢复默认名字的功能。
- 可选集成 PlaceholderAPI，提供称号、昵称和展示名称变量。
- 玩家数据保存在插件目录的 `players.yml` 中，配置简单、便于迁移。

## 安装

1. 使用 Java 17 或更高版本运行 Paper 服务器。
2. 将附件中的 `SimpMC-Title-1.0.0.jar` 放入服务器的 `plugins` 目录。
3. 重启服务器。
4. 根据需要编辑 `plugins/SimpMC-Title/config.yml`，然后执行 `/smtitle reload`。
5. 如果需要使用 PlaceholderAPI 变量，请额外安装 PlaceholderAPI。

## 常用命令

- `/smtitle menu`：打开称号界面。
- `/smtitle colors`：打开名字颜色界面。
- `/smtitle list`：查看可用称号。
- `/smtitle equip <id>`：装备称号。
- `/smtitle unequip`：取消称号。
- `/smtitle nickname <内容>`：修改昵称。
- `/smtitle color <颜色>`：修改名字颜色。
- `/smtitle resetname`：恢复默认名字。
- `/smtitle reload`：重载配置（管理员）。

管理员命令还包括 `/smtitle give`、`/smtitle take`、`/smtitle set`，详细用法可执行 `/smtitle help` 查看。

## PlaceholderAPI 变量

- `%simpmctitle_title%`
- `%simpmctitle_title_plain%`
- `%simpmctitle_nickname%`
- `%simpmctitle_display_name%`
- `%simpmctitle_name_color%`

## 兼容信息

- Minecraft/Paper：1.20.4
- Java：17+
- PlaceholderAPI：可选

## 文件校验

SHA-256：

`20B25B71A4420A04D7F69B0F9129384B2E5AB6EE4324FB84757D5BE10EF782DA`
