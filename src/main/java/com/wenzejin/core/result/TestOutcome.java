package com.wenzejin.core.result;

import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.mr.assertion.TestAssertion;
import java.util.List;
import java.util.ArrayList;

public class TestOutcome {
    public final TestCase testCase;
    public final String testCaseId;
    public final boolean isSource;
    public final ExecutionStatus status;
    public final Result sutResult;
    public final List<TestAssertion> failedAssertions;
    public final String errorMessage;
    public final long durationMs;

    public TestOutcome(TestCase testCase, boolean isSource, ExecutionStatus status, Result sutResult, List<TestAssertion> failedAssertions, String errorMessage, long durationMs) {
        this.testCase = testCase;
        this.testCaseId = String.valueOf(testCase.getId());
        this.isSource = isSource;
        this.status = status;
        this.sutResult = sutResult;
        this.failedAssertions = failedAssertions != null ? failedAssertions : new ArrayList<>();
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
    }

    public boolean isPassed() {
        return status == ExecutionStatus.SUCCESS && failedAssertions.isEmpty();
    }

    public boolean hasSystemError() {
        return status == ExecutionStatus.CRASHED || status == ExecutionStatus.TIMEOUT || status == ExecutionStatus.INVALID_OUTPUT;
    }
}
