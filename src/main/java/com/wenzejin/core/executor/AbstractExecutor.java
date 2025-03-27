package com.wenzejin.core.executor;

import com.wenzejin.core.db.TestCaseDB;
import com.wenzejin.core.util.TestCase;
import com.wenzejin.core.worker.TestWorker;
import com.wenzejin.core.worker.TestWorkerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AbstractExecutor
 * It will be extended by TestOnlyExecutor and TestAndCheckExecutor
 * Using template method pattern （模板方法模式）and hook method （钩子方法）to implement different test strategies.
 */
public abstract class AbstractExecutor {

    final String workerType;
    final Map<String, Object> workerArguments;
    int threadNum;
    ExecutorService executor;
    int checked;

    ThreadLocal<TestWorker> threadLocalWorker;

    private static final Logger logger = LogManager.getLogger("Executor");

    public AbstractExecutor(String workerType, Map<String, Object> workerArguments) {
        this.workerType = workerType;
        this.workerArguments = workerArguments;
        this.threadNum = Runtime.getRuntime().availableProcessors();
    }

    public AbstractExecutor(String workerType, Map<String, Object> workerArguments, int threadNum) {
        this.workerType = workerType;
        this.workerArguments = workerArguments;
        this.threadNum = threadNum;
    }

    public int getChecked() {
        return checked;
    }

    public void init() {
        executor = Executors.newFixedThreadPool(threadNum);

        threadLocalWorker = ThreadLocal.withInitial(() -> {
            TestWorker worker = TestWorkerFactory.getTestWorker(workerType, workerArguments);
            try {
                worker.initWorkspace();
            } catch (IOException e) {
                logger.fatal("Init Workspace Failed.");
            }
            return worker;
        });
    }

    public void execute(List<TestCase> tcs) {
        beforeTest(tcs);
        AtomicInteger checkCount = new AtomicInteger(0);
        AtomicInteger progress = new AtomicInteger(0);
        int totalTasks = tcs.size();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (TestCase tc : tcs) {
            tasks.add(() -> {
                TestWorker worker = threadLocalWorker.get();
                try {
                    int retry = 0;
                    boolean succeed = false;
                    while (!succeed && retry <= 3) {
                        int error = worker.singleRun(tc); // 执行测试用例
                        if (error == 0) {
                            succeed = true;
                        } else {
                            logger.warn("Test case {} failed. Retry {} / 3", tc.getId(), retry);
                            retry++;
                        }
                    }
                    if (succeed) {
                        tc.setChecked(true);
                        checkCount.incrementAndGet();
                        checkResult(tc);
                    } else {
                        uncheckTestCase(tc);
                    }
                } catch (Exception e) {
                    logger.warn("Test case {} failed.", tc.getId());
                }
                int currentProgress = progress.incrementAndGet();
                procedureCallback(currentProgress, totalTasks);
                return null;
            });
        }

        try {
            executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            logger.fatal("Execution interrupted.");
        }

        checked = checkCount.get();
        afterTest(tcs);
    }

    public void close() {
        for (int i = 0; i < threadNum; i++) {
            executor.submit(() -> {
                TestWorker worker = threadLocalWorker.get();
                try {
                    worker.destroyWorkspace();
                } catch (IOException e) {
                    logger.warn("Destroy Workspace Failed. Maybe there is no workspace to destroy.");
                }
                return null;
            });
        }


        try {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.warn("Execution interrupted.");
        }
    }

    protected void beforeTest(List<TestCase> tcs) {
        checked = 0;
    }

    protected void afterTest(List<TestCase> tcs) {
        // Do nothing
    }

    protected void procedureCallback(int currentProgress, int totalTasks) {
        if (currentProgress % 50 == 0 || currentProgress == totalTasks) {
            logger.info("Test progress: {}/{}", currentProgress, totalTasks);
        }
    }

    protected abstract boolean checkResult(TestCase tc);

    protected abstract void uncheckTestCase(TestCase tc);
}
