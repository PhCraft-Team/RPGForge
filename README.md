# RPGForge — 可视化 RPG 道具编辑器

> Paper 26.3 预研分支：编译目标固定为 `26.3.build.19-alpha`，运行需 Java 25。产物仅供隔离测试，验证结果见 `PAPER_26_3.md`；暂不作为正式服升级依据。

> 在游戏内用 GUI 轻松制作各种特效 RPG 道具，无需写一行配置。

![Java](https://img.shields.io/badge/Java-25-orange)
![Paper](https://img.shields.io/badge/Paper-1.21+-green)
![License](https://img.shields.io/badge/license-MIT-blue)

## ✨ 特性

- **全 GUI 可视化编辑**：无需记忆命令，点点点就能做道具
- **25+ 种内置 Power**：命令、药水、闪电、爆炸、粒子、传送、体积变化、范围伤害、暴击、吸血、护盾…
- **15+ 种触发方式**：右键、左键、攻击命中、被攻击、食用、持有刻、弹射物发射、弹射物命中、穿上盔甲…
- **Condition 条件系统**：概率、血量、饱食度、等级、时间、天气、权限、计分板、目标类型…
- **耐久显示多样化**：原版耐久条、进度条、数字、隐藏 四种模式可选
- **维修系统**：命令维修 + 铁砧维修，兼容原版耐久机制
- **配方系统**：支持有序/无序/熔炉三种配方，合成出 RPG 物品
- **Vault 经济支持**：消耗金币使用道具（软依赖）
- **失败消息自定义**：条件不满足或触发失败时可配置提示消息
- **物品数据存储**：每个物品一个 YAML 文件，易于备份和分享

## 📦 安装

1. 下载最新版 `RPGForge-x.x.x.jar`
2. 放入服务器 `plugins/` 目录
3. 重启服务器
4. （可选）安装 Vault 经济插件以启用金币消耗功能

> 支持 Paper 1.21 及以上版本，需要 **Java 25** 运行环境。

## 🚀 快速开始

### 创建你的第一个 RPG 物品

1. 输入 `/rpgforge` 打开管理面板
2. 点击「创建物品」，输入物品 ID（如 `flame_sword`）
3. 点击物品进入编辑界面
4. 添加 Power —— 选择「召唤闪电」，绑定「右键」触发
5. 保存，使用 `/rpgforge give flame_sword` 获取物品
6. 右键试试！

### 常用命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/rpg` | 打开编辑器 | `rpgforge.use` |
| `/rpgforge give <id> [玩家]` | 给予 RPG 物品 | `rpgforge.admin` |
| `/rpgforge list` | 列出所有物品 | `rpgforge.admin` |
| `/rpgforge create <id>` | 创建新物品 | `rpgforge.admin` |
| `/rpgforge delete <id>` | 删除物品 | `rpgforge.admin` |
| `/rpgforge reload` | 重载配置 | `rpgforge.admin` |
| `/rpgforge repair [amount]` | 修复手中物品耐久 | `rpgforge.admin.repair` |
| `/rpgforge recipe list` | 列出所有配方 | `rpgforge.admin` |
| `/rpgforge recipe create <id> <物品ID> <类型>` | 创建配方 | `rpgforge.admin` |
| `/rpgforge recipe delete <id>` | 删除配方 | `rpgforge.admin` |

## 📚 完整文档

详见 [GitHub Wiki](https://github.com/LHXPaul/RPGForge/wiki)

## 🧩 Power 列表

### 实用类
- **打开编辑器** — 右键打开 RPGForge 面板（无视权限）
- **执行命令** — 以玩家/控制台身份执行命令，支持临时提权
- **消耗物品** — 使用后消耗物品数量
- **耐久消耗** — 使用时消耗耐久度
- **消耗金钱** — 使用时扣除金币（需 Vault）

### 战斗类
- **召唤闪电** — 在目标位置召唤闪电
- **爆炸** — 指定位置引发爆炸
- **伤害加成** — 攻击时增加/乘以伤害
- **范围伤害** — 对周围所有实体造成伤害
- **暴击** — 概率触发暴击，伤害翻倍
- **吸血** — 攻击命中时按伤害百分比回血
- **燃烧** — 点燃目标
- **击退** — 将目标击飞

### 药水效果类
- **自身药水** — 给自己施加药水效果
- **命中药水** — 给命中目标施加药水效果
- **范围药水** — 对周围所有实体施加药水效果
- **漂浮** — 让目标失重漂浮
- **护盾** — 获得伤害吸收心（金色心）

### 位移类
- **传送** — 朝视线方向传送
- **冲刺** — 朝前方快速冲刺
- **体积变化** — 放大或缩小目标体积
- **吸引** — 将周围实体拉向玩家
- **排斥** — 将周围实体推开

### 弹射物类
- **发射火球** — 发射爆炸火球
- **发射箭矢** — 发射箭矢

### 视觉/音效类
- **粒子特效** — 生成各种粒子
- **播放声音** — 播放音效

## 🎯 Trigger 列表

| Trigger | 说明 |
|---------|------|
| `RIGHT_CLICK` | 右键点击（空气/方块/实体） |
| `LEFT_CLICK` | 左键点击 |
| `HIT` | 攻击命中实体 |
| `HIT_TAKEN` | 被攻击时 |
| `PROJECTILE_LAUNCH` | 发射弹射物时 |
| `PROJECTILE_HIT` | 弹射物命中时 |
| `CONSUME` | 食用/饮用时 |
| `HELD` | 持有（每刻触发） |
| `EQUIP` | 穿上装备时 |
| `UNEQUIP` | 脱下装备时 |
| `DEATH` | 死亡时 |
| `RESPAWN` | 复活时 |
| `SNEAK` | 潜行时 |
| `JUMP` | 跳跃时 |
| `BREAK_BLOCK` | 破坏方块时 |

## 📝 License

MIT License

## 构建和检查

构建配置放在 `plugin.json`。提交 PR 后，GitHub Actions 会检查中文标题、运行构建和测试、核对 JAR 的名称与版本，并上传保留 7 天的构建包。

本预研分支的 `releaseEnabled` 为 `false`，合并后也不会自动发布正式版。修改或升级依赖前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。

本地检查：

```bash
python -X utf8 -m unittest discover -s .github/scripts -p 'test_*.py' -v
gradle --no-daemon clean build
```
