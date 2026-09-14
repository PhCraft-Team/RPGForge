package com.phcraft.rpgforge;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RPG 物品定义 —— 包含基础属性和一组 Power。
 *
 * <p>每个物品有唯一 id（字符串标识），数据存储在 items/ 目录下的 YAML 文件中。
 * 运行时物品通过 PDC (PersistentDataContainer) 标记 rpgforge:item-id 识别。
 */
public class RPGItem {

    /** 耐久显示类型 */
    public enum DurabilityDisplay {
        /** 默认（原版耐久条） */
        DEFAULT,
        /** 进度条（Lore 里显示，如「耐久：■■■■□□」） */
        BAR,
        /** 数字（Lore 里显示，如「耐久：156/250」） */
        NUMERIC,
        /** 隐藏（不显示耐久信息） */
        HIDDEN
    }

    private final String id;
    private String displayName;
    private Material material;
    private List<String> lore;
    private int customModelData;
    private boolean unbreakable;
    private int maxDurability;
    private DurabilityDisplay durabilityDisplay;
    private String barColor;
    private int barLength;
    private List<RPGPower> powers;

    public RPGItem(String id) {
        this.id = id;
        this.displayName = "&f" + id;
        this.material = Material.IRON_SWORD;
        this.lore = new ArrayList<>();
        this.customModelData = 0;
        this.unbreakable = false;
        this.maxDurability = 0;
        this.durabilityDisplay = DurabilityDisplay.DEFAULT;
        this.barColor = "&a";
        this.barLength = 10;
        this.powers = new ArrayList<>();
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Material material() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public List<String> lore() {
        return lore;
    }

    public void setLore(List<String> lore) {
        this.lore = new ArrayList<>(lore);
    }

    public int customModelData() {
        return customModelData;
    }

    public void setCustomModelData(int customModelData) {
        this.customModelData = customModelData;
    }

    public boolean unbreakable() {
        return unbreakable;
    }

    public void setUnbreakable(boolean unbreakable) {
        this.unbreakable = unbreakable;
    }

    public int maxDurability() {
        return maxDurability;
    }

    public void setMaxDurability(int maxDurability) {
        this.maxDurability = maxDurability;
    }

    public DurabilityDisplay durabilityDisplay() {
        return durabilityDisplay;
    }

    public void setDurabilityDisplay(DurabilityDisplay durabilityDisplay) {
        this.durabilityDisplay = durabilityDisplay;
    }

    public String barColor() {
        return barColor;
    }

    public void setBarColor(String barColor) {
        this.barColor = barColor;
    }

    public int barLength() {
        return barLength;
    }

    public void setBarLength(int barLength) {
        this.barLength = barLength;
    }

    /**
     * 获取物品的有效最大耐久值。
     * 如果设置了 maxDurability（>0）则使用该值，否则使用材质自身的最大耐久。
     */
    public int getEffectiveMaxDurability() {
        if (maxDurability > 0) {
            return maxDurability;
        }
        return material.getMaxDurability();
    }

    public List<RPGPower> powers() {
        return powers;
    }

    public void setPowers(List<RPGPower> powers) {
        this.powers = new ArrayList<>(powers);
        for (int i = 0; i < this.powers.size(); i++) {
            this.powers.get(i).setIndex(i);
        }
    }

    public void addPower(RPGPower power) {
        power.setIndex(powers.size());
        powers.add(power);
    }

    public void removePower(int index) {
        if (index >= 0 && index < powers.size()) {
            powers.remove(index);
            for (int i = index; i < powers.size(); i++) {
                powers.get(i).setIndex(i);
            }
        }
    }

    /** 生成一个可直接给玩家的 ItemStack */
    public ItemStack buildItemStack() {
        return buildItemStack(1);
    }

    public ItemStack buildItemStack(int amount) {
        ItemStack item = new ItemStack(material, amount);

        // 使用 Paper 推荐的 editMeta 方式（兼容 1.21+ 数据组件系统）
        item.editMeta(meta -> {
            meta.setDisplayName(RPGForgePlugin.cc(displayName));

            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(RPGForgePlugin.cc(line));
            }
            // 附加 Power 信息（玩家可见）
            if (!powers.isEmpty()) {
                coloredLore.add("");
                coloredLore.add(RPGForgePlugin.cc("&7能力 &e" + powers.size() + " 个"));
                for (RPGPower p : powers) {
                    coloredLore.add(RPGForgePlugin.cc(" &8▪ &f" + p.type().displayName()));
                }
            }

            // 附加耐久显示信息（放在 Lore 末尾）
            int effectiveMax = getEffectiveMaxDurability();
            if (effectiveMax > 0 && durabilityDisplay != DurabilityDisplay.DEFAULT
                    && durabilityDisplay != DurabilityDisplay.HIDDEN) {
                int currentDurability;
                if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                    currentDurability = effectiveMax - damageable.getDamage();
                } else {
                    currentDurability = effectiveMax;
                }
                coloredLore.add("");
                coloredLore.add(buildDurabilityLoreLine(currentDurability, effectiveMax));
            }
            meta.setLore(coloredLore);

            // CustomModelData（关键：必须显式设置，即使是 0 也设一次确保组件存在）
            meta.setCustomModelData(customModelData);

            if (unbreakable) {
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            }
            if (maxDurability > 0 && meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                damageable.setMaxDamage(maxDurability);
            }

            // PDC 标记：RPGForge 物品 ID
            NamespacedKey key = RPGForgePlugin.itemIdKey();
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        });

