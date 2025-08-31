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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Core {

    // Configurations
    private static final Logger logger = LogManager.getLogger("TesterCore");

    private final TesterConfig config;

    public Core() {
        this.config = new TesterConfig();
    }

    public void loadDefaultSettings() {
        config.loadDefaultSettings();
    }

    public void loadSettingsFromYaml(String yamlPath) throws IOException {
        config.loadSettingsFromYaml(yamlPath);
    }

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

    private boolean executeNormal() {
        try {
            // Initialize database
            TestCaseDataBaseDriver.setDatabaseUrl(config.databaseUrl);
            TestCaseDataBaseDriver.clearDB();
            TestCaseDataBaseDriver.initializeDB();

            // Declare executors
            TestExecutor preExecutor;
            TestExecutor postExecutor;

            // Initialize timer
            long startTime = System.currentTimeMillis();

            // Prepare arguments
            Map<String, Object> arguments = prepareArguments();

            // Prepare test cases
            Preprocessor preprocessor = new Preprocessor(arguments);
            preprocessor.generateTestCases();
            preprocessor.reduceSize(config.sourceLimit);
            logger.info("Source cases preparation completed in {} ms.", System.currentTimeMillis() - startTime);
            startTime = System.currentTimeMillis();

            // Initialize statistics
            boolean hasFailures = false;
            boolean sourceCaseFailures = false;
            Map<Integer, List<TestAssertion>> failedCases = new HashMap<>();

            // Prepare executors
            preExecutor = new TestExecutor(TestExecutor.Mode.SOURCE, config.workerType, arguments, config.threadLimit);
            postExecutor = new TestExecutor(TestExecutor.Mode.FOLLOW, config.workerType, arguments, config.threadLimit);
            preExecutor.init();
            postExecutor.init();

            // set rules configurations
            TagApplication.simpleMode = config.simpleMode;

            int totalBatches = preprocessor.getSize() / config.batchSize;
            int batchFollowUpLimit = config.followUpLimit / totalBatches;
            int totalFollowUp = 0;
            int firstFailure = -1;

            for (int batch = 1; batch <= totalBatches; batch++) {
                logger.info("*** Starting Batch {} ***", batch);

                // Test source cases
                logger.info("Testing source test cases...");
                List<TestCase> sourceCases = preprocessor.getBatch(config.batchSize);
                preExecutor.execute(sourceCases);
                sourceCaseFailures = preExecutor.getChecked() != sourceCases.size();
                logger.info("Source cases testing completed.");

                TestCaseDataBaseDriver.storeTestCases(sourceCases, "source");

                if (!sourceCaseFailures) {

                    MTCore mtCore;

                    if (config.ART) {
                        mtCore = new ARTCore(config.threadLimit, config.enableUndetermined);
                    } else {
                        mtCore = new RandomCore(config.threadLimit, config.enableUndetermined);
                    }

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

            // Dump results
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

    public boolean executeSingle() {
        if (!config.otherArgs.containsKey("id")) {
            logger.error("Single mode requires a case id.");
            return false;
        }

        int caseId = -1;
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
                int error = worker.singleRun(tc); // 执行测试用例
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

    /* Tool functions for loading configurations */


}
