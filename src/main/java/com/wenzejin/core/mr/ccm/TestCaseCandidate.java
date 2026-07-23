package com.wenzejin.core.mr.ccm;

import com.wenzejin.core.cases.TestCase;

public interface TestCaseCandidate extends Comparable<TestCaseCandidate> {

    TestCase genTestCase();

    double getMinDistance();

    void updateMinDistance(TestCaseCandidate another);
}
