package com.wenzejin.core;

import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.mr.assertion.TestAssertion;
import com.wenzejin.core.worker.TestWorker;
import com.wenzejin.core.worker.TestWorkerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TestExecutor {

    public enum Mode { SOURCE, FOLLOW }

    // --- Configuration ---
    private final String workspace;
    private final String workerType;
    private final Map<String, Object> workerArguments;
    private final int threadNum;
    private final Mode mode;
    private final Path logSpace;
    private final String storeMode;

    // --- State ---
    private int checked;
    private final Map<TestCase, List<TestAssertion>> failedCases;
    private int firstFailure;
    private long startTime;

    private ExecutorService executor;
    private ThreadLocal<TestWorker> threadLocalWorker;
    private Set<TestWorker> workersRegistry;

    private static final Logger logger = LogManager.getLogger("TestExecutor");

    /**
     * Constructs a new TestExecutor instance with the specified parameters.
     */
    public TestExecutor(Mode mode, String workspace, String workerType, Map<String, Object> workerArguments, int threadNum, String storeMode) {
        this.mode = mode;
        this.workspace = workspace;
        this.workerType = workerType;
        this.workerArguments = workerArguments;
        this.threadNum = threadNum;

        logSpace = Paths.get(workspace, "logs");
        this.firstFailure = -1;
        this.failedCases = new HashMap<>();

        this.storeMode = storeMode;
    }

    public int getChecked() { return checked; }
    public Map<TestCase, List<TestAssertion>> getFailedCases() { return failedCases; }
    public int getFirstFailure() { return firstFailure; }

    /**
     * Initializes the TestExecutor by setting up the worker's registry, thread pool executor,
     * and thread-local worker instances. Each worker is initialized with a workspace.
     */
    public void init() {
        logger.info("Initializing test executor");
        workersRegistry = Collections.newSetFromMap(new ConcurrentHashMap<>());
        executor = Executors.newFixedThreadPool(threadNum);
        threadLocalWorker = ThreadLocal.withInitial(() -> {
            TestWorker worker = TestWorkerFactory.getTestWorker(workerType, workspace, workerArguments);
            logger.info("Initializing worker of type {}.", workerType);
            try {
                worker.initWorkspace();
                workersRegistry.add(worker);
            } catch (IOException e) {
                logger.error("Failed to initialize workspace for worker of type {}. Error: {}", workerType, e.getMessage(), e);
                throw new IllegalStateException("Worker initialization failed", e);
            } catch (NullPointerException e) {
                logger.error("Worker type {} not found. Error: {}", workerType, e.getMessage(), e);
                throw new IllegalStateException("Worker type not found", e);
            }
            return worker;
        });
    }

    /**
     * Executes a list of test cases using a thread pool executor. Each test case is processed
     * by a thread-local worker, with retries for failed executions. Progress is logged.
     */
    public void execute(List<TestCase> tcs) {
        if (tcs == null || tcs.isEmpty()) {
            logger.warn("No test cases provided for execution.");
            return;
        }
        if (executor == null || threadLocalWorker == null) {
            logger.error("Executor or thread-local worker is not initialized. Call init() before execute().");
            throw new IllegalStateException("TestExecutor is not properly initialized.");
        }
        beforeTest(tcs);
        final AtomicInteger checkCount = new AtomicInteger(0);
        final AtomicInteger progress = new AtomicInteger(0);
        final int totalTasks = tcs.size();
        logger.info("Executing tests for {} tasks.", totalTasks);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (final TestCase tc : tcs) {
            tasks.add(() -> {

                TestWorker worker = threadLocalWorker.get();

                boolean succeed = false;
                boolean passed = false;
                try {
                    for (int retry = 0; retry <= 3 && !succeed; retry++) {
                        if (worker.execute(tc, mode == Mode.SOURCE) == 0) {
                            succeed = true;
                        } else {
                            logger.warn("Test case {} failed. Retry {} / 3", tc.getId(), retry);
                        }
                    }
                    if (succeed) {
                        tc.setExecuted(true);
                        checkCount.incrementAndGet();
                        passed = checkResult(tc);
                    } else {
                        handleUncheckTestCase(tc);
                    }
                    logTestCase(tc, worker);
                } catch (Exception e) {
                    logger.error("Unexpected error while executing test case {}: {}", tc.getId(), e.getMessage(), e);
                    tc.setPassed(false);
                    logTestCase(tc, worker);
                    handleUncheckTestCase(tc);
                } finally {
                    int currentProgress = progress.incrementAndGet();
                    if (!succeed || !passed) {
                        synchronized (this) {
                            firstFailure = Math.min(firstFailure, currentProgress);
                        }
                    }
                    procedureCallback(currentProgress, totalTasks);
                }
                return null;
            });
        }

        try {
            executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            logger.error("Execution interrupted. Terminating remaining tasks.", e);
            Thread.currentThread().interrupt();
        }
        checked = checkCount.get();
        afterTest(tcs);
    }

    /**
     * Closes the TestExecutor by shutting down the executor service and cleaning up resources.
     */
    public void close() {
        if (executor != null && threadLocalWorker != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warn("Executor did not terminate in the specified time. Forcing shutdown...");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.warn("Execution interrupted during shutdown. Forcing shutdown...");
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            for (TestWorker worker : workersRegistry) {
                try {
                    worker.destroyWorkspace();
                } catch (IOException e) {
                    logger.error("Failed to close workspace for worker of type {}. Error: {}", workerType, e.getMessage(), e);
                }
            }
            workersRegistry = null;
            executor = null;
            threadLocalWorker = null;
        }
    }

    // --- Internal helpers ---

    private void beforeTest(List<TestCase> tcs) {
        checked = 0;
        firstFailure = tcs.size() + 1;
        failedCases.clear();
        startTime = System.currentTimeMillis();
    }

    private void afterTest(List<TestCase> tcs) {
        long endTime = System.currentTimeMillis();
        logger.info("Execution finished. Test cases checked: {} / {} , Time elapsed: {} ms", checked, tcs.size(), endTime - startTime);
        if (mode == Mode.FOLLOW) {
            logger.info("Test cases failed: {} / {}", failedCases.size(), tcs.size());
        }
    }

    private boolean checkResult(TestCase tc) {
        if (mode == Mode.SOURCE) {
            return true;
        }
        List<TestAssertion> failedAssertions = new ArrayList<>();
        boolean boolRes = tc.assertTest(failedAssertions);
        if (!boolRes) {
            handleFailedTestCase(tc, failedAssertions);
            logger.warn("Test case id {} failed.", tc.getId());
            return false;
        }
        return true;
    }

    private synchronized void handleFailedTestCase(TestCase tc, List<TestAssertion> failedAssertions) {
        failedCases.put(tc, failedAssertions);
    }

    private synchronized void handleUncheckTestCase(TestCase tc) {
        failedCases.put(tc, tc.getAssertions());
    }

    private void procedureCallback(int currentProgress, int totalTasks) {
        int onePercent = totalTasks / 10;
        if (onePercent == 0) onePercent = 1;
        long currentTime = System.currentTimeMillis();
        if (currentProgress % onePercent == 0) {
            long elapsedTime = currentTime - startTime;
            long eta = elapsedTime * (totalTasks - currentProgress) / currentProgress;
            String formattedETA = formatETA(eta);
            logger.info("Progress: {} / {}, {}%, ETA: {}", currentProgress, totalTasks, currentProgress * 100 / totalTasks, formattedETA);
        }
    }

    /**
     * Formats the estimated time of arrival (ETA) given in milliseconds into a human-readable string.
     */
    private static String formatETA(long eta) {
        long hours = eta / (3600 * 1000);
        eta %= 3600 * 1000;
        long minutes = eta / (60 * 1000);
        eta %= 60 * 1000;
        long seconds = eta / 1000;
        StringBuilder formattedETA = new StringBuilder();
        if (hours > 0) formattedETA.append(hours).append(" h ");
        if (minutes > 0) formattedETA.append(minutes).append(" m ");
        if (seconds > 0 || formattedETA.isEmpty()) formattedETA.append(seconds).append(" s ");
        return formattedETA.toString().trim();
    }

    private void logTestCase(TestCase tc, TestWorker worker) {
        if (storeMode.equals("none")) {
            return;
        }

        Path logDir;
        String id = "" + tc.getId();
        if (mode == Mode.SOURCE) {
            logDir = logSpace.resolve("source").resolve(id);
        } else if (!tc.getPassed()) {
            logDir = logSpace.resolve("failed").resolve(id);
        } else if (storeMode.equals("all")) {
            logDir = logSpace.resolve("passed").resolve(id);
        } else {
            return;
        }

        worker.logTestCase(logDir);
        if (mode == Mode.FOLLOW) {
            // create file info.txt
            Path infoFile = logDir.resolve("info.txt");
            try {
                Files.write(infoFile, (tc + "\n" + tc.getSource()).getBytes());
            } catch (IOException ignore) {}
        }
    }
}
