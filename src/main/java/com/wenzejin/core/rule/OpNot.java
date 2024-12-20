package com.wenzejin.core.rule;

import java.util.Objects;

public class OpNot implements Formula {
    public Formula formula;

    public OpNot(Formula formula) {
        this.formula = formula;
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
}
