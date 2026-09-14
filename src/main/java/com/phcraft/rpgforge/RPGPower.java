package com.phcraft.rpgforge;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一个 Power 实例：属于某个 RPGItem，有类型、参数、绑定的 Trigger、触发条件。
 *
 * <p>同一个 RPGItem 上可以有多个 Power，每个 Power 有自己的 index（执行顺序）。
 */
public class RPGPower {

    private final PowerType type;
    private final Map<String, Object> params;
    private final Set<TriggerType> triggers;
    private final List<RPGCondition> conditions;
    private int index;
    private String failMessage; // 条件不满足时的提示（null = 不提示）

    public RPGPower(PowerType type) {
        this.type = type;
        this.params = new LinkedHashMap<>(type.defaultParams());
        this.triggers = EnumSet.noneOf(TriggerType.class);
        this.conditions = new ArrayList<>();
        // 默认绑定右键
        this.triggers.add(TriggerType.RIGHT_CLICK);
        this.failMessage = null;
    }

    public PowerType type() { return type; }
    public Map<String, Object> params() { return params; }
    public Set<TriggerType> triggers() { return triggers; }
    public List<RPGCondition> conditions() { return conditions; }
    public int index() { return index; }
    public void setIndex(int index) { this.index = index; }
    public String failMessage() { return failMessage; }
    public void setFailMessage(String msg) { this.failMessage = msg; }

    /** 获取参数值（自动转型为 int） */
    public int paramInt(String key, int def) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.intValue();
        return def;
    }

    /** 获取参数值（自动转型为 double） */
    public double paramDouble(String key, double def) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return def;
    }

    /** 获取参数值（自动转型为 float） */
    public float paramFloat(String key, float def) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.floatValue();
        return def;
    }

    /** 获取参数值（自动转型为 String） */
    public String paramString(String key, String def) {
        Object v = params.get(key);
        return v != null ? v.toString() : def;
    }

    /** 获取参数值（自动转型为 boolean） */
    public boolean paramBool(String key, boolean def) {
        Object v = params.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return def;
    }

    /** 序列化到 Map（用于 YAML 存储） */
    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type.id());
        map.put("triggers", triggers.stream().map(Enum::name).toList());
        map.put("params", new LinkedHashMap<>(params));

        if (!conditions.isEmpty()) {
            List<Map<String, Object>> condList = new ArrayList<>();
            for (RPGCondition cond : conditions) {
                condList.add(cond.serialize());
            }
            map.put("conditions", condList);
        }

        if (failMessage != null && !failMessage.isEmpty()) {
            map.put("fail-message", failMessage);
        }

        return map;
    }

    /** 从 Map 反序列化 */
    public static RPGPower deserialize(Map<String, Object> map) {
        String typeId = (String) map.get("type");
        PowerType type = PowerType.byId(typeId);
        if (type == null) return null;

        RPGPower power = new RPGPower(type);
        power.triggers.clear();

        Object triggersObj = map.get("triggers");
        if (triggersObj instanceof List<?> list) {
            for (Object o : list) {
                try {
                    power.triggers.add(TriggerType.valueOf(o.toString()));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        if (power.triggers.isEmpty()) {
            power.triggers.add(TriggerType.RIGHT_CLICK);
        }

        Object paramsObj = map.get("params");
        if (paramsObj instanceof Map<?, ?> pm) {
            for (var entry : pm.entrySet()) {
                power.params.put(entry.getKey().toString(), entry.getValue());
            }
        }

        // 条件列表
        Object condsObj = map.get("conditions");
        if (condsObj instanceof List<?> condList) {
            for (Object o : condList) {
                if (o instanceof Map<?, ?> cm) {
                    @SuppressWarnings("unchecked")
                    RPGCondition cond = RPGCondition.deserialize((Map<String, Object>) cm);
                    if (cond != null) {
                        power.conditions.add(cond);
                    }
                }
            }
        }

        // 失败消息
        Object failMsg = map.get("fail-message");
        if (failMsg != null) {
            power.failMessage = failMsg.toString();
        }

        return power;
    }

    /** 批量反序列化 */
    public static List<RPGPower> deserializeList(List<?> list) {
        List<RPGPower> result = new ArrayList<>();
        if (list == null) return result;
        int idx = 0;
        for (Object o : list) {
            if (o instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                RPGPower p = deserialize((Map<String, Object>) map);
                if (p != null) {
                    p.setIndex(idx++);
                    result.add(p);
                }
            }
        }
        return result;
    }
}
