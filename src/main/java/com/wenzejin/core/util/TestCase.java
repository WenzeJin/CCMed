package com.wenzejin.core.util;

import com.wenzejin.core.mr.TestAssertion;
import com.wenzejin.core.rule.Rule;

public class TestCase {
    // Constraint
    Rule constraint;
    // TODO: Data
    // TODO: Bfunc
    TestAssertion assertion;    // Assertion
    TestResult result;          // Result
    boolean checked;            // Checked
    boolean passed;             // Passed

    public TestCase() {
        result = null;
        assertion = null;
        checked = false;
        passed = false;
    }
}
