/**
 * Rule class of test cases.
 * Author: Wenze Jin
 */
package com.wenzejin.core.rule;


import java.util.Objects;

public class Rule implements Cloneable {
    public String id;
    public Formula formula;

    public Rule(String id, Formula formula) {
        this.id = id;
        this.formula = formula;
    }

    public boolean isValid() {
        if (id == null || id.isEmpty() || formula == null) return false;
        return formula.isValid();
    }

    public String toXmlString() {
        return "<rule>\n"
            + "<id>" + id + "</id>\n"
            + "<formula>\n" + formula.toXmlString() + "</formula>\n"
            + "</rule>\n";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Rule rule = (Rule) o;
        return id.equals(rule.id) && formula.equals(rule.formula);
    }

    @Override
    public int hashCode() {
        return Objects.hash("Rule", id, formula);
    }

    @Override
    public Rule clone() {
        try {
            // 调用 Object 的 clone() 方法，进行浅拷贝
            Rule clonedRule = (Rule) super.clone();

            // 深拷贝 formula
            if (this.formula != null) {
                clonedRule.formula = this.formula.clone();
            }

            return clonedRule;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("Cloning not supported for Rule class", e);
        }
    }
}
