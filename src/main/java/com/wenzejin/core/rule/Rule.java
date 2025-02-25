/**
 * Rule class of test cases.
 * Author: Wenze Jin
 */
package com.wenzejin.core.rule;


import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Rule implements Cloneable {
    public String id;
    public Formula formula;

    public Rule() {
        this.id = "";
        this.formula = null;
    }

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

    public String getId() {
        return id;
    }

    public void rebindPatterns(Map<String, String> patternMap) {
        if (formula != null) {
            formula.rebindPatterns(patternMap);
        }
    }

    public void rebindBfuncs(Map<String, String> bfuncMap) {
        if (formula != null) {
            formula.rebindBfuncs(bfuncMap);
        }
    }

    public void rebindVariables(Map<String, String> variableMap) {
        if (formula != null) {
            formula.rebindVariables(variableMap);
        }
    }

    public Set<String> collectPatterns() {
        if (formula != null) {
            return formula.collectPatterns();
        }
        return Set.of();
    }

    public Set<LiteralBfunc> collectBfuncs() {
        if (formula != null) {
            return formula.collectBfuncs();
        }
        return Set.of();
    }

    public Set<String> collectVariables() {
        return formula.collectVariables();
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

    @Override
    public String toString() {
        return id + " : " + formula;
    }


}
