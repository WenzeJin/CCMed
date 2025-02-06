package com.wenzejin.core.rule;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    public void rebindPatterns(Map<String, String> patternMap) {
        if (left != null) {
            left.rebindPatterns(patternMap);
        }
        if (right != null) {
            right.rebindPatterns(patternMap);
        }
    }

    @Override
    public Set<String> collectPatterns() {
        Set<String> patternSet = new HashSet<>();
        if (left != null) {
            patternSet.addAll(left.collectPatterns());
        }
        if (right != null) {
            patternSet.addAll(right.collectPatterns());
        }
        return patternSet;
    }

    @Override
    public void rebindBfuncs(Map<String, String> bfuncMap) {
        if (left != null) {
            left.rebindBfuncs(bfuncMap);
        }
        if (right != null) {
            right.rebindBfuncs(bfuncMap);
        }
    }

    @Override
    public Set<LiteralBfunc> collectBfuncs(){
        Set<LiteralBfunc> bfuncSet = new HashSet<>();
        if (left != null) {
            bfuncSet.addAll(left.collectBfuncs());
        }
        if (right != null) {
            bfuncSet.addAll(right.collectBfuncs());
        }
        return bfuncSet;
    }

    @Override
    public void rebindVariables(Map<String, String> variableMap) {
        if (left != null) {
            left.rebindVariables(variableMap);
        }
        if (right != null) {
            right.rebindVariables(variableMap);
        }
    }

    @Override
    public Set<String> collectVariables() {
        Set<String> variableSet = new HashSet<>();
        if (left != null) {
            variableSet.addAll(left.collectVariables());
        }
        if (right != null) {
            variableSet.addAll(right.collectVariables());
        }
        return variableSet;
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

    @Override
    public String toString() {
        return "(" + left + ") " + getNodeType() + " (" + right + ")";
    }
}
