package com.wenzejin.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.cases.TestCaseDataBaseDriver;
import com.wenzejin.core.mr.MTCore;
import com.wenzejin.core.mr.assertion.TestAssertion;
import com.wenzejin.core.mr.jin.ARTCore;
import com.wenzejin.core.mr.jin.RandomCore;
import com.wenzejin.core.mr.jin.rules.TagApplication;
import com.wenzejin.core.worker.TestWorker;
import com.wenzejin.core.worker.TestWorkerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class Core {
    // --- Logger & Config ---
    private static final Logger logger = LogManager.getLogger("TesterCore");
    private final Config config;

    // --- Constructor ---
    public Core() {
        this.config = new Config();
    }

    // --- Configuration Loading ---
    public void loadDefaultSettings() {
        config.loadDefaultSettings();
    }

    public void loadSettingsFromYaml(String yamlPath) throws IOException {
        config.loadSettingsFromYaml(yamlPath);
    }

    // --- Main Execution Entry ---
    public boolean execute() {
        return switch (config.mode) {
            case "normal" -> executeNormal();
            case "single" -> executeSingle();
            default -> {
                logger.error("Invalid mode: {}", config.mode);
                yield false;
            }
        };
    }

    // --- Normal Batch Execution ---
    private boolean executeNormal() {
        try {
            TestCaseDataBaseDriver.setDatabaseUrl(config.databaseUrl);
            TestCaseDataBaseDriver.clearDB();
            TestCaseDataBaseDriver.initializeDB();

            final Map<String, Object> arguments = prepareArguments();
            final Preprocessor preprocessor = new Preprocessor(arguments);
            preprocessor.generateTestCases();
            preprocessor.reduceSize(config.sourceLimit);
            logger.info("Source cases preparation completed in {} ms.", System.currentTimeMillis());

            boolean hasFailures = false;
            boolean sourceCaseFailures = false;
            final Map<Integer, List<TestAssertion>> failedCases = new HashMap<>();

            final TestExecutor preExecutor = new TestExecutor(TestExecutor.Mode.SOURCE, config.workerType, arguments, config.threadLimit);
            final TestExecutor postExecutor = new TestExecutor(TestExecutor.Mode.FOLLOW, config.workerType, arguments, config.threadLimit);
            preExecutor.init();
            postExecutor.init();

            TagApplication.simpleMode = config.simpleMode;

            final int totalBatches = preprocessor.getSize() / config.batchSize;
            final int batchFollowUpLimit = config.followUpLimit / totalBatches;
            int totalFollowUp = 0;
            int firstFailure = -1;
            long startTime = System.currentTimeMillis();

            for (int batch = 1; batch <= totalBatches; batch++) {
                logger.info("*** Starting Batch {} ***", batch);
                logger.info("Testing source test cases...");
                List<TestCase> sourceCases = preprocessor.getBatch(config.batchSize);
                preExecutor.execute(sourceCases);
                sourceCaseFailures = preExecutor.getChecked() != sourceCases.size();
                logger.info("Source cases testing completed.");
                TestCaseDataBaseDriver.storeTestCases(sourceCases, "source");

                if (!sourceCaseFailures) {
                    MTCore mtCore = config.ART ? new ARTCore(config.threadLimit, config.enableUndetermined)
                                               : new RandomCore(config.threadLimit, config.enableUndetermined);
                    mtCore.initCandidates(sourceCases);
                    int round = 0;
                    int processedCount = 0;
                    while (mtCore.hasMore() && (processedCount < batchFollowUpLimit || !config.stopMode.contains("limited"))) {
                        round++;
                        logger.info("*** Batch {} : Round {} ***", batch, round);
                        int currentBatchSize = config.stopMode.contains("limited")
                                ? Math.min(batchFollowUpLimit - processedCount, config.miniBatchSize)
                                : config.miniBatchSize;
                        List<TestCase> followUpCases = mtCore.getNextBatch(currentBatchSize);
                        postExecutor.execute(followUpCases);
                        processedCount += followUpCases.size();
                        Map<TestCase, List<TestAssertion>> batchFailures = postExecutor.getFailedCases();
                        batchFailures.forEach((testCase, assertions) -> failedCases.put(testCase.getId(), assertions));
                        if ("all".equalsIgnoreCase(config.storeMode)) {
                            TestCaseDataBaseDriver.storeTestCases(followUpCases, "followup");
                        } else {
                            TestCaseDataBaseDriver.storeTestCases(batchFailures.keySet(), "failed");
                        }
                        hasFailures = hasFailures || !batchFailures.isEmpty();
                        if (!batchFailures.isEmpty() && firstFailure == -1) {
                            firstFailure = totalFollowUp + postExecutor.getFirstFailure();
                        }
                        totalFollowUp += followUpCases.size();
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
                    break;
                }
            }
            preExecutor.close();
            postExecutor.close();
            dumpJsonResult(hasFailures, sourceCaseFailures, failedCases, firstFailure);
            logger.info("Testing completed successfully! Failed cases have been stored in the database.");
            logger.info("Total time elapsed: {} ms. {} source -> {} follow-up checked, {} failed cases.",
                    System.currentTimeMillis() - startTime, totalBatches * config.batchSize, totalFollowUp, failedCases.size());
            return failedCases.isEmpty();
        } catch (SQLException e) {
            logger.error("SQL Exception occurred: {}", e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal Argument Exception occurred: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected Exception occurred: {}", e.getMessage(), e);
        }
        return false;
    }

    // --- Single Case Execution ---
    public boolean executeSingle() {
        if (!config.otherArgs.containsKey("id")) {
            logger.error("Single mode requires a case id.");
            return false;
        }
        int caseId;
        boolean passed = false;
        try {
            caseId = Integer.parseInt(config.otherArgs.get("id").toString());
        } catch (NumberFormatException e) {
            logger.error("Invalid case id: {}", config.otherArgs.get("id"));
            return false;
        }
        TestCaseDataBaseDriver.setDatabaseUrl(config.databaseUrl);
        Map<String, Object> arguments = prepareArguments();
        TestWorker worker = TestWorkerFactory.getTestWorker(config.workerType, arguments);
        try {
            TestCase tc = TestCaseDataBaseDriver.getTestCaseById(caseId);
            worker.initWorkspace();
            int retry = 0;
            boolean succeed = false;
            while (!succeed && retry <= 3) {
                int error = worker.singleRun(tc);
                if (error == 0) {
                    succeed = true;
                } else {
                    logger.warn("Test case {} RE or TLE. Retry {} / 3", tc.getId(), retry);
                    retry++;
                }
            }
            if (succeed) {
                if (tc.getAssertions() != null) {
                    List<TestAssertion> failedAssertions = new ArrayList<>();
                    passed = tc.assertTest(failedAssertions);
                    if (passed) {
                        logger.info("Test case {} passed.", tc.getId());
                    } else {
                        logger.warn("Test case {} failed.", tc.getId());
                        System.out.println("Failed assertions:");
                        for (TestAssertion a : failedAssertions) {
                            System.out.println(a);
                        }
                    }
                }
            } else {
                logger.error("Test case {} failed after 3 retries.", tc.getId());
            }
            System.out.println("Test case details:");
            System.out.println(tc);
            System.out.println("Destroy workspace?: [Y/N]");
            int ch = System.in.read();
            if (ch == 'Y' || ch == 'y') {
                worker.destroyWorkspace();
            }
        } catch (SQLException e) {
            logger.error("SQL Exception Occurred: {}, check case id maybe.", e.getMessage());
        } catch (IOException e) {
            logger.error("IO Exception Occurred: {}.", e.getMessage());
        }
        return passed;
    }

    // --- Argument Preparation ---
    private Map<String, Object> prepareArguments() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("preprocessor.rules-input-directory", config.rulesPath);
        arguments.put("workspace-root", config.workspaceRoot);
        if (config.workerType.equalsIgnoreCase("infuse") || config.workerType.equalsIgnoreCase("infuse-old")) {
            arguments.put("infuse.workspace-root", config.workspaceRoot);
            arguments.put("infuse.jar-target-path", config.targetPath);
            arguments.put("infuse.bfunc-class-path", config.bfuncPath);
            arguments.put("infuse.arguments", config.arguments);
        } else {
            throw new IllegalArgumentException("Invalid worker type: " + config.workerType + ". Please check configurations.");
        }
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
            failedCase.addProperty("case-id", entry.getKey());
            failedCase.addProperty("failed-assertions", entry.getValue().toString());
            failedArray.add(failedCase);
        }
        result.add("failed-cases", failedArray);
        try (FileOutputStream fos = new FileOutputStream(config.resultPath)){
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            fos.write(gson.toJson(result).getBytes());
        } catch (IOException e) {
            logger.error("Failed to dump result to file: {}", e.getMessage());
        }
    }
}
