package com.phcraft.rpgforge;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Collection;

/**
 * Power 执行器 —— 集中处理所有 PowerType 的实际效果。
 */
final class PowerExecutor {

    private PowerExecutor() {}

    static boolean execute(PowerType type, Player player, LivingEntity target,
                           Location hitLocation, Map<String, Object> params) {
        return switch (type) {
            case OPEN_EDITOR -> openEditor(player);
            case COMMAND -> runCommand(player, params);
            case LIGHTNING -> strikeLightning(player, target, hitLocation, params);
            case EXPLOSION -> triggerExplosion(player, target, hitLocation, params);
            case DAMAGE_BOOST -> false; // 伤害加成由事件监听器单独处理
            case POTION_SELF -> applyPotionSelf(player, params);
            case POTION_HIT -> applyPotionHit(target, params);
            case HEAL -> healPlayer(player, params);
            case FEED -> feedPlayer(player, params);
            case PARTICLE -> spawnParticle(player, target, hitLocation, params);
            case SOUND -> playSound(player, params);
            case TELEPORT -> teleportForward(player, params);
            case KNOCKBACK -> knockbackTarget(player, target, params);
            case SCALE -> applyScale(player, target, params);
            case CONSUME -> false; // 由事件监听器统一处理
            case RETURN_CONTAINER -> giveContainer(player, params);
            case DURABILITY -> false; // 由事件监听器统一处理
            case FIREBALL -> launchFireball(player, params);
            case ARROW -> launchArrow(player, params);
            // 新 Power
            case AOE_DAMAGE -> aoeDamage(player, params);
            case AOE_POTION -> aoePotion(player, params);
            case CRITICAL_HIT -> false; // 由攻击事件监听器处理
            case VAMPIRIC -> false;     // 由攻击事件监听器处理
            case FLAME -> flameTarget(target, params);
            case LEVITATION -> levitationTarget(player, target, params);
            case DASH -> dashForward(player, params);
            case ATTRACT -> attractEntities(player, params);
            case REPULSE -> repulseEntities(player, params);
            case SHIELD -> applyShield(player, params);
            case ECONOMY_COST -> economyCost(player, params);
            case REPAIR -> false; // 由事件监听器处理（需要访问 itemStack）
            // 拔刀剑风格特效
            case DOMAIN_SLASH -> domainSlash(player, params);
            case BLADE_WAVE -> bladeWave(player, params);
            case BLOOD_FEAST -> false;    // 由攻击事件监听器处理
            case CHAIN_BREAK -> false;   // 由方块破坏监听器处理（需要 block）
            case CHAIN_TILL -> false;    // 由右键监听器处理（需要 block）
            case CHAIN_HARVEST -> false; // 由右键监听器处理（需要 block）
            case LAUNCH_UP -> launchUp(target, params);
            case FORTRESS -> fortressAbsorption(player, params);
            case INSIGHT -> false; // 由攻击事件监听器处理
        };
    }

    // ===== 打开编辑器 =====
    private static boolean openEditor(Player player) {
        // 延迟 1 tick，避免在右键事件中打开 GUI 被弹掉
        Bukkit.getScheduler().runTask(RPGForgePlugin.instance(), () -> {
            if (!player.isOnline()) return;
            RPGForgePlugin.instance().gui().openItemList(player);
        });
        return true;
    }

