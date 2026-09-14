package com.phcraft.rpgforge;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * 条件类型 —— Power 执行前的检查条件。
 * 所有条件返回 true 才能执行 Power，失败则触发失败消息。
 */
public enum ConditionType {

    // ===== 概率类 =====
    CHANCE("chance", "概率", "按随机概率触发",
            Map.of("value", 50.0),
            Map.of("value", "概率百分比（0-100）")),

    // ===== 玩家状态 =====
    HEALTH_ABOVE("health-above", "血量高于", "玩家血量高于指定值时触发",
            Map.of("value", 10.0),
            Map.of("value", "血量下限（心数x2）")),

    HEALTH_BELOW("health-below", "血量低于", "玩家血量低于指定值时触发",
            Map.of("value", 10.0),
            Map.of("value", "血量大上限（心数x2）")),

    FOOD_ABOVE("food-above", "饱食度高于", "玩家饱食度高于指定值",
            Map.of("value", 15),
            Map.of("value", "饱食度下限（0-20）")),

    FOOD_BELOW("food-below", "饱食度低于", "玩家饱食度低于指定值",
            Map.of("value", 10),
            Map.of("value", "饱食度上限（0-20）")),

    LEVEL_ABOVE("level-above", "经验等级高于", "玩家经验等级高于指定值",
            Map.of("value", 10),
            Map.of("value", "等级下限")),

    // ===== 物品 =====
    DURABILITY_ABOVE("durability-above", "耐久高于", "物品剩余耐久高于指定值",
            Map.of("value", 10),
            Map.of("value", "耐久下限（绝对值）")),

    DURABILITY_BELOW("durability-below", "耐久低于", "物品剩余耐久低于指定值",
            Map.of("value", 50),
            Map.of("value", "耐久上限（绝对值）")),

    // ===== 环境 =====
    IS_DAY("is-day", "白天", "仅在白天触发",
            Map.of(),
            Map.of()),

    IS_NIGHT("is-night", "夜晚", "仅在夜晚触发",
            Map.of(),
            Map.of()),

    IS_RAINING("is-raining", "下雨", "仅在下雨时触发",
            Map.of(),
            Map.of()),

    // ===== 权限/计分板 =====
    PERMISSION("permission", "权限节点", "玩家需要拥有指定权限",
            Map.of("value", "rpgforge.use"),
            Map.of("value", "权限节点")),

    SCOREBOARD_ABOVE("scoreboard-above", "计分板高于", "计分板分数高于指定值",
            Map.of("objective", "kills", "value", 10),
            Map.of("objective", "计分板目标名", "value", "分数下限")),

    // ===== 目标 =====
    TARGET_LIVING("target-living", "目标为生物", "被命中的必须是生物实体",
            Map.of(),
            Map.of()),

    TARGET_PLAYER("target-player", "目标为玩家", "被命中的必须是玩家",
            Map.of(),
            Map.of()),

    TARGET_MOB("target-mob", "目标为怪物", "被命中的必须是非玩家怪物",
            Map.of(),
            Map.of()),

    // ===== 逻辑组合 =====
    AND("and", "全部满足（且）", "所有子条件都必须满足",
            Map.of(),
            Map.of()),

    OR("or", "任一满足（或）", "任一子条件满足即可",
            Map.of(),
            Map.of());

    private final String id;
    private final String name;
    private final String description;
    private final Map<String, Object> defaultParams;
    private final Map<String, String> paramDescriptions;

    ConditionType(String id, String name, String description,
                  Map<String, Object> defaultParams,
                  Map<String, String> paramDescriptions) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.defaultParams = defaultParams;
        this.paramDescriptions = paramDescriptions;
    }

    public String id() { return id; }
    public String getName() { return name; }
    public String description() { return description; }
    public Map<String, Object> defaultParams() { return defaultParams; }
    public Map<String, String> paramDescriptions() { return paramDescriptions; }

    /**
     * 检查条件是否满足。
     * @param player 玩家（使用物品者）
     * @param target 目标实体（可能为 null）
     * @param itemDurability 物品当前耐久（-1 表示无耐久概念）
     * @param params 条件参数
     * @return true = 满足条件，可以执行 Power
     */
    public boolean check(Player player, LivingEntity target, int itemDurability,
                         Map<String, Object> params) {
        try {
            return switch (this) {
                case CHANCE -> Math.random() * 100 < getDouble(params, "value", 50.0);
                case HEALTH_ABOVE -> player.getHealth() > getDouble(params, "value", 10.0);
                case HEALTH_BELOW -> player.getHealth() < getDouble(params, "value", 10.0);
                case FOOD_ABOVE -> player.getFoodLevel() > getInt(params, "value", 15);
                case FOOD_BELOW -> player.getFoodLevel() < getInt(params, "value", 10);
                case LEVEL_ABOVE -> player.getLevel() >= getInt(params, "value", 10);
                case DURABILITY_ABOVE -> itemDurability < 0 || itemDurability > getInt(params, "value", 10);
                case DURABILITY_BELOW -> itemDurability < 0 || itemDurability < getInt(params, "value", 50);
                case IS_DAY -> player.getWorld().getTime() < 12000 || player.getWorld().getTime() > 23999;
                case IS_NIGHT -> player.getWorld().getTime() >= 13000 && player.getWorld().getTime() <= 23000;
                case IS_RAINING -> player.getWorld().hasStorm();
                case PERMISSION -> player.hasPermission(getStr(params, "value", ""));
                case SCOREBOARD_ABOVE -> {
                    var obj = player.getScoreboard().getObjective(getStr(params, "objective", ""));
                    yield obj != null && obj.getScore(player.getName()).getScore() > getInt(params, "value", 0);
                }
                case TARGET_LIVING -> target != null;
                case TARGET_PLAYER -> target instanceof Player;
                case TARGET_MOB -> target != null && !(target instanceof Player);
                case AND, OR -> true; // 逻辑组合由上层处理
            };
        } catch (Exception e) {
            return false; // 条件检查出错，默认不满足
        }
    }

    private double getDouble(Map<String, Object> params, String key, double def) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return def;
    }

    private int getInt(Map<String, Object> params, String key, int def) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.intValue();
        return def;
    }

    private String getStr(Map<String, Object> params, String key, String def) {
        Object v = params.get(key);
        return v != null ? v.toString() : def;
    }

    /** 根据 id 查找类型 */
    public static ConditionType byId(String id) {
        for (ConditionType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }
}
