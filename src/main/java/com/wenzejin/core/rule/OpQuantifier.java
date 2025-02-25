package com.wenzejin.core.rule;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class OpQuantifier implements Formula {
    public enum Op {
        FORALL,
        EXISTS,
    }

    public Op opType;
    public String var;
    public String pattern;
    public Formula formula;

    public OpQuantifier(Op opType, String var, String pattern, Formula formula) {
        this.opType = opType;
        this.var = var;
        this.pattern = pattern;
        this.formula = formula;
    }

    public OpQuantifier(Op opType) {
        this.opType = opType;
        this.var = null;
        this.pattern = null;
        this.formula = null;
    }

    @Override
    public boolean isValid() {
        if (opType == null || var == null || pattern == null) return false;
        return formula.isValid();
    }


    @Override
    public String getNodeType() {
        return switch (opType) {
            case FORALL -> "forall";
            case EXISTS -> "exists";
        };
    }

    @Override
    public String toXmlString() {
        return "<" + getNodeType() + " var = \"" + var + "\" in = \"" + pattern + "\">\n"
                + formula.toXmlString()
                + "</" + getNodeType() + ">\n";
    }

    @Override
    public void rebindPatterns(Map<String, String> patternMap) {
        if (formula != null) {
            formula.rebindPatterns(patternMap);
        }
        if (patternMap.containsKey(pattern)) {
            pattern = patternMap.get(pattern);
        }
    }

    @Override
    public Set<String> collectPatterns() {
        Set<String> patterns = new HashSet<>();
        if (formula != null) {
            patterns.addAll(formula.collectPatterns());
        }
        patterns.add(pattern);
        return patterns;
    }

    @Override
    public void rebindBfuncs(Map<String, String> bfuncMap) {
        if (formula != null) {
            formula.rebindBfuncs(bfuncMap);
        }
    }

    @Override
    public Set<LiteralBfunc> collectBfuncs() {
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
        if (variableMap.containsKey(var)) {
            var = variableMap.get(var);
        }
    }

    @Override
    public Set<String> collectVariables() {
        Set<String> variables = new HashSet<>();
        if (formula != null) {
            variables.addAll(formula.collectVariables());
        }
        variables.add(var);
        return variables;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpQuantifier that = (OpQuantifier) o;
        return opType == that.opType
                && var.equals(that.var)
                && pattern.equals(that.pattern)
                && formula.equals(that.formula);
    }

    @Override
    public int hashCode() {
        return Objects.hash("OpQuantifier", opType, var, pattern, formula);
    }

    @Override
    public OpQuantifier clone() {
        try {
            OpQuantifier clonedOp = (OpQuantifier) super.clone();

            if (formula != null) {
                clonedOp.formula = formula.clone();
            }

            return clonedOp;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("Cloning not supported for OpQuantifier class", e);
        }
    }

    @Override
    public String toString() {
        return getNodeType() + " " + var + " in " + pattern + " (" + formula + ")";
    }
}
