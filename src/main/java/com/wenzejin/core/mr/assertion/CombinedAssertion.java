package com.wenzejin.core.mr.assertion;

import com.wenzejin.core.result.Result;

public class CombinedAssertion implements TestAssertion {



    public enum Type {
        AND,
        OR,
        IMPLIES,
        NOT,
    }

    private final TestAssertion left;
    private final TestAssertion right;

    private final Type type;



    /**
     * Combines two assertions with a logical operator.
     * If type is NOT, right is ignored.
     * @param left left assertion
     * @param right right assertion
     * @param type logical operator
     */
    public CombinedAssertion(TestAssertion left, TestAssertion right, Type type) {
        this.left = left;
        this.right = right;
        this.type = type;
    }

    /**
     * Tests the result with the assertion.
     * @param testResult result to test
     * @return true if the assertion is true
     */
    @Override
    public boolean assertTest(Result testResult) {
        switch (type) {
            case AND:
                return left.assertTest(testResult) && right.assertTest(testResult);
            case OR:
                return left.assertTest(testResult) || right.assertTest(testResult);
            case IMPLIES:
                return !left.assertTest(testResult) || right.assertTest(testResult);
            case NOT:
                return !left.assertTest(testResult);
            default:
                throw new IllegalStateException("Unexpected value: " + type);
        }
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
    public String toString() {
        return "CombinedAssertion{" +
                "left=" + left +
                ", right=" + right +
                ", type=" + type +
                ", according to rule: " + rule +
                '}';
    }
}
