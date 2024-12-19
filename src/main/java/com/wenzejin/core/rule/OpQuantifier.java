package com.wenzejin.core.rule;

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
}
