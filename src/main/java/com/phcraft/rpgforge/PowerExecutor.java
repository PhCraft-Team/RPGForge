package com.phcraft.rpgforge;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
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
}
