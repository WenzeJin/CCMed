package com.wenzejin.gui;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wenzejin.core.Config;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;

/**
 * Discovers config fields from the Config class via reflection,
 * so the GUI auto-adapts when new {@code @JsonProperty} fields are added.
 */
public final class ConfigFieldDiscoverer {

    public enum FieldType { STRING, INT, BOOL, STRING_LIST, JSON_MAP }

    public enum Category {
        TASK("Task Info"),
        THREADING("Threading & Batching"),
        STOPPING("Stopping Criteria"),
        TARGET("Target & Arguments"),
        RULES("Rules & Generation"),
        OPTIONS("Options"),
        CUSTOM("Custom Parameters");

        public final String label;
        Category(String label) { this.label = label; }
    }

    public record ConfigFieldMeta(
        String name,
        String label,
        FieldType type,
        Category category,
        String[] comboValues,
        String hint
    ) {}

    private static final Map<String, Category> CATEGORY_MAP = Map.ofEntries(
        Map.entry("taskName",       Category.TASK),
        Map.entry("timeStamp",      Category.TASK),
        Map.entry("workspaceRoot",  Category.TASK),
        Map.entry("threadLimit",    Category.THREADING),
        Map.entry("batchSize",      Category.THREADING),
        Map.entry("miniBatchSize",  Category.THREADING),
        Map.entry("stopMode",       Category.STOPPING),
        Map.entry("sourceLimit",    Category.STOPPING),
        Map.entry("followUpLimit",  Category.STOPPING),
        Map.entry("workerType",     Category.TARGET),
        Map.entry("targetPath",     Category.TARGET),
        Map.entry("bfuncPath",      Category.TARGET),
        Map.entry("arguments",      Category.TARGET),
        Map.entry("rulesPath",      Category.RULES),
        Map.entry("bfuncPolicy",    Category.RULES),
        Map.entry("enumLimit",      Category.RULES),
        Map.entry("mtCore",         Category.RULES),
        Map.entry("chainLength",    Category.RULES),
        Map.entry("simpleMode",     Category.OPTIONS),
        Map.entry("storeMode",      Category.OPTIONS),
        Map.entry("detailData",     Category.OPTIONS)
    );

    private static final Map<String, String[]> COMBO_VALUES = Map.of(
        "stopMode",    new String[]{"limited", "panic", "limited+panic"},
        "workerType",  new String[]{"infuse", "infuse-old", "mg", "cmid", "standard"},
        "bfuncPolicy", new String[]{"enum", "random"},
        "mtCore",      new String[]{"ART", "chain"},
        "storeMode",   new String[]{"all", "fail", "none"}
    );

    private static final Map<String, String> HINTS = Map.of(
        "bfuncPath",    "Leave empty if the SUT does not use Bfunction support",
        "enumLimit",    "Max Integer.MAX_VALUE = unlimited",
        "chainLength",  "Only used when mtCore = chain (2-6)",
        "simpleMode",   "Enable simplified test case generation",
        "detailData",   "Export follow-up detail data in the workspace",
        "stopMode",     "limited = run all up to limits; panic = stop on first failure"
    );

    static List<ConfigFieldMeta> discover() {
        List<ConfigFieldMeta> fields = new ArrayList<>();
        Field[] declaredFields = Config.class.getDeclaredFields();

        for (Field f : declaredFields) {
            JsonProperty jp = f.getAnnotation(JsonProperty.class);
            if (jp == null) continue;

            String name = f.getName();
            // Ensure "otherArgs" appears last but is still a known field with JsonProperty
            String label = camelToLabel(name);
            FieldType type = mapType(f);
            Category cat = CATEGORY_MAP.getOrDefault(name, Category.CUSTOM);
            String[] combos = COMBO_VALUES.get(name);
            String hint = HINTS.getOrDefault(name, "");

            fields.add(new ConfigFieldMeta(name, label, type, cat, combos, hint));
        }

        // "otherArgs" is already annotated with @JsonProperty so it's included.
        // Sort: known categories first, CUSTOM last
        fields.sort(Comparator.comparingInt(f -> f.category.ordinal()));

        return fields;
    }

    private static FieldType mapType(Field f) {
        Class<?> t = f.getType();
        if (t == String.class) return FieldType.STRING;
        if (t == int.class || t == Integer.class) return FieldType.INT;
        if (t == boolean.class || t == Boolean.class) return FieldType.BOOL;
        if (t == List.class) {
            // Check generic parameter
            var pt = f.getGenericType();
            if (pt instanceof ParameterizedType pType) {
                var args = pType.getActualTypeArguments();
                if (args.length > 0 && args[0] == String.class) return FieldType.STRING_LIST;
            }
            return FieldType.STRING_LIST;
        }
        if (t == Map.class) return FieldType.JSON_MAP;
        return FieldType.STRING; // fallback
    }

    /**
     * Read the value of a config field by name using reflection.
     */
    public static Object readValue(Config config, String fieldName) {
        try {
            Field f = findField(fieldName);
            f.setAccessible(true);
            return f.get(config);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Write a value to a config field by name using reflection.
     */
    public static void writeValue(Config config, String fieldName, Object value) {
        try {
            Field f = findField(fieldName);
            f.setAccessible(true);
            f.set(config, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set " + fieldName, e);
        }
    }

    private static Field findField(String name) throws NoSuchFieldException {
        for (Field f : Config.class.getDeclaredFields()) {
            if (f.getName().equals(name)) return f;
        }
        throw new NoSuchFieldException(name);
    }

    private static String camelToLabel(String camel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                sb.append(' ');
            }
            sb.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return sb.toString();
    }

    private ConfigFieldDiscoverer() {}
}
