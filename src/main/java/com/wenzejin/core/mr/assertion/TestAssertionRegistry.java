package com.wenzejin.core.mr.assertion;

import java.util.HashMap;
import java.util.Map;

public class TestAssertionRegistry {
    private static final Map<String, Class<? extends TestAssertion>> registry = new HashMap<>();

    public static void register(Class<? extends TestAssertion> assertionClass) {
        registry.put(assertionClass.getSimpleName(), assertionClass);
    }

    public static Class<? extends TestAssertion> get(String type) {
        return registry.get(type);
    }
}
