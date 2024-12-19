package com.wenzejin.core.rule;

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
}
