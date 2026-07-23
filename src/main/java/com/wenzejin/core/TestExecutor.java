package com.wenzejin.core;

import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.mr.assertion.TestAssertion;
import com.wenzejin.core.result.ExecutionStatus;
import com.wenzejin.core.result.Result;
import com.wenzejin.core.result.TestOutcome;
import com.wenzejin.core.worker.RawExecutionResult;
import com.wenzejin.core.worker.TestWorker;
import com.wenzejin.core.worker.TestWorkerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final boolean panicMode;

    // --- State ---
    private long startTime;
    private final AtomicBoolean panicFlag = new AtomicBoolean(false);

    private static final int MAX_RETRY_COUNT = 3;
    private static final int PROGRESS_REPORT_INTERVAL = 10; // report progress every 10%

    private ExecutorService executor;
    private ThreadLocal<TestWorker> threadLocalWorker;
    private Set<TestWorker> workersRegistry;

    private static final Logger logger = LogManager.getLogger("TestExecutor");

    /**
     * Constructs a new TestExecutor instance with the specified parameters.
     */
    public TestExecutor(Mode mode, String workspace, String workerType, Map<String, Object> workerArguments, int threadNum, String storeMode, boolean panicMode) {
        this.mode = mode;
        this.workspace = workspace;
        this.workerType = workerType;
        this.workerArguments = workerArguments;
        this.threadNum = threadNum;
        this.panicMode = panicMode;

        logSpace = Paths.get(workspace, "logs");
        this.storeMode = storeMode;
    }

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
     * Executes a list of test cases with an optional per-step callback.
     *
     * @param tcs the test cases to execute
     * @param onEachStep a callback invoked after each test case completes (may be null);
     *                   called from worker threads — keep it fast and thread-safe
     */
    public List<TestOutcome> execute(List<TestCase> tcs, Runnable onEachStep) {
        if (tcs == null || tcs.isEmpty()) {
            logger.warn("No test cases provided for execution.");
            return Collections.emptyList();
        }
        if (executor == null || threadLocalWorker == null) {
            logger.error("Executor or thread-local worker is not initialized. Call init() before execute().");
            throw new IllegalStateException("TestExecutor is not properly initialized.");
        }

        startTime = System.currentTimeMillis();
        panicFlag.set(false);
        final AtomicInteger progress = new AtomicInteger(0);
        final int totalTasks = tcs.size();
        logger.info("Executing tests for {} tasks.", totalTasks);

        List<Callable<TestOutcome>> tasks = new ArrayList<>();
        for (final TestCase tc : tcs) {
            tasks.add(() -> {
                TestWorker worker = threadLocalWorker.get();
                RawExecutionResult rawResult = null;
                ExecutionStatus status = ExecutionStatus.SKIPPED;
                Result sutResult = null;
                List<TestAssertion> failedAssertions = new ArrayList<>();
                String errorMessage = null;

                // Panic mode early-stop: if the flag is set, skip this test case entirely.
                if (panicMode && panicFlag.get()) {
                    int currentProgress = progress.incrementAndGet();
                    procedureCallback(currentProgress, totalTasks);
                    if (onEachStep != null) onEachStep.run();
                    TestOutcome skipped = new TestOutcome(tc, mode == Mode.SOURCE, ExecutionStatus.SKIPPED, null, new ArrayList<>(), "Skipped due to panic mode", 0);
                    logTestCase(skipped, null);
                    return skipped;
                }

                try {
                    for (int retry = 0; retry < MAX_RETRY_COUNT; retry++) {
                        rawResult = worker.execute(tc);
                        if (rawResult.exitCode == 0) {
                            status = ExecutionStatus.SUCCESS;
                            break;
                        } else if (rawResult.exitCode == 2) { // Assuming 2 is timeout based on StandardWorker
                             status = ExecutionStatus.TIMEOUT;
                             logger.warn("Test case {} timed out. Retry {} / 3", tc.getId(), retry);
                        } else {
                             status = ExecutionStatus.CRASHED;
                             logger.warn("Test case {} crashed. Retry {} / 3", tc.getId(), retry);
                        }
                    }

                    if (status == ExecutionStatus.SUCCESS) {
                        sutResult = worker.parseResult(rawResult, tc);
                        if (sutResult == null) {
                            status = ExecutionStatus.INVALID_OUTPUT;
                            errorMessage = "Failed to parse result";
                            logger.warn("Failed to parse result for test case: {}", tc.getId());
                        } else {
                            if (mode == Mode.FOLLOW) {
                                for (TestAssertion assertion : tc.getAssertions()) {
                                    if (!assertion.assertTest(sutResult)) {
                                        failedAssertions.add(assertion);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("Unexpected error while executing test case {}: {}", tc.getId(), e.getMessage(), e);
                    status = ExecutionStatus.CRASHED;
                    errorMessage = e.getMessage();
                } finally {
                    int currentProgress = progress.incrementAndGet();
                    procedureCallback(currentProgress, totalTasks);
                    if (onEachStep != null) onEachStep.run();
                }
                
                TestOutcome outcome = new TestOutcome(
                    tc, 
                    mode == Mode.SOURCE, 
                    status, 
                    sutResult, 
                    failedAssertions, 
                    errorMessage, 
                    rawResult != null ? rawResult.durationMs : 0
                );

                if (panicMode && ((mode == Mode.FOLLOW && !outcome.isPassed()) || (status != ExecutionStatus.SUCCESS && status != ExecutionStatus.SKIPPED))) {
                    logger.info("Panic activated due to test case {} status: {} passed: {}. Setting panic flag.", tc.getId(), status, outcome.isPassed());
                    panicFlag.set(true);
                }
                
                logTestCase(outcome, rawResult);
                return outcome;
            });
        }

        List<TestOutcome> outcomes = new ArrayList<>();
        try {
            List<Future<TestOutcome>> futures = executor.invokeAll(tasks);
            for (Future<TestOutcome> future : futures) {
                outcomes.add(future.get());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Execution interrupted or failed.", e);
            Thread.currentThread().interrupt();
        }
        
        afterTest(outcomes);
        return outcomes;
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

    private void afterTest(List<TestOutcome> outcomes) {
        long endTime = System.currentTimeMillis();
        long checked = outcomes.stream().filter(o -> o.status == ExecutionStatus.SUCCESS).count();
        long failed = outcomes.stream().filter(o -> !o.isPassed() && o.status != ExecutionStatus.SKIPPED).count();
        
        logger.info("Execution finished. Test cases checked: {} / {} , Time elapsed: {} ms", checked, outcomes.size(), endTime - startTime);
        if (mode == Mode.FOLLOW) {
            logger.info("Test cases failed: {} / {}", failed, outcomes.size());
        }
    }

    private void procedureCallback(int currentProgress, int totalTasks) {
        int onePercent = totalTasks / PROGRESS_REPORT_INTERVAL;
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

    private void logTestCase(TestOutcome outcome, RawExecutionResult rawResult) {
        if (storeMode.equals("none")) {
            return;
        }

        Path logDir;
        String id = outcome.testCaseId;
        if (mode == Mode.SOURCE) {
            logDir = logSpace.resolve("source").resolve(id);
        } else if (!outcome.isPassed()) {
            logDir = logSpace.resolve("failed").resolve(id);
        } else if (storeMode.equals("all")) {
            logDir = logSpace.resolve("passed").resolve(id);
        } else {
            return;
        }

        try {
            Files.createDirectories(logDir);

            if (rawResult != null) {
                if (rawResult.artifacts != null) {
                    for (Map.Entry<String, String> entry : rawResult.artifacts.entrySet()) {
                        Files.writeString(logDir.resolve(entry.getKey()), entry.getValue());
                    }
                }
                if (rawResult.stdout != null) {
                    Files.writeString(logDir.resolve("stdout.txt"), rawResult.stdout);
                }
                if (rawResult.stderr != null) {
                    Files.writeString(logDir.resolve("stderr.txt"), rawResult.stderr);
                }
            }

            // create file info.json
            JSONObject info = new JSONObject();
            info.put("id", outcome.testCaseId);
            info.put("isSource", outcome.isSource);
            info.put("status", outcome.status.toString());
            info.put("passed", outcome.isPassed());
            info.put("duration", outcome.durationMs);
            info.put("exitCode", rawResult != null ? rawResult.exitCode : -1);
            
            if (outcome.errorMessage != null) {
                info.put("error", outcome.errorMessage);
            }
            
            if (!outcome.isSource) {
                info.put("source", outcome.testCase.getSource());
            }
            
            if (!outcome.failedAssertions.isEmpty()) {
                JSONArray failures = new JSONArray();
                for (TestAssertion assertion : outcome.failedAssertions) {
                    failures.put(assertion.toString());
                }
                info.put("failedAssertions", failures);
            }

            Files.writeString(logDir.resolve("info.json"), info.toString(2));
            
        } catch (IOException e) {
            logger.error("Failed to log test case {}", outcome.testCaseId, e);
        }
    }
}
