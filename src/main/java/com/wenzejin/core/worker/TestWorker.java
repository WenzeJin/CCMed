package com.wenzejin.core.worker;

import com.wenzejin.core.util.TestCase;

import java.io.IOException;

public interface TestWorker {

    void initWorkspace() throws IOException;

    void destroyWorkspace() throws IOException;

    int singleRun(TestCase tc);
}
