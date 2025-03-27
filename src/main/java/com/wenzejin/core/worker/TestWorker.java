package com.wenzejin.core.worker;

import java.io.IOException;

import com.wenzejin.core.TestCase;

public interface TestWorker {

    void initWorkspace() throws IOException;

    void destroyWorkspace() throws IOException;

    int singleRun(TestCase tc);
}
