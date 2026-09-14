package com.phcraft.rpgforge;

/**
 * Power 触发方式。
 *
 * <p>对应玩家与物品交互的各种事件，每个 Power 可绑定多个 Trigger。
 */
public enum TriggerType {

    RIGHT_CLICK("右键点击", "玩家右键使用物品时触发"),
    LEFT_CLICK("左键点击", "玩家左键点击物品时触发"),
    HIT("攻击命中", "玩家手持物品攻击命中实体时触发"),
    HIT_TAKEN("受到攻击", "玩家持有物品受到攻击时触发"),
    CONSUME("食用/饮用", "玩家食用或饮用物品时触发"),
    TICK("持有刻", "玩家持有物品时每刻触发"),
    EQUIP("装备时", "玩家装备物品时触发"),
    UNEQUIP("卸下时", "玩家卸下物品时触发"),
    SNEAK("潜行", "玩家潜行时触发"),
    SPRINT("疾跑", "玩家疾跑时触发"),
    BREAK_BLOCK("破坏方块", "玩家手持物品破坏方块时触发"),
    PROJECTILE_LAUNCH("发射弹射物", "玩家手持弓等发射弹射物时触发"),
    PROJECTILE_HIT("弹射物命中", "玩家发射的弹射物命中时触发"),
    DEATH("死亡时", "玩家死亡时触发（手持/装备/背包中皆可）"),
    RESPAWN("复活时", "玩家复活时触发");

    private final String displayName;
    private final String description;

    TriggerType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
