package com.wenzejin.core.mr;

import com.wenzejin.core.util.TestCase;

import java.util.List;

public interface MRCore {
    List<TestCase> generateTestCases(List<TestCase> sourceTestCases);
}

