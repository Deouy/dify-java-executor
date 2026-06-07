package com.dify.workflow.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java 8 兼容性工具类。
 *
 * <p>提供 Java 9+ 集合工厂（List.of/Map.of/Set.of）和
 * Java 11+ 文件 API（Files.readString）的 Java 8 等价实现。</p>
 *
 * <p>用法：
 * <pre>{@code
 *   // 原 List.of("a", "b", "c")
 *   List<String> list = Java8Compat.listOf("a", "b", "c");
 *
 *   // 原 Map.of("k1", "v1", "k2", "v2")
 *   Map<String, Integer> map = Java8Compat.mapOf("k1", 1, "k2", 2);
 *
 *   // 原 Files.readString(path)
 *   String content = Java8Compat.readString(path);
 * }</pre>
 *
 * <p>所有返回的集合都是不可变的（Collections.unmodifiableXxx 包装）。</p>
 */
public final class Java8Compat {

    private Java8Compat() {
        // 工具类不允许实例化
    }

    // ========== List 工厂 ==========

    /**
     * 创建不可变空 List。
     */
    public static <T> List<T> listOf() {
        return Collections.emptyList();
    }

    /**
     * 创建包含一个元素的不可变 List。
     */
    public static <T> List<T> listOf(T e1) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(e1)));
    }

    /**
     * 创建包含两个元素的不可变 List。
     */
    public static <T> List<T> listOf(T e1, T e2) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(e1, e2)));
    }

    /**
     * 创建包含三个元素的不可变 List。
     */
    public static <T> List<T> listOf(T e1, T e2, T e3) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(e1, e2, e3)));
    }

    /**
     * 创建包含多个元素的不可变 List（varargs）。
     */
    @SafeVarargs
    public static <T> List<T> listOf(T... elements) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(elements)));
    }

    // ========== Map 工厂 ==========

    /**
     * 创建不可变空 Map。
     */
    public static <K, V> Map<K, V> mapOf() {
        return Collections.emptyMap();
    }

    /**
     * 创建包含一对键值对的不可变 Map。
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1) {
        Map<K, V> map = new LinkedHashMap<>();
        map.put(k1, v1);
        return Collections.unmodifiableMap(map);
    }

    /**
     * 创建包含两对键值对的不可变 Map。
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
        Map<K, V> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return Collections.unmodifiableMap(map);
    }

    /**
     * 创建包含三对键值对的不可变 Map。
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3) {
        Map<K, V> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return Collections.unmodifiableMap(map);
    }

    /**
     * 创建包含多对键值对的不可变 Map（varargs 键值对）。
     * 参数数量必须为偶数。
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> mapOf(Object... kvs) {
        if (kvs.length % 2 != 0) {
            throw new IllegalArgumentException("Key-value pairs must be even, got " + kvs.length);
        }
        Map<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            map.put((K) kvs[i], (V) kvs[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    // ========== Set 工厂 ==========

    /**
     * 创建不可变空 Set。
     */
    public static <T> Set<T> setOf() {
        return Collections.emptySet();
    }

    /**
     * 创建包含一个元素的不可变 Set。
     */
    public static <T> Set<T> setOf(T e1) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(e1)));
    }

    /**
     * 创建包含多个元素的不可变 Set（varargs）。
     */
    @SafeVarargs
    public static <T> Set<T> setOf(T... elements) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(elements)));
    }

    /**
     * 创建包含多个元素的不可变 LinkedHashSet（保持插入顺序）。
     */
    @SafeVarargs
    public static <T> Set<T> linkedSetOf(T... elements) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(elements)));
    }

    // ========== 文件 API ==========

    /**
     * 读取文件全部内容为字符串（UTF-8）。
     * 等价于 Java 11+ 的 Files.readString(path)。
     */
    public static String readString(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /**
     * 读取文件全部内容为字符串（指定字符集）。
     */
    public static String readString(Path path, java.nio.charset.Charset charset) throws IOException {
        return new String(Files.readAllBytes(path), charset);
    }

    /**
     * 将字符串以 UTF-8 编码写入文件,覆盖现有内容。
     * 等价于 Java 11+ 的 {@link Files#writeString(Path, CharSequence)}。
     *
     * @param path 目标文件路径
     * @param content 要写入的字符串内容
     * @throws IOException 写入失败时抛出
     */
    public static void writeString(Path path, String content) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将字符串以 UTF-8 编码写入文件,可指定 {@link OpenOption} 行为。
     * 等价于 Java 11+ 的 {@link Files#writeString(Path, CharSequence, OpenOption...)}。
     *
     * <p>典型用法:追加写入(StandardOpenOption.APPEND)、截断(TRUNCATE_EXISTING)、
     * 创建(CREATE) 等。可变参数形式与 Files.write 完全兼容。</p>
     *
     * @param path 目标文件路径
     * @param content 要写入的字符串内容
     * @param options 文件打开选项(同 Files.write 的 OpenOption 语义)
     * @throws IOException 写入失败时抛出
     */
    public static void writeString(Path path, String content, OpenOption... options) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8), options);
    }

    // ========== 路径工厂 ==========

    /**
     * 创建 Path。等价于 Java 11+ 的 Path.of(first, more)。
     */
    public static Path pathOf(String first, String... more) {
        if (more == null || more.length == 0) {
            return java.nio.file.Paths.get(first);
        }
        String[] combined = new String[more.length + 1];
        combined[0] = first;
        System.arraycopy(more, 0, combined, 1, more.length);
        return java.nio.file.Paths.get(first, more);
    }

    // ========== 字符串辅助 ==========

    /**
     * 判断字符串是否为空白（Java 11+ String.isBlank 的等价实现）。
     */
    public static boolean isBlank(String s) {
        if (s == null) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // ========== 集合不可变包装 ==========

    /**
     * 将现有集合包装为不可变 List。
     */
    public static <T> List<T> unmodifiableList(List<T> list) {
        return Collections.unmodifiableList(new ArrayList<>(list));
    }

    /**
     * 将现有 Map 包装为不可变 Map。
     */
    public static <K, V> Map<K, V> unmodifiableMap(Map<K, V> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    // ========== Nashorn 兼容 ==========

    /**
     * 将 Nashorn 返回的 ScriptObjectMirror 转成普通 Map。
     * 反射调用避免 ct.sym 限制直接 import {@code jdk.nashorn.api.scripting.ScriptObjectMirror}。
     *
     * <p>Code 节点用 Nashorn eval JS 函数,返回值是 ScriptObjectMirror(不是 java.util.Map)。
     * 此方法把 ScriptObjectMirror 通过反射 {@code .to(Map.class)} 转成普通 Map,方便后续取值。</p>
     *
     * <p>关键:遇到 Nashorn 数组(JS 数组)时返回 null,让调用方走 toList 路径(否则数组会被错转成整数 key 的 Map)。</p>
     *
     * @param obj Nashorn eval 返回值(可能是 ScriptObjectMirror / Map / 其他)
     * @return 普通 Map;若 obj 是数组则返回 null(走 toList);若既不是 ScriptObjectMirror 也不是 Map,返回 null
     */
    public static Map<String, Object> toMap(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) obj;
            return m;
        }
        // Nashorn 反射:ScriptObjectMirror.to(Map.class)
        try {
            Class<?> somClass = Class.forName("jdk.nashorn.api.scripting.ScriptObjectMirror");
            if (somClass.isInstance(obj)) {
                // 关键:数组返回 null(让调用方走 toList)
                Object isArray = somClass.getMethod("isArray").invoke(obj);
                if (Boolean.TRUE.equals(isArray)) {
                    return null;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) somClass.getMethod("to", Class.class)
                    .invoke(obj, Map.class);
                return m;
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                 | java.lang.reflect.InvocationTargetException e) {
            // Nashorn 不可用或 obj 不是 ScriptObjectMirror,返回 null
        }
        return null;
    }

    /**
     * 将 Nashorn 返回的 ScriptObjectMirror(数组形式)转成普通 Java List。
     * 反射调用避免 ct.sym 限制。
     *
     * <p>Code 节点返回的 JS 数组是 Nashorn ScriptObjectMirror(不是 java.util.List),
     * 下游 IterationNode 等用 {@code instanceof List} 判断会失败。需要先把数组转 List。</p>
     *
     * <p>如果 obj 元素本身也是 ScriptObjectMirror(对象数组),会递归转成 Map,
     * 数字/字符串等基本类型保持原样。</p>
     *
     * @param obj Nashorn eval 返回值(可能是 ScriptObjectMirror / List / 其他)
     * @return 普通 List;若 obj 既不是 ScriptObjectMirror 也不是 List/数组,返回 null
     */
    public static List<Object> toList(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> l = (List<Object>) obj;
            return l;
        }
        if (obj instanceof Object[]) {
            // 数组转 List,递归转元素
            Object[] arr = (Object[]) obj;
            List<Object> result = new ArrayList<>(arr.length);
            for (Object e : arr) {
                result.add(unwrapNashorn(e));
            }
            return result;
        }
        // Nashorn 反射:ScriptObjectMirror.to(List.class) 或转数组后转 List
        try {
            Class<?> somClass = Class.forName("jdk.nashorn.api.scripting.ScriptObjectMirror");
            if (somClass.isInstance(obj)) {
                // 优先用 to(List.class)
                try {
                    @SuppressWarnings("unchecked")
                    List<Object> l = (List<Object>) somClass.getMethod("to", Class.class)
                        .invoke(obj, List.class);
                    if (l != null) {
                        return l;
                    }
                } catch (Exception ignore) {
                    // to(List) 失败,fallback
                }
                // fallback:先转数组再转 List
                Object arr = obj.getClass().getMethod("toArray").invoke(obj);
                if (arr instanceof Object[]) {
                    return toList(arr);
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                 | java.lang.reflect.InvocationTargetException e) {
            // Nashorn 不可用或 obj 不是 ScriptObjectMirror
        }
        return null;
    }

    /**
     * 把"伪数组"Map(整数 key,值列表)转成 Java List。
     * Nashorn eval 返回 JS 数组时会自动转成 HashMap(或 LinkedHashMap),
     * 破坏下游 instanceof List 判断。这个方法识别这种 HashMap 并转 List。
     *
     * <p>识别规则更严格:keys 必须形成连续 0..n-1 整数序列(顺序无所谓)。
     * 若 keys 是 Number(自动 Number 序列);若是 String,必须是 "0","1","2",...,
     * 不能是 "a","b" 等有语义名字(避免误转 JS 对象的键)。</p>
     *
     * @param value 可能是 Map(伪数组)或 List 或 其他
     * @return List 如果 value 是伪数组 Map;否则原样返回
     */
    @SuppressWarnings("unchecked")
    public static Object arrayLikeMapToList(Object value) {
        if (value instanceof List) {
            return value;
        }
        if (value instanceof Map) {
            Map<Object, Object> m = (Map<Object, Object>) value;
            if (m.isEmpty()) {
                return new ArrayList<>();
            }
            int size = m.size();
            boolean keysAreNumber = m.keySet().iterator().next() instanceof Number;
            // 检查 keys 是不是 {0, 1, 2, ..., size-1} 的完整集合
            for (int i = 0; i < size; i++) {
                Object expectedKey = keysAreNumber ? (Object) (Integer) i : String.valueOf(i);
                if (!m.containsKey(expectedKey)) {
                    return value; // 不是完整的 0..n-1 序列,不是数组
                }
            }
            // 转 List 按 index 顺序
            List<Object> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                Object key = keysAreNumber ? (Object) (Integer) i : String.valueOf(i);
                result.add(m.get(key));
            }
            return result;
        }
        return value;
    }

    /**
     * 递归把 Nashorn ScriptObjectMirror 包装的 Map/Array 转成普通 Java Map/List。
     * 关键:先判断 ScriptObjectMirror.isArray() — 数组转 List(否则会被错转成 Map)
     * 基本类型(数字/字符串/布尔)保持原样。
     */
    @SuppressWarnings("unchecked")
    public static Object unwrapNashorn(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map || obj instanceof List || obj instanceof Object[]
            || obj instanceof Number || obj instanceof Boolean || obj instanceof String
            || obj instanceof Character) {
            // 已经是非 Nashorn 类型,直接处理容器
            if (obj instanceof List) {
                List<Object> result = new ArrayList<>(((List<?>) obj).size());
                for (Object e : (List<?>) obj) {
                    result.add(unwrapNashorn(e));
                }
                return result;
            }
            if (obj instanceof Map) {
                Map<Object, Object> src = (Map<Object, Object>) obj;
                Map<Object, Object> result = new HashMap<>(src.size());
                for (Map.Entry<Object, Object> e : src.entrySet()) {
                    result.put(e.getKey(), unwrapNashorn(e.getValue()));
                }
                return result;
            }
            if (obj instanceof Object[]) {
                Object[] arr = (Object[]) obj;
                List<Object> result = new ArrayList<>(arr.length);
                for (Object e : arr) {
                    result.add(unwrapNashorn(e));
                }
                return result;
            }
            return obj; // 基本类型
        }
        // 试 Nashorn 转换:先判断是否数组(否则会被错转成 Map)
        try {
            Class<?> somClass = Class.forName("jdk.nashorn.api.scripting.ScriptObjectMirror");
            if (somClass.isInstance(obj)) {
                // 关键:Nashorn 数组的 mirror.isArray() == true
                Object isArray = somClass.getMethod("isArray").invoke(obj);
                System.out.println("[DEBUG-unwrap] obj=" + obj.getClass().getSimpleName() + " isArray=" + isArray);
                if (Boolean.TRUE.equals(isArray)) {
                    return toList(obj);
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                 | java.lang.reflect.InvocationTargetException e) {
            // ignore
        }

        // 试 Nashorn 转换
        Map<String, Object> m = toMap(obj);
        if (m != null) {
            // 元素递归
            Map<String, Object> result = new HashMap<>(m.size());
            for (Map.Entry<String, Object> e : m.entrySet()) {
                result.put(e.getKey(), unwrapNashorn(e.getValue()));
            }
            return result;
        }
        List<Object> l = toList(obj);
        if (l != null) {
            List<Object> result = new ArrayList<>(l.size());
            for (Object e : l) {
                result.add(unwrapNashorn(e));
            }
            return result;
        }
        return obj; // 兜底
    }
}
