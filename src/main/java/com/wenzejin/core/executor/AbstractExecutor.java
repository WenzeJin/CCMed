package com.wenzejin.core.executor;

import com.wenzejin.core.db.TestCaseDB;
import com.wenzejin.core.util.TestCase;
import com.wenzejin.core.worker.TestWorker;
import com.wenzejin.core.worker.TestWorkerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AbstractExecutor
 * It will be extended by TestOnlyExecutor and TestAndCheckExecutor
 * Using template method pattern （模板方法模式）and hook method （钩子方法）to implement different test strategies.
 */
public abstract class AbstractExecutor {

    String workerType;
    Map<String, Object> workerArguments;
    int threadNum;
    ExecutorService executor;
    int checked;

    private static final Logger logger = LogManager.getLogger("AbstractExecutor");

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

    public void executeTest(List<TestCase> tcs) {
        // hook method
        beforeTest(tcs);
        // template method
        runTest(tcs);
        // hook method
        afterTest(tcs);
    }

    public void executeTestDB(List<Integer> ids) {
        beforeTestDB(ids);
        runTestDB(ids);
        afterTestDB(ids);
    }


    protected void beforeTest(List<TestCase> tcs) {
        // do nothing
        checked = 0;
    }

    protected void beforeTestDB(List<Integer> ids) {
        // do nothing
        checked = 0;
    }

    protected void afterTest(List<TestCase> tcs) {
        // do nothing
    }

    protected void afterTestDB(List<Integer> ids) {
        // do nothing
    }

    private void runTest(List<TestCase> tcs) {
        int totalTasks = tcs.size();
        int numThreads = Runtime.getRuntime().availableProcessors();
        executor = Executors.newFixedThreadPool(numThreads); // 创建线程池

        AtomicInteger progress = new AtomicInteger(0); // 用于跟踪进度
        List<Integer> failedIds = Collections.synchronizedList(new ArrayList<>()); // 线程安全的失败列表

        // 初始化线程本地 Worker，每个线程管理一个 Worker
        ThreadLocal<TestWorker> threadLocalWorker = ThreadLocal.withInitial(() -> {
            TestWorker worker = TestWorkerFactory.getTestWorker(workerType, workerArguments);
            try {
                worker.initWorkspace(); // 每个线程的 Worker 只初始化一次工作区
            } catch (IOException e) {
                logger.fatal("Init Workspace Failed.");
            }
            return worker;
        });


        List<Callable<Object>> tasks = new ArrayList<>();
        for (TestCase tc : tcs) {
            tasks.add(() -> {
                TestWorker worker = threadLocalWorker.get(); // 获取线程本地 Worker

                try {
                    worker.singleRun(tc); // 执行测试用例
                } catch (Exception e) {
                    synchronized (failedIds) {
                        failedIds.add(tc.getId());
                    }
                }

                boolean passed = checkResult(tc);

                if (passed) {
                    passTestCase(tc);
                } else {
                    failTestCase(tc);
                }

                int currentProgress = progress.incrementAndGet();
                procedureCallback(currentProgress, totalTasks);
                return null;
            });
        }

        try {
            executor.invokeAll(tasks); // 执行所有任务
        } catch (InterruptedException e) {
            logger.fatal("Execution interrupted.");
        }

        checked = tcs.size() - failedIds.size();

        // 销毁线程本地 Worker 的工作区
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                TestWorker worker = threadLocalWorker.get();
                try {
                    worker.destroyWorkspace(); // 每个线程的 Worker 只销毁一次工作区
                } catch (IOException e) {
                    logger.warn("Destroy Workspace Failed. Maybe there is no workspace to destroy.");
                }
                return null;
            });
        }

        executor.shutdown();
    }

    private void runTestDB(List<Integer> ids) {
        int totalTasks = ids.size();
        int numThreads = Runtime.getRuntime().availableProcessors();
        executor = Executors.newFixedThreadPool(numThreads);

        AtomicInteger progress = new AtomicInteger(0);
        AtomicInteger checkCount = new AtomicInteger(0);
        List<Integer> failedIds = Collections.synchronizedList(new ArrayList<>());

        ThreadLocal<TestWorker> threadLocalWorker = ThreadLocal.withInitial(() -> {
            TestWorker worker = TestWorkerFactory.getTestWorker(workerType, workerArguments);
            try {
                worker.initWorkspace();
            } catch (IOException e) {
                logger.fatal("Init Workspace Failed.");
            }
            return worker;
        });

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int id : ids) {
            tasks.add(() -> {
                TestWorker worker = threadLocalWorker.get();
                try {
                    TestCase tc = TestCaseDB.getTestCaseById(id);
                    worker.singleRun(tc);
                    checkCount.incrementAndGet();
                    TestCaseDB.setChecked(id, true);
                    boolean passed = checkResult(tc);
                    if (passed) {
                        passTestCase(tc);
                        TestCaseDB.setPassed(id, true);
                    } else {
                        failTestCase(tc);
                        TestCaseDB.setPassed(id, false);
                    }
                    TestCaseDB.updateCompressedData(id, tc);
                } catch (Exception e) {
                    synchronized (failedIds) {
                        failedIds.add(id);
                    }
                }
                int currentProgress = progress.incrementAndGet();
                procedureCallback(currentProgress, totalTasks);
                return null;
            });
        }

        checked = checkCount.get();

        try {
            executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            logger.fatal("Execution interrupted.");
        }

        for (int i = 0; i < numThreads; i++) {
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

        executor.shutdown();
    }

    protected void procedureCallback(int currentProgress, int totalTasks) {
        if (currentProgress % 50 == 0 || currentProgress == totalTasks) {
            logger.info("Test progress: {}/{}", currentProgress, totalTasks);
        }
    }

    protected abstract boolean checkResult(TestCase tc);

    protected abstract void passTestCase(TestCase tc);

    protected abstract void failTestCase(TestCase tc);

    // TODO: provide DB version of passTestCase and failTestCase
}
