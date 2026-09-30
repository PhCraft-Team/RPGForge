package com.phcraft.rpgforge;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 附魔预设注册表 —— 加载、保存、查询所有 RPGEnchant。
 *
 * <p>预设存储在 enchantments.yml 中。物品上的附魔实例（附魔id + 等级）存储在物品自己的 YAML 中。
 */
public class EnchantRegistry {

    private final RPGForgePlugin plugin;
    private final Map<String, RPGEnchant> enchants = new LinkedHashMap<>();
    private File file;

    public EnchantRegistry(RPGForgePlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        enchants.clear();
        file = new File(plugin.getDataFolder(), "enchantments.yml");
        if (!file.exists()) {
            createDefaults();
            saveAll();
        }

        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = cfg.getConfigurationSection("enchantments");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    ConfigurationSection eSection = section.getConfigurationSection(key);
                    if (eSection == null) continue;
                    Map<String, Object> map = YamlUtil.deepMap(eSection);
                    RPGEnchant enchant = RPGEnchant.deserialize(key.toLowerCase(), map);
                    if (enchant == null) {
                        plugin.getLogger().warning("跳过附魔预设 " + key + "：格式无效");
                        continue;
                    }
                    if (!ItemRegistry.isValidId(enchant.id())) {
                        plugin.getLogger().warning("跳过附魔预设 " + key + "：ID 含非法字符");
                        continue;
                    }
                    enchants.put(enchant.id(), enchant);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("加载附魔预设失败: " + e.getMessage());
        }

        // 补齐内置默认预设 + 迁移旧预设 + 规范化（有变更则存盘）
        boolean changed = ensureDefaults();
        changed |= normalizePresets();
        if (changed) {
            saveAll();
        }

