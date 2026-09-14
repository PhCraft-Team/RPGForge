package com.phcraft.rpgforge;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Power 类型枚举 —— 每种类型对应一种特效/功能。
 *
 * <p>每个 PowerType 定义：
 * <ul>
 *   <li>id / 显示名 / 描述 / 图标材质</li>
 *   <li>默认参数（用于 GUI 初始化）</li>
 *   <li>参数元数据（名称、类型、描述）—— GUI 据此生成编辑界面</li>
 *   <li>执行逻辑 {@link #execute(Player, LivingEntity, Map, RPGItem)}</li>
 * </ul>
 */
public enum PowerType {

    // ===== 实用类 =====
    OPEN_EDITOR("open-editor", "打开编辑器", "右键打开 RPGForge 物品编辑面板（无视权限）",
            "CRAFTING_TABLE",
            Map.of(),
            Map.of()),

    COMMAND("command", "执行命令", "以玩家/控制台身份执行命令，可临时提权",
            "COMMAND_BLOCK",
            Map.of(
                    "command", "say Hello!",
                    "as-console", false,
                    "bypass-permissions", "",
                    "cooldown", 0
            ),
            Map.of(
                    "command", new ParamInfo("命令内容", ParamType.STRING, "支持 {player} 占位符"),
                    "as-console", new ParamInfo("以控制台执行", ParamType.BOOLEAN, "true=控制台身份（全权限），false=玩家身份"),
                    "bypass-permissions", new ParamInfo("临时权限节点", ParamType.STRING, "以玩家身份执行时临时给予的权限，多个用逗号分隔（留空=不额外给）"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    // ===== 战斗类 =====
    LIGHTNING("lightning", "召唤闪电", "在目标位置召唤一道闪电",
            "BLAZE_ROD",
            Map.of(
                    "damage", 5.0,
                    "cooldown", 3
            ),
            Map.of(
                    "damage", new ParamInfo("伤害", ParamType.DOUBLE, "闪电造成的伤害"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    EXPLOSION("explosion", "爆炸", "在指定位置引发爆炸",
            "TNT",
            Map.of(
                    "power", 2.0,
                    "fire", false,
                    "break-blocks", false,
                    "location-mode", "forward",
                    "cooldown", 5
            ),
            Map.of(
                    "power", new ParamInfo("爆炸威力", ParamType.DOUBLE, "1=小，4=TNT，10=大"),
                    "fire", new ParamInfo("是否点火", ParamType.BOOLEAN, "爆炸后是否点燃方块"),
                    "break-blocks", new ParamInfo("破坏方块", ParamType.BOOLEAN, "是否破坏周围方块"),
                    "location-mode", new ParamInfo("爆炸位置", ParamType.STRING, "forward=玩家前方，target=目标位置（需HIT类trigger），hit=命中点（需弹射物命中）"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    DAMAGE_BOOST("damage-boost", "伤害加成", "攻击时额外增加伤害",
            "IRON_SWORD",
            Map.of(
                    "bonus", 3.0,
                    "mode", "add"
            ),
            Map.of(
                    "bonus", new ParamInfo("伤害数值", ParamType.DOUBLE, "增加或乘以的伤害值"),
                    "mode", new ParamInfo("计算模式", ParamType.STRING, "add=叠加，multiply=倍率")
            )),

    // ===== 药水效果类 =====
    POTION_SELF("potion-self", "自身药水", "使用时给自己施加药水效果",
            "POTION",
            Map.of(
                    "effect", "SPEED",
                    "amplifier", 1,
                    "duration-seconds", 10,
                    "cooldown", 15
            ),
            Map.of(
                    "effect", new ParamInfo("药水效果", ParamType.STRING, "如 SPEED, JUMP, STRENGTH"),
                    "amplifier", new ParamInfo("等级", ParamType.INTEGER, "从 0 开始，0=I级，1=II级"),
                    "duration-seconds", new ParamInfo("持续时间(秒)", ParamType.INTEGER, "效果持续秒数"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    POTION_HIT("potion-hit", "命中药水", "攻击命中时给目标施加药水效果",
            "LINGERING_POTION",
            Map.of(
                    "effect", "SLOWNESS",
                    "amplifier", 1,
                    "duration-seconds", 5
            ),
            Map.of(
                    "effect", new ParamInfo("药水效果", ParamType.STRING, "如 SLOWNESS, POISON, WITHER"),
                    "amplifier", new ParamInfo("等级", ParamType.INTEGER, "从 0 开始"),
                    "duration-seconds", new ParamInfo("持续时间(秒)", ParamType.INTEGER, "效果持续秒数")
            )),

    // ===== 恢复类 =====
    HEAL("heal", "治疗", "恢复生命值",
            "GOLDEN_APPLE",
            Map.of(
                    "amount", 4.0,
                    "cooldown", 10
            ),
            Map.of(
                    "amount", new ParamInfo("治疗量", ParamType.DOUBLE, "恢复的生命值（1心=2）"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    FEED("feed", "恢复饱食", "恢复饥饿值与饱和度",
            "COOKED_BEEF",
            Map.of(
                    "hunger", 6,
                    "saturation", 8.0f,
                    "cooldown", 5
            ),
            Map.of(
                    "hunger", new ParamInfo("饥饿值", ParamType.INTEGER, "恢复的饥饿值（1鸡腿=2）"),
                    "saturation", new ParamInfo("饱和度", ParamType.DOUBLE, "恢复的饱和度"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    // ===== 视觉/音效类 =====
    PARTICLE("particle", "粒子特效", "在玩家周围生成粒子",
            "BLAZE_POWDER",
            Map.of(
                    "particle", "FLAME",
                    "count", 10,
                    "radius", 1.0,
                    "cooldown", 1
            ),
            Map.of(
                    "particle", new ParamInfo("粒子类型", ParamType.STRING, "如 FLAME, HEART, CRIT, VILLAGER_HAPPY"),
                    "count", new ParamInfo("粒子数量", ParamType.INTEGER, "每次生成的粒子数"),
                    "radius", new ParamInfo("扩散半径", ParamType.DOUBLE, "粒子扩散的范围"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    SOUND("sound", "播放声音", "播放指定的音效",
            "NOTE_BLOCK",
            Map.of(
                    "sound", "entity.player.levelup",
                    "volume", 1.0,
                    "pitch", 1.0,
                    "cooldown", 0
            ),
            Map.of(
                    "sound", new ParamInfo("声音键", ParamType.STRING, "如 entity.player.levelup, block.note_block.chime"),
                    "volume", new ParamInfo("音量", ParamType.DOUBLE, "0.0 ~ 1.0"),
                    "pitch", new ParamInfo("音调", ParamType.DOUBLE, "0.5 ~ 2.0，1.0=正常"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    // ===== 移动类 =====
    TELEPORT("teleport", "传送", "朝视线方向传送一段距离",
            "ENDER_PEARL",
            Map.of(
                    "distance", 10.0,
                    "cooldown", 15
            ),
            Map.of(
                    "distance", new ParamInfo("传送距离", ParamType.DOUBLE, "向前传送的格数"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    KNOCKBACK("knockback", "击退", "攻击命中时将目标击飞",
            "STICK",
            Map.of(
                    "strength", 1.5,
                    "vertical", 0.5
            ),
            Map.of(
                    "strength", new ParamInfo("水平力度", ParamType.DOUBLE, "水平击退力度"),
                    "vertical", new ParamInfo("垂直力度", ParamType.DOUBLE, "垂直击飞高度")
            )),

    SCALE("scale", "体积变化", "改变目标的体积大小（放大或缩小）",
            "PUFFERFISH",
            Map.of(
                    "target", "hit",
                    "scale", 2.0,
                    "duration-seconds", 10,
                    "cooldown", 15
            ),
            Map.of(
                    "target", new ParamInfo("目标", ParamType.STRING, "self=自身，hit=命中目标"),
                    "scale", new ParamInfo("缩放倍数", ParamType.DOUBLE, "1.0=正常，2.0=两倍大，0.5=一半小"),
                    "duration-seconds", new ParamInfo("持续时间(秒)", ParamType.INTEGER, "效果持续秒数，0=永久（直到死亡/重置）"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    // ===== 消耗类 =====
    CONSUME("consume", "消耗物品", "使用后消耗一个物品",
            "CAKE",
            Map.of(
                    "amount", 1
            ),
            Map.of(
                    "amount", new ParamInfo("消耗数量", ParamType.INTEGER, "每次使用消耗多少个")
            )),

    DURABILITY("durability", "耐久消耗", "使用时消耗耐久度",
            "ANVIL",
            Map.of(
                    "amount", 1
            ),
            Map.of(
                    "amount", new ParamInfo("消耗耐久", ParamType.INTEGER, "每次使用消耗多少点耐久")
            )),

    // ===== 特殊类 =====
    FIREBALL("fireball", "发射火球", "朝视线方向发射火球",
            "FIRE_CHARGE",
            Map.of(
                    "yield", 1.0,
                    "cooldown", 5
            ),
            Map.of(
                    "yield", new ParamInfo("爆炸威力", ParamType.DOUBLE, "火球命中后的爆炸威力"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    ARROW("arrow", "发射箭矢", "发射一支箭",
            "ARROW",
            Map.of(
                    "damage", 2.0,
                    "speed", 2.0,
                    "cooldown", 1
            ),
            Map.of(
                    "damage", new ParamInfo("伤害", ParamType.DOUBLE, "箭矢伤害"),
                    "speed", new ParamInfo("速度", ParamType.DOUBLE, "箭矢飞行速度"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    // ===== AOE 范围类 =====
    AOE_DAMAGE("aoe-damage", "范围伤害", "对周围一定范围内的所有实体造成伤害",
            "IRON_AXE",
            Map.of(
                    "radius", 5.0,
                    "damage", 5.0,
                    "cooldown", 8
            ),
            Map.of(
                    "radius", new ParamInfo("范围半径", ParamType.DOUBLE, "以玩家为中心的半径"),
                    "damage", new ParamInfo("伤害", ParamType.DOUBLE, "对每个目标造成的伤害"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    AOE_POTION("aoe-potion", "范围药水", "对周围一定范围内的所有实体施加药水效果",
            "SPLASH_POTION",
            Map.of(
                    "radius", 5.0,
                    "effect", "SLOWNESS",
                    "amplifier", 1,
                    "duration-seconds", 5,
                    "cooldown", 10
            ),
            Map.of(
                    "radius", new ParamInfo("范围半径", ParamType.DOUBLE, "以玩家为中心的半径"),
                    "effect", new ParamInfo("药水效果", ParamType.STRING, "如 SLOWNESS, POISON, WEAKNESS"),
                    "amplifier", new ParamInfo("等级", ParamType.INTEGER, "从 0 开始"),
                    "duration-seconds", new ParamInfo("持续时间(秒)", ParamType.INTEGER, "效果持续秒数"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    // ===== 战斗进阶 =====
    CRITICAL_HIT("critical-hit", "暴击", "攻击时有概率造成额外暴击伤害",
            "IRON_SWORD",
            Map.of(
                    "chance", 20.0,
                    "multiplier", 2.0
            ),
            Map.of(
                    "chance", new ParamInfo("暴击概率(%)", ParamType.DOUBLE, "触发暴击的概率，0-100"),
                    "multiplier", new ParamInfo("伤害倍率", ParamType.DOUBLE, "暴击时伤害乘以的倍数，如 2.0=两倍")
            )),

    VAMPIRIC("vampiric", "吸血", "攻击命中时恢复自身生命值（按伤害百分比）",
            "REDSTONE",
            Map.of(
                    "percent", 30.0,
                    "cooldown", 0
            ),
            Map.of(
                    "percent", new ParamInfo("吸血比例(%)", ParamType.DOUBLE, "按造成伤害的百分比回血"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    FLAME("flame", "燃烧", "攻击命中时点燃目标",
            "FLINT_AND_STEEL",
            Map.of(
                    "seconds", 5,
                    "cooldown", 0
            ),
            Map.of(
                    "seconds", new ParamInfo("燃烧秒数", ParamType.INTEGER, "目标燃烧的秒数"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    LEVITATION("levitation", "漂浮", "让目标漂浮起来（失重）",
            "SHULKER_SHELL",
            Map.of(
                    "target", "hit",
                    "amplifier", 1,
                    "duration-seconds", 3,
                    "cooldown", 10
            ),
            Map.of(
                    "target", new ParamInfo("目标", ParamType.STRING, "self=自身，hit=命中目标"),
                    "amplifier", new ParamInfo("等级", ParamType.INTEGER, "0=慢漂浮，1=快漂浮"),
                    "duration-seconds", new ParamInfo("持续时间(秒)", ParamType.INTEGER, "效果持续秒数"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    // ===== 位移类 =====
    DASH("dash", "冲刺", "朝视线方向快速冲刺一段距离",
            "FEATHER",
            Map.of(
                    "distance", 6.0,
                    "cooldown", 8
            ),
            Map.of(
                    "distance", new ParamInfo("冲刺距离", ParamType.DOUBLE, "向前冲刺的格数"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    ATTRACT("attract", "吸引", "将周围的实体拉向玩家",
            "FISHING_ROD",
            Map.of(
                    "radius", 8.0,
                    "strength", 0.8,
                    "cooldown", 10
            ),
            Map.of(
                    "radius", new ParamInfo("范围半径", ParamType.DOUBLE, "吸引的范围"),
                    "strength", new ParamInfo("拉力强度", ParamType.DOUBLE, "吸引的力量大小"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    REPULSE("repulse", "排斥", "将周围的实体推开",
            "PISTON",
            Map.of(
                    "radius", 5.0,
                    "strength", 1.5,
                    "cooldown", 8
            ),
            Map.of(
                    "radius", new ParamInfo("范围半径", ParamType.DOUBLE, "排斥的范围"),
                    "strength", new ParamInfo("推力强度", ParamType.DOUBLE, "推开的力量大小"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    // ===== 防御类 =====
    SHIELD("shield", "护盾", "获得额外的伤害吸收心（金色心）",
            "SHIELD",
            Map.of(
                    "amount", 4.0,
                    "duration-seconds", 15,
                    "cooldown", 30
            ),
            Map.of(
                    "amount", new ParamInfo("护盾值", ParamType.DOUBLE, "伤害吸收量（1心=2）"),
                    "duration-seconds", new ParamInfo("持续时间(秒)", ParamType.INTEGER, "护盾持续秒数"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            )),

    // ===== 经济类 =====
    ECONOMY_COST("economy-cost", "消耗金钱", "使用时扣除玩家金钱（需 Vault 经济插件）",
            "GOLD_INGOT",
            Map.of(
                    "amount", 100.0,
                    "fail-message", "&c金币不足！"
            ),
            Map.of(
                    "amount", new ParamInfo("消耗金额", ParamType.DOUBLE, "每次使用消耗的金币数"),
                    "fail-message", new ParamInfo("失败提示", ParamType.STRING, "金币不足时显示的消息")
            )),

    // ===== 维修类 =====
    REPAIR("repair", "修复耐久", "使用时恢复物品耐久度，可设置消耗材料",
            "MENDING_BOOK",
            Map.of(
                    "repair-amount", 50,
                    "repair-mode", "add",
                    "cost-type", "none",
                    "cost-id", "",
                    "cost-amount", 1,
                    "cost-location", "inventory",
                    "cooldown", 0
            ),
            Map.of(
                    "repair-amount", new ParamInfo("修复数量", ParamType.INTEGER, "修复的耐久点数或百分比"),
                    "repair-mode", new ParamInfo("修复模式", ParamType.STRING, "add=增加耐久点，percent=按最大耐久百分比恢复"),
                    "cost-type", new ParamInfo("消耗类型", ParamType.STRING, "none=不消耗，material=原版物品，rpgitem=RPG物品"),
                    "cost-id", new ParamInfo("消耗物品ID", ParamType.STRING, "原版材质名（如 DIAMOND）或 RPG 物品 ID"),
                    "cost-amount", new ParamInfo("消耗数量", ParamType.INTEGER, "每次维修消耗的物品数量"),
                    "cost-location", new ParamInfo("消耗位置", ParamType.STRING, "inventory=背包任意位置，offhand=副手，mainhand=主手"),
                    "cooldown", new ParamInfo("冷却时间(秒)", ParamType.INTEGER, "0 表示无冷却")
            ));

    // ============================================================

    private final String id;
    private final String displayName;
    private final String description;
    private final String iconMaterial;
    private final Map<String, Object> defaultParams;
    private final Map<String, ParamInfo> paramInfo;

    PowerType(String id, String displayName, String description, String iconMaterial,
              Map<String, Object> defaultParams, Map<String, ParamInfo> paramInfo) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.iconMaterial = iconMaterial;
        this.defaultParams = new LinkedHashMap<>(defaultParams);
        this.paramInfo = new LinkedHashMap<>(paramInfo);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String iconMaterial() {
        return iconMaterial;
    }

    public Map<String, Object> defaultParams() {
        return new LinkedHashMap<>(defaultParams);
    }

    public Map<String, ParamInfo> paramInfo() {
        return paramInfo;
    }

    /** 根据 id 查找 PowerType */
    public static PowerType byId(String id) {
        for (PowerType type : values()) {
            if (type.id.equalsIgnoreCase(id)) return type;
        }
        return null;
    }

    // ============================================================

    /**
     * 参数元信息：用于 GUI 生成编辑界面。
     */
    public static final class ParamInfo {
        private final String name;
        private final ParamType type;
        private final String description;

        public ParamInfo(String name, ParamType type, String description) {
            this.name = name;
            this.type = type;
            this.description = description;
        }

        public String name() {
            return name;
        }

        public ParamType type() {
            return type;
        }

        public String description() {
            return description;
        }
    }

    /** 参数值类型 */
    public enum ParamType {
        INTEGER, DOUBLE, STRING, BOOLEAN
    }

    // ============================================================

    /**
     * 执行该 Power。
     *
     * @param player  触发者（玩家）
     * @param target  目标实体（可能为 null，例如右键空气时）
     * @param params  Power 参数
     * @param item    触发的 RPG 物品
     * @return true 表示执行成功，false 表示失败（可用于中断后续 Power 链）
     */
    public boolean execute(Player player, LivingEntity target,
                           Location hitLocation, Map<String, Object> params, RPGItem item) {
        return PowerExecutor.execute(this, player, target, hitLocation, params);
    }
}