        return item;
    }

    /**
     * 根据耐久显示类型生成耐久 Lore 行。
     *
     * @param current 当前耐久值
     * @param max     最大耐久值
     * @return 带颜色代码的耐久显示字符串
     */
    private String buildDurabilityLoreLine(int current, int max) {
        if (durabilityDisplay == DurabilityDisplay.BAR) {
            int filled = (int) Math.round((double) current / max * barLength);
            filled = Math.max(0, Math.min(barLength, filled));
            StringBuilder bar = new StringBuilder();
            bar.append(RPGForgePlugin.cc(barColor));
            for (int i = 0; i < filled; i++) {
                bar.append("■");
            }
            bar.append(RPGForgePlugin.cc("&7"));
            for (int i = filled; i < barLength; i++) {
                bar.append("□");
            }
            return RPGForgePlugin.cc("&7耐久：") + bar;
        } else if (durabilityDisplay == DurabilityDisplay.NUMERIC) {
            return RPGForgePlugin.cc("&7耐久：&f" + current + "&7/&f" + max);
        }
        return "";
    }

    /**
     * 更新已有物品的耐久 Lore 行（用于维修后刷新显示等场景）。
     * 如果物品不是 RPG 物品或不需要显示耐久，则不做修改。
     *
     * @param item  要更新的物品
     * @param rpgId 物品的 RPG ID（若为 null 则尝试从 PDC 读取）
     */
    public static void updateDurabilityLore(ItemStack item, String rpgId) {
        if (item == null || !item.hasItemMeta()) return;
        if (rpgId == null) {
            rpgId = readItemId(item);
        }
        if (rpgId == null) return;
        RPGItem rpgItem = RPGForgePlugin.instance().registry().get(rpgId);
        if (rpgItem == null) return;

        int effectiveMax = rpgItem.getEffectiveMaxDurability();
        if (effectiveMax <= 0) return;
        if (rpgItem.durabilityDisplay == DurabilityDisplay.DEFAULT
                || rpgItem.durabilityDisplay == DurabilityDisplay.HIDDEN) {
            return;
        }

        item.editMeta(meta -> {
            List<String> currentLore = meta.getLore();
            if (currentLore == null) currentLore = new ArrayList<>();

            // 找到旧的耐久行及其前导空行并移除
            List<String> newLore = new ArrayList<>();
            boolean lastWasEmpty = false;
            for (int i = 0; i < currentLore.size(); i++) {
                String line = currentLore.get(i);
                String stripped = ChatColor.stripColor(line);
                if (stripped.startsWith("耐久：")) {
                    // 移除这一行，同时如果上一行是空行也移除
                    if (!newLore.isEmpty() && lastWasEmpty) {
                        newLore.remove(newLore.size() - 1);
                    }
                    lastWasEmpty = false;
                } else {
                    newLore.add(line);
                    lastWasEmpty = line.isEmpty();
                }
            }

            // 计算当前耐久
            int currentDurability = effectiveMax;
            if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                currentDurability = effectiveMax - damageable.getDamage();
            }

            // 添加新的耐久行（放在末尾，前面加空行）
            newLore.add("");
            newLore.add(rpgItem.buildDurabilityLoreLine(currentDurability, effectiveMax));

            meta.setLore(newLore);
        });
    }

    /** 判断一个 ItemStack 是否是 RPGForge 物品，返回其 id（或 null） */
    public static String readItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var meta = item.getItemMeta();
        if (meta == null) return null;
        var pdc = meta.getPersistentDataContainer();
        NamespacedKey key = RPGForgePlugin.itemIdKey();
        return pdc.get(key, PersistentDataType.STRING);
    }

    // ===== 序列化 / 反序列化 =====

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("display-name", displayName);
        map.put("material", material.name());
        map.put("lore", new ArrayList<>(lore));
        map.put("custom-model-data", customModelData);
        map.put("unbreakable", unbreakable);
        map.put("max-durability", maxDurability);
        map.put("durability-display", durabilityDisplay.name());
        map.put("bar-color", barColor);
        map.put("bar-length", barLength);

        List<Map<String, Object>> powersList = new ArrayList<>();
        for (RPGPower p : powers) {
            powersList.add(p.serialize());
        }
        map.put("powers", powersList);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static RPGItem deserialize(Map<String, Object> map) {
        String id = (String) map.get("id");
        if (id == null) return null;

        RPGItem item = new RPGItem(id);
        item.displayName = (String) map.getOrDefault("display-name", "&f" + id);
        String matName = (String) map.getOrDefault("material", "IRON_SWORD");
        try {
            item.material = Material.valueOf(matName);
        } catch (IllegalArgumentException e) {
            item.material = Material.IRON_SWORD;
        }
        Object loreObj = map.get("lore");
        if (loreObj instanceof List<?> list) {
            for (Object o : list) {
                item.lore.add(o.toString());
            }
        }
        item.customModelData = ((Number) map.getOrDefault("custom-model-data", 0)).intValue();
        item.unbreakable = (Boolean) map.getOrDefault("unbreakable", false);
        item.maxDurability = ((Number) map.getOrDefault("max-durability", 0)).intValue();

        String displayStr = (String) map.get("durability-display");
        if (displayStr != null) {
            try {
                item.durabilityDisplay = DurabilityDisplay.valueOf(displayStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                item.durabilityDisplay = DurabilityDisplay.DEFAULT;
            }
        } else {
            item.durabilityDisplay = DurabilityDisplay.DEFAULT;
        }
        item.barColor = (String) map.getOrDefault("bar-color", "&a");
        item.barLength = ((Number) map.getOrDefault("bar-length", 10)).intValue();

        Object powersObj = map.get("powers");
        if (powersObj instanceof List<?> list) {
            item.powers = RPGPower.deserializeList((List<?>) powersObj);
        }
        return item;
    }
}
