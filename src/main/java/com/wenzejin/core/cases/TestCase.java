package com.wenzejin.core.cases;

import com.wenzejin.core.mr.assertion.TestAssertion;
import com.wenzejin.core.rule.Bfunc;
import com.wenzejin.core.rule.Pattern;
import com.wenzejin.core.rule.Rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class TestCase {

    private static AtomicInteger idCounter = new AtomicInteger(0);

    public static void syncCounter(int id) {
        idCounter.set(id);
    }

    private final int id;

    // Constraint
    private Rule rule;
    private String oriRule;
    private List<Pattern> patterns;
    private List<Bfunc> bfuncs;
    private List<TestAssertion> assertions;    // Assertion
    private List<String> ruleUsed;
    private final List<Integer> source;

    public TestCase() {
        id = idCounter.getAndIncrement();
        oriRule = null;
        assertions = null;
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

    public String getOriRule() { return oriRule; }

    public void setOriRule(String oriRule) { this.oriRule = oriRule; }

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

    public List<String> getRuleUsed() { return ruleUsed; }

    public List<Integer> getSource() {
        return source;
    }

    public void setSource(List<Integer> source) {
        this.source.addAll(source);
    }

    public boolean isValid() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestCase testCase = (TestCase) o;
        if (!Objects.equals(rule, testCase.rule)) return false;
        if (!Objects.equals(patterns, testCase.patterns)) return false;
        if (!Objects.equals(bfuncs, testCase.bfuncs)) return false;
        return Objects.equals(assertions, testCase.assertions);
    }

    @Override
    public String toString() {
        return "TestCase{" +
                "id=" + id +
                ", rule=" + rule +
                ", patterns=" + patterns +
                ", bfuncs=" + bfuncs +
                ", assertions=" + assertions +
                ", source=" + source +
                '}';
    }
}
