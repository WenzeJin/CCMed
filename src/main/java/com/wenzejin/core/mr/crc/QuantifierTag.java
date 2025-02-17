package com.wenzejin.core.mr.crc;

import com.wenzejin.core.result.Link;

import java.util.HashSet;
import java.util.Set;

public class QuantifierTag implements Tag{

    public String variable;
    public Set<String> valueTrue;
    public Set<String> valueFalse;
    public Set<Link> linksTrue;
    public Set<Link> linksFalse;
    public String ruleUsed;

    public QuantifierTag(String variable) {
        this.variable = variable;
        valueTrue = new HashSet<>();
        valueFalse = new HashSet<>();
        linksTrue = new HashSet<>();
        linksFalse = new HashSet<>();
        ruleUsed = "";
    }

    @Override
    public String toString() {
        return "QuantifierTag{" +
                "variable=" + variable +
                ", valueTrue=" + valueTrue +
                ", valueFalse=" + valueFalse +
                ", linksTrue=" + linksTrue +
                ", linksFalse=" + linksFalse +
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
        return variable.equals(that.variable) && valueTrue.equals(that.valueTrue) && valueFalse.equals(that.valueFalse) && linksTrue.equals(that.linksTrue) && linksFalse.equals(that.linksFalse);
    }

    @Override
    public int hashCode() {
        int result = variable.hashCode();
        result = 31 * result + valueTrue.hashCode();
        result = 31 * result + valueFalse.hashCode();
        result = 31 * result + linksTrue.hashCode();
        result = 31 * result + linksFalse.hashCode();
        return result;
    }

}
