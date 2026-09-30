# Paper 26.3 适配与验证

## 当前候选

- 分支：`compat/paper-26.3`；基于最新 `main` `91fd4c692eb5152029c9a973b2f6cfbf457ef87b` 同步。
- 目标：Paper API `26.3.build.19-alpha`、`api-version: 26.3`、Java 25。保留主线版本 `1.6.1`，并将 `releaseEnabled` 设为 `false`，避免合并时自动创建正式 Release。
- 该 Alpha 构建只供隔离测试，不兼容 Paper 1.21.x 正式服，不作为生产服升级包。

## 当前候选的自动化验证

- Java 25.0.4.1、Gradle 9.4.0 下 `gradle --no-daemon clean build` 通过；构建包含 10 项 JUnit 测试，失败 0、错误 0、跳过 0。此环境预装 Gradle 9.4.0；`plugin.json` 和 GitHub Actions 配置的 Gradle 版本仍为 9.1.0，远端 CI 会用该配置版本验证当前 head。
- `python3 -X utf8 -m unittest discover -s .github/scripts -p 'test_*.py' -v`：34 项通过。
- `check_artifact.py` 校验主 JAR 名称、版本、插件名和入口类通过；JAR 为 `RPGForge-1.6.1.jar`，SHA-256：`ad1d0c470d5ec92294f4ed3542801ad2d32d905beaf2828f955e7977b7e12915`。
- 已检查生成的 `plugin.yml` 使用插件版本 `1.6.1` 和 API 版本 `26.3`，`git diff --check` 通过。
- 本次同步后的候选包没有启动 Minecraft 服务端，也没有完成新的游戏内操作验证。

## 旧候选包的历史服务端验证

2026-09-19 的旧预研候选包 `RPGForge-1.3.0.jar`（SHA-256：`d6a5553672065e9c421cf5a62304d06176bc6d13a8db5ffd4844f3ce8030cff4`）曾在 Paper 26.3 `#19` 隔离环境验证：10 个组织插件共同加载并正常停服，三个示例配方可读取；探针环境含 VaultUnlocked、EssentialsX 与 LuckPerms。该 JAR 是同步当前 `main` 之前的旧版本，不代表当前合并候选已通过服务端验证。EssentialsX 曾报告不支持该 Alpha 服务端；插件注册成功不等于经济功能已获生产认证。

旧候选另有 8 项 JUnit 回归测试记录，覆盖权限、主线程调度、权限撤销、拖拽保护及路径边界。当前候选已改用主线合并后的 10 项 JUnit 测试，结果见上节。

## 合并后的功能与升级影响

当前候选保留主线已合入的 `rpgforge.use` 权限检查、主线程聊天编辑与排队、聊天事件先取消、编辑菜单拖拽保护、物品 ID 与文件名校验、路径穿越防护、`plugin.yml` 版本展开及 YAML 配方材料读取修复。权限节点与存储格式不变，Vault 仍为可选经济依赖。

物品 ID 沿用最新主线的白名单 `[a-z0-9_]+`。大写字母、连字符及其他非法字符会被拒绝；不符合规则或文件名与 ID 不一致的旧物品会跳过加载并记日志。升级前请备份，并将 ID、文件名和配方引用统一改为小写字母、数字及下划线。以前已经被保存为空材料的配方需从备份恢复。回滚时恢复与旧版本匹配的插件数据、配置和世界备份，不能只降级 JAR。

Paper 26.3 目标仍为 Alpha；未验证真人客户端完整 GUI、多人交易、正式数据副本迁移、Spigot 或 Folia。不要把此构建用于旧 Paper 服务端。正式部署前需在隔离服对当前 head 的候选 JAR 完成目标服务端验证。
