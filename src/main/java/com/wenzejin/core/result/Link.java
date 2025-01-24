package com.wenzejin.core.result;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Link {
    public enum Type {
        SATISFIED,
        VIOLATED
    }

    public Type type;

    public Set<Map.Entry<String, String>> vaSet;

    public Link(Type type) {
        this.type = type;
        vaSet = new HashSet<>();
    }

    public Link(Type type, Set<Map.Entry<String, String>> vaSet) {
        this.type = type;
        this.vaSet = vaSet;
    }

    public void addVaPair(String variable, String value) {
        vaSet.add(Map.entry(variable, value));
    }

    @Override
    public int hashCode() {
        return (type.hashCode() << 1) ^ vaSet.hashCode();
    }

    @Override
    public String toString() {
        return "{" + type + ", " + vaSet + "}";
    }
}
