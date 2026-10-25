# SimpMC-Title 1.5.0

- 新人首次进服自动佩戴初来乍到，固定为首次进服后的 7 天；手动取消或更换后不再自动恢复。到期自动回收，沿用既有首次进服记录。
- 聊天必须有称号；取消个人称号后使用玩家预设保底。新增 simpmctitle.title.hide 权限（default: false），允许手动取消后聊天无称号。OP/admin 本身不豁免。
- 玩家预设默认不显示在 Tab；聊天、头顶、其他个人称号和组称号规则独立。
- 检测 TAB / TAB-Bridge，提供 tab_title、tab_prefix、tab_name 占位符，避免竞争写入列表名。需按 TAB_BRIDGE.md 配置消费端。
- 自动补充缺失的配置键，保留既有值、称号定义及注释；新增玩家初始化标记保留手动选择。
- 更新 README、API 文档和 TAB-Bridge 接入指南，增加相应回归测试。

目标：Paper 1.20.4 / Java 17，声明 Folia 支持。真实客户端、代理端和 TAB-Bridge 联机效果尚待服务器验证。
