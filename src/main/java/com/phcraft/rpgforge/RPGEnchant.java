package com.phcraft.rpgforge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 附魔预设 —— 定义一种可挂到 RPG 物品上的"附魔"。
 *
 * <p>两种类型：
 * <ul>
 *   <li>属性型（attribute）：每级提供固定的属性加成（如 攻击力 +2/级）</li>
 *   <li>能力型（power）：挂载一个 RPGPower 模板，参数可按等级线性缩放</li>
 * </ul>
 *
 * <p>预设存储在 enchantments.yml 中，物品通过 enchantments 映射（附魔id -&gt; 等级）引用。
 */
public class RPGEnchant {

    /** 稀有度 */
    public enum Rarity {
        COMMON("&7", "普通"),
        UNCOMMON("&a", "优秀"),
        RARE("&9", "稀有"),
        EPIC("&5", "史诗"),
        LEGENDARY("&6", "传说");

        private final String color;
        private final String displayName;

        Rarity(String color, String displayName) {
            this.color = color;
            this.displayName = displayName;
        }

        public String color() { return color; }
        public String displayName() { return displayName; }

        public static Rarity parse(String str, Rarity def) {
            if (str == null) return def;
            try {
                return Rarity.valueOf(str.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return def;
            }
        }
    }

    private final String id;
    private String displayName;
    private String description;
    private int maxLevel;
    private Rarity rarity;

    // ===== 属性型 =====
    private String attribute;        // 属性键（如 attack_damage），null 表示非属性型
    private double valuePerLevel;    // 每级加成数值

    // ===== 能力型 =====
    private RPGPower powerTemplate;          // null 表示非能力型
    private Map<String, Double> scalePerLevel; // 参数每级增量

