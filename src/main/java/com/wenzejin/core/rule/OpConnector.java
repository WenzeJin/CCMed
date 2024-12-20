package com.wenzejin.core.rule;

import java.util.Objects;

public class OpConnector implements Formula {
    public enum Op {
        AND,
        OR,
        IMPLIES,
    }

    public Op opType;

    public Formula left;
    public Formula right;

    public OpConnector(Op opType, Formula left, Formula right) {
        this.opType = opType;
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean isValid() {
        if (opType == null || left == null || right == null) return false;
        return left.isValid() && right.isValid();
    }

    @Override
    public String getNodeType() {
        return switch (opType) {
            case AND        -> "and";
            case OR         -> "or";
            case IMPLIES    -> "implies";
        };
    }

    @Override
    public String toXmlString() {
        return "<" + getNodeType() + ">\n"
                + left.toXmlString()
                + right.toXmlString()
                + "</" + getNodeType() + ">\n";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpConnector that = (OpConnector) o;
        return opType == that.opType && Objects.equals(left, that.left) && Objects.equals(right, that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash("OpConnector", opType, left, right);
    }

    @Override
    public OpConnector clone() {
        try {
            OpConnector clonedOp = (OpConnector) super.clone();

            if (left != null) {
                clonedOp.left = left.clone();
            }

            if (right != null) {
                clonedOp.right = right.clone();
            }

            return clonedOp;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("Cloning not supported for OpConnector class", e);
        }
    }
}
