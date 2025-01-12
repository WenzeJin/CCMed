package com.wenzejin.core.worker;

import com.wenzejin.core.util.TestCase;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class InfuseWorker implements TestWorker{


    public InfuseWorker(Map<String,Object> args) {

    }


    /**
     * @throws IOException
     */
    @Override
    public void initWorkspace() throws IOException {

    }

    /**
     * @throws IOException
     */
    @Override
    public void destroyWorkspace() throws IOException {

    }

    /**
     * @param tc
     */
    @Override
    public void singleRun(TestCase tc) {

    }

    /**
     * @param testCaseList
     */
    @Override
    public void batchRun(List<TestCase> testCaseList) {

    }
}
