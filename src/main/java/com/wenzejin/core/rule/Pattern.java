package com.wenzejin.core.rule;


import java.util.HashSet;
import java.util.Set;

public class Pattern {

    String name;

    Set<String> data;

    public Pattern(String name) {
        this.name = name;
        data = new HashSet<>();
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

    /**
     * Generate random data in [dataMin, dataMax]
     * @param patternSize size of data
     * @param dataMin min
     * @param dataMax max
     */
    public void setRandomData(int patternSize, int dataMin, int dataMax) {
        data.clear();
        while (data.size() < patternSize) {
            data.add(String.valueOf((int) (Math.random() * (dataMax - dataMin + 1) + dataMin)));
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name + " : " + data;
    }
}
