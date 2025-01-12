package com.wenzejin.core.util;

import com.wenzejin.core.mr.TestAssertion;
import com.wenzejin.core.rule.Bfunc;
import com.wenzejin.core.rule.Pattern;
import com.wenzejin.core.rule.Rule;

import java.util.List;

public class TestCase {

    static int idCounter = 0;

    int id;
    // Constraint
    Rule rule;
    List<Pattern> patterns;
    // TODO: Bfunc
    List<Bfunc> bfuncs;
    TestAssertion assertion;    // Assertion
    TestResult result;          // Result
    boolean checked;            // Checked
    boolean passed;             // Passed

    public TestCase() {
        id = idCounter++;
        result = null;
        assertion = null;
        checked = false;
        passed = false;
    }

    public TestCase(Rule rule) {
        this();
        this.rule = rule;
    }

    public Rule getRule() {
        return rule;
    }

    public List<Pattern> getPatterns() {
        return patterns;
    }

    public void setPatterns(List<Pattern> patterns) {
        this.patterns = patterns;
    }

    public List<Bfunc> getBfuncs() {
        return bfuncs;
    }

    public void setBfuncs(List<Bfunc> bfuncs) {
        this.bfuncs = bfuncs;
    }

    public TestAssertion getAssertion() {
        return assertion;
    }

    public void setAssertion(TestAssertion assertion) {
        this.assertion = assertion;
    }

    public int getId() {
        return id;
    }

    public boolean isValid() {
        // TODO: Implement this method
        return true;
    }
}
