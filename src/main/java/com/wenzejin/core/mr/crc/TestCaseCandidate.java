package com.wenzejin.core.mr.crc;

import com.wenzejin.core.TestCase;

public interface TestCaseCandidate extends Comparable<TestCaseCandidate> {

    TestCase genTestCase();

    double getMinDistance();

    void updateMinDistance(TestCaseCandidate another);
}
