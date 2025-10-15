package com.wenzejin.core.mr.assertion;

import com.wenzejin.core.result.Result;

public class TruthAssertion implements TestAssertion {

    private final boolean truthValue;

    public TruthAssertion(boolean truthValue) {
        this.truthValue = truthValue;
    }

    @Override
    public boolean assertTest(Result result) {
        return truthValue == result.truth;
    }

    @Override
    public String toString() {
        return "TruthAssertion{" +
                "truthValue=" + truthValue +
                ", according to rule: " + rule +
                '}';
    }

    private String rule;

    @Override
    public void setRule(String rule) {
        this.rule = rule;
    }

    @Override
    public String getRule() {
        return rule;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        TruthAssertion that = (TruthAssertion) obj;
        return truthValue == that.truthValue;
    }
}
