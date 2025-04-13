package com.wenzejin.core.mr.crc;


import java.util.HashSet;
import java.util.Set;

public class QuantifierTag implements Tag{

    public String variable;
    public Set<String> valueTrue;
    public Set<String> valueFalse;
    public String ruleUsed;
    public String parentID;

    public QuantifierTag(String variable) {
        this.variable = variable;
        valueTrue = new HashSet<>();
        valueFalse = new HashSet<>();
        ruleUsed = "";
        parentID = "";
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
        QuantifierTag that = (QuantifierTag) obj;
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
