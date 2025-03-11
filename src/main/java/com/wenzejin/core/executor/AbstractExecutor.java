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

    public void executeTest(List<Integer> ids) {
        beforeTest(ids);
        runTest(ids);
        afterTest(ids);
    }

    protected void beforeTest(List<Integer> ids) {
        // do nothing
        checked = 0;
    }

    protected void afterTest(List<Integer> ids) {
        // do nothing
    }

    private void runTest(List<Integer> ids) {
        Collections.shuffle(ids);
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
                    } else {
                        uncheckTestCase(tc);
                        synchronized (failedIds) {
                            failedIds.add(id);
                        }
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

        // 删除 workspace 下的所有文件
        String workspace = (String) workerArguments.get("workspace-root");
        try {
            if (workspace != null) {
                deleteFolder(new File(workspace).toPath());
            }
        } catch (IOException e) {
            logger.warn("Failed to clean workspace.");
        }
    }

    protected void procedureCallback(int currentProgress, int totalTasks) {
        if (currentProgress % 50 == 0 || currentProgress == totalTasks) {
            logger.info("Test progress: {}/{}", currentProgress, totalTasks);
        }
    }

    protected abstract boolean checkResult(TestCase tc);

    protected abstract void passTestCase(TestCase tc);

    protected abstract void failTestCase(TestCase tc);

    protected abstract void uncheckTestCase(TestCase tc);

    private static void deleteFolder(Path folder) throws IOException {
        Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file); // 删除文件
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir); // 删除空文件夹
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
