package com.phcraft.rpgforge;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RPG 物品注册表 —— 负责加载、保存、查询所有 RPGItem 和 RPGRecipe。
 *
 * <p>每个物品存储在 items/ 目录下的独立 YAML 文件中，文件名 = 物品id.yml。
 * 配方统一存储在 recipes.yml 文件中。
 */
public class ItemRegistry {

    private final RPGForgePlugin plugin;
    private final Map<String, RPGItem> items = new LinkedHashMap<>();
    private final Map<String, RPGRecipe> recipes = new LinkedHashMap<>();
    private File itemsFolder;
    private File recipesFile;

    public ItemRegistry(RPGForgePlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        items.clear();
        itemsFolder = new File(plugin.getDataFolder(), "items");
        if (!itemsFolder.exists()) {
            itemsFolder.mkdirs();
            // 创建示例物品
            createExampleItems();
        }

        File[] files = itemsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                Map<String, Object> map = new LinkedHashMap<>();
                for (String key : cfg.getKeys(false)) {
                    map.put(key, cfg.get(key));
                }
                RPGItem item = RPGItem.deserialize(map);
                if (item != null) {
                    requireValidId(item.id());
                    if (!file.getName().equalsIgnoreCase(item.id() + ".yml")) {
                        throw new IllegalArgumentException("文件名与物品 ID 不一致");
                    }
                    items.put(item.id().toLowerCase(), item);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("加载物品失败: " + file.getName() + " - " + e.getMessage());
            }
        }

