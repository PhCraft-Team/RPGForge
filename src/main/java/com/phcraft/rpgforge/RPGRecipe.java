package com.phcraft.rpgforge;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.FurnaceRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RPG 配方定义 —— 支持有序、无序和熔炉三种配方类型。
 *
 * <p>配方产出为 RPGItem（通过 RPGItem.buildItemStack() 构建），原料为普通 Material。
 * 配方使用 NamespacedKey（plugin 命名空间）作为 Bukkit 配方标识。
 */
public class RPGRecipe {

    /** 配方类型 */
    public enum RecipeType {
        SHAPED,     // 有序配方
        SHAPELESS,  // 无序配方
        FURNACE     // 熔炉配方
    }

    private final String id;
    private String resultItemId;
    private int resultAmount;
    private RecipeType type;

    // SHAPED 专用
    private List<String> shape;
    private Map<Character, Material> ingredientMap;

    // SHAPELESS 专用
    private List<Material> ingredients;

    // FURNACE 专用
    private Material furnaceInput;
    private float experience;
    private int cookingTime;

    public RPGRecipe(String id) {
        this.id = id;
        this.resultItemId = "";
        this.resultAmount = 1;
        this.type = RecipeType.SHAPED;
        this.shape = new ArrayList<>();
        this.ingredientMap = new LinkedHashMap<>();
        this.ingredients = new ArrayList<>();
        this.furnaceInput = Material.AIR;
        this.experience = 0.0f;
        this.cookingTime = 200; // 默认 10 秒
    }

    public String id() {
        return id;
    }

    public String resultItemId() {
        return resultItemId;
    }

    public void setResultItemId(String resultItemId) {
        this.resultItemId = resultItemId;
    }

    public int resultAmount() {
        return resultAmount;
    }

    public void setResultAmount(int resultAmount) {
        this.resultAmount = resultAmount;
    }

    public RecipeType type() {
        return type;
    }

    public void setType(RecipeType type) {
        this.type = type;
    }

    // SHAPED 相关

    public List<String> shape() {
        return shape;
    }

    public void setShape(List<String> shape) {
        this.shape = new ArrayList<>(shape);
    }

    public Map<Character, Material> ingredientMap() {
        return ingredientMap;
    }

    public void setIngredientMap(Map<Character, Material> ingredientMap) {
        this.ingredientMap = new LinkedHashMap<>(ingredientMap);
    }

    // SHAPELESS 相关

    public List<Material> ingredients() {
        return ingredients;
    }

    public void setIngredients(List<Material> ingredients) {
        this.ingredients = new ArrayList<>(ingredients);
    }

    // FURNACE 相关

    public Material furnaceInput() {
        return furnaceInput;
    }

    public void setFurnaceInput(Material furnaceInput) {
        this.furnaceInput = furnaceInput;
    }

    public float experience() {
        return experience;
    }

    public void setExperience(float experience) {
        this.experience = experience;
    }

    public int cookingTime() {
        return cookingTime;
    }

    public void setCookingTime(int cookingTime) {
        this.cookingTime = cookingTime;
    }

    // ===== 配方注册 / 注销 =====

    /**
     * 向 Bukkit 注册此配方。
     * 若产出的 RPGItem 不存在则注册失败。
     *
     * @return 是否注册成功
     */
    public boolean register(RPGForgePlugin plugin) {
        RPGItem rpgItem = plugin.registry().get(resultItemId);
        if (rpgItem == null) {
            plugin.getLogger().warning("注册配方失败: " + id + " - 找不到产出物品 " + resultItemId);
            return false;
        }

        ItemStack result = rpgItem.buildItemStack(resultAmount);
        NamespacedKey key = new NamespacedKey(plugin, "recipe_" + id.toLowerCase());

        try {
            Recipe recipe = switch (type) {
                case SHAPED -> buildShapedRecipe(key, result);
                case SHAPELESS -> buildShapelessRecipe(key, result);
                case FURNACE -> buildFurnaceRecipe(key, result);
            };
            if (recipe != null) {
                plugin.getServer().addRecipe(recipe);
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("注册配方失败: " + id + " - " + e.getMessage());
        }
        return false;
    }

    /**
     * 从 Bukkit 注销此配方。
     */
    public void unregister(RPGForgePlugin plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "recipe_" + id.toLowerCase());
        plugin.getServer().removeRecipe(key, true); // true = 即使是石切配方也移除
    }

