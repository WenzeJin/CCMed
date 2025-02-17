package com.wenzejin.core.util;

import com.wenzejin.core.mr.assertion.TestAssertion;
import com.wenzejin.core.result.Result;
import com.wenzejin.core.rule.Bfunc;
import com.wenzejin.core.rule.Pattern;
import com.wenzejin.core.rule.Rule;

import java.util.ArrayList;
import java.util.List;

public class TestCase {

    private static int idCounter = 0;

    private final int id;

    // Constraint
    private Rule rule;
    private List<Pattern> patterns;
    private List<Bfunc> bfuncs;
    private List<TestAssertion> assertions;    // Assertion
    private Result result;          // Result
    private boolean checked;            // Checked
    private boolean passed;             // Passed
    private List<String> ruleUsed;
    private final List<Integer> source;

    public TestCase() {
        id = idCounter++;
        result = null;
        assertions = null;
        checked = false;
        passed = false;
        ruleUsed = new ArrayList<>();
        source = new ArrayList<>();
    }

    public TestCase(Rule rule) {
        this();
        this.rule = rule;
    }


    // getters and setters

    public Rule getRule() {
        return rule;
    }

    public void setRule(Rule rule) { this.rule = rule; }

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

    public List<TestAssertion> getAssertions() {
        return assertions;
    }

    public void setAssertions(List<TestAssertion> assertions) {
        this.assertions = assertions;
    }

    public int getId() {
        return id;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public Result getResult() {
        return result;
    }

    public List<String> getRuleUsed() { return ruleUsed; }

    public List<Integer> getSource() {
        return source;
    }

    public boolean assertTest() {
        if (result == null) {
            return false;
        }
        boolean res = true;
        for (TestAssertion a : assertions) {
            res = res && a.assertTest(result);
        }
        return res;
    }

    public boolean assertTest(List<TestAssertion> failedAssertions) {
        if (result == null) {
            return false;
        }
        passed = true;
        for (TestAssertion a : assertions) {
            if (!a.assertTest(result)) {
                failedAssertions.add(a);
                passed = false;
            }
        }
        return passed;
    }

    public boolean isValid() {
        // TODO: Implement this method
        return true;
    }

    @Override
    public String toString() {
        return "TestCase{" +
                "id=" + id +
                ", rule=" + rule +
                ", patterns=" + patterns +
                ", bfuncs=" + bfuncs +
                ", assertions=" + assertions +
                ", result=" + result +
                ", checked=" + checked +
                ", passed=" + passed +
                '}';
    }


}
