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

import java.util.*;

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

        try {
            final Map<String, Object> arguments = prepareArguments();
            final Preprocessor preprocessor = new Preprocessor(config.rulesPath, config.enumLimit, config.threadLimit, config.bfuncPolicy);

            preprocessor.generateTestCases();
            preprocessor.reduceSize(config.sourceLimit);
            logger.info("Source cases preparation completed in {} ms.", System.currentTimeMillis());

            boolean hasFailures = false;
            boolean sourceCaseFailures = false;
            final List<TestOutcome> failedOutcomes = new ArrayList<>();

            final TestExecutor preExecutor = new TestExecutor(
                    TestExecutor.Mode.SOURCE, workspace, config.workerType, arguments, config.threadLimit, config.storeMode);
            final TestExecutor postExecutor = new TestExecutor(
                    TestExecutor.Mode.FOLLOW, workspace, config.workerType, arguments, config.threadLimit, config.storeMode);
            preExecutor.init();
            postExecutor.init();

            TagApplication.simpleMode = config.simpleMode;

            final int totalBatches = preprocessor.getSize() / config.batchSize + ((preprocessor.getSize() % config.batchSize) == 0 ? 0 : 1);
            final int batchFollowUpLimit = config.followUpLimit / totalBatches;
            int totalFollowUp = 0;
            int firstFailure = -1;
            long startTime = System.currentTimeMillis();


            if (config.stopMode.contains("limited")) {
                progressListener.onStart(config.followUpLimit, "Testing Progress");
            } else {
                progressListener.onStart(-1, "Testing Progress");
            }

            for (int batch = 1; batch <= totalBatches; batch++) {
                logger.info("*** Starting Batch {} ***", batch);
                logger.info("Testing source test cases...");
                progressListener.onStep(0, "B #" + batch + " S");
                List<TestCase> sourceCases = preprocessor.getBatch(config.batchSize);
                
                List<TestOutcome> sourceOutcomes = preExecutor.execute(sourceCases);
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
                        logger.info("*** Batch {} : Round {} ***", batch, round);
                        progressListener.onStep(0, "B #" + batch + " FMB #" + round);
                        int currentBatchSize = config.stopMode.contains("limited")
                                ? Math.min(batchFollowUpLimit - processedCount, config.miniBatchSize)
                                : config.miniBatchSize;
                        List<TestCase> followUpCases = mtCore.getNextBatch(currentBatchSize);
                        
                        List<TestOutcome> followUpOutcomes = postExecutor.execute(followUpCases);
                        processedCount += followUpCases.size();
                        
                        List<TestOutcome> batchFailures = new ArrayList<>();
                        for (int i = 0; i < followUpOutcomes.size(); i++) {
                            TestOutcome outcome = followUpOutcomes.get(i);
                            if (!outcome.isPassed()) {
                                batchFailures.add(outcome);
                                if (firstFailure == -1) {
                                    firstFailure = totalFollowUp + i + 1;
                                }
                            }
                        }
                        
                        failedOutcomes.addAll(batchFailures);
                        hasFailures = hasFailures || !batchFailures.isEmpty();
                        
                        totalFollowUp += followUpCases.size();
                        progressListener.onStep(followUpCases.size(), null);
                        if (hasFailures && config.stopMode.contains("panic")) {
                            logger.info("Batch {} interrupted due to failed cases. Panic mode activated.", batch);
                            break;
                        }
                    }
                    logger.info("Batch {} completed. Processed {} follow-up cases.", batch, processedCount);
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
            preExecutor.close();
            postExecutor.close();
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
