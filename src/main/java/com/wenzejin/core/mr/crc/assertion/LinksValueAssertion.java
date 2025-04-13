package com.wenzejin.core.mr.crc.assertion;


import com.wenzejin.core.mr.assertion.TestAssertion;
import com.wenzejin.core.mr.assertion.TestAssertionRegistry;
import com.wenzejin.core.mr.crc.CRCFunc;
import com.wenzejin.core.result.Result;

import java.util.Set;

public class LinksValueAssertion implements TestAssertion {
    static {
        // 在 TestAssertionRegistry 中注册该断言，以便测试用例的序列化和反序列化
        TestAssertionRegistry.register(LinksValueAssertion.class);
    }

    private final Set<String> valueSet;

    private final boolean truth;

    private final String variable;

    public LinksValueAssertion(String variable, boolean truth, Set<String> valueSet) {
        this.valueSet = valueSet;
        this.truth = truth;
        this.variable = variable;
    }

    @Override
    public boolean assertTest(Result result) {
        Set<String> valueExtract = CRCFunc.val(result.links, truth, variable);
        return valueExtract.containsAll(valueSet);
    }

    @Override
    public String toString() {
        return "LinksValueAssertion{" +
                "valueSet=" + valueSet +
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
        LinksValueAssertion that = (LinksValueAssertion) obj;
        return valueSet.equals(that.valueSet);
    }
}
