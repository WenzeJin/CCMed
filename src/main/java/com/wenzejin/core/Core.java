package com.wenzejin.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.mr.MTCore;
import com.wenzejin.core.mr.assertion.TestAssertion;
import com.wenzejin.core.mr.jin.ARTCore;
import com.wenzejin.core.mr.jin.RandomCore;
import com.wenzejin.core.mr.jin.rules.TagApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import me.tongfei.progressbar.ProgressBar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Core {
    // --- Logger & Config ---
    private static final Logger logger = LogManager.getLogger("TesterCore");
    private final Config config;

    private String workspace;

    public Core(Config config) {
        this.config = config;
    }

    // --- Main Execution Entry ---

    private void setWorkspace() {
        String taskName = config.taskName;
        if (config.timeStamp) {
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            taskName = taskName + "_" + timestamp;
        }
        workspace = Paths.get(config.workspaceRoot, taskName).toString();
        try {
            Files.createDirectories(Paths.get(config.workspaceRoot, taskName));
        }  catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    private void exportConfig() {
        try {
            String path = Paths.get(workspace, "config.json").toString();
            config.saveToJson(path);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    // --- Normal Batch Execution ---
    public void execute() {
        setWorkspace();
        exportConfig();

        try {
            final Map<String, Object> arguments = prepareArguments();
            final Preprocessor preprocessor = new Preprocessor(config.rulesPath, config.enumLimit, config.threadLimit, config.bfuncPolicy);

            preprocessor.generateTestCases();
            preprocessor.reduceSize(config.sourceLimit);
            logger.info("Source cases preparation completed in {} ms.", System.currentTimeMillis());

            boolean hasFailures = false;
            boolean sourceCaseFailures = false;
            final Map<Integer, List<TestAssertion>> failedCases = new HashMap<>();

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


            ProgressBar pb;
            if (config.stopMode.contains("limited")) {
                pb = new ProgressBar("Testing Progress", config.followUpLimit);
            } else {
                pb = new ProgressBar("Testing Progress", -1);
            }

            for (int batch = 1; batch <= totalBatches; batch++) {
                logger.info("*** Starting Batch {} ***", batch);
                logger.info("Testing source test cases...");
                pb.setExtraMessage("B #" + batch + " S");
                List<TestCase> sourceCases = preprocessor.getBatch(config.batchSize);
                preExecutor.execute(sourceCases);
                sourceCaseFailures = preExecutor.getChecked() != sourceCases.size();
                logger.info("Source cases testing completed.");

                if (!sourceCaseFailures) {
                    MTCore mtCore = config.ART ? new ARTCore(config.threadLimit)
                                               : new RandomCore(config.threadLimit);
                    mtCore.initCandidates(sourceCases);
                    int round = 0;
                    int processedCount = 0;
                    while (mtCore.hasMore() && (processedCount < batchFollowUpLimit || !config.stopMode.contains("limited"))) {
                        round++;
                        logger.info("*** Batch {} : Round {} ***", batch, round);
                        pb.setExtraMessage("B #" + batch + " FMB #" + round);
                        int currentBatchSize = config.stopMode.contains("limited")
                                ? Math.min(batchFollowUpLimit - processedCount, config.miniBatchSize)
                                : config.miniBatchSize;
                        List<TestCase> followUpCases = mtCore.getNextBatch(currentBatchSize);
                        postExecutor.execute(followUpCases);
                        processedCount += followUpCases.size();
                        Map<TestCase, List<TestAssertion>> batchFailures = postExecutor.getFailedCases();
                        batchFailures.forEach((testCase, assertions) -> failedCases.put(testCase.getId(), assertions));
                        hasFailures = hasFailures || !batchFailures.isEmpty();
                        if (!batchFailures.isEmpty() && firstFailure == -1) {
                            firstFailure = totalFollowUp + postExecutor.getFirstFailure();
                        }
                        totalFollowUp += followUpCases.size();
                        pb.stepBy(followUpCases.size());
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
            pb.close();
            dumpJsonResult(hasFailures, sourceCaseFailures, failedCases, firstFailure);
            logger.info("Testing completed successfully! Failed cases have been stored in the database.");
            logger.info("Total time elapsed: {} ms. {} source -> {} follow-up checked, {} failed cases.",
                    System.currentTimeMillis() - startTime, totalBatches * config.batchSize, totalFollowUp, failedCases.size());
            System.out.println("Testing completed successfully! Result: " +
                    (hasFailures ? "FAILED cases detected." : "All passed.") +
                    " Total time: " + (System.currentTimeMillis() - startTime) / 1000 + " s.");
        } catch (IllegalArgumentException e) {
            logger.error("Illegal Argument Exception occurred: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected Exception occurred: {}", e.getMessage(), e);
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

    // --- Result Dumping ---
    private void dumpJsonResult(boolean failed, boolean sourceFailed, Map<Integer, List<TestAssertion>> failedCases, int firstFailure) {
        JsonObject result = new JsonObject();
        result.addProperty("passed", !failed && !sourceFailed);
        result.addProperty("source-failed", sourceFailed);
        result.addProperty("first-failure", firstFailure);
        JsonArray failedArray = new JsonArray();
        for (Map.Entry<Integer, List<TestAssertion>> entry: failedCases.entrySet()) {
            JsonObject failedCase = new JsonObject();
            failedCase.addProperty("case_id", entry.getKey());
            failedCase.addProperty("failed_assertions", entry.getValue().toString());
            failedArray.add(failedCase);
        }
        result.add("failed_cases", failedArray);

        Path resultPath = Paths.get(workspace, "result.json");

        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.write(resultPath, gson.toJson(result).getBytes());
        } catch (IOException e) {
            logger.error("Failed to dump result to file: {}", e.getMessage());
        }
    }
}
