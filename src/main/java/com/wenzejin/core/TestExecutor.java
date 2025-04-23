package com.wenzejin.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wenzejin.core.worker.TestWorker;
import com.wenzejin.core.worker.TestWorkerFactory;
import com.wenzejin.core.mr.assertion.TestAssertion;

public class TestExecutor {

    public enum Mode { SOURCE, FOLLOW }

    // Configurations
    final String workerType;
    final Map<String, Object> workerArguments;
    final int threadNum;
    final Mode mode;
    
    int checked;
    final Map<TestCase, List<TestAssertion>> failedCases;
    int firstFailure;

    private long startTime;

    ExecutorService executor;
    ThreadLocal<TestWorker> threadLocalWorker;
    Set<TestWorker> workersRegistry;
    

    private static final Logger logger = LogManager.getLogger("TestExecutor");

    /**
     * Constructs a new TestExecutor instance with the specified parameters.
     *
     * @param mode            The mode in which the test executor operates.
     * @param workerType      The type of worker to be used by the test executor.
     * @param workerArguments A map of arguments to configure the worker.
     * @param threadNum       The number of threads to be used by the test executor.
     */
    public TestExecutor(Mode mode, String workerType, Map<String, Object> workerArguments, int threadNum) {
        this.mode = mode;
        this.workerType = workerType;
        this.workerArguments = workerArguments;
        this.threadNum = threadNum;
        this.firstFailure = -1;
        failedCases = new HashMap<>();
    }

    /**
     * Retrieves the number of test cases that have been checked.
     *
     * @return the count of checked cases.
     */
    public int getChecked() {
        return checked;
    }

    /**
     * Retrieves a map of failed test cases and their associated assertions.
     *
     * @return A map where the keys are {@link TestCase} objects representing
     *         the failed test cases, and the values are lists of {@link TestAssertion}
     *         objects detailing the assertions that failed for each test case.
     */
    public Map<TestCase, List<TestAssertion>> getFailedCases() {
        return failedCases;
    }

    /**
     * Initializes the TestExecutor by setting up the workers registry, thread pool executor, 
     * and thread-local worker instances. Each worker is initialized with a workspace, and 
     * any errors during initialization are logged and result in an IllegalStateException.
     *
     * <p>Steps performed during initialization:
     * <ul>
     *   <li>Creates a thread-safe set to register workers.</li>
     *   <li>Initializes a fixed thread pool executor with the specified number of threads.</li>
     *   <li>Configures a thread-local variable to lazily initialize worker instances.</li>
     * </ul>
     *
     * @throws IllegalStateException if a worker fails to initialize its workspace.
     */
    public void init() {
        workersRegistry = Collections.newSetFromMap(new ConcurrentHashMap<>());
        executor = Executors.newFixedThreadPool(threadNum);

        threadLocalWorker = ThreadLocal.withInitial(() -> {
            TestWorker worker = TestWorkerFactory.getTestWorker(workerType, workerArguments);
            try {
                worker.initWorkspace();
                workersRegistry.add(worker);
            } catch (IOException e) {
                logger.error("Failed to initialize workspace for worker of type {}. Error: {}", workerType, e.getMessage(), e);
                throw new IllegalStateException("Worker initialization failed", e);
            }
            return worker;
        });
    }

