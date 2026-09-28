# 开发与发布规范

本文件供人工贡献者及 Codex、Claude、DSH、Copilot 等 Agent 共同遵守。维护者在当前任务中的明确要求优先。

## 开发和提交

- 从最新默认分支创建工作分支，所有修改通过 PR，维护者手动决定合并，优先使用 Squash Merge。
- 提交和 PR 标题使用 `<type>(可选范围): 中文说明`。类型：feat、fix、docs、refactor、test、build、ci、chore、perf、revert；不兼容变更使用 `!`。
- 完整填写 PR 模板，说明实际改动、测试、兼容性和升级影响。发布摘要用 1–3 条中文，避免口号、流程复述和测试日志。
- 完成任务范围内的必要修改，不顺带重构或增加无关测试。准备完成时明确交付最终提交和验证结果。

## 新项目初始化

按 README 修改 `plugin.json`，替换示例名及兼容性说明。业务代码位于 `src/main/java/`，资源位于 `src/main/resources/`。修改入口类包名时必须同步 `plugin.yml` 的 `main`。

`plugin.json` 是插件名、版本、Java/Gradle/Paper 版本及发布开关的配置源。Gradle 自动展开 `plugin.yml` 的插件名、版本和 api-version，避免重复维护。模板初始化时不要删掉 `.github`、规范或 Agent 入口文件。

## 实际构建和测试

本项目不包含 Gradle Wrapper，使用与 `plugin.json` 一致的 Java 和 Gradle：

```bash
java -version
gradle --version
python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v
gradle --no-daemon clean build
```

CI 会读取配置、构建、执行已配置的测试，检查主 JAR 的名称、版本、内嵌插件名与入口类。没有插件测试代码时，`test` 为 `NO-SOURCE`，只能报告构建成功。模板 Python 测试不会验证 Minecraft 中的插件行为。

禁止声称执行了未执行的测试。构建失败、依赖无法下载、缺少环境或未做游戏内验证，均需如实说明。涉及经济交易、抽奖、物品发放和数据保存时，应验证实际失败路径；单元测试不能替代服务端验证。

## 兼容性和数据影响

- 区分编译依赖、目标兼容性与实际验证环境；不得因 Paper 编译通过就声称兼容全部 Spigot 或 Folia。
- 变更 Minecraft/Paper/Java 或插件依赖时，同步 `plugin.json` 的兼容性说明和构建依赖；必需、可选依赖分别列明。
- 修改配置、数据库/存储、权限、命令、API、玩家/世界/物品数据时，说明旧数据兼容性、升级步骤、破坏性变更及回滚方法。没有影响填“无”。

## CI 和发布

- PR 自动检查中文标题、执行构建并上传临时 JAR；失败时维护者停止合并。组织免费私有仓库依靠此约定，不能把绿色 CI 以外的人工门禁当作已强制启用。
- 合并到默认分支后重新构建；只有 `releaseEnabled: true`、插件名已初始化且源码版本未发布时，才自动发布正式 Release。模板仓库 `PhCraft-Team/plugin-template` 自身始终跳过插件发布。
- PR 和手动运行工作流只做构建，不创建正式 Release。需要新版本时修改 `plugin.json` 的 version，使用稳定版本 `X.Y.Z`；Tag 为 `vX.Y.Z`，JAR 为 `<插件名>-X.Y.Z.jar`。
- 已发布版本（包括预发布）不覆盖、不重新上传；相同 Tag 指向不同提交时停止发布。同版本未完成草稿仅允许在原提交重跑恢复。
- 每次推送独立构建，发布任务排队执行。不要改回按默认分支共用单个待运行任务的并发组，以免连续推送漏发版本。
- Release 从前一个版本以来合并的 PR 整理中文修改摘要和有效升级提醒；仅在工作流确认发布成功后报告“已发布”。

## 下载和部署

正式 JAR 在仓库 Releases → 对应版本 → Assets 下载；临时构建在 Actions → 对应运行 → Artifacts 下载并解压，保留 7 天。私有仓库下载需要访问权限。

由维护者停服、备份插件和数据、替换旧 JAR，然后启动验证。本流程不部署正式服务器。存储有迁移时，按该版本说明备份和回滚。
