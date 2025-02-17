package com.wenzejin.core.rule;


import java.util.HashSet;
import java.util.Set;

public class Pattern {

    private static double randomGate = 0.5;

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

    public void randomExtendByGate() {
        if (Math.random() > randomGate) {
            int randomCount = (int) (Math.random() * 3) + 1;
            for (int i = 0; i < randomCount; i++) {
                data.add(String.valueOf((int) (Math.random() * 10)));
            }
        }
    }

    public void randomReduceByGate() {
        if (Math.random() > randomGate) {
            int randomCount = (int) (Math.random() * 2) + 1;
            for (int i = 0; i < randomCount && data.size() > 1; i++) {
                data.remove(data.iterator().next());
            }
        }
    }

    @Override
    public String toString() {
        return name + " : " + data;
    }
}
