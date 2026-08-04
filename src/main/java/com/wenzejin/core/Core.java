package com.wenzejin.core;

import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.mr.MTCore;
import com.wenzejin.core.mr.ccm.ARTCore;
import com.wenzejin.core.mr.ccm.ChainCompositionCore;
import com.wenzejin.core.mr.ccm.rules.TestCaseSetup;
import com.wenzejin.core.result.ExecutionStatus;
import com.wenzejin.core.result.TestOutcome;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Core {
    // --- Logger & Config ---
    private static final Logger logger = LogManager.getLogger("TesterCore");
    private final Config config;
    private final ProgressListener progressListener;

    private String workspace;

    public Core(Config config) {
        this(config, new CLIProgressListener());
    }

    public Core(Config config, ProgressListener progressListener) {
        this.config = config;
        this.progressListener = progressListener;
    }

    // --- Main Execution Entry ---

    // --- Normal Batch Execution ---
    public void execute() {
        WorkspaceManager workspaceManager = new WorkspaceManager(config);
        workspace = workspaceManager.setupWorkspace();
        workspaceManager.exportConfig();

        // ── Welcome banner ──────────────────────────────────────
        ConsoleUI.printWelcome(config);

        TestExecutor preExecutor = null;
        TestExecutor postExecutor = null;
        DetailDataRecorder detailDataRecorder = null;

        try {
            final Map<String, Object> arguments = prepareArguments();
            final SourceGenerator preprocessor = new SourceGenerator(config.getRulesPath(), config.getEnumLimit(), config.getThreadLimit(), config.getBfuncPolicy());

            preprocessor.generateTestCases();
            preprocessor.reduceSize(config.getSourceLimit());
            logger.info("Source cases preparation completed in {} ms.", System.currentTimeMillis());

            boolean hasFailures = false;
            boolean sourceCaseFailures = false;
            int failedSourceCount = 0;
            final List<TestOutcome> failedOutcomes = new ArrayList<>();
            final List<TestOutcome> sourceFailures = new ArrayList<>();

                preExecutor = new TestExecutor(
                    TestExecutor.Mode.SOURCE, workspace, config.getWorkerType(), arguments, config.getThreadLimit(), config.getStoreMode(), config.getStopMode().contains("panic"));
                postExecutor = new TestExecutor(
                    TestExecutor.Mode.FOLLOW, workspace, config.getWorkerType(), arguments, config.getThreadLimit(), config.getStoreMode(), config.getStopMode().contains("panic"));
            preExecutor.init();
            postExecutor.init();

            TestCaseSetup.simpleMode = config.isSimpleMode();

            final int totalBatches = preprocessor.getSize() / config.getBatchSize() + ((preprocessor.getSize() % config.getBatchSize()) == 0 ? 0 : 1);
            final int batchFollowUpLimit = config.getFollowUpLimit() / totalBatches;
            int totalFollowUp = 0;
            int firstFailure = -1;
            long startTime = System.currentTimeMillis();
            final Map<Integer, String> sourceRootTypeMap = new HashMap<>();

            if (config.isDetailData()) {
                String detailDataPath = Paths.get(workspace, "detail-data.txt").toString();
                detailDataRecorder = new DetailDataRecorder(detailDataPath);
            }

            final int totalSourceCases = preprocessor.getSize();
            if (config.getStopMode().contains("limited")) {
                progressListener.onStart(totalSourceCases + config.getFollowUpLimit(), "Testing Progress");
            } else {
                progressListener.onStart(-1, "Testing Progress");
            }

            for (int batch = 1; batch <= totalBatches; batch++) {
                final int currentBatch = batch;
                logger.info("*** Starting Batch {} ***", currentBatch);
                logger.info("Testing source test cases...");
                progressListener.onStep(0, "B #" + currentBatch + " S");
                List<TestCase> sourceCases = preprocessor.getBatch(config.getBatchSize());
                final int sourceTotal = sourceCases.size();
                AtomicInteger sourceDone = new AtomicInteger(0);
                List<TestOutcome> sourceOutcomes = preExecutor.execute(sourceCases, () -> {
                    int done = sourceDone.incrementAndGet();
                    progressListener.onStep(1, "B #" + currentBatch + " S (" + done + "/" + sourceTotal + ")");
                });
                for (TestOutcome sourceOutcome : sourceOutcomes) {
                    if (sourceOutcome.testCase != null
                            && sourceOutcome.testCase.getRule() != null
                            && sourceOutcome.testCase.getRule().formula != null) {
                        sourceRootTypeMap.put(
                                sourceOutcome.testCase.getId(),
                                sourceOutcome.testCase.getRule().formula.getNodeType()
                        );
                    }
                }
                long successfulSourceCases = sourceOutcomes.stream()
                        .filter(o -> o.status == ExecutionStatus.SUCCESS)
                        .count();
                sourceCaseFailures = successfulSourceCases != sourceCases.size();

                // Collect source-case failures for reporting
                for (TestOutcome so : sourceOutcomes) {
                    if (!so.isPassed() && so.status != ExecutionStatus.SKIPPED) {
                        sourceFailures.add(so);
                    }
                }
                failedSourceCount += (int) sourceOutcomes.stream()
                        .filter(o -> !o.isPassed() && o.status != ExecutionStatus.SKIPPED)
                        .count();
                
                logger.info("Source cases testing completed.");

                if (!sourceCaseFailures) {
                    MTCore mtCore;
                    if ("chain".equalsIgnoreCase(config.getMtCore())) {
                        mtCore = new ChainCompositionCore(config.getChainLength(), "random");
                        logger.info("Using ChainCompositionCore with chainLength={}", config.getChainLength());
                    } else {
                        mtCore = new ARTCore(config.getThreadLimit());
                    }
                    mtCore.initCandidates(sourceOutcomes);
                    int round = 0;
                    int processedCount = 0;
                    while (mtCore.hasMore() && (processedCount < batchFollowUpLimit || !config.getStopMode().contains("limited"))) {
                        round++;
                        final int currentRound = round;
                        logger.info("*** Batch {} : Round {} ***", currentBatch, currentRound);
                        progressListener.onStep(0, "B #" + currentBatch + " FMB #" + currentRound);
                        int currentBatchSize = config.getStopMode().contains("limited")
                                ? Math.min(batchFollowUpLimit - processedCount, config.getMiniBatchSize())
                                : config.getMiniBatchSize();
                        List<TestCase> followUpCases = mtCore.getNextBatch(currentBatchSize);
                        final int followUpTotal = followUpCases.size();
                        AtomicInteger followUpDone = new AtomicInteger(0);
                        List<TestOutcome> followUpOutcomes = postExecutor.execute(followUpCases, () -> {
                            int done = followUpDone.incrementAndGet();
                            progressListener.onStep(1, "B #" + currentBatch + " FMB #" + currentRound + " (" + done + "/" + followUpTotal + ")");
                        });
                        if (detailDataRecorder != null) {
                            detailDataRecorder.recordFollowUpOutcomes(followUpOutcomes, sourceRootTypeMap);
                        }
                        processedCount += followUpCases.size();

                        List<TestOutcome> batchFailures = new ArrayList<>();
                        for (int i = 0; i < followUpOutcomes.size(); i++) {
                            TestOutcome outcome = followUpOutcomes.get(i);
                            if (!outcome.isPassed() && outcome.status != ExecutionStatus.SKIPPED) {
                                batchFailures.add(outcome);
                                if (firstFailure == -1) {
                                    firstFailure = totalFollowUp + i + 1;
                                }
                            }
                        }

                        failedOutcomes.addAll(batchFailures);
                        hasFailures = hasFailures || !batchFailures.isEmpty();

                        totalFollowUp += followUpCases.size();
                        if (hasFailures && config.getStopMode().contains("panic")) {
                            logger.info("Batch {} interrupted due to failed cases. Panic mode activated.", currentBatch);
                            break;
                        }
                    }
                    logger.info("Batch {} completed. Processed {} follow-up cases.", currentBatch, processedCount);
                    mtCore.logStats();
                } else {
                    logger.warn("Testing interrupted due to failed source cases. No follow-up tests will be performed.");
                    break;
                }
                if (hasFailures && config.getStopMode().contains("panic")) {
                    logger.warn("Testing interrupted due to failed cases. Panic mode activated.");
                    System.out.println("Testing interrupted due to failed cases. (panic mode activated)");
                    break;
                }
            }
            progressListener.onFinish(null);

            // ── Categorise all failures for the result summary ─────
            int assertionFails = 0, crashesCount = 0, timeoutsCount = 0, invalidCount = 0;
            for (TestOutcome o : sourceFailures) {
                if (!o.failedAssertions.isEmpty()) assertionFails++;
                switch (o.status) {
                    case CRASHED:        crashesCount++;  break;
                    case TIMEOUT:        timeoutsCount++;  break;
                    case INVALID_OUTPUT: invalidCount++;   break;
                    default: break;
                }
            }
            for (TestOutcome o : failedOutcomes) {
                if (!o.failedAssertions.isEmpty()) assertionFails++;
                switch (o.status) {
                    case CRASHED:        crashesCount++;  break;
                    case TIMEOUT:        timeoutsCount++;  break;
                    case INVALID_OUTPUT: invalidCount++;   break;
                    default: break;
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            ConsoleUI.printResult(sourceCaseFailures,
                    totalSourceCases, failedSourceCount,
                    totalFollowUp, failedOutcomes.size(),
                    assertionFails, crashesCount, timeoutsCount, invalidCount,
                    elapsed, workspace);

            ResultReporter reporter = new ResultReporter(workspace);
            reporter.dumpJsonResult(hasFailures, sourceCaseFailures, failedOutcomes, firstFailure);

            logger.info("Testing completed successfully! Failed cases have been stored in the database.");
            logger.info("Total time elapsed: {} ms. {} source -> {} follow-up checked, {} failed cases.",
                    elapsed, totalBatches * config.getBatchSize(), totalFollowUp, failedOutcomes.size());
        } catch (IllegalArgumentException e) {
            logger.error("Illegal Argument Exception occurred: {}", e.getMessage(), e);
            progressListener.onError(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected Exception occurred: {}", e.getMessage(), e);
            progressListener.onError(e.getMessage());
        } finally {
            if (preExecutor != null) {
                preExecutor.close();
            }
            if (postExecutor != null) {
                postExecutor.close();
            }
            if (detailDataRecorder != null) {
                try {
                    detailDataRecorder.close();
                } catch (Exception e) {
                    logger.warn("Failed to close detail-data recorder: {}", e.getMessage());
                }
            }
        }
    }

    // --- Argument Preparation ---
    private Map<String, Object> prepareArguments() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("preprocessor.rules-input-directory", config.getRulesPath());

        arguments.put("std.jar-target-path", config.getTargetPath());
        arguments.put("std.bfunc-class-path", config.getBfuncPath());
        arguments.put("std.arguments", config.getArguments());

        arguments.putAll(config.getOtherArgs());

        return arguments;
    }
}
