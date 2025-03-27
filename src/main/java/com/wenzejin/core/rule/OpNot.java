package com.wenzejin.core.rule;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class OpNot implements Formula {
    public Formula formula;

    public OpNot(Formula formula) {
        this.formula = formula;
    }

    public OpNot() {
        this.formula = null;
    }

    @Override
    public double distance(Formula formula) {
        return switch (formula.getNodeType()) {
            case "and", "or", "implies", "bfunc", "forall", "exists" ->
                // This layer of two formulas are different, but we can still compare the inner formula.
                // The formula with NOT operator will go deeper in the recursion.
                // And the other formula will stay in the same layer.
                    50 + formula.distance(this.formula) * 0.5;
            case "not" ->
                // This layer of two formulas are the same, so we compare the inner formulae.
                    formula.distance(((OpNot) formula).formula) * 0.5;
            default -> 100;
        };
    }

    @Override
    public boolean isValid() {
        if (formula == null) return false;
        return formula.isValid();
    }

    @Override
    public String getNodeType() {
        return "not";
    }

    @Override
    public String toXmlString() {
        return "<not>\n"
                + formula.toXmlString()
                + "</not>\n";
    }

    @Override
    public void rebindPatterns(Map<String, String> patternMap) {
        if (formula != null) {
            formula.rebindPatterns(patternMap);
        }
    }

    @Override
    public Set<String> collectPatterns() {
        if (formula != null) {
            return formula.collectPatterns();
        }
        return new HashSet<>();
    }

    @Override
    public void rebindBfuncs(Map<String, String> bfuncMap) {
        if (formula != null) {
            formula.rebindBfuncs(bfuncMap);
        }
    }

    @Override
    public Set<BfuncLiteral> collectBfuncs() {
        if (formula != null) {
            return formula.collectBfuncs();
        }
        return new HashSet<>();
    }

    @Override
    public void rebindVariables(Map<String, String> variableMap) {
        if (formula != null) {
            formula.rebindVariables(variableMap);
        }
    }

    @Override
    public Set<String> collectVariables() {
        if (formula != null) {
            return formula.collectVariables();
        }
        return new HashSet<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpNot opNot = (OpNot) o;
        return formula.equals(opNot.formula);
    }

    @Override
    public int hashCode() {
        return Objects.hash("OpNot", formula);
    }

    @Override
    public OpNot clone() {
        try {
            OpNot clonedOp = (OpNot) super.clone();

            if (formula != null) {
                clonedOp.formula = formula.clone();
            }

            return clonedOp;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("Cloning not supported for OpNot class", e);
        }
    }

    @Override
    public String toString() {
        return "(not " + formula + ")";
    }


}
