package com.wenzejin.core.mr.assertion;

import com.wenzejin.core.result.Result;

public interface TestAssertion {

    boolean assertTest(Result testResult);

    void setRule(String rule);

    String getRule();
}
