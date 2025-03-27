package com.wenzejin.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wenzejin.core.mr.assertion.TestAssertion;
import com.wenzejin.core.mr.crc.ARTCore;
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

    public TesterCore() {
        this.otherArgs = new HashMap<>();
    }

    public boolean execute() {
        switch (mode) {
            case "normal":
                return executeNormal();
            case "single":
                return executeSingle();
            default:
                logger.error("Invalid mode: {}", mode);
                return false;
        }
    }

    private boolean executeNormal() {
        try {
            // Initialize database
            TestCaseDB.setDatabaseUrl(databaseUrl);
            TestCaseDB.clearDB();
            TestCaseDB.initializeDB();

            // Declare executors
            TestExecutor preExecutor;
            TestExecutor postExecutor;
            ARTCore artmr = new ARTCore(threadLimit);

            // Initialize timer
            long checkPoint = System.currentTimeMillis();

            // Prepare test cases
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("preprocessor.rules-input-directory", rulesPath);
            Preprocessor preprocessor = new Preprocessor(arguments);
            preprocessor.generateTestCases();
            preprocessor.reduceSize(sourceLimit);
            logger.info("Source cases preparation done in {} ms.", System.currentTimeMillis() - checkPoint);
            checkPoint = System.currentTimeMillis();

            // Initialize statistics
            boolean failed = false;
            boolean sourceFailed = false;
            Map<Integer, List<TestAssertion>> failedCases = new HashMap<>();

            // prepare executors
            arguments.put("workspace-root", workspaceRoot);
            if (workerType.equalsIgnoreCase("infuse")) {
                arguments.put("infuse.workspace-root", workspaceRoot);
                arguments.put("infuse.jar-target-path", targetPath);
                arguments.put("infuse.bfunc-class-path", bfuncPath);
                arguments.put("infuse.arguments", this.arguments);
            } else {
                throw new IllegalArgumentException("Invalid worker type " + workerType + " . Please check configurations.");
            }
            preExecutor = new TestExecutor(TestExecutor.Mode.SOURCE, workerType, arguments, threadLimit);
            postExecutor = new TestExecutor(TestExecutor.Mode.FOLLOW, workerType, arguments, threadLimit);
            preExecutor.init();
            postExecutor.init();

            int totalBatch = preprocessor.getSize() / batchSize;
            int eachBatchLimit = followUpLimit / totalBatch;


            for (int batch = 1; batch <= totalBatch; batch++) {
                // Testing source cases
                logger.info("*** Batch {} start ***", batch);
                logger.info("Testing source test cases...");
                List<TestCase> tcs = preprocessor.getBatch(batchSize);
                preExecutor.execute(tcs);
                sourceFailed = preExecutor.getChecked() != tcs.size();
                logger.info("Source cases testing done.");

                if (!sourceFailed) {
                    artmr.initCandidates(tcs);
                    int round = 0;
                    int count = 0;
                    while (artmr.hasMore() && count < eachBatchLimit) {
                        ++round;
                        logger.info("*** Batch {} : Round {} start. ***", batch, round);
                        int thisBatchSize = stopMode.contains("limited") ? Math.min(eachBatchLimit - count, miniBatchSize) : miniBatchSize;
                        List<TestCase> batchTC = artmr.getNextBatch(thisBatchSize);

                        postExecutor.execute(batchTC);
                        count += batchTC.size();

                        var failedCasesInBatch = postExecutor.getFailedCases();
                        failedCasesInBatch.forEach((k, v) -> {
                            failedCases.put(k.getId(), v);
                        });
                        TestCaseDB.storeTestCases(failedCasesInBatch.keySet(), "failed");
                        failed = failed || !failedCasesInBatch.isEmpty();

                        if (failed && stopMode.contains("panic")) {
                            logger.info("Batch {} interrupted due to failed cases. Panic mode.", batch);
                            break;
                        }
                    }
                } else {
                    logger.info("Test interrupted due to failed source cases. No more follow-up test will be performed.");
                    break;
                }

                if (failed && stopMode.contains("panic")) {
                    logger.info("Test interrupted due to failed cases. Panic mode.");
                    break;
                }
            }

            preExecutor.close();
            postExecutor.close();

            // dump result
            dumpJsonResult(failed, sourceFailed, failedCases);

            logger.info("Test ended successfully! Failed cases have been stored in database.");

            return failedCases.isEmpty();
        } catch (SQLException e) {
            logger.error("SQL Exception Occurred: {}", e.getMessage());
            e.printStackTrace();
        // } catch (IOException e) {
        //     logger.error("IO Exception Occurred: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("Illegal Argument Occurred: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Exception: {}", e.getMessage());
            e.printStackTrace();
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

        TestCaseDB.setDatabaseUrl(databaseUrl);
        Map<String, Object> arguments = new HashMap<>();
        // prepare executors

        if (workerType.equalsIgnoreCase("infuse")) {
            arguments.put("infuse.workspace-root", workspaceRoot);
            arguments.put("infuse.jar-target-path", targetPath);
            arguments.put("infuse.bfunc-class-path", bfuncPath);
            arguments.put("infuse.arguments", this.arguments);
        } else {
            throw new IllegalArgumentException("Invalid worker type " + workerType + " . Please check configurations.");
        }
        TestWorker worker = TestWorkerFactory.getTestWorker(workerType, arguments);

        try {
            TestCase tc = TestCaseDB.getTestCaseById(caseId);
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

    private void dumpJsonResult(boolean failed, boolean sourceFailed, Map<Integer, List<TestAssertion>> failedCases) {
        JsonObject result = new JsonObject();
        result.addProperty("passed", !failed && !sourceFailed);
        result.addProperty("source-failed", sourceFailed);
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
            default -> this.otherArgs.put(key, value); // 未匹配的参数存入 otherArgs
        }
    }


}
