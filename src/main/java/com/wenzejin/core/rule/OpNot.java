package com.wenzejin.core.rule;

public class OpNot implements Formula {
    private Formula formula;

    public OpNot(Formula formula) {
        this.formula = formula;
    }

    @Override
    public boolean isValid() {
        if (formula == null) return false;
        return formula.isValid();
    }
}
