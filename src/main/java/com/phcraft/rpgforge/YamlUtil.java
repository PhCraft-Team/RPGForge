package com.phcraft.rpgforge;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * YAML 读取工具。
 *
 * <p>Bukkit 的 ConfigurationSection.get() 对嵌套节点返回 MemorySection
 * （不实现 Map 接口），导致 deserialize 里 instanceof Map 判断静默失败。
 * deepValue 递归把嵌套 section（包括 List 内的）转换为纯 Map/List 结构。
 */
public final class YamlUtil {

    private YamlUtil() {
    }

    public static Object deepValue(Object o) {
        if (o instanceof ConfigurationSection cs) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (String k : cs.getKeys(false)) {
                m.put(k, deepValue(cs.get(k)));
            }
            return m;
        }
        if (o instanceof List<?> l) {
            List<Object> out = new ArrayList<>(l.size());
            for (Object e : l) {
                out.add(deepValue(e));
            }
            return out;
        }
        return o;
    }

    public static Map<String, Object> deepMap(ConfigurationSection cs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : cs.getKeys(false)) {
            m.put(k, deepValue(cs.get(k)));
        }
        return m;
    }
}