    /**
     * Executes a list of test cases using a thread pool executor. Each test case is processed
     * by a thread-local worker, with retries for failed executions. The method ensures proper
     * initialization of the executor and worker before execution and provides progress updates
     * via a callback.
     * 
     * <br>
     * The execution process includes:
     * <ul>
     *     <li>Pre-test setup via {@code beforeTest(List<TestCase>)}.</li>
     *     <li>Execution of each test case with up to 3 retries for failures.</li>
     *     <li>Marking test cases as checked or handling unchecked cases.</li>
     *     <li>Progress updates via {@code procedureCallback(int, int)}.</li>
     *     <li>Post-test cleanup via {@code afterTest(List<TestCase>)}.</li>
     * </ul>
     *
     * Logging:
     * <ul>
     *     <li>Warnings for missing test cases or retries on failures.</li>
     *     <li>Errors for unexpected exceptions or interrupted execution.</li>
     * </ul>
     *
     * Threading:
     * <ul>
     *     <li>Uses a thread pool executor to parallelize test case execution.</li>
     *     <li>Handles interruptions gracefully by restoring the thread's interrupted status.</li>
     * </ul>
     * <br>
     * 
     * @param tcs the list of test cases to execute. If null or empty, the method logs a warning
     *            and returns without performing any execution.
     * @throws IllegalStateException if the executor or thread-local worker is not initialized.
     *                               Ensure {@code init()} is called before invoking this method.
    
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
        AtomicInteger checkCount = new AtomicInteger(0);
        AtomicInteger progress = new AtomicInteger(0);
        int totalTasks = tcs.size();
        List<Callable<Void>> tasks = new ArrayList<>();

        for (TestCase tc : tcs) {
            tasks.add(() -> {
                TestWorker worker = threadLocalWorker.get();
                boolean succeed = false;
                boolean passed = false;
                try {
                    for (int retry = 0; retry <= 3 && !succeed; retry++) {
                        if (worker.singleRun(tc) == 0) {
                            succeed = true;
                        } else {
                            logger.warn("Test case {} failed. Retry {} / 3", tc.getId(), retry);
                        }
                    }

                    if (succeed) {
                        tc.setChecked(true);
                        checkCount.incrementAndGet();
                        passed = checkResult(tc);
                    } else {
                        handleUncheckTestCase(tc);
                    }
                } catch (Exception e) {
                    logger.error("Unexpected error while executing test case {}: {}", tc.getId(), e.getMessage(), e);
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
            Thread.currentThread().interrupt(); // Restore interrupted status
        }

        checked = checkCount.get();
        afterTest(tcs);
    }

    /**
     * Closes the TestExecutor by shutting down the executor service and cleaning up resources.
     * <p>
     * This method performs the following steps:
     * <ul>
     *   <li>Shuts down the executor service gracefully, waiting for up to 10 seconds for tasks to complete.</li>
     *   <li>If tasks do not complete within the specified time, forces a shutdown of the executor service.</li>
     *   <li>Handles interruptions during the shutdown process by forcing a shutdown and restoring the interrupted status.</li>
     *   <li>Invokes the {@code destroyWorkspace} method on all registered workers to clean up their workspaces.</li>
     *   <li>Logs warnings or errors if any issues occur during the shutdown or workspace cleanup process.</li>
     *   <li>Clears the workers registry, executor, and thread-local worker references to release resources.</li>
     * </ul>
     * <p>
     * Note: This method ensures that all resources are properly released, but it may log errors if workspace cleanup fails.
     *
     * @throws IOException if an I/O error occurs during workspace cleanup for any worker.
     */
    public void close() {
        if (executor != null && threadLocalWorker != null) {
            executor.shutdown(); // Initiate shutdown of the executor
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warn("Executor did not terminate in the specified time. Forcing shutdown...");
                    executor.shutdownNow(); // Force shutdown if tasks did not finish in time
                }
            } catch (InterruptedException e) {
                logger.warn("Execution interrupted during shutdown. Forcing shutdown...");
                executor.shutdownNow(); // Force shutdown on interruption
                Thread.currentThread().interrupt(); // Restore interrupted status
            }
            for (TestWorker worker : workersRegistry) {
                try {
                    worker.destroyWorkspace();
                } catch (IOException e) {
                    logger.error("Failed to close workspace for worker of type {}. Error: {}", workerType,
                            e.getMessage(), e);
                }
            }
            workersRegistry = null;
            executor = null;
            threadLocalWorker = null;
        }
    }

    public int getFirstFailure() {
        return firstFailure;
    }

    private void beforeTest(List<TestCase> tcs) {
        checked = 0;
        firstFailure = tcs.size() + 1;
        failedCases.clear();
        startTime = System.currentTimeMillis();
    }

    private void afterTest(List<TestCase> tcs) {
        long endTime = System.currentTimeMillis();
        logger.info("Execution finished. Test cases checked: {} / {} , Time elapsed: {} ms",
                checked, tcs.size(), endTime - startTime);
        if (mode == Mode.FOLLOW) {
            logger.info("Test cases failed: {} / {}", failedCases.size(), tcs.size());
        }
    }

    private boolean checkResult(TestCase tc) {
        if (mode == Mode.SOURCE) {
            // When in source mode, we don't need to check the result
            return true;
        }

        // When in follow mode, we need to check the result
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
        int onePercent = totalTasks / 20;
        if (onePercent == 0) {
            onePercent = 1;
        }
        long currentTime = System.currentTimeMillis();
        if (currentProgress % onePercent == 0) {
            long elapsedTime = currentTime - startTime;
            long eta = elapsedTime * (totalTasks - currentProgress) / currentProgress;

            // 格式化 ETA
            String formattedETA = formatETA(eta);

            logger.info("Progress: {} / {}, {}%, ETA: {}", currentProgress, totalTasks,
                    currentProgress * 100 / totalTasks,
                    formattedETA);
        }
    }

    /**
     * Formats the estimated time of arrival (ETA) given in milliseconds into a human-readable
     * string representation in the format of hours, minutes, and seconds.
     *
     * @param eta The estimated time of arrival in milliseconds.
     * @return A formatted string representing the ETA in hours, minutes, and seconds.
     *         For example, "2 h 15 m 30 s" or "45 s" if only seconds are present.
     */
    private static String formatETA(long eta) {
        long hours = eta / (3600 * 1000);
        eta %= 3600 * 1000;

        long minutes = eta / (60 * 1000);
        eta %= 60 * 1000;

        long seconds = eta / 1000;

        StringBuilder formattedETA = new StringBuilder();
        if (hours > 0) {
            formattedETA.append(hours).append(" h ");
        }
        if (minutes > 0) {
            formattedETA.append(minutes).append(" m ");
        }
        if (seconds > 0 || formattedETA.isEmpty()) {
            formattedETA.append(seconds).append(" s ");
        }

        return formattedETA.toString().trim();
    }
}