        plugin.getLogger().info("已加载 " + enchants.size() + " 个附魔预设。");
    }

    /**
     * 把内置默认预设补齐到当前注册表（仅添加缺失的 ID）。
     * 注意：手动从注册表中删除内置预设会在下次重载时复活。
     *
     * @return 是否有新增预设
     */
    private boolean ensureDefaults() {
        Map<String, RPGEnchant> defaults = buildDefaults();
        boolean added = false;
        for (var e : defaults.entrySet()) {
            if (!enchants.containsKey(e.getKey())) {
                enchants.put(e.getKey(), e.getValue());
                plugin.getLogger().info("补充新附魔预设: " + e.getKey());
                added = true;
            }
        }
        return added;
    }

    /**
     * 加载后的规范化（设计规则自愈，返回是否有变更）：
     * 1) 移除已合并的旧预设 ID（连锁挖矿/连锁砍伐 → 连环）
     * 2) 内置预设的最大等级跟随默认值（1~5 级设计）
     * 3) 能力型预设剥离 cooldown 参数（TICK 周期型如「丰饶」「堡垒」除外，其 cooldown 是周期）
     */
    private boolean normalizePresets() {
        boolean changed = false;

        for (String legacyId : java.util.List.of("chain_mining", "chain_lumber")) {
            if (enchants.remove(legacyId) != null) {
                plugin.getLogger().info("迁移：移除旧预设 " + legacyId + "（已合并为 chain/连环）");
                changed = true;
            }
        }

        Map<String, RPGEnchant> defaults = buildDefaults();
        for (var e : defaults.entrySet()) {
            RPGEnchant current = enchants.get(e.getKey());
            if (current == null) continue;

            if (current.maxLevel() != e.getValue().maxLevel()) {
                current.setMaxLevel(e.getValue().maxLevel());
                changed = true;
            }
            if (current.isPowerType()
                    && !current.powerTemplate().triggers().contains(TriggerType.TICK)) {
                if (current.powerTemplate().params().remove("cooldown") != null) {
                    changed = true;
                }
                if (current.scalePerLevel().remove("cooldown") != null) {
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** 在临时注册表里生成一份内置默认预设（不污染当前注册表）。 */
    private Map<String, RPGEnchant> buildDefaults() {
        Map<String, RPGEnchant> saved = new LinkedHashMap<>(enchants);
        enchants.clear();
        createDefaults();
        Map<String, RPGEnchant> defaults = new LinkedHashMap<>(enchants);
        enchants.clear();
        enchants.putAll(saved);
        return defaults;
    }

    public void saveAll() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), "enchantments.yml");
        }
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            for (RPGEnchant enchant : enchants.values()) {
                for (var entry : enchant.serialize().entrySet()) {
                    cfg.set("enchantments." + enchant.id() + "." + entry.getKey(), entry.getValue());
                }
            }
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("保存附魔预设失败: " + e.getMessage());
        }
    }

    public RPGEnchant get(String id) {
        return id == null ? null : enchants.get(id.toLowerCase());
    }

    public Collection<RPGEnchant> all() {
        return enchants.values();
    }

    public Set<String> ids() {
        return enchants.keySet();
    }

    public boolean exists(String id) {
        return id != null && enchants.containsKey(id.toLowerCase());
    }

    /** 给物品应用/提升附魔等级（已满级则返回 false） */
    public boolean applyTo(RPGItem item, String enchantId) {
        RPGEnchant enchant = get(enchantId);
        if (enchant == null) return false;
        int current = item.enchantments().getOrDefault(enchant.id(), 0);
        if (current >= enchant.maxLevel()) return false;
        item.enchantments().put(enchant.id(), current + 1);
        item.refreshEnchantPowers();
        return true;
    }

    /** 降低附魔等级一级（0 级时移除；不存在返回 false） */
    public boolean downgrade(RPGItem item, String enchantId) {
        RPGEnchant enchant = get(enchantId);
        if (enchant == null) return false;
        Integer current = item.enchantments().get(enchant.id());
        if (current == null) return false;
        if (current <= 1) {
            item.enchantments().remove(enchant.id());
        } else {
            item.enchantments().put(enchant.id(), current - 1);
        }
        item.refreshEnchantPowers();
        return true;
    }

    /** 移除物品上的附魔 */
    public boolean removeFrom(RPGItem item, String enchantId) {
        RPGEnchant enchant = get(enchantId);
        if (enchant == null) return false;
        if (item.enchantments().remove(enchant.id()) != null) {
            item.refreshEnchantPowers();
            return true;
        }
        return false;
    }

    // ===== 默认附魔预设 =====

    private void createDefaults() {
        enchants.clear();

        // 属性型
        RPGEnchant sharpness = new RPGEnchant("sharpness");
        sharpness.setDisplayName("&c锋利");
        sharpness.setDescription("每一级提升攻击伤害");
        sharpness.setMaxLevel(5);
        sharpness.setRarity(RPGEnchant.Rarity.COMMON);
        sharpness.setAttribute("attack_damage");
        sharpness.setValuePerLevel(2.0);
        enchants.put(sharpness.id(), sharpness);

        RPGEnchant swiftness = new RPGEnchant("swiftness");
        swiftness.setDisplayName("&b迅捷");
        swiftness.setDescription("每一级提升攻击速度");
        swiftness.setMaxLevel(5);
        swiftness.setRarity(RPGEnchant.Rarity.UNCOMMON);
        swiftness.setAttribute("attack_speed");
        swiftness.setValuePerLevel(0.2);
        enchants.put(swiftness.id(), swiftness);

        RPGEnchant fortitude = new RPGEnchant("fortitude");
        fortitude.setDisplayName("&e坚韧");
        fortitude.setDescription("每一级提升护甲值");
        fortitude.setMaxLevel(5);
        fortitude.setRarity(RPGEnchant.Rarity.UNCOMMON);
        fortitude.setAttribute("armor");
        fortitude.setValuePerLevel(1.0);
        enchants.put(fortitude.id(), fortitude);

        RPGEnchant vitality = new RPGEnchant("vitality");
        vitality.setDisplayName("&a生命");
        vitality.setDescription("每一级提升最大生命（2点=1颗心）");
        vitality.setMaxLevel(5);
        vitality.setRarity(RPGEnchant.Rarity.RARE);
        vitality.setAttribute("max_health");
        vitality.setValuePerLevel(4.0);
        enchants.put(vitality.id(), vitality);

        RPGEnchant steadfast = new RPGEnchant("steadfast");
        steadfast.setDisplayName("&6稳固");
        steadfast.setDescription("每一级提升击退抗性");
        steadfast.setMaxLevel(5);
        steadfast.setRarity(RPGEnchant.Rarity.UNCOMMON);
        steadfast.setAttribute("knockback_resistance");
        steadfast.setValuePerLevel(0.2);
        enchants.put(steadfast.id(), steadfast);

        // 能力型
        RPGEnchant vampire = new RPGEnchant("vampire");
        vampire.setDisplayName("&4吸血");
        vampire.setDescription("攻击命中时按伤害百分比恢复生命");
        vampire.setMaxLevel(5);
        vampire.setRarity(RPGEnchant.Rarity.RARE);
        RPGPower vampiric = new RPGPower(PowerType.VAMPIRIC);
        vampiric.triggers().clear();
        vampiric.triggers().add(TriggerType.HIT);
        vampiric.params().put("percent", 10.0);
        vampire.setPowerTemplate(vampiric);
        vampire.scalePerLevel().put("percent", 10.0);
        enchants.put(vampire.id(), vampire);

        RPGEnchant flameEdge = new RPGEnchant("flame_edge");
        flameEdge.setDisplayName("&c炽刃");
        flameEdge.setDescription("攻击命中时点燃目标");
        flameEdge.setMaxLevel(5);
        flameEdge.setRarity(RPGEnchant.Rarity.UNCOMMON);
        RPGPower flame = new RPGPower(PowerType.FLAME);
        flame.triggers().clear();
        flame.triggers().add(TriggerType.HIT);
        flame.params().put("seconds", 3);
        flameEdge.setPowerTemplate(flame);
        flameEdge.scalePerLevel().put("seconds", 1.0);
        enchants.put(flameEdge.id(), flameEdge);

        RPGEnchant thunder = new RPGEnchant("thunder");
        thunder.setDisplayName("&e雷鸣");
        thunder.setDescription("攻击命中时召唤闪电");
        thunder.setMaxLevel(5);
        thunder.setRarity(RPGEnchant.Rarity.EPIC);
        RPGPower lightning = new RPGPower(PowerType.LIGHTNING);
        lightning.triggers().clear();
        lightning.triggers().add(TriggerType.HIT);
        lightning.params().put("damage", 3.0);
        thunder.setPowerTemplate(lightning);
        thunder.scalePerLevel().put("damage", 2.0);
        enchants.put(thunder.id(), thunder);

        // ===== 拔刀剑风格特效 =====
        RPGEnchant domain = new RPGEnchant("domain");
        domain.setDisplayName("&5领域");
        domain.setDescription("右键展开圆形领域，斩击周围所有敌人");
        domain.setMaxLevel(5);
        domain.setRarity(RPGEnchant.Rarity.EPIC);
        RPGPower domainPower = new RPGPower(PowerType.DOMAIN_SLASH);
        domainPower.triggers().clear();
        domainPower.triggers().add(TriggerType.RIGHT_CLICK);
        domainPower.params().put("radius", 4.0);
        domainPower.params().put("damage", 6.0);
        domain.setPowerTemplate(domainPower);
        domain.scalePerLevel().put("radius", 0.5);
        domain.scalePerLevel().put("damage", 3.0);
        enchants.put(domain.id(), domain);

        RPGEnchant bloodFeast = new RPGEnchant("blood_feast");
        bloodFeast.setDisplayName("&4饮血");
        bloodFeast.setDescription("攻击命中吸取伤害回复生命，并补充饥饿");
        bloodFeast.setMaxLevel(5);
        bloodFeast.setRarity(RPGEnchant.Rarity.RARE);
        RPGPower bloodPower = new RPGPower(PowerType.BLOOD_FEAST);
        bloodPower.triggers().clear();
        bloodPower.triggers().add(TriggerType.HIT);
        bloodPower.params().put("percent", 15.0);
        bloodPower.params().put("hunger", 1.0);
        bloodFeast.setPowerTemplate(bloodPower);
        bloodFeast.scalePerLevel().put("percent", 10.0);
        bloodFeast.scalePerLevel().put("hunger", 1.0);
        enchants.put(bloodFeast.id(), bloodFeast);

        RPGEnchant bladeWave = new RPGEnchant("blade_wave");
        bladeWave.setDisplayName("&b气刃");
        bladeWave.setDescription("右键斩出飞行剑气，贯穿路径上的敌人");
        bladeWave.setMaxLevel(5);
        bladeWave.setRarity(RPGEnchant.Rarity.EPIC);
        RPGPower wavePower = new RPGPower(PowerType.BLADE_WAVE);
        wavePower.triggers().clear();
        wavePower.triggers().add(TriggerType.RIGHT_CLICK);
        wavePower.params().put("damage", 4.0);
        wavePower.params().put("distance", 12.0);
        wavePower.params().put("speed", 1.2);
        bladeWave.setPowerTemplate(wavePower);
        bladeWave.scalePerLevel().put("damage", 2.0);
        bladeWave.scalePerLevel().put("distance", 2.0);
        enchants.put(bladeWave.id(), bladeWave);

        // ===== 工具连锁（挖矿/砍伐合并为「连环」） =====
        RPGEnchant chain = new RPGEnchant("chain");
        chain.setDisplayName("&a连环");
        chain.setDescription("破坏方块时连锁破坏周围同类方块（挖矿/砍伐通用）");
        chain.setMaxLevel(5);
        chain.setRarity(RPGEnchant.Rarity.UNCOMMON);
        RPGPower chainPower = new RPGPower(PowerType.CHAIN_BREAK);
        chainPower.triggers().clear();
        chainPower.triggers().add(TriggerType.BREAK_BLOCK);
        chainPower.params().put("count", 8);
        chainPower.params().put("radius", 2.0);
        chain.setPowerTemplate(chainPower);
        chain.scalePerLevel().put("count", 4.0);
        chain.scalePerLevel().put("radius", 0.5);
        enchants.put(chain.id(), chain);

        RPGEnchant chainHarvest = new RPGEnchant("chain_harvest");
        chainHarvest.setDisplayName("&a连锁收获");
        chainHarvest.setDescription("右键收割作物时连锁收获范围内所有成熟作物并自动回种");
        chainHarvest.setMaxLevel(5);
        chainHarvest.setRarity(RPGEnchant.Rarity.UNCOMMON);
        RPGPower harvestPower = new RPGPower(PowerType.CHAIN_HARVEST);
        harvestPower.triggers().clear();
        harvestPower.triggers().add(TriggerType.RIGHT_CLICK);
        harvestPower.params().put("radius", 3.0);
        chainHarvest.setPowerTemplate(harvestPower);
        chainHarvest.scalePerLevel().put("radius", 1.0);
        enchants.put(chainHarvest.id(), chainHarvest);

        RPGEnchant chainTill = new RPGEnchant("chain_till");
        chainTill.setDisplayName("&a连锁耕地");
        chainTill.setDescription("右键耕地时连锁开垦周围土地（Lv1: 3x3，每级半径 +1，Lv5: 11x11）");
        chainTill.setMaxLevel(5);
        chainTill.setRarity(RPGEnchant.Rarity.COMMON);
        RPGPower tillPower = new RPGPower(PowerType.CHAIN_TILL);
        tillPower.triggers().clear();
        tillPower.triggers().add(TriggerType.RIGHT_CLICK);
        tillPower.params().put("size", 1);
        chainTill.setPowerTemplate(tillPower);
        chainTill.scalePerLevel().put("size", 1.0);
        enchants.put(chainTill.id(), chainTill);

        // ===== 防具类 =====
        RPGEnchant feast = new RPGEnchant("feast");
        feast.setDisplayName("&6丰饶");
        feast.setDescription("装备时持续滋养身体，缓慢恢复饥饿与饱和（饥饿上限受客户端限制，以持续回复等效实现）");
        feast.setMaxLevel(5);
        feast.setRarity(RPGEnchant.Rarity.RARE);
        RPGPower feastPower = new RPGPower(PowerType.FEED);
        feastPower.triggers().clear();
        feastPower.triggers().add(TriggerType.TICK);
        feastPower.params().put("hunger", 2);
        feastPower.params().put("saturation", 2.0f);
        feastPower.params().put("cooldown", 30);
        feast.setPowerTemplate(feastPower);
        feast.scalePerLevel().put("hunger", 1.0);
        feast.scalePerLevel().put("saturation", 0.5);
        feast.scalePerLevel().put("cooldown", -5.0);
        enchants.put(feast.id(), feast);

        // ===== 新增特效类 =====
        RPGEnchant launchUp = new RPGEnchant("launch_up");
        launchUp.setDisplayName("&b击飞");
        launchUp.setDescription("攻击命中时将目标垂直抬升（纯升空，不击退）");
        launchUp.setMaxLevel(5);
        launchUp.setRarity(RPGEnchant.Rarity.UNCOMMON);
        RPGPower launchPower = new RPGPower(PowerType.LAUNCH_UP);
        launchPower.triggers().clear();
        launchPower.triggers().add(TriggerType.HIT);
        launchPower.params().put("lift", 0.8);
        launchUp.setPowerTemplate(launchPower);
        launchUp.scalePerLevel().put("lift", 0.2);
        enchants.put(launchUp.id(), launchUp);

        RPGEnchant fortress = new RPGEnchant("fortress");
        fortress.setDisplayName("&6堡垒");
        fortress.setDescription("手持盾牌时每周期回复吸收值（黄色心），总量封顶 20 点，多件装备不叠加");
        fortress.setMaxLevel(5);
        fortress.setRarity(RPGEnchant.Rarity.RARE);
        RPGPower fortressPower = new RPGPower(PowerType.FORTRESS);
        fortressPower.triggers().clear();
        fortressPower.triggers().add(TriggerType.TICK);
        fortressPower.params().put("amount", 2.0);
        fortressPower.params().put("cap", 20.0);
        fortressPower.params().put("cooldown", 30);
        fortress.setPowerTemplate(fortressPower);
        fortress.scalePerLevel().put("amount", 0.5);
        fortress.scalePerLevel().put("cooldown", -3.0);
        enchants.put(fortress.id(), fortress);

        RPGEnchant insight = new RPGEnchant("insight");
        insight.setDisplayName("&d看破");
        insight.setDescription("对带有负面状态的目标概率造成倍率伤害，debuff 越多倍率越高");
        insight.setMaxLevel(5);
        insight.setRarity(RPGEnchant.Rarity.EPIC);
        RPGPower insightPower = new RPGPower(PowerType.INSIGHT);
        insightPower.triggers().clear();
        insightPower.triggers().add(TriggerType.HIT);
        insightPower.params().put("chance", 50.0);
        insightPower.params().put("base", 1.5);
        insightPower.params().put("per-debuff", 0.25);
        insight.setPowerTemplate(insightPower);
        insight.scalePerLevel().put("chance", 5.0);
        insight.scalePerLevel().put("per-debuff", 0.05);
        enchants.put(insight.id(), insight);
    }

    /** 所有附魔能力型实例的索引基数（避开物品本体 Power 的 0..n 索引） */
    public static final int ENCHANT_INDEX_BASE = 1000;
}