    public RPGEnchant(String id) {
        this.id = id;
        this.displayName = "&f" + id;
        this.description = "";
        this.maxLevel = 3;
        this.rarity = Rarity.COMMON;
        this.attribute = null;
        this.valuePerLevel = 1.0;
        this.powerTemplate = null;
        this.scalePerLevel = new LinkedHashMap<>();
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String description() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int maxLevel() { return maxLevel; }
    public void setMaxLevel(int maxLevel) { this.maxLevel = Math.max(1, maxLevel); }
    public Rarity rarity() { return rarity; }
    public void setRarity(Rarity rarity) { this.rarity = rarity; }

    public boolean isAttributeType() { return attribute != null; }
    public String attribute() { return attribute; }
    public void setAttribute(String attribute) { this.attribute = attribute; }
    public double valuePerLevel() { return valuePerLevel; }
    public void setValuePerLevel(double valuePerLevel) { this.valuePerLevel = valuePerLevel; }

    public boolean isPowerType() { return powerTemplate != null; }
    public RPGPower powerTemplate() { return powerTemplate; }
    public void setPowerTemplate(RPGPower powerTemplate) { this.powerTemplate = powerTemplate; }
    public Map<String, Double> scalePerLevel() { return scalePerLevel; }

    /** 属性型附魔在指定等级下的加成数值 */
    public double attributeValueAt(int level) {
        return valuePerLevel * level;
    }

    /**
     * 能力型附魔生成指定等级的 RPGPower 实例。
     * 缩放参数：最终值 = 基础值 + 每级增量 × (level - 1)。
     *
     * @param indexCache 生成的 Power 的稳定索引（用于冷却键）
     */
    public RPGPower buildPowerAt(int level, int indexCache) {
        if (powerTemplate == null) return null;
        RPGPower power = new RPGPower(powerTemplate.type());
        power.triggers().clear();
        power.triggers().addAll(powerTemplate.triggers());
        for (var entry : powerTemplate.params().entrySet()) {
            Object value = entry.getValue();
            Double scale = scalePerLevel.get(entry.getKey());
            if (scale != null && value instanceof Number n) {
                value = n.doubleValue() + scale * (level - 1);
            }
            power.params().put(entry.getKey(), value);
        }
        power.setFailMessage(powerTemplate.failMessage());
        power.setIndex(indexCache);
        return power;
    }

    /** 罗马数字（等级显示用） */
    public static String roman(int level) {
        if (level <= 0) return "0";
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        int remaining = level;
        for (int i = 0; i < values.length; i++) {
            while (remaining >= values[i]) {
                sb.append(symbols[i]);
                remaining -= values[i];
            }
        }
        return sb.toString();
    }

    /** 附魔在物品 Lore 里的显示行（如 "&c锋利 &7III"） */
    public String loreLine(int level) {
        return rarity.color() + displayName.replace("&f", rarity.color())
                + " &f" + roman(level);
    }

    /** 效果摘要（GUI 卡片用） */
    public List<String> effectSummary() {
        List<String> lines = new ArrayList<>();
        if (isAttributeType()) {
            String attrDisplay = AttributeDef.displayNameOf(attribute);
            lines.add("&7每级：&e" + (valuePerLevel >= 0 ? "+" : "") + valuePerLevel + " " + attrDisplay);
        } else if (isPowerType()) {
            lines.add("&7效果：&e" + powerTemplate.type().displayName());
            for (var entry : scalePerLevel.entrySet()) {
                PowerType.ParamInfo info = powerTemplate.type().paramInfo().get(entry.getKey());
                String name = info != null ? info.name() : entry.getKey();
                lines.add("&7每级：&e" + name + " +" + entry.getValue());
            }
        }
        return lines;
    }

    // ===== 序列化 =====

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("display-name", displayName);
        map.put("description", description);
        map.put("max-level", maxLevel);
        map.put("rarity", rarity.name());
        if (isAttributeType()) {
            map.put("attribute", attribute);
            map.put("value-per-level", valuePerLevel);
        } else if (isPowerType()) {
            Map<String, Object> powerMap = new LinkedHashMap<>();
            powerMap.put("type", powerTemplate.type().id());
            powerMap.put("triggers", powerTemplate.triggers().stream().map(Enum::name).toList());
            powerMap.put("params", new LinkedHashMap<>(powerTemplate.params()));
            if (!scalePerLevel.isEmpty()) {
                powerMap.put("scale-per-level", new LinkedHashMap<>(scalePerLevel));
            }
            map.put("power", powerMap);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static RPGEnchant deserialize(String id, Map<String, Object> map) {
        RPGEnchant enchant = new RPGEnchant(id);
        enchant.displayName = (String) map.getOrDefault("display-name", "&f" + id);
        enchant.description = (String) map.getOrDefault("description", "");
        enchant.maxLevel = ((Number) map.getOrDefault("max-level", 3)).intValue();
        enchant.rarity = Rarity.parse((String) map.get("rarity"), Rarity.COMMON);

        Object attrObj = map.get("attribute");
        if (attrObj != null) {
            enchant.attribute = attrObj.toString();
            enchant.valuePerLevel = ((Number) map.getOrDefault("value-per-level", 1.0)).doubleValue();
            return enchant;
        }

        Object powerObj = map.get("power");
        if (powerObj instanceof Map<?, ?> pm) {
            String typeId = String.valueOf(pm.get("type"));
            PowerType type = PowerType.byId(typeId);
            if (type == null) return null;
            RPGPower template = new RPGPower(type);
            template.triggers().clear();
            if (pm.get("triggers") instanceof List<?> tl) {
                for (Object o : tl) {
                    try {
                        template.triggers().add(TriggerType.valueOf(o.toString()));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            if (template.triggers().isEmpty()) {
                template.triggers().add(TriggerType.HIT);
            }
            if (pm.get("params") instanceof Map<?, ?> params) {
                for (var entry : params.entrySet()) {
                    template.params().put(entry.getKey().toString(), entry.getValue());
                }
            }
            enchant.powerTemplate = template;
            if (pm.get("scale-per-level") instanceof Map<?, ?> scale) {
                for (var entry : scale.entrySet()) {
                    if (entry.getValue() instanceof Number n) {
                        enchant.scalePerLevel.put(entry.getKey().toString(), n.doubleValue());
                    }
                }
            }
        }
        return enchant;
    }

    // ===== 支持的属性定义 =====

    /**
     * RPGForge 支持的物品属性（编辑界面与附魔共用）。
     * slotGroup 决定属性在哪个装备槽位生效。
     */
    public enum AttributeDef {
        ATTACK_DAMAGE("attack_damage", "攻击伤害", org.bukkit.inventory.EquipmentSlotGroup.MAINHAND),
        ATTACK_SPEED("attack_speed", "攻击速度", org.bukkit.inventory.EquipmentSlotGroup.MAINHAND),
        ARMOR("armor", "护甲", org.bukkit.inventory.EquipmentSlotGroup.ARMOR),
        ARMOR_TOUGHNESS("armor_toughness", "护甲韧性", org.bukkit.inventory.EquipmentSlotGroup.ARMOR),
        KNOCKBACK_RESISTANCE("knockback_resistance", "击退抗性", org.bukkit.inventory.EquipmentSlotGroup.ARMOR),
        MAX_HEALTH("max_health", "最大生命", org.bukkit.inventory.EquipmentSlotGroup.ANY),
        MOVEMENT_SPEED("movement_speed", "移动速度", org.bukkit.inventory.EquipmentSlotGroup.ANY);

        private final String key;
        private final String displayName;
        private final org.bukkit.inventory.EquipmentSlotGroup slotGroup;

        AttributeDef(String key, String displayName, org.bukkit.inventory.EquipmentSlotGroup slotGroup) {
            this.key = key;
            this.displayName = displayName;
            this.slotGroup = slotGroup;
        }

        public String key() { return key; }
        public String displayName() { return displayName; }
        public org.bukkit.inventory.EquipmentSlotGroup slotGroup() { return slotGroup; }

        public static AttributeDef byKey(String key) {
            for (AttributeDef def : values()) {
                if (def.key.equalsIgnoreCase(key)) return def;
            }
            return null;
        }

        public static String displayNameOf(String key) {
            AttributeDef def = byKey(key);
            return def != null ? def.displayName() : key;
        }

        /** 解析为 Bukkit Attribute（兼容新旧属性名：1.21.3+ 无前缀，旧版 generic. 前缀） */
        public org.bukkit.attribute.Attribute bukkitAttribute() {
            var registry = org.bukkit.Registry.ATTRIBUTE;
            org.bukkit.attribute.Attribute attr = registry.get(org.bukkit.NamespacedKey.minecraft(key));
            if (attr == null) {
                attr = registry.get(org.bukkit.NamespacedKey.minecraft("generic." + key));
            }
            return attr;
        }
    }
}
