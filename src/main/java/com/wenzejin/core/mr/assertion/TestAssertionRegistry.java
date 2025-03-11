package com.wenzejin.core.mr.assertion;

import com.wenzejin.core.mr.crc.assertion.LinksValueAssertion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestAssertionRegistry {
    private static final List<Class<? extends TestAssertion>> classes = List.of(
        CombinedAssertion.class,
        LinksEqualsAssertion.class,
        LinksExceptAssertion.class,
        LinksSubsetAssertion.class,
        LinksValueAssertion.class,
        TruthAssertion.class
    );

    private static final Map<String, Class<? extends TestAssertion>> registry = new HashMap<>();

    public static void register(Class<? extends TestAssertion> assertionClass) {
        registry.put(assertionClass.getSimpleName(), assertionClass);
    }

    public static Class<? extends TestAssertion> get(String type) {
        return registry.get(type);
    }

    public static void register() {
        for (var cls: classes) {
            register(cls);
        }
    }
}
