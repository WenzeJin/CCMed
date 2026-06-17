package com.wenzejin.core;

import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.mr.MTCore;
import com.wenzejin.core.mr.jin.ARTCore;
import com.wenzejin.core.mr.jin.RandomCore;
import com.wenzejin.core.mr.jin.rules.TagApplication;
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

        TestExecutor preExecutor = null;
        TestExecutor postExecutor = null;
        DetailDataRecorder detailDataRecorder = null;

        try {
            final Map<String, Object> arguments = prepareArguments();
            final Preprocessor preprocessor = new Preprocessor(config.rulesPath, config.enumLimit, config.threadLimit, config.bfuncPolicy);

            preprocessor.generateTestCases();
            preprocessor.reduceSize(config.sourceLimit);
            logger.info("Source cases preparation completed in {} ms.", System.currentTimeMillis());

            boolean hasFailures = false;
            boolean sourceCaseFailures = false;
            final List<TestOutcome> failedOutcomes = new ArrayList<>();

                preExecutor = new TestExecutor(
                    TestExecutor.Mode.SOURCE, workspace, config.workerType, arguments, config.threadLimit, config.storeMode, config.stopMode.contains("panic"));
                postExecutor = new TestExecutor(
                    TestExecutor.Mode.FOLLOW, workspace, config.workerType, arguments, config.threadLimit, config.storeMode, config.stopMode.contains("panic"));
            preExecutor.init();
            postExecutor.init();

            TagApplication.simpleMode = config.simpleMode;

            final int totalBatches = preprocessor.getSize() / config.batchSize + ((preprocessor.getSize() % config.batchSize) == 0 ? 0 : 1);
            final int batchFollowUpLimit = config.followUpLimit / totalBatches;
            int totalFollowUp = 0;
            int firstFailure = -1;
            long startTime = System.currentTimeMillis();
            final Map<Integer, String> sourceRootTypeMap = new HashMap<>();

            if (config.detailData) {
                String detailDataPath = Paths.get(workspace, "detail-data.txt").toString();
                detailDataRecorder = new DetailDataRecorder(detailDataPath);
            }

            final int totalSourceCases = preprocessor.getSize();
            if (config.stopMode.contains("limited")) {
                progressListener.onStart(totalSourceCases + config.followUpLimit, "Testing Progress");
            } else {
                progressListener.onStart(-1, "Testing Progress");
            }

            for (int batch = 1; batch <= totalBatches; batch++) {
                final int currentBatch = batch;
                logger.info("*** Starting Batch {} ***", currentBatch);
                logger.info("Testing source test cases...");
                progressListener.onStep(0, "B #" + currentBatch + " S");
                List<TestCase> sourceCases = preprocessor.getBatch(config.batchSize);
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
                
                logger.info("Source cases testing completed.");

                if (!sourceCaseFailures) {
                    MTCore mtCore = config.ART ? new ARTCore(config.threadLimit)
                                               : new RandomCore(config.threadLimit);
                    mtCore.initCandidates(sourceOutcomes);
                    int round = 0;
                    int processedCount = 0;
                    while (mtCore.hasMore() && (processedCount < batchFollowUpLimit || !config.stopMode.contains("limited"))) {
                        round++;
                        final int currentRound = round;
                        logger.info("*** Batch {} : Round {} ***", currentBatch, currentRound);
                        progressListener.onStep(0, "B #" + currentBatch + " FMB #" + currentRound);
                        int currentBatchSize = config.stopMode.contains("limited")
                                ? Math.min(batchFollowUpLimit - processedCount, config.miniBatchSize)
                                : config.miniBatchSize;
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
                        if (hasFailures && config.stopMode.contains("panic")) {
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
                if (hasFailures && config.stopMode.contains("panic")) {
                    logger.warn("Testing interrupted due to failed cases. Panic mode activated.");
                    System.out.println("Testing interrupted due to failed cases. (panic mode activated)");
                    break;
                }
            }
            progressListener.onFinish(null);
            
            ResultReporter reporter = new ResultReporter(workspace);
            reporter.dumpJsonResult(hasFailures, sourceCaseFailures, failedOutcomes, firstFailure);
            
            logger.info("Testing completed successfully! Failed cases have been stored in the database.");
            logger.info("Total time elapsed: {} ms. {} source -> {} follow-up checked, {} failed cases.",
                    System.currentTimeMillis() - startTime, totalBatches * config.batchSize, totalFollowUp, failedOutcomes.size());
            System.out.println("Testing completed successfully! Result: " +
                    (hasFailures ? "FAILED cases detected." : "All passed.") +
                    " Total time: " + (System.currentTimeMillis() - startTime) / 1000 + " s.");
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
        arguments.put("preprocessor.rules-input-directory", config.rulesPath);

        arguments.put("std.jar-target-path", config.targetPath);
        arguments.put("std.bfunc-class-path", config.bfuncPath);
        arguments.put("std.arguments", config.arguments);

        arguments.putAll(config.otherArgs);

        return arguments;
    }
}
