package com.wenzejin.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wenzejin.core.mr.MTCore;
import com.wenzejin.core.mr.assertion.TestAssertion;
import com.wenzejin.core.mr.jin.ARTCore;
import com.wenzejin.core.mr.jin.RandomCore;
import com.wenzejin.core.mr.jin.rules.TagApplication;
import com.wenzejin.core.worker.TestWorker;
import com.wenzejin.core.worker.TestWorkerFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TesterCore {

    // Configurations
    private static final Logger logger = LogManager.getLogger("TesterCore");

    String      mode;
    String      stopMode;
    int         sourceLimit;
    int         followUpLimit;
    int         threadLimit;
    int         batchSize;
    int         miniBatchSize;
    String      databaseUrl;
    String      workerType;
    String      targetPath;
    String      bfuncPath;
    String      rulesPath;
    String      workspaceRoot;
    List<String> arguments;
    Map<String, Object> otherArgs;
    String      resultPath;
    boolean     ART;
    boolean     enableUndetermined;
    boolean     simpleMode;

    public TesterCore() {
        this.otherArgs = new HashMap<>();
    }

    public boolean execute() {
        return switch (mode) {
            case "normal" -> executeNormal();
            case "single" -> executeSingle();
            default -> {
                logger.error("Invalid mode: {}", mode);
                yield false;
            }
        };
    }

    private boolean executeNormal() {
        try {
            // Initialize database
            TestCaseDataBaseDriver.setDatabaseUrl(databaseUrl);
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
            preprocessor.reduceSize(sourceLimit);
            logger.info("Source cases preparation completed in {} ms.", System.currentTimeMillis() - startTime);
            startTime = System.currentTimeMillis();

            // Initialize statistics
            boolean hasFailures = false;
            boolean sourceCaseFailures = false;
            Map<Integer, List<TestAssertion>> failedCases = new HashMap<>();

            // Prepare executors
            preExecutor = new TestExecutor(TestExecutor.Mode.SOURCE, workerType, arguments, threadLimit);
            postExecutor = new TestExecutor(TestExecutor.Mode.FOLLOW, workerType, arguments, threadLimit);
            preExecutor.init();
            postExecutor.init();

            // set rules configurations
            TagApplication.simpleMode = simpleMode;

            int totalBatches = preprocessor.getSize() / batchSize;
            int batchFollowUpLimit = followUpLimit / totalBatches;
            int totalFollowUp = 0;
            int firstFailure = -1;

            for (int batch = 1; batch <= totalBatches; batch++) {
                logger.info("*** Starting Batch {} ***", batch);

                // Test source cases
                logger.info("Testing source test cases...");
                List<TestCase> sourceCases = preprocessor.getBatch(batchSize);
                preExecutor.execute(sourceCases);
                sourceCaseFailures = preExecutor.getChecked() != sourceCases.size();
                logger.info("Source cases testing completed.");

                TestCaseDataBaseDriver.storeTestCases(sourceCases, "source");

                if (!sourceCaseFailures) {

                    MTCore mtCore;

                    if (ART) {
                        mtCore = new ARTCore(threadLimit, enableUndetermined);
                    } else {
                        mtCore = new RandomCore(threadLimit, enableUndetermined);
                    }

                    mtCore.initCandidates(sourceCases);
                    int round = 0;
                    int processedCount = 0;

                    while (mtCore.hasMore() && (processedCount < batchFollowUpLimit || !stopMode.contains("limited"))) {
                        round++;
                        logger.info("*** Batch {} : Round {} ***", batch, round);

                        int currentBatchSize = stopMode.contains("limited")
                                ? Math.min(batchFollowUpLimit - processedCount, miniBatchSize)
                                : miniBatchSize;
                        List<TestCase> followUpCases = mtCore.getNextBatch(currentBatchSize);

                        postExecutor.execute(followUpCases);
                        processedCount += followUpCases.size();

                        Map<TestCase, List<TestAssertion>> batchFailures = postExecutor.getFailedCases();
                        batchFailures.forEach((testCase, assertions) -> failedCases.put(testCase.getId(), assertions));
                        TestCaseDataBaseDriver.storeTestCases(batchFailures.keySet(), "failed");
                        hasFailures = hasFailures || !batchFailures.isEmpty();
                        if (!batchFailures.isEmpty() && firstFailure == -1) {
                            firstFailure = totalFollowUp + postExecutor.getFirstFailure();
                        }
                        totalFollowUp += followUpCases.size();

                        if (hasFailures && stopMode.contains("panic")) {
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

                if (hasFailures && stopMode.contains("panic")) {
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
                    System.currentTimeMillis() - startTime, totalBatches * batchSize, totalFollowUp, failedCases.size());
            
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
        if (!otherArgs.containsKey("id")) {
            logger.error("Single mode requires a case id.");
            return false;
        }

        int caseId = -1;
        boolean passed = false;

        try {
            caseId = Integer.parseInt(otherArgs.get("id").toString());
        } catch (NumberFormatException e) {
            logger.error("Invalid case id: {}", otherArgs.get("id"));
            return false;
        }

        TestCaseDataBaseDriver.setDatabaseUrl(databaseUrl);
        Map<String, Object> arguments = prepareArguments();

        TestWorker worker = TestWorkerFactory.getTestWorker(workerType, arguments);

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
            } else {
                logger.error("Test case {} failed after 3 retries.", tc.getId());
            }
            System.out.println("Test case details:");
            System.out.println(tc);
            worker.destroyWorkspace();
        } catch (SQLException e) {
            logger.error("SQL Exception Occurred: {}, check case id maybe.", e.getMessage());
        } catch (IOException e) {
            logger.error("IO Exception Occurred: {}.", e.getMessage());
        }
        return passed;
    }

    /**
     * Prepares a map of arguments based on the current configuration.
     * 
     * <p>This method initializes a map with key-value pairs required for processing.
     * The arguments include paths and configurations specific to the worker type.
     * If the worker type is "infuse", additional infuse-specific arguments are added.
     * For any other worker type, an exception is thrown.
     * 
     * @return a map containing the prepared arguments.
     * @throws IllegalArgumentException if the worker type is invalid.
     */
    private Map<String, Object> prepareArguments() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("preprocessor.rules-input-directory", rulesPath);
        arguments.put("workspace-root", workspaceRoot);
        if (workerType.equalsIgnoreCase("infuse") || workerType.equalsIgnoreCase("infuse-old")) {
            arguments.put("infuse.workspace-root", workspaceRoot);
            arguments.put("infuse.jar-target-path", targetPath);
            arguments.put("infuse.bfunc-class-path", bfuncPath);
            arguments.put("infuse.arguments", this.arguments);
        } else {
            throw new IllegalArgumentException("Invalid worker type: " + workerType + ". Please check configurations.");
        }
        return arguments;
    }


    /**
     * Dumps the test result as a JSON object to a file. <br>
     * 
     * The JSON output includes:
     * - "passed": A boolean indicating if the test passed (true if neither `failed` nor `sourceFailed` is true).
     * - "source-failed": A boolean indicating if the source code failed.
     * - "failed-cases": An array of objects, each containing:
     *   - "case-id": The ID of the failed test case.
     *   - "failed-assertions": A string representation of the failed assertions.
     *
     * The JSON is written to the file specified by the `resultPath` variable.
     * If an error occurs during file writing, it logs an error message.
     *
     * @param failed        Indicates whether the test has failed.
     * @param sourceFailed  Indicates whether the source code has failed.
     * @param failedCases   A map containing failed test cases, where the key is the case ID
     *                      and the value is a list of failed assertions.
     *
     */
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

        try (FileOutputStream fos = new FileOutputStream(resultPath)){
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            fos.write(gson.toJson(result).getBytes());
        } catch (IOException e) {
            logger.error("Failed to dump result to file: {}", e.getMessage());
        }
    }

    /* Tool functions for loading configurations */

    /**
     * Load default settings.
     */
    public void loadDefaultSettings() {
        mode = "normal";
        stopMode = "panic";
        sourceLimit = 500;
        followUpLimit = 2000;
        batchSize = 500;
        miniBatchSize = 200;
        threadLimit = 8;
        databaseUrl = "data/testcase.db";
        workerType = "infuse";
        workspaceRoot = "workspace";
        targetPath = "cc_utils/INFUSE_ORI/INFUSE-2.1.1-test-modified-withbug2.jar";
        bfuncPath = "cc_utils/INFUSE_ORI/Bfunction.class";
        rulesPath = "rules/4_level_rule/rule_data";
        arguments = List.of("-approach", "ECC+IMD", "-mg");
        otherArgs = new HashMap<>(); // Ensure a fresh map is created
        resultPath = "./result.txt";
        ART = true;
        enableUndetermined = false;
        simpleMode = false;

        logger.info("Default settings loaded successfully.");
    }

    /**
     * Load settings from a YAML file.
     *
     * @param yamlPath Path to the YAML file.
     * @throws IOException If the file does not exist.
     */
    public void loadSettingsFromYaml(String yamlPath) throws IOException {
        loadDefaultSettings(); // 先加载默认配置
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions())); // 使用安全构造器
        try (FileInputStream fis = new FileInputStream(yamlPath)) {
            Object data = yaml.load(fis);
            if (!(data instanceof Map<?, ?> mapData)) {
                throw new IllegalArgumentException("Invalid YAML format: Root must be a mapping.");
            }

            // 解析 YAML 配置
            parseYaml(mapData, "");
        } catch (IOException e) {
            logger.fatal("Failed to load settings from YAML: {}, check if the file exists.", yamlPath);
            throw e;
        }
    }

    /**
     * Parse a YAML map.
     *
     * @param yamlMap The YAML map.
     * @param prefix The prefix of the current key.
     */
    private void parseYaml(Map<?, ?> yamlMap, String prefix) {
        for (Map.Entry<?, ?> entry : yamlMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue; // 忽略无效 key
            }
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = entry.getValue();

            // 检查是否为嵌套结构
            if (value instanceof Map<?, ?> nestedMap) {
                parseYaml(nestedMap, fullKey); // 递归解析
            } else {
                assignSetting(fullKey, value); // 赋值
            }
        }
    }

    /**
     * Assign a setting to the corresponding field.
     *
     * @param key The key of the setting.
     * @param value The value of the setting.
     */
    private void assignSetting(String key, Object value) {
        switch (key) {
            case "mode" -> this.mode = value.toString();
            case "stopMode" -> this.stopMode = value.toString();
            case "threadLimit" -> this.threadLimit = Integer.parseInt(value.toString());
            case "sourceLimit" -> this.sourceLimit = Integer.parseInt(value.toString());
            case "followUpLimit" -> this.followUpLimit = Integer.parseInt(value.toString());
            case "batchSize" -> this.batchSize = Integer.parseInt(value.toString());
            case "miniBatchSize" -> this.miniBatchSize = Integer.parseInt(value.toString());
            case "databaseUrl" -> this.databaseUrl = value.toString();
            case "workerType" -> this.workerType = value.toString();
            case "workspaceRoot" -> this.workspaceRoot = value.toString();
            case "targetPath" -> this.targetPath = value.toString();
            case "bfuncPath" -> this.bfuncPath = value.toString();
            case "rulesPath" -> this.rulesPath = value.toString();
            case "arguments" -> this.arguments = (value instanceof List<?> list) ?
                    list.stream().map(Object::toString).toList() : List.of();
            case "resultPath" -> this.resultPath = value.toString();
            case "ART" -> this.ART = Boolean.parseBoolean(value.toString());
            case "enableUndetermined" -> this.enableUndetermined = Boolean.parseBoolean(value.toString());
            case "simpleMode" -> this.simpleMode = Boolean.parseBoolean(value.toString());
            default -> this.otherArgs.put(key, value); // 未匹配的参数存入 otherArgs
        }
    }


}