    // ===== 命令 =====
    private static boolean runCommand(Player player, Map<String, Object> params) {
        String cmd = getStr(params, "command", "");
        boolean asConsole = getBool(params, "as-console", false);
        String bypassPerms = getStr(params, "bypass-permissions", "");
        cmd = cmd.replace("{player}", player.getName());

        // 延迟 1 tick 执行，避免在事件处理中打开 GUI / 执行命令被拦截
        final String finalCmd = cmd;
        Bukkit.getScheduler().runTask(RPGForgePlugin.instance(), () -> {
            if (!player.isOnline()) return;

            if (asConsole) {
                // 控制台身份：天然拥有所有权限（推荐用于无视权限场景）
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
            } else {
                // 玩家身份
                if (bypassPerms.isEmpty()) {
                    // 无提权，直接执行（受玩家权限限制）
                    Bukkit.dispatchCommand(player, finalCmd);
                } else {
                    // 临时提权：添加权限 → 执行命令 → 移除权限
                    java.util.List<org.bukkit.permissions.PermissionAttachment> attachments =
                            new java.util.ArrayList<>();
                    try {
                        for (String perm : bypassPerms.split(",")) {
                            String p = perm.trim();
                            if (p.isEmpty()) continue;
                            if (!player.hasPermission(p)) {
                                org.bukkit.permissions.PermissionAttachment att =
                                        player.addAttachment(RPGForgePlugin.instance(), p, true);
                                attachments.add(att);
                            }
                        }
                        Bukkit.dispatchCommand(player, finalCmd);
                    } finally {
                        // 移除所有临时权限
                        for (org.bukkit.permissions.PermissionAttachment att : attachments) {
                            att.remove();
                        }
                    }
                }
            }
        });

        return true;
    }

    // ===== 闪电 =====
    private static boolean strikeLightning(Player player, LivingEntity target,
                                           Location hitLocation, Map<String, Object> params) {
        Location loc;
        if (hitLocation != null) {
            // 命中点优先（弹射物命中时）
            loc = hitLocation.clone();
        } else if (target != null) {
            loc = target.getLocation();
        } else {
            // 玩家视线前方 10 格地面
            var ray = player.rayTraceBlocks(10);
            if (ray != null && ray.getHitBlock() != null) {
                loc = ray.getHitBlock().getLocation().add(0.5, 1, 0.5);
            } else {
                loc = player.getTargetBlock(null, 10).getLocation().add(0.5, 1, 0.5);
            }
        }
        if (loc.getWorld() == null) return false;
        org.bukkit.Bukkit.getLogger().info("[RPGForge-DEBUG] strikeLightning 执行 at " + loc);
        loc.getWorld().strikeLightning(loc);
        double damage = getDbl(params, "damage", 5.0);
        if (target != null && damage > 0) {
            target.damage(damage, player);
        }
        return true;
    }

