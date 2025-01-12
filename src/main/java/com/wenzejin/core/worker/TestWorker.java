package com.wenzejin.core.worker;

import com.wenzejin.core.util.TestCase;

import java.io.IOException;
import java.util.List;

public interface TestWorker {

    void initWorkspace() throws IOException;

    void destroyWorkspace() throws IOException;

    void singleRun(TestCase tc);

    void batchRun(List<TestCase> testCaseList);

}
