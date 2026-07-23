package com.wenzejin.core.mr.jin;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OpenTag implements Tag{

    public String variable;
    public Set<String> valueTrue;
    public Set<String> valueFalse;
    public String ruleUsed;
    public List<Integer> source;
    public boolean isRealQuantifier;

    public OpenTag(String variable) {
        this.variable = variable;
        valueTrue = new HashSet<>();
        valueFalse = new HashSet<>();
        ruleUsed = "";
        source = new ArrayList<>();
        isRealQuantifier = true;
    }

    public OpenTag(boolean truth) {
        isRealQuantifier = false;
        if (truth) {
            valueTrue = new HashSet<>(Set.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"));
            valueFalse = new HashSet<>();
        } else {
            valueTrue = new HashSet<>();
            valueFalse = new HashSet<>(Set.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"));
        }
        variable = "fake_variable";
        ruleUsed = "";
        source = new ArrayList<>();
    }

    @Override
    public void addSource(int sourceId) {
        if (!source.contains(sourceId)) {
            source.add(sourceId);
        }
    }

    @Override
    public List<Integer> getSource() {
        return source;
    }

    @Override
    public void addAllSources(List<Integer> sourceIds) {
        for (Integer sourceId : sourceIds) {
            if (!source.contains(sourceId)) {
                source.add(sourceId);
            }
        }
    }

    @Override
    public String toString() {
        return "QuantifierTag{" +
                "variable=" + variable +
                ", valueTrue=" + valueTrue +
                ", valueFalse=" + valueFalse +
                ", according to rule: " + ruleUsed +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        OpenTag that = (OpenTag) obj;
        return variable.equals(that.variable) && valueTrue.equals(that.valueTrue) && valueFalse.equals(that.valueFalse);
    }

    @Override
    public int hashCode() {
        int result = variable.hashCode();
        result = 31 * result + valueTrue.hashCode();
        result = 31 * result + valueFalse.hashCode();
        return result;
    }

}
