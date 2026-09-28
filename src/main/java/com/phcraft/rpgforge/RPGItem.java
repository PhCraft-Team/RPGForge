package com.phcraft.rpgforge;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
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

    /** item_model 组件（如 "majestica:soul_devourer"），null = 未设置 */
    private String itemModel;
    /** equippable 组件的装备模型资产（如 "ani:angel"），null = 不设置；穿上身后客户端加载 assets/<ns>/equipment/<name>.json */
    private String equipmentAsset;
    /** equippable 槽位（HEAD/CHEST/LEGS/FEET），null = 按材质自动推断 */
    private String equipmentSlot;
    /** 手动属性加成（属性键 -> 数值），添加后覆盖原版物品默认属性 */
    private final Map<String, Double> attributes = new LinkedHashMap<>();
    /** 物品附魔（附魔id -> 等级） */
    private final Map<String, Integer> enchantments = new LinkedHashMap<>();
    /** 附魔展开后的能力缓存（索引从 ENCHANT_INDEX_BASE 起，冷却键稳定） */
    private List<RPGPower> enchantPowers = new ArrayList<>();

    // ===== 食物属性（1.21+ Food/Consumable 组件），nutrition > 0 即启用 =====
    private int foodNutrition;
    private float foodSaturation;
    private boolean foodCanAlwaysEat;

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

    // ===== 物品模型 / 属性 / 附魔 =====

    public String itemModel() {
        return itemModel;
    }

    public void setItemModel(String itemModel) {
        this.itemModel = (itemModel == null || itemModel.isBlank()) ? null : itemModel;
    }

    public String equipmentAsset() {
        return equipmentAsset;
    }

    public void setEquipmentAsset(String equipmentAsset) {
        this.equipmentAsset = (equipmentAsset == null || equipmentAsset.isBlank()) ? null : equipmentAsset;
    }

    public String equipmentSlot() {
        return equipmentSlot;
    }

    public void setEquipmentSlot(String equipmentSlot) {
        this.equipmentSlot = (equipmentSlot == null || equipmentSlot.isBlank())
                ? null : equipmentSlot.trim().toUpperCase();
    }

    /**
     * 解析 equippable 槽位：显式设置优先，否则按材质名后缀推断（非盔甲材质返回 null）。
     */
    EquipmentSlot resolveEquipmentSlot() {
        if (equipmentSlot != null) {
            try {
                return EquipmentSlot.valueOf(equipmentSlot);
            } catch (IllegalArgumentException ignored) {
            }
        }
        String n = material.name();
        if (n.endsWith("_HELMET")) return EquipmentSlot.HEAD;
        if (n.endsWith("_CHESTPLATE")) return EquipmentSlot.CHEST;
        if (n.endsWith("_LEGGINGS")) return EquipmentSlot.LEGS;
        if (n.endsWith("_BOOTS")) return EquipmentSlot.FEET;
        if (n.equals("ELYTRA")) return EquipmentSlot.CHEST;
        return null;
    }

    public Map<String, Double> attributes() {
        return attributes;
    }

    public Map<String, Integer> enchantments() {
        return enchantments;
    }

    // ===== 食物属性 =====

    /** 是否为可食用物品（nutrition > 0） */
    public boolean isFood() {
        return foodNutrition > 0;
    }

    public int foodNutrition() {
        return foodNutrition;
    }

    public void setFoodNutrition(int nutrition) {
        this.foodNutrition = Math.max(0, nutrition);
    }

    public float foodSaturation() {
        return foodSaturation;
    }

    public void setFoodSaturation(float saturation) {
        this.foodSaturation = Math.max(0f, saturation);
    }

    public boolean foodCanAlwaysEat() {
        return foodCanAlwaysEat;
    }

    public void setFoodCanAlwaysEat(boolean canAlwaysEat) {
        this.foodCanAlwaysEat = canAlwaysEat;
    }

    /** 关闭食物功能 */
    public void clearFood() {
        this.foodNutrition = 0;
        this.foodSaturation = 0f;
        this.foodCanAlwaysEat = false;
    }

    public List<RPGPower> enchantPowers() {
        return enchantPowers;
    }

    /** 本体 Power + 附魔展开 Power 的合并视图（触发监听器使用） */
    public List<RPGPower> effectivePowers() {
        List<RPGPower> all = new ArrayList<>(powers);
        all.addAll(enchantPowers);
        return all;
    }

    /**
     * 根据当前附魔等级重建附魔 Power 缓存。
     * 索引从 {@link EnchantRegistry#ENCHANT_INDEX_BASE} 起，保证冷却键与本体 Power 不冲突。
     * 在附魔注册表加载完成、或物品附魔变更后调用。
     */
    public void refreshEnchantPowers() {
        List<RPGPower> list = new ArrayList<>();
        RPGForgePlugin plugin = RPGForgePlugin.instance();
        if (plugin != null && plugin.enchantRegistry() != null && !enchantments.isEmpty()) {
            int idx = EnchantRegistry.ENCHANT_INDEX_BASE;
            for (var entry : enchantments.entrySet()) {
                RPGEnchant enchant = plugin.enchantRegistry().get(entry.getKey());
                if (enchant == null || !enchant.isPowerType()) continue;
                RPGPower power = enchant.buildPowerAt(entry.getValue(), idx++);
                if (power != null) list.add(power);
            }
        }
        this.enchantPowers = list;
    }

    /** 手动属性 + 属性型附魔的合计值（属性键 -> 总数值） */
    public Map<String, Double> effectiveAttributes() {
        Map<String, Double> merged = new LinkedHashMap<>(attributes);
        RPGForgePlugin plugin = RPGForgePlugin.instance();
        if (plugin != null && plugin.enchantRegistry() != null) {
            for (var entry : enchantments.entrySet()) {
                RPGEnchant enchant = plugin.enchantRegistry().get(entry.getKey());
                if (enchant == null || !enchant.isAttributeType()) continue;
                merged.merge(enchant.attribute(), enchant.attributeValueAt(entry.getValue()), Double::sum);
            }
        }
        return merged;
    }

    /** Bukkit Attribute 反查 RPGForge 属性键（不属于支持属性时返回 null） */
    static String attributeKeyOf(Attribute attr) {
        for (RPGEnchant.AttributeDef def : RPGEnchant.AttributeDef.values()) {
            if (attr.equals(def.bukkitAttribute())) return def.key();
        }
        return null;
    }

    /** 生成一个可直接给玩家的 ItemStack */
    public ItemStack buildItemStack() {
        return buildItemStack(1);
    }

    public ItemStack buildItemStack(int amount) {
        ItemStack item = new ItemStack(material, amount);

        // 使用 Paper 推荐的 editMeta 方式（兼容 1.21+ 数据组件系统）
        item.editMeta(meta -> {
            // 纯文本名直接构造 literal 组件：NBT 形式与铁砧改名一致（"名"字符串简写），
            // 可被 1.21.5+ 资源包的 component select 按名字精确匹配切换模型；
            // Bukkit legacy 转换会生成 [空父组件, 文本] 结构，导致匹配失败。
            // 带颜色码的名字保持 legacy 路径（渲染与旧版一致，反正纯文本 when 也匹配不上）。
            if (displayName.indexOf('&') >= 0 || displayName.indexOf('§') >= 0) {
                meta.setDisplayName(RPGForgePlugin.cc(displayName));
            } else {
                meta.displayName(Component.text(displayName));
            }

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

            // 附加属性信息（手动属性 + 属性型附魔的合计值）
            Map<String, Double> attrTotal = effectiveAttributes();
            // 攻速钳制：玩家基础攻速 4.0，总攻速 ≤0 时蓄力条永不回复（如配置 -5 → 总量 -1 卡死攻击条）。
            // 修饰值下限 -3.8（总量 0.2，与原版重锤核心同级，约 5 秒蓄满一次）。
            Double speedVal = attrTotal.get("attack_speed");
            if (speedVal != null && speedVal < -3.8) {
                attrTotal.put("attack_speed", -3.8);
            }
            if (!attrTotal.isEmpty()) {
                coloredLore.add("");
                for (var entry : attrTotal.entrySet()) {
                    double v = entry.getValue();
                    String num = (v == Math.floor(v) && !Double.isInfinite(v))
                            ? String.valueOf((long) v) : String.valueOf(v);
                    coloredLore.add(RPGForgePlugin.cc(" &8▪ &7"
                            + RPGEnchant.AttributeDef.displayNameOf(entry.getKey())
                            + " &8+ &e" + num));
                }
            }

            // 附加附魔信息
            if (!enchantments.isEmpty()) {
                coloredLore.add("");
                coloredLore.add(RPGForgePlugin.cc("&7附魔 &e" + enchantments.size() + " 个"));
                for (var entry : enchantments.entrySet()) {
                    RPGEnchant enchant = RPGForgePlugin.instance().enchantRegistry().get(entry.getKey());
                    if (enchant == null) continue;
                    coloredLore.add(RPGForgePlugin.cc(" &8▪ " + enchant.loreLine(entry.getValue())));
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

            // item_model 组件（1.21.4+，指向资源包物品模型定义，如 "majestica:soul_devourer"）
            if (itemModel != null && !itemModel.isBlank()) {
                NamespacedKey modelKey = NamespacedKey.fromString(itemModel);
                if (modelKey != null) {
                    meta.setItemModel(modelKey);
                }
            }

            // equippable 组件（1.21.2+ 穿戴模型）：穿上身后客户端按装备资产渲染 3D 外观
            if (equipmentAsset != null && !equipmentAsset.isBlank()) {
                NamespacedKey assetKey = NamespacedKey.fromString(equipmentAsset);
                EquipmentSlot slot = resolveEquipmentSlot();
                if (assetKey != null && slot != null) {
                    EquippableComponent eq = meta.getEquippable();
                    eq.setSlot(slot);
                    eq.setModel(assetKey);
                    meta.setEquippable(eq);
                }
            }

            // 食物组件（1.21+）：营养/饱和/随时可吃
            if (isFood()) {
                FoodComponent food = meta.getFood();
                food.setNutrition(foodNutrition);
                food.setSaturation(foodSaturation);
                food.setCanAlwaysEat(foodCanAlwaysEat);
                meta.setFood(food);
            }

            // 发动冷却组：附魔冷却条只作用于本 RPG 物品（而非同材质的所有物品）
            // cooldown_seconds 要求严格正数；0.05s = 1 tick，自身的使用后冷却不可感知
            UseCooldownComponent cooldown = meta.getUseCooldown();
            cooldown.setCooldownSeconds(0.05f);
            cooldown.setCooldownGroup(new NamespacedKey(RPGForgePlugin.instance(), "item/" + id));
            meta.setUseCooldown(cooldown);

            if (unbreakable) {
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            }
            if (maxDurability > 0 && meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                damageable.setMaxDamage(maxDurability);
            }

            // 属性修饰符：手动属性 + 属性型附魔的合计值。
            // 配置过的属性完全覆盖原版默认；未配置的原版默认（如剑的攻速惩罚）保留。
            if (!attrTotal.isEmpty()) {
                Multimap<Attribute, AttributeModifier> mm = LinkedHashMultimap.create();
                ItemMeta probeMeta = new ItemStack(material).getItemMeta();
                Multimap<Attribute, AttributeModifier> defaults =
                        probeMeta != null ? probeMeta.getAttributeModifiers() : null;
                if (defaults != null) {
                    for (var e : defaults.entries()) {
                        String keyOf = attributeKeyOf(e.getKey());
                        if (keyOf == null || !attrTotal.containsKey(keyOf)) {
                            mm.put(e.getKey(), e.getValue());
                        }
                    }
                }
                for (var entry : attrTotal.entrySet()) {
                    RPGEnchant.AttributeDef def = RPGEnchant.AttributeDef.byKey(entry.getKey());
                    if (def == null) continue;
                    Attribute attr = def.bukkitAttribute();
                    if (attr == null) continue;
                    // key 带物品 ID：AttributeInstance 按 key 存装备修改器，多件装备共用同一 key 会互相覆盖
                    // （表现为两件「生命V」防具只加一件的量）；带上物品 ID 后各自独立、正常叠加。
                    mm.put(attr, new AttributeModifier(
                            new NamespacedKey(RPGForgePlugin.instance(), id + "/" + def.key()),
                            entry.getValue(),
                            AttributeModifier.Operation.ADD_NUMBER,
                            def.slotGroup()));
                }
                meta.setAttributeModifiers(mm);
            }

            // PDC 标记：RPGForge 物品 ID
            NamespacedKey key = RPGForgePlugin.itemIdKey();
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        });

        // 进食组件（1.21.2+）：与原版一致的进食动画/音效/粒子/时长（1.6 秒）
        if (isFood()) {
            item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                    .animation(ItemUseAnimation.EAT)
                    .consumeSeconds(1.6f)
                    .hasConsumeParticles(true)
                    .sound(Key.key("minecraft", "entity.generic.eat"))
                    .build());
        }

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

        if (itemModel != null) {
            map.put("item-model", itemModel);
        }
        if (equipmentAsset != null) {
            map.put("equipment-asset", equipmentAsset);
            if (equipmentSlot != null) {
                map.put("equipment-slot", equipmentSlot);
            }
        }
        if (!attributes.isEmpty()) {
            map.put("attributes", new LinkedHashMap<>(attributes));
        }
        if (!enchantments.isEmpty()) {
            map.put("enchantments", new LinkedHashMap<>(enchantments));
        }
        if (isFood()) {
            Map<String, Object> food = new LinkedHashMap<>();
            food.put("nutrition", foodNutrition);
            food.put("saturation", foodSaturation);
            food.put("can-always-eat", foodCanAlwaysEat);
            map.put("food", food);
        }

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

        Object modelObj = map.get("item-model");
        if (modelObj != null && !modelObj.toString().isBlank()) {
            item.itemModel = modelObj.toString();
        }
        Object eqAssetObj = map.get("equipment-asset");
        if (eqAssetObj != null && !eqAssetObj.toString().isBlank()) {
            item.equipmentAsset = eqAssetObj.toString();
            Object eqSlotObj = map.get("equipment-slot");
            if (eqSlotObj != null && !eqSlotObj.toString().isBlank()) {
                item.equipmentSlot = eqSlotObj.toString().trim().toUpperCase();
            }
        }
        Object attrObj = map.get("attributes");
        if (attrObj instanceof Map<?, ?> am) {
            for (var e : am.entrySet()) {
                if (e.getValue() instanceof Number n
                        && RPGEnchant.AttributeDef.byKey(e.getKey().toString()) != null) {
                    item.attributes.put(e.getKey().toString().toLowerCase(), n.doubleValue());
                }
            }
        }
        Object enchObj = map.get("enchantments");
        if (enchObj instanceof Map<?, ?> em) {
            for (var e : em.entrySet()) {
                if (e.getValue() instanceof Number n) {
                    String eid = e.getKey().toString().toLowerCase();
                    if (ItemRegistry.isValidId(eid)) {
                        item.enchantments.put(eid, Math.max(1, n.intValue()));
                    }
                }
            }
        }
        Object foodObj = map.get("food");
        if (foodObj instanceof Map<?, ?> fm) {
            Object n = fm.get("nutrition");
            if (n instanceof Number num) {
                item.foodNutrition = Math.max(0, num.intValue());
            }
            Object s = fm.get("saturation");
            if (s instanceof Number num) {
                item.foodSaturation = Math.max(0f, num.floatValue());
            }
            Object c = fm.get("can-always-eat");
            item.foodCanAlwaysEat = "true".equalsIgnoreCase(String.valueOf(c));
        }
        return item;
    }
}
