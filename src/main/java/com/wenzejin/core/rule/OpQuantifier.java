package com.wenzejin.core.rule;

import java.util.Objects;

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
}