    // ===== 爆炸 =====
    private static boolean triggerExplosion(Player player, LivingEntity target,
                                            Location hitLocation, Map<String, Object> params) {
        double power = getDbl(params, "power", 2.0);
        boolean fire = getBool(params, "fire", false);
        boolean breakBlocks = getBool(params, "break-blocks", false);
        String locationMode = getStr(params, "location-mode", "forward");

        Location loc;
        switch (locationMode.toLowerCase()) {
            case "target" -> {
                // 目标位置：如果有 target 就用 target 的位置，没有则回退到玩家前方
                loc = (target != null) ? target.getLocation()
                        : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(3));
            }
            case "hit" -> {
                // 命中点：如果有 hitLocation 就用，没有则回退到目标位置，再没有就玩家前方
                loc = (hitLocation != null) ? hitLocation
                        : (target != null ? target.getLocation()
                        : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(3)));
            }
            default -> {
                // forward：玩家前方
                loc = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(3));
            }
        }

        if (loc.getWorld() == null) return false;
        loc.getWorld().createExplosion(loc, (float) power, fire, breakBlocks, player);
        return true;
    }

    // ===== 自身药水 =====
    private static boolean applyPotionSelf(Player player, Map<String, Object> params) {
        String effectName = getStr(params, "effect", "SPEED");
        int amplifier = getInt(params, "amplifier", 0);
        int duration = getInt(params, "duration-seconds", 10) * 20;

        PotionEffectType type = PotionEffectType.getByName(effectName);
        if (type == null) return false;

        player.addPotionEffect(new PotionEffect(type, duration, amplifier, true, true));
        return true;
    }

    // ===== 命中药水 =====
    private static boolean applyPotionHit(LivingEntity target, Map<String, Object> params) {
        if (target == null) return false;
        String effectName = getStr(params, "effect", "SLOWNESS");
        int amplifier = getInt(params, "amplifier", 0);
        int duration = getInt(params, "duration-seconds", 5) * 20;

        PotionEffectType type = PotionEffectType.getByName(effectName);
        if (type == null) return false;

        target.addPotionEffect(new PotionEffect(type, duration, amplifier, true, true));
        return true;
    }

    // ===== 治疗 =====
    private static boolean healPlayer(Player player, Map<String, Object> params) {
        double amount = getDbl(params, "amount", 4.0);
        double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + amount);
        player.setHealth(newHealth);
        return true;
    }

    // ===== 饱食 =====
    private static boolean feedPlayer(Player player, Map<String, Object> params) {
        int hunger = getInt(params, "hunger", 6);
        float saturation = getFlt(params, "saturation", 8.0f);

        int newHunger = Math.min(20, player.getFoodLevel() + hunger);
        player.setFoodLevel(newHunger);

        float newSat = Math.min(player.getFoodLevel(), player.getSaturation() + saturation);
        player.setSaturation(newSat);
        return true;
    }

    // ===== 吃完返还容器 =====
    private static boolean giveContainer(Player player, Map<String, Object> params) {
        String containerName = getStr(params, "container", "BOWL");
        Material material;
        try {
            material = Material.valueOf(containerName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
        int amount = Math.max(1, getInt(params, "amount", 1));

        var leftover = player.getInventory().addItem(new ItemStack(material, amount));
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
        return true;
    }

    // ===== 粒子 =====
    private static boolean spawnParticle(Player player, LivingEntity target,
                                         Location hitLocation, Map<String, Object> params) {
        String particleName = getStr(params, "particle", "FLAME");
        int count = getInt(params, "count", 10);
        double radius = getDbl(params, "radius", 1.0);

        Particle particle;
        try {
            particle = Particle.valueOf(particleName);
        } catch (IllegalArgumentException e) {
            return false;
        }

        Location loc;
        if (hitLocation != null) {
            loc = hitLocation.clone().add(0, 0.5, 0);
        } else if (target != null) {
            loc = target.getLocation().add(0, 1, 0);
        } else {
            loc = player.getLocation().add(0, 1, 0);
        }

        if (loc.getWorld() == null) return false;
        loc.getWorld().spawnParticle(particle, loc, count, radius, 0.5, radius, 0);
        return true;
    }

    // ===== 声音 =====
    private static boolean playSound(Player player, Map<String, Object> params) {
        String soundKey = getStr(params, "sound", "entity.player.levelup");
        float volume = getFlt(params, "volume", 1.0f);
        float pitch = getFlt(params, "pitch", 1.0f);

        // 先尝试 Sound 枚举，失败则用字符串 key
        try {
            Sound sound = Sound.valueOf(soundKey.toUpperCase().replace('.', '_'));
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            player.playSound(player.getLocation(), soundKey, volume, pitch);
        }
        return true;
    }

    // ===== 传送 =====
    private static boolean teleportForward(Player player, Map<String, Object> params) {
        double distance = getDbl(params, "distance", 10.0);
        Vector dir = player.getEyeLocation().getDirection();

        Location target = player.getEyeLocation().add(dir.multiply(distance));
        // 简单的防卡：如果目标位置是实心方块，向上找
        for (int i = 0; i < 3; i++) {
            Location check = target.clone().add(0, i, 0);
            if (check.getBlock().isPassable()
                    && check.clone().add(0, 1, 0).getBlock().isPassable()) {
                target = check;
                break;
            }
        }
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());

        // 末影粒子
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(),
                20, 0.3, 1, 0.3, 0.1);
        player.teleport(target);
        player.getWorld().spawnParticle(Particle.PORTAL, target,
                20, 0.3, 1, 0.3, 0.1);
        return true;
    }

    // ===== 击退 =====
    private static boolean knockbackTarget(Player player, LivingEntity target,
                                           Map<String, Object> params) {
        if (target == null) return false;
        double strength = getDbl(params, "strength", 1.5);
        double vertical = getDbl(params, "vertical", 0.5);

        Vector dir = target.getLocation().toVector().subtract(player.getLocation().toVector())
                .normalize().multiply(strength);
        dir.setY(vertical);
        target.setVelocity(dir);
        return true;
    }

    // ===== 体积变化 =====
    private static boolean applyScale(Player player, LivingEntity target,
                                      Map<String, Object> params) {
        String targetMode = getStr(params, "target", "hit");
        double scale = getDbl(params, "scale", 2.0);
        int duration = getInt(params, "duration-seconds", 10);

        LivingEntity entity = "self".equalsIgnoreCase(targetMode) ? player : target;
        if (entity == null) return false;
        if (scale <= 0) scale = 1.0;

        // 用 AttributeModifier 修改 scale 属性（兼容新旧属性名）
        Attribute scaleAttrType = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("scale"));
        if (scaleAttrType == null) {
            scaleAttrType = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.scale"));
        }
        if (scaleAttrType == null) return false;

        var scaleAttr = entity.getAttribute(scaleAttrType);
        if (scaleAttr == null) return false;

        NamespacedKey key = new NamespacedKey(
                RPGForgePlugin.instance(), "rpgforge-scale");
        // 先移除旧的 modifier
        for (var mod : scaleAttr.getModifiers()) {
            if (key.equals(mod.getKey())) {
                scaleAttr.removeModifier(mod);
            }
        }

        // 计算倍率差：scale=2.0 → modifier 加 +1.0（因为 base 是 1.0，ADD_NUMBER 模式）
        double modifierValue = scale - 1.0;
        scaleAttr.addTransientModifier(new org.bukkit.attribute.AttributeModifier(
                key, modifierValue,
                org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));

        // 定时还原
        if (duration > 0) {
            final LivingEntity finalEntity = entity;
            final NamespacedKey finalKey = key;
            Bukkit.getScheduler().runTaskLater(RPGForgePlugin.instance(), () -> {
                if (!finalEntity.isValid() || finalEntity.isDead()) return;
                // 在 lambda 内重新获取 scale 属性（避免非 final 变量问题）
                Attribute attrType = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("scale"));
                if (attrType == null) {
                    attrType = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.scale"));
                }
                if (attrType == null) return;
                var attr = finalEntity.getAttribute(attrType);
                if (attr == null) return;
                for (var mod : attr.getModifiers()) {
                    if (finalKey.equals(mod.getKey())) {
                        attr.removeModifier(mod);
                    }
                }
            }, duration * 20L);
        }

        return true;
    }

    // ===== 火球 =====
    private static boolean launchFireball(Player player, Map<String, Object> params) {
        double yield = getDbl(params, "yield", 1.0);

        Vector direction = player.getEyeLocation().getDirection();
        Fireball fireball = player.launchProjectile(Fireball.class, direction);
        fireball.setYield((float) yield);
        fireball.setShooter(player);
        return true;
    }

    // ===== 箭矢 =====
    private static boolean launchArrow(Player player, Map<String, Object> params) {
        double damage = getDbl(params, "damage", 2.0);
        double speed = getDbl(params, "speed", 2.0);

        Arrow arrow = player.launchProjectile(Arrow.class);
        arrow.setDamage(damage);
        arrow.setVelocity(arrow.getVelocity().multiply(speed / 3.0));
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setShooter(player);
        return true;
    }

    // ===== 范围伤害 =====
    private static boolean aoeDamage(Player player, Map<String, Object> params) {
        double radius = getDbl(params, "radius", 5.0);
        double damage = getDbl(params, "damage", 5.0);

        Collection<LivingEntity> nearby = player.getWorld().getNearbyEntitiesByType(
                LivingEntity.class, player.getLocation(), radius);
        for (LivingEntity entity : nearby) {
            if (entity.equals(player)) continue;
            entity.damage(damage, player);
        }
        // 视觉效果
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(),
                8, radius * 0.5, 1, radius * 0.5, 0.1);
        return true;
    }

    // ===== 范围药水 =====
    private static boolean aoePotion(Player player, Map<String, Object> params) {
        double radius = getDbl(params, "radius", 5.0);
        String effectName = getStr(params, "effect", "SLOWNESS");
        int amplifier = getInt(params, "amplifier", 0);
        int duration = getInt(params, "duration-seconds", 5) * 20;

        PotionEffectType type = PotionEffectType.getByName(effectName);
        if (type == null) return false;

        PotionEffect effect = new PotionEffect(type, duration, amplifier, true, true);
        Collection<LivingEntity> nearby = player.getWorld().getNearbyEntitiesByType(
                LivingEntity.class, player.getLocation(), radius);
        for (LivingEntity entity : nearby) {
            if (entity.equals(player)) continue;
            entity.addPotionEffect(effect);
        }
        return true;
    }

    // ===== 燃烧 =====
    private static boolean flameTarget(LivingEntity target, Map<String, Object> params) {
        if (target == null) return false;
        int seconds = getInt(params, "seconds", 5);
        target.setFireTicks(seconds * 20);
        return true;
    }

    // ===== 漂浮 =====
    private static boolean levitationTarget(Player player, LivingEntity target,
                                            Map<String, Object> params) {
        String targetMode = getStr(params, "target", "hit");
        int amplifier = getInt(params, "amplifier", 1);
        int duration = getInt(params, "duration-seconds", 3) * 20;

        LivingEntity entity = "self".equalsIgnoreCase(targetMode) ? player : target;
        if (entity == null) return false;

        PotionEffectType type = PotionEffectType.getByName("LEVITATION");
        if (type == null) return false;
        entity.addPotionEffect(new PotionEffect(type, duration, amplifier, true, true));
        return true;
    }

    // ===== 冲刺 =====
    private static boolean dashForward(Player player, Map<String, Object> params) {
        double distance = getDbl(params, "distance", 6.0);
        Vector dir = player.getEyeLocation().getDirection().setY(0).normalize();
        if (dir.lengthSquared() < 0.001) return false;

        Vector velocity = dir.multiply(distance * 0.3);
        velocity.setY(0.2); // 轻微上抛防止贴地
        player.setVelocity(velocity);

        // 冲刺粒子
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(),
                10, 0.3, 0.5, 0.3, 0.1);
        return true;
    }

    // ===== 吸引 =====
    private static boolean attractEntities(Player player, Map<String, Object> params) {
        double radius = getDbl(params, "radius", 8.0);
        double strength = getDbl(params, "strength", 0.8);

        Collection<LivingEntity> nearby = player.getWorld().getNearbyEntitiesByType(
                LivingEntity.class, player.getLocation(), radius);
        for (LivingEntity entity : nearby) {
            if (entity.equals(player)) continue;
            Vector dir = player.getLocation().toVector().subtract(entity.getLocation().toVector())
                    .normalize().multiply(strength);
            dir.setY(strength * 0.3);
            entity.setVelocity(dir);
        }
        return true;
    }

    // ===== 排斥 =====
    private static boolean repulseEntities(Player player, Map<String, Object> params) {
        double radius = getDbl(params, "radius", 5.0);
        double strength = getDbl(params, "strength", 1.5);

        Collection<LivingEntity> nearby = player.getWorld().getNearbyEntitiesByType(
                LivingEntity.class, player.getLocation(), radius);
        for (LivingEntity entity : nearby) {
            if (entity.equals(player)) continue;
            Vector dir = entity.getLocation().toVector().subtract(player.getLocation().toVector())
                    .normalize().multiply(strength);
            dir.setY(strength * 0.6);
            entity.setVelocity(dir);
        }

        // 冲击波粒子
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 0.5, 0),
                12, 0.5, 0, 0.5, 0.2);
        return true;
    }

    // ===== 护盾 =====
    private static boolean applyShield(Player player, Map<String, Object> params) {
        double amount = getDbl(params, "amount", 4.0);
        int duration = getInt(params, "duration-seconds", 15) * 20;

        PotionEffectType type = PotionEffectType.getByName("ABSORPTION");
        if (type == null) return false;
        // ABSORPTION 的 amplifier 决定金色心数量，但我们直接用 amount 控制
        // 计算 amplifier：每级 = 2 颗心 = 4 点吸收
        int amplifier = (int) Math.max(0, Math.round(amount / 4.0) - 1);
        player.addPotionEffect(new PotionEffect(type, duration, amplifier, true, true));
        return true;
    }

    // ===== 消耗金钱 =====
    private static boolean economyCost(Player player, Map<String, Object> params) {
        double amount = getDbl(params, "amount", 100.0);
        // 简单实现：尝试从玩家余额扣除（需要 Vault 经济插件，这里做软依赖）
        try {
            var econ = RPGForgePlugin.instance().economy();
            if (econ == null) {
                player.sendMessage("§c服务器未安装经济插件，无法扣除金币");
                return false;
            }
            if (econ.getBalance(player) >= amount) {
                econ.withdrawPlayer(player, amount);
                return true;
            } else {
                String failMsg = getStr(params, "fail-message", "&c金币不足！");
                player.sendMessage(failMsg.replace('&', '§'));
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ===== 工具方法 =====
    private static String getStr(Map<String, Object> params, String key, String def) {
        Object v = params.get(key);
        return v != null ? v.toString() : def;
    }

    private static int getInt(Map<String, Object> params, String key, int def) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.intValue();
        return def;
    }

    private static double getDbl(Map<String, Object> params, String key, double def) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return def;
    }

    private static float getFlt(Map<String, Object> params, String key, float def) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.floatValue();
        return def;
    }

    private static boolean getBool(Map<String, Object> params, String key, boolean def) {
        Object v = params.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return def;
    }

    // ============================================================
    //  拔刀剑风格特效
    // ============================================================

    // ===== 领域斩（残月斩风格：360° 圆形 AOE + 双层刀光圆环） =====
    private static boolean domainSlash(Player player, Map<String, Object> params) {
        double radius = getDbl(params, "radius", 4.0);
        double damage = getDbl(params, "damage", 6.0);
        Location center = player.getLocation();

        int hit = 0;
        for (LivingEntity entity : player.getWorld().getNearbyEntitiesByType(
                LivingEntity.class, center, radius)) {
            if (entity.equals(player)) continue;
            entity.damage(damage, player);
            hit++;
        }

        // 双层刀光圆环（内环 + 外环 SWEEP）
        for (int ring = 0; ring < 2; ring++) {
            double r = radius * (ring == 0 ? 0.6 : 0.95);
            for (int i = 0; i < 24; i++) {
                double angle = 2 * Math.PI * i / 24;
                Location point = center.clone().add(
                        Math.cos(angle) * r, 1.0 + ring * 0.4, Math.sin(angle) * r);
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, point, 1, 0, 0, 0, 0);
            }
        }
        // 红色尘埃地面环
        for (int i = 0; i < 30; i++) {
            double angle = 2 * Math.PI * i / 30;
            Location point = center.clone().add(
                    Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius);
            player.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.RED, 1.4f));
        }
        player.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
        player.getWorld().playSound(center, Sound.ENTITY_WITHER_HURT, 0.6f, 1.4f);

        if (hit > 0) {
            player.sendActionBar(net.kyori.adventure.text.Component
                    .text("领域展开 — " + hit + " 个目标")
                    .color(net.kyori.adventure.text.format.NamedTextColor.RED));
        }
        return true;
    }

    // ===== 气刃（飞行剑气，逐刻推进 + 穿透命中） =====
    private static boolean bladeWave(Player player, Map<String, Object> params) {
        double damage = getDbl(params, "damage", 4.0);
        double distance = getDbl(params, "distance", 12.0);
        double speed = getDbl(params, "speed", 1.2);

        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        player.getWorld().playSound(start, Sound.ITEM_TRIDENT_THROW, 1.0f, 1.5f);

        new org.bukkit.scheduler.BukkitRunnable() {
            double traveled = 0;
            final java.util.Set<UUID> hitEntities =
                    java.util.Collections.newSetFromMap(new java.util.HashMap<>());

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                traveled += speed;
                Location pos = start.clone().add(dir.clone().multiply(traveled));
                if (traveled > distance || pos.getBlock().getType().isSolid()) {
                    player.getWorld().spawnParticle(Particle.CRIT, pos, 15, 0.3, 0.3, 0.3, 0.2);
                    cancel();
                    return;
                }
                // 拖尾：CRIT 火花 + SWEEP 核心
                player.getWorld().spawnParticle(Particle.CRIT, pos, 8, 0.15, 0.15, 0.15, 0.1);
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, pos, 1, 0, 0, 0, 0);
                // 命中判定（1.5 格范围，穿透，去重）
                for (LivingEntity entity : player.getWorld().getNearbyEntitiesByType(
                        LivingEntity.class, pos, 1.5)) {
                    if (entity.equals(player)) continue;
                    if (!hitEntities.add(entity.getUniqueId())) continue;
                    entity.damage(damage, player);
                    player.getWorld().spawnParticle(Particle.CRIT,
                            entity.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.15);
                }
            }
        }.runTaskTimer(RPGForgePlugin.instance(), 1, 1);
        return true;
    }

    // ============================================================
    //  工具连锁
    // ============================================================

    // ===== 连锁采集（BFS 同类方块连锁破坏） =====
    static boolean chainBreak(Player player, org.bukkit.block.Block origin, Map<String, Object> params) {
        int count = getInt(params, "count", 8);
        int radius = (int) Math.max(1, Math.round(getDbl(params, "radius", 2.0)));

        java.util.List<org.bukkit.block.Block> chain = findChain(origin, count, radius);
        if (chain.isEmpty()) return false;
        ItemStack tool = player.getInventory().getItemInMainHand();

        int broken = 0;
        for (org.bukkit.block.Block b : chain) {
            // breakNaturally 不触发 BlockBreakEvent，天然防止连锁递归
            if (b.breakNaturally(tool)) {
                broken++;
                b.getWorld().spawnParticle(Particle.CRIT,
                        b.getLocation().add(0.5, 0.5, 0.5), 5, 0.25, 0.25, 0.25, 0.05);
            }
        }
        if (broken > 0) {
            player.getWorld().playSound(origin.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
            player.sendActionBar(net.kyori.adventure.text.Component
                    .text("连锁采集 × " + broken)
                    .color(net.kyori.adventure.text.format.NamedTextColor.GOLD));
        }
        return broken > 0;
    }

    /** 六向 BFS 搜索同类方块（限制数量与半径） */
    private static java.util.List<org.bukkit.block.Block> findChain(
            org.bukkit.block.Block origin, int count, int radius) {
        java.util.List<org.bukkit.block.Block> result = new java.util.ArrayList<>();
        java.util.Deque<org.bukkit.block.Block> queue = new java.util.ArrayDeque<>();
        java.util.Set<org.bukkit.block.Block> visited = new java.util.HashSet<>();
        org.bukkit.Material type = origin.getType();
        int[] dx = {1, -1, 0, 0, 0, 0};
        int[] dy = {0, 0, 1, -1, 0, 0};
        int[] dz = {0, 0, 0, 0, 1, -1};

        queue.add(origin);
        visited.add(origin);
        while (!queue.isEmpty() && result.size() < count) {
            org.bukkit.block.Block cur = queue.poll();
            for (int i = 0; i < 6; i++) {
                org.bukkit.block.Block next = cur.getRelative(dx[i], dy[i], dz[i]);
                if (!visited.add(next)) continue;
                if (!next.getType().equals(type)) continue;
                if (next.getLocation().distance(origin.getLocation()) > radius) continue;
                result.add(next);
                queue.add(next);
                if (result.size() >= count) break;
            }
        }
        return result;
    }

    // ===== 连锁耕地（以目标方块为中心开垦 size*2+1 的方形） =====
    static boolean chainTill(Player player, org.bukkit.block.Block center, Map<String, Object> params) {
        int size = Math.max(1, getInt(params, "size", 1));
        int tilled = 0;
        for (int dx = -size; dx <= size; dx++) {
            for (int dz = -size; dz <= size; dz++) {
                org.bukkit.block.Block b = center.getRelative(dx, 0, dz);
                org.bukkit.block.Block above = b.getRelative(0, 1, 0);
                if ((b.getType() == org.bukkit.Material.DIRT || b.getType() == org.bukkit.Material.GRASS_BLOCK)
                        && above.getType().isAir()) {
                    b.setType(org.bukkit.Material.FARMLAND);
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                            above.getLocation().add(0.5, 0.3, 0.5), 3, 0.2, 0.1, 0.2, 0);
                    tilled++;
                }
            }
        }
        if (tilled > 0) {
            player.getWorld().playSound(center.getLocation(),
                    Sound.ITEM_HOE_TILL, 1.0f, 1.0f);
        }
        return tilled > 0;
    }

    // ===== 连锁收获（半径内成熟作物收割 + 自动回种） =====
    private static final java.util.Set<org.bukkit.Material> HARVESTABLE = java.util.Set.of(
            org.bukkit.Material.WHEAT, org.bukkit.Material.CARROTS, org.bukkit.Material.POTATOES,
            org.bukkit.Material.BEETROOTS, org.bukkit.Material.NETHER_WART,
            org.bukkit.Material.SWEET_BERRY_BUSH, org.bukkit.Material.COCOA);

    static boolean chainHarvest(Player player, org.bukkit.block.Block center, Map<String, Object> params) {
        double radius = getDbl(params, "radius", 3.0);
        ItemStack tool = player.getInventory().getItemInMainHand();
        int harvested = 0;

        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx * dx + dz * dz + dy * dy > radius * radius) continue;
                    org.bukkit.block.Block b = center.getRelative(dx, dy, dz);
                    if (!HARVESTABLE.contains(b.getType())) continue;
                    if (!(b.getBlockData() instanceof org.bukkit.block.data.Ageable age)) continue;
                    if (age.getAge() < age.getMaximumAge()) continue;
                    // 掉落成熟产物，重置回幼苗（自动回种）
                    for (ItemStack drop : b.getDrops(tool)) {
                        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
                    }
                    age.setAge(0);
                    b.setBlockData(age);
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                            b.getLocation().add(0.5, 0.4, 0.5), 4, 0.25, 0.15, 0.25, 0);
                    harvested++;
                }
            }
        }
        if (harvested > 0) {
            player.getWorld().playSound(center.getLocation(),
                    Sound.BLOCK_CROP_BREAK, 0.8f, 1.2f);
            player.sendActionBar(net.kyori.adventure.text.Component
                    .text("连锁收获 × " + harvested)
                    .color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
        }
        return harvested > 0;
    }

    // ============================================================
    //  击飞 / 堡垒
    // ============================================================

    // ===== 击飞（纯垂直抬升，不击退；原版攻击自带的击退不受影响） =====
    private static boolean launchUp(LivingEntity target, Map<String, Object> params) {
        if (target == null) return false;
        double lift = getDbl(params, "lift", 0.8);
        target.setVelocity(new Vector(0, lift, 0));
        target.getWorld().spawnParticle(Particle.CLOUD,
                target.getLocation(), 10, 0.3, 0.1, 0.3, 0.05);
        target.getWorld().playSound(target.getLocation(),
                Sound.ENTITY_IRON_GOLEM_ATTACK, 0.7f, 1.2f);
        return true;
    }

    // ===== 堡垒（周期回复吸收值，封顶不叠加） =====
    private static boolean fortressAbsorption(Player player, Map<String, Object> params) {
        double amount = getDbl(params, "amount", 2.0);
        double cap = getDbl(params, "cap", 20.0);
        double current = player.getAbsorptionAmount();
        if (current >= cap) return true;
        player.setAbsorptionAmount(Math.min(cap, current + amount));
        player.getWorld().playSound(player.getLocation(),
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.6f);
        return true;
    }
}