        plugin.getLogger().info("已加载 " + items.size() + " 个 RPG 物品。");
    }

    public void saveAll() {
        for (RPGItem item : items.values()) {
            saveItem(item);
        }
    }

    public static boolean isValidId(String id) {
        return id != null && id.matches("[a-zA-Z0-9_-]+");
    }

    private static void requireValidId(String id) {
        if (!isValidId(id)) throw new IllegalArgumentException("物品 ID 只能包含字母、数字、下划线和连字符");
    }

    public void saveItem(RPGItem item) {
        requireValidId(item.id());
        if (itemsFolder == null) return;
        File file = new File(itemsFolder, item.id() + ".yml");
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            Map<String, Object> data = item.serialize();
            for (var entry : data.entrySet()) {
                cfg.set(entry.getKey(), entry.getValue());
            }
            cfg.save(file);
        } catch (Exception e) {
            plugin.getLogger().severe("保存物品失败: " + item.id() + " - " + e.getMessage());
        }
    }

    public RPGItem get(String id) {
        return items.get(id.toLowerCase());
    }

    public Collection<RPGItem> all() {
        return items.values();
    }

    public Set<String> ids() {
        return items.keySet();
    }

    public boolean exists(String id) {
        return items.containsKey(id.toLowerCase());
    }

    public void add(RPGItem item) {
        requireValidId(item.id());
        items.put(item.id().toLowerCase(), item);
        saveItem(item);
    }

    public void remove(String id) {
        requireValidId(id);
        items.remove(id.toLowerCase());
        File file = new File(itemsFolder, id + ".yml");
        if (file.exists()) {
            file.delete();
        }
    }

    /** 分页获取物品列表 */
    public List<RPGItem> page(int page, int perPage) {
        List<RPGItem> all = new ArrayList<>(items.values());
        int start = page * perPage;
        int end = Math.min(start + perPage, all.size());
        if (start >= all.size()) return List.of();
        return all.subList(start, end);
    }

    public int totalPages(int perPage) {
        return (int) Math.ceil((double) items.size() / perPage);
    }

    // ===== 示例物品 =====
    private void createExampleItems() {
        // 火焰剑
        RPGItem flameSword = new RPGItem("flame_sword");
        flameSword.setDisplayName("&c&l烈焰之剑");
        flameSword.setMaterial(org.bukkit.Material.IRON_SWORD);
        flameSword.setLore(List.of(
                "&7一把燃烧着永恒之火的利剑",
                "&7右键释放火球"
        ));
        flameSword.setUnbreakable(true);

        RPGPower fireball = new RPGPower(PowerType.FIREBALL);
        fireball.params().put("yield", 2.0);
        fireball.params().put("cooldown", 5);
        flameSword.addPower(fireball);

        RPGPower lightParticle = new RPGPower(PowerType.PARTICLE);
        lightParticle.params().put("particle", "FLAME");
        lightParticle.params().put("count", 15);
        lightParticle.params().put("radius", 0.8);
        lightParticle.params().put("cooldown", 1);
        flameSword.addPower(lightParticle);

        add(flameSword);

        // 治愈之杖
        RPGItem healWand = new RPGItem("heal_wand");
        healWand.setDisplayName("&a&l生命之杖");
        healWand.setMaterial(org.bukkit.Material.STICK);
        healWand.setLore(List.of(
                "&7蕴含生命能量的法杖",
                "&7右键治疗自己"
        ));
        healWand.setCustomModelData(1001);

        RPGPower heal = new RPGPower(PowerType.HEAL);
        heal.params().put("amount", 6.0);
        heal.params().put("cooldown", 10);
        healWand.addPower(heal);

        RPGPower heartParticle = new RPGPower(PowerType.PARTICLE);
        heartParticle.params().put("particle", "HEART");
        heartParticle.params().put("count", 8);
        heartParticle.params().put("radius", 0.5);
        heartParticle.params().put("cooldown", 0);
        healWand.addPower(heartParticle);

        add(healWand);

        // 闪电之锤
        RPGItem thunderHammer = new RPGItem("thunder_hammer");
        thunderHammer.setDisplayName("&e&l雷霆战锤");
        thunderHammer.setMaterial(org.bukkit.Material.GOLDEN_AXE);
        thunderHammer.setLore(List.of(
                "&7传说中雷神的武器",
                "&7攻击命中时召唤闪电"
        ));
        thunderHammer.setUnbreakable(true);

        RPGPower lightning = new RPGPower(PowerType.LIGHTNING);
        lightning.params().put("damage", 8.0);
        lightning.params().put("cooldown", 2);
        lightning.triggers().clear();
        lightning.triggers().add(TriggerType.HIT);
        thunderHammer.addPower(lightning);

        RPGPower dmgBoost = new RPGPower(PowerType.DAMAGE_BOOST);
        dmgBoost.params().put("bonus", 5.0);
        dmgBoost.params().put("mode", "add");
        dmgBoost.triggers().clear();
        dmgBoost.triggers().add(TriggerType.HIT);
        thunderHammer.addPower(dmgBoost);

        add(thunderHammer);

        // 锻造铁砧（打开编辑器）
        RPGItem forgeAnvil = new RPGItem("forge_anvil");
        forgeAnvil.setDisplayName("&6&l锻造铁砧");
        forgeAnvil.setMaterial(org.bukkit.Material.ANVIL);
        forgeAnvil.setLore(List.of(
                "&7传说中的锻造神器",
                "&7右键打开物品锻造面板"
        ));
        forgeAnvil.setUnbreakable(true);

        RPGPower openEditor = new RPGPower(PowerType.OPEN_EDITOR);
        openEditor.triggers().clear();
        openEditor.triggers().add(TriggerType.RIGHT_CLICK);
        forgeAnvil.addPower(openEditor);

        add(forgeAnvil);
    }

    // ===== 配方管理 =====

    /** 加载所有配方 */
    public void loadRecipes() {
        recipes.clear();
        recipesFile = new File(plugin.getDataFolder(), "recipes.yml");
        if (!recipesFile.exists()) {
            // 创建示例配方
            createExampleRecipes();
            saveRecipes();
        }

        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(recipesFile);
            ConfigurationSection section = cfg.getConfigurationSection("recipes");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    ConfigurationSection rSection = section.getConfigurationSection(key);
                    if (rSection == null) continue;
                    for (String k : rSection.getKeys(false)) {
                        map.put(k, rSection.get(k));
                    }
                    RPGRecipe recipe = RPGRecipe.deserialize(map);
                    if (recipe != null) {
                        recipes.put(recipe.id().toLowerCase(), recipe);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("加载配方失败: " + e.getMessage());
        }

        plugin.getLogger().info("已加载 " + recipes.size() + " 个配方。");
    }

    /** 保存所有配方 */
    public void saveRecipes() {
        if (recipesFile == null) {
            recipesFile = new File(plugin.getDataFolder(), "recipes.yml");
        }
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            for (RPGRecipe recipe : recipes.values()) {
                Map<String, Object> data = recipe.serialize();
                for (var entry : data.entrySet()) {
                    cfg.set("recipes." + recipe.id() + "." + entry.getKey(), entry.getValue());
                }
            }
            cfg.save(recipesFile);
        } catch (Exception e) {
            plugin.getLogger().severe("保存配方失败: " + e.getMessage());
        }
    }

    /** 注册所有配方到 Bukkit */
    public void registerAllRecipes() {
        int count = 0;
        for (RPGRecipe recipe : recipes.values()) {
            if (recipe.register(plugin)) {
                count++;
            }
        }
        plugin.getLogger().info("已注册 " + count + " 个配方。");
    }

    /** 注销所有配方（用于重载） */
    public void unregisterAllRecipes() {
        for (RPGRecipe recipe : recipes.values()) {
            recipe.unregister(plugin);
        }
        plugin.getLogger().info("已注销所有配方。");
    }

    /** 重新加载并重新注册所有配方 */
    public void reloadRecipes() {
        unregisterAllRecipes();
        loadRecipes();
        registerAllRecipes();
    }

    // 配方查询

    public RPGRecipe getRecipe(String id) {
        return recipes.get(id.toLowerCase());
    }

    public Collection<RPGRecipe> allRecipes() {
        return recipes.values();
    }

    public Set<String> recipeIds() {
        return recipes.keySet();
    }

    public boolean recipeExists(String id) {
        return recipes.containsKey(id.toLowerCase());
    }

    public void addRecipe(RPGRecipe recipe) {
        recipes.put(recipe.id().toLowerCase(), recipe);
        saveRecipes();
    }

    public void removeRecipe(String id) {
        RPGRecipe recipe = recipes.remove(id.toLowerCase());
        if (recipe != null) {
            recipe.unregister(plugin);
        }
        saveRecipes();
    }

    /** 创建示例配方 */
    private void createExampleRecipes() {
        // 示例：用铁锭 + 烈焰粉合成烈焰之剑（有序配方）
        RPGRecipe flameSwordRecipe = new RPGRecipe("flame_sword_recipe");
        flameSwordRecipe.setResultItemId("flame_sword");
        flameSwordRecipe.setResultAmount(1);
        flameSwordRecipe.setType(RPGRecipe.RecipeType.SHAPED);
        flameSwordRecipe.setShape(List.of(
                " F ",
                " I ",
                " S "
        ));
        Map<Character, org.bukkit.Material> ingMap = new LinkedHashMap<>();
        ingMap.put('F', org.bukkit.Material.BLAZE_POWDER);
        ingMap.put('I', org.bukkit.Material.IRON_INGOT);
        ingMap.put('S', org.bukkit.Material.STICK);
        flameSwordRecipe.setIngredientMap(ingMap);
        recipes.put(flameSwordRecipe.id().toLowerCase(), flameSwordRecipe);

        // 示例：用金锭 + 木棍无序合成生命之杖
        RPGRecipe healWandRecipe = new RPGRecipe("heal_wand_recipe");
        healWandRecipe.setResultItemId("heal_wand");
        healWandRecipe.setResultAmount(1);
        healWandRecipe.setType(RPGRecipe.RecipeType.SHAPELESS);
        healWandRecipe.setIngredients(List.of(
                org.bukkit.Material.GOLD_INGOT,
                org.bukkit.Material.GOLD_INGOT,
                org.bukkit.Material.STICK
        ));
        recipes.put(healWandRecipe.id().toLowerCase(), healWandRecipe);

        // 示例：熔炉配方 - 铁锭烧制成雷霆战锤（仅演示）
        RPGRecipe thunderHammerRecipe = new RPGRecipe("thunder_hammer_recipe");
        thunderHammerRecipe.setResultItemId("thunder_hammer");
        thunderHammerRecipe.setResultAmount(1);
        thunderHammerRecipe.setType(RPGRecipe.RecipeType.FURNACE);
        thunderHammerRecipe.setFurnaceInput(org.bukkit.Material.GOLDEN_AXE);
        thunderHammerRecipe.setExperience(5.0f);
        thunderHammerRecipe.setCookingTime(200);
        recipes.put(thunderHammerRecipe.id().toLowerCase(), thunderHammerRecipe);
    }
}
