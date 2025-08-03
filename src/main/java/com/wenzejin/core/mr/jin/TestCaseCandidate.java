package com.wenzejin.core.mr.jin;

import com.wenzejin.core.TestCase;

public interface TestCaseCandidate extends Comparable<TestCaseCandidate> {

    TestCase genTestCase();

    double getMinDistance();

    void updateMinDistance(TestCaseCandidate another);
}
