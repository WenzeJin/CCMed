package com.wenzejin.core.result;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Link implements Cloneable {
    public enum Type {
        SATISFIED,
        VIOLATED
    }

    public Type type;

    public Set<VAPair> vaSet;

    public Link(Type type) {
        this.type = type;
        vaSet = new HashSet<>();
    }

    public Link(Type type, Set<VAPair> vaSet) {
        this.type = type;
        this.vaSet = vaSet;
    }

    public void addVaPair(String variable, String value) {
        vaSet.add(new VAPair(variable, value));
    }

    public void flip() {
        if (type == Type.SATISFIED) {
            type = Type.VIOLATED;
        } else {
            type = Type.SATISFIED;
        }
    }

    public Link concat(Link other) {
        if (type != other.type) {
            return null;
        }
        Link res = new Link(type);
        res.vaSet.addAll(vaSet);
        res.vaSet.addAll(other.vaSet);
        return res;
    }

    public void rebindVariables(Map<String, String> variableMap) {
        Set<VAPair> newVaSet = new HashSet<>();
        for (VAPair entry : vaSet) {
            String newVariable = variableMap.getOrDefault(entry.getKey(), entry.getKey());
            String newValue = entry.getValue();
            newVaSet.add(new VAPair(newVariable, newValue));
        }
        vaSet = newVaSet;
    }

    @Override
    public int hashCode() {
        return (type.hashCode() << 1) ^ vaSet.hashCode();
    }

    @Override
    public String toString() {
        return "{" + type + ", " + vaSet + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Link that = (Link) obj;
        return type == that.type && vaSet.containsAll(that.vaSet) && that.vaSet.containsAll(vaSet);
    }

    @Override
    public Link clone() {
        return new Link(type, new HashSet<>(vaSet));
    }
}
