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

import com.wenzejin.core.db.TestCaseDB;
import com.wenzejin.core.exception.XmlParseException;
import com.wenzejin.core.executor.TestAndCheckExecutor;
import com.wenzejin.core.executor.TestOnlyExecutor;
import com.wenzejin.core.mr.assertion.TestAssertion;
import com.wenzejin.core.mr.crc.CRCCore;
import com.wenzejin.core.preprocess.Preprocessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TesterCore {

    // Configurations
    private static final Logger logger = LogManager.getLogger("TesterCore");

    int         sourceLimit;
    int         followUpLimit;
    boolean     enableBatch;
    int         batchSize;
    String      workerType;
    String      targetPath;
    String      bfuncPath;
    String      workspaceRoot;
    List<String> arguments;
    Map<String, Object> otherArgs;

    private void developExecuteWithDB() {
        try {
            TestCaseDB.clearDB();
            TestCaseDB.initializeDB();
            int TEST_SIZE = 1000;
            int limit = 3000;
            int batch_size = 100;
            TestOnlyExecutor preExecutor;
            TestAndCheckExecutor postExecutor;
            CRCCore mrCore;

            // prepare test cases
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("preprocessor.rules-input-directory", "rules/4_level_rule/rule_data");
            Preprocessor preprocessor = new Preprocessor(arguments);
            preprocessor.readRules();
            preprocessor.generateTestCases();
            preprocessor.reduceSize(TEST_SIZE);
            preprocessor.saveTestCasesDB("source");

            // TODO: split batch

            List<String> batches = TestCaseDB.splitAndUpdateBatchNames("source", batch_size);
            int count = 0;
            int checked = 0;
            int failed = 0;
            Map<Integer, List<TestAssertion>> failedCases = new HashMap<>();

            // prepare executor
            arguments.put("infuse.workspace-root", "workspace");
            arguments.put("infuse.jar-target-path", "cc_utils/INFUSE_ORI/INFUSE-2.1.1-test-modified.jar");
            arguments.put("infuse.bfunc-class-path", "cc_utils/INFUSE_ORI/Bfunction.class");
            arguments.put("infuse.arguments", List.of("-approach", "INFUSE", "-mg"));
            preExecutor = new TestOnlyExecutor("infuse", arguments);
            postExecutor = new TestAndCheckExecutor("infuse", arguments);

            for (String batchName: batches) {
                count++;
                logger.info("*** EXECUTING BATCH {} / {}: ***", count, batches.size());

                // test source
                preExecutor.executeTestDB(TestCaseDB.getBatchIds(batchName));

                mrCore = new CRCCore(limit / batches.size());
                mrCore.generateTestCases(TestCaseDB.getBatchIds(batchName), batchName + "-f");

                // test follow-up
                postExecutor.executeTestDB(TestCaseDB.getBatchIds(batchName + "-f"));

                checked += postExecutor.getChecked();
                failedCases.putAll(postExecutor.getFailedCases());

                TestCaseDB.updateBatchNameFromOld(batchName + "-f", "follow_up");

            }
            logger.info("Follow up limit: {}, {} checked, {} passed. Test passed? {}", limit, checked, checked - failedCases.size(), failedCases.isEmpty());

            try (FileOutputStream fos = new FileOutputStream("./result.txt")){
                fos.write(("Passed: " + failedCases.isEmpty() + "\n").getBytes());
                fos.write(failedCases.toString().getBytes());
            }

            System.out.println("Done!");
        } catch (SQLException e) {
            logger.error("SQL Exception: {}", e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            logger.error("IO Exception: {}", e.getMessage());
        } catch (XmlParseException e) {
            logger.error("XML Parse Exception: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Exception: {}", e.getMessage());
        }

    }

    public TesterCore() {
        this.otherArgs = new HashMap<>();
    }

    public void loadDefaultSettings() {
        this.sourceLimit = 1000;
        this.followUpLimit = 3000;
        this.enableBatch = true;
        this.batchSize = 100;
        this.workerType = "infuse";
        this.workspaceRoot = "workspace";
        this.targetPath = "cc_utils/INFUSE_ORI/INFUSE-2.1.1-test-modified.jar";
        this.bfuncPath = "cc_utils/INFUSE_ORI/Bfunction.class";
        this.arguments = List.of("-approach", "INFUSE", "-mg");
        this.otherArgs.clear();
    }

    public void loadSettingsFromYaml(String yamlPath) throws IOException {

    }


    public static void main(String[] args) {
        TesterCore testerCore = new TesterCore();
        try {
            testerCore.developExecuteWithDB();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
