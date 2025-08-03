package com.wenzejin.core.rule;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Pattern {

    private static final double randomGate = 0.5;

    String name;

    Set<String> data;

    public Pattern(String name) {
        this.name = name;
        data = new HashSet<>();
    }

    public Pattern(String name, Set<String> data) {
        this.name = name;
        this.data = new HashSet<>(data);
    }

    public String getName() {
        return name;
    }

    public Set<String> getData() {
        return data;
    }

    /**
     * Generate {0, 1}  for data
     */
    public void setBinaryData() {
        data.clear();
        data.add("0");
        data.add("1");
    }

    public void randomExtend() {
        int randomCount = (int) (Math.random() * 5) + 1;
        for (int i = 0; i < randomCount; i++) {
            data.add(String.valueOf((int) (Math.random() * 20)));
        }
    }

    public void randomReduceByGate() {
        if (Math.random() > randomGate && data.size() > 1) {
            String toRemove = new ArrayList<>(data).get((int) (Math.random() * data.size()));
            data.remove(toRemove);
        }
    }

    @Override
    public String toString() {
        return name + " : " + data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pattern pattern = (Pattern) o;
        return name.equals(pattern.name) && data.equals(pattern.data);
    }
}
