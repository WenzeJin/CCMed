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
                '}';
    }
}
