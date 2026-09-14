package com.phcraft.rpgforge;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个条件实例 —— 条件类型 + 参数 + 失败消息。
 */
public class RPGCondition {

    private final ConditionType type;
    private final Map<String, Object> params;
    private String failMessage; // 失败时的提示消息（null 表示不提示）

    public RPGCondition(ConditionType type) {
        this.type = type;
        this.params = new LinkedHashMap<>(type.defaultParams());
        this.failMessage = null;
    }

    public ConditionType type() { return type; }
    public Map<String, Object> params() { return params; }
    public String failMessage() { return failMessage; }
    public void setFailMessage(String msg) { this.failMessage = msg; }

    public Object param(String key) { return params.get(key); }
    public String paramString(String key, String def) {
        Object v = params.get(key);
        return v != null ? v.toString() : def;
    }
    public int paramInt(String key, int def) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.intValue();
        return def;
    }
    public double paramDouble(String key, double def) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return def;
    }
    public boolean paramBool(String key, boolean def) {
        Object v = params.get(key);
        if (v instanceof Boolean b) return b;
        return def;
    }
    public void setParam(String key, Object value) {
        params.put(key, value);
    }

    // ===== 序列化 =====
    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type.id());
        if (!params.isEmpty()) {
            map.put("params", new LinkedHashMap<>(params));
        }
        if (failMessage != null && !failMessage.isEmpty()) {
            map.put("fail-message", failMessage);
        }
        return map;
    }

    public static RPGCondition deserialize(Map<String, Object> map) {
        String typeId = (String) map.get("type");
        ConditionType type = ConditionType.byId(typeId);
        if (type == null) return null;

        RPGCondition cond = new RPGCondition(type);

        Object paramsObj = map.get("params");
        if (paramsObj instanceof ConfigurationSection sec) {
            for (String key : sec.getKeys(true)) {
                cond.params.put(key, sec.get(key));
            }
        } else if (paramsObj instanceof Map<?, ?> pm) {
            for (var entry : pm.entrySet()) {
                cond.params.put(entry.getKey().toString(), entry.getValue());
            }
        }

        Object failMsg = map.get("fail-message");
        if (failMsg != null) {
            cond.failMessage = failMsg.toString();
        }

        return cond;
    }
}
