/**
 * The Core of this CC Tester.
 * All tests will be performed by 5 steps:
 * 1. Preprocess of source test case inputs.    -> source test cases
 *      This step include read constraints from xml, generate random data input, enumerate bfuncs
 * 2. Testing with source test cases.           -> source test cases with result
 *      This step will be performed by SourceExecutor.
 * 3. Generate follow-up test cases with MRs.   -> follow-up test cases with assertions
 *      This step will be performed by implements of different MRs.
 *      All of them will implement interface `MR`. Which takes a list of source test cases (with result)
 *      and generates a list of follow-up test cases.
 * 4. Testing with follow-up test cases.        -> follow-up test cases with result
 *      This step will also be performed by FollowExecutor.
 *      Analyse results of follow-up test cases, with `MR` class.
 *      If assertion failed, save records in TestCase. If interrupt-when-failed enabled, stop testing.
 * 5. Analyse result and generate report.       -> report.
 * Author: Wenze Jin
 */

package com.wenzejin.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wenzejin.core.db.TestCaseDB;
import com.wenzejin.core.exception.XmlParseException;
import com.wenzejin.core.executor.TestAndCheckExecutor;
import com.wenzejin.core.executor.TestOnlyExecutor;
import com.wenzejin.core.mr.assertion.TestAssertion;
import com.wenzejin.core.mr.crc.ARTCore;
import com.wenzejin.core.mr.crc.CRCCore;
import com.wenzejin.core.preprocess.Preprocessor;
import com.wenzejin.core.util.TestCase;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TesterCore {

    // Configurations
    private static final Logger logger = LogManager.getLogger("TesterCore");

    String      mode;
    int         sourceLimit;
    int         followUpLimit;
    int         threadLimit;
    boolean     enableBatch;
    boolean     enableBoost;
    int         batchSize;
    String      databaseUrl;
    String      workerType;
    String      targetPath;
    String      bfuncPath;
    String      rulesPath;
    String      workspaceRoot;
    List<String> arguments;
    Map<String, Object> otherArgs;
    String      resultPath;
    double      process;

    public TesterCore() {
        this.otherArgs = new HashMap<>();
    }

    public boolean execute() {
        return switch (mode) {
            case "normal" -> executeNormal();
            case "single" -> executeSingle();
            case null, default -> false;
        };
    }

    public double getProcess() {
        return process;
    }

    private boolean executeNormal() {
        try {
            // Initialize database
            TestCaseDB.setDatabaseUrl(databaseUrl);
            TestCaseDB.clearDB();
            TestCaseDB.initializeDB();

            // Declare executors
            TestOnlyExecutor preExecutor;
            TestAndCheckExecutor postExecutor;
            ARTCore artmr = new ARTCore();

            // Initialize timer
            long startTime = System.currentTimeMillis();
            long checkPoint = System.currentTimeMillis();

            // Prepare test cases
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("preprocessor.rules-input-directory", rulesPath);
            Preprocessor preprocessor = new Preprocessor(arguments);
            preprocessor.readRules();
            preprocessor.generateTestCases();
            preprocessor.reduceSize(sourceLimit);
            logger.info("Source cases preparation done in {} ms.", System.currentTimeMillis() - checkPoint);
            checkPoint = System.currentTimeMillis();

            // Initialize statistics
            int count = 0;
            int checked = 0;
            int failed = 0;
            boolean source_failed = false;
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
            preExecutor = new TestOnlyExecutor(workerType, arguments, threadLimit);
            postExecutor = new TestAndCheckExecutor(workerType, arguments, threadLimit);
            preExecutor.init();
            postExecutor.init();

            List<TestCase> tcs = preprocessor.getTestCases();
            preExecutor.execute(tcs);

            logger.info("Source cases testing done in {} ms.", System.currentTimeMillis() - checkPoint);
            checkPoint = System.currentTimeMillis();
            artmr.initCandidates(tcs);


            // TODO: change this, this is just for testing
            for (int i = 0; i < 2; i++) {
                logger.info("*** Round {} start. ***", i);
                List<TestCase> batch = artmr.getNextBatch(batchSize);
                postExecutor.execute(batch);
            }

            preExecutor.close();
            postExecutor.close();



            // TODO

            JsonObject result = new JsonObject();
            result.addProperty("passed", failedCases.isEmpty() && !source_failed);
            result.addProperty("source-failed", source_failed);
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
            }

            logger.info("Test ended successfully!");

            return failedCases.isEmpty();
        } catch (SQLException e) {
            logger.error("SQL Exception Occurred: {}", e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            logger.error("IO Exception Occurred: {}", e.getMessage());
        } catch (XmlParseException e) {
            logger.error("XML Parse Exception Occurred: {}", e.getMessage());
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
                passed = tc.assertTest();
                if (passed) {
                    logger.info("Test case {} passed.", tc.getId());
                } else {
                    logger.warn("Test case {} failed.", tc.getId());
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

    public void loadDefaultSettings() {
        this.mode = "normal";
        this.sourceLimit = 500;
        this.followUpLimit = 2000;
        this.enableBatch = true;
        this.enableBoost = true;
        this.batchSize = 250;
        this.threadLimit = 8;
        this.databaseUrl = "data/testcase.db";
        this.workerType = "infuse";
        this.workspaceRoot = "workspace";
        this.targetPath = "cc_utils/INFUSE_ORI/INFUSE-2.1.1-test-modified-withbug2.jar";
        this.bfuncPath = "cc_utils/INFUSE_ORI/Bfunction.class";
        this.rulesPath = "rules/4_level_rule/rule_data";
        this.arguments = List.of("-approach", "ECC+IMD", "-mg");
        this.otherArgs.clear();
        this.resultPath = "./result.txt";
    }

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

    private void assignSetting(String key, Object value) {
        switch (key) {
            case "mode" -> this.mode = value.toString();
            case "sourceLimit" -> this.sourceLimit = Integer.parseInt(value.toString());
            case "followUpLimit" -> this.followUpLimit = Integer.parseInt(value.toString());
            case "enableBatch" -> this.enableBatch = Boolean.parseBoolean(value.toString());
            case "batchSize" -> this.batchSize = Integer.parseInt(value.toString());
            case "databaseUrl" -> this.databaseUrl = value.toString();
            case "workerType" -> this.workerType = value.toString();
            case "workspaceRoot" -> this.workspaceRoot = value.toString();
            case "targetPath" -> this.targetPath = value.toString();
            case "bfuncPath" -> this.bfuncPath = value.toString();
            case "rulesPath" -> this.rulesPath = value.toString();
            case "arguments" -> this.arguments = (value instanceof List<?> list) ?
                    list.stream().map(Object::toString).toList() : List.of();
            case "resultPath" -> this.resultPath = value.toString();
            case "enableBoost" -> this.enableBoost = Boolean.parseBoolean(value.toString());
            default -> this.otherArgs.put(key, value); // 未匹配的参数存入 otherArgs
        }
    }


}