    private Recipe buildShapedRecipe(NamespacedKey key, ItemStack result) {
        if (shape == null || shape.isEmpty()) return null;

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape.toArray(new String[0]));

        if (ingredientMap != null) {
            for (var entry : ingredientMap.entrySet()) {
                if (entry.getValue() != null && entry.getValue() != Material.AIR) {
                    recipe.setIngredient(entry.getKey(), entry.getValue());
                }
            }
        }
        return recipe;
    }

    private Recipe buildShapelessRecipe(NamespacedKey key, ItemStack result) {
        if (ingredients == null || ingredients.isEmpty()) return null;

        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        for (Material mat : ingredients) {
            if (mat != null && mat != Material.AIR) {
                recipe.addIngredient(mat);
            }
        }
        return recipe;
    }

    private Recipe buildFurnaceRecipe(NamespacedKey key, ItemStack result) {
        if (furnaceInput == null || furnaceInput == Material.AIR) return null;

        return new FurnaceRecipe(key, result, furnaceInput, experience, cookingTime);
    }

    // ===== 序列化 / 反序列化 =====

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("result-item-id", resultItemId);
        map.put("result-amount", resultAmount);
        map.put("type", type.name().toLowerCase());

        switch (type) {
            case SHAPED -> {
                map.put("shape", new ArrayList<>(shape));
                Map<String, String> ingMap = new LinkedHashMap<>();
                for (var entry : ingredientMap.entrySet()) {
                    ingMap.put(String.valueOf(entry.getKey()), entry.getValue().name());
                }
                map.put("ingredients", ingMap);
            }
            case SHAPELESS -> {
                List<String> ingList = new ArrayList<>();
                for (Material mat : ingredients) {
                    ingList.add(mat.name());
                }
                map.put("ingredients", ingList);
            }
            case FURNACE -> {
                map.put("input", furnaceInput.name());
                map.put("experience", experience);
                map.put("cooking-time", cookingTime);
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static RPGRecipe deserialize(Map<String, Object> map) {
        String id = (String) map.get("id");
        if (id == null) return null;

        RPGRecipe recipe = new RPGRecipe(id);
        recipe.resultItemId = (String) map.getOrDefault("result-item-id", "");
        recipe.resultAmount = ((Number) map.getOrDefault("result-amount", 1)).intValue();

        String typeName = (String) map.getOrDefault("type", "shaped");
        try {
            recipe.type = RecipeType.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            recipe.type = RecipeType.SHAPED;
        }

        switch (recipe.type) {
            case SHAPED -> {
                Object shapeObj = map.get("shape");
                if (shapeObj instanceof List<?> list) {
                    for (Object o : list) {
                        recipe.shape.add(o.toString());
                    }
                }
                Object ingObj = map.get("ingredients");
                if (ingObj instanceof org.bukkit.configuration.ConfigurationSection section) {
                    ingObj = section.getValues(false);
                }
                if (ingObj instanceof Map<?, ?> m) {
                    for (var entry : m.entrySet()) {
                        String key = entry.getKey().toString();
                        if (!key.isEmpty()) {
                            char c = key.charAt(0);
                            try {
                                recipe.ingredientMap.put(c, Material.valueOf(entry.getValue().toString()));
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                }
            }
            case SHAPELESS -> {
                Object ingObj = map.get("ingredients");
                if (ingObj instanceof List<?> list) {
                    for (Object o : list) {
                        try {
                            recipe.ingredients.add(Material.valueOf(o.toString()));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            case FURNACE -> {
                String inputName = (String) map.getOrDefault("input", "AIR");
                try {
                    recipe.furnaceInput = Material.valueOf(inputName);
                } catch (IllegalArgumentException e) {
                    recipe.furnaceInput = Material.AIR;
                }
                recipe.experience = ((Number) map.getOrDefault("experience", 0.0)).floatValue();
                recipe.cookingTime = ((Number) map.getOrDefault("cooking-time", 200)).intValue();
            }
        }
        return recipe;
    }
}
