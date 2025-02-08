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

import com.wenzejin.core.executor.AbstractExecutor;
import com.wenzejin.core.executor.TestAndCheckExecutor;
import com.wenzejin.core.executor.TestOnlyExecutor;
import com.wenzejin.core.mr.crc.CRCCore;
import com.wenzejin.core.preprocess.Preprocessor;
import com.wenzejin.core.util.RandomSubset;
import com.wenzejin.core.util.TestCase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TesterCore {

    // Configurations

    private static void developExecute() throws Exception {
        int TEST_SIZE = 100;
        int limit = 200;
        List<TestCase> testCaseList;
        AbstractExecutor executor;
        CRCCore mrCore;

        // prepare test cases
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("preprocessor.rules-input-directory", "rules/4_level_rule/rule_data");
        Preprocessor preprocessor = new Preprocessor(arguments);
        preprocessor.readRules();
        preprocessor.generateTestCases();
        testCaseList = preprocessor.getTestCases();
        testCaseList = RandomSubset.getRandom(testCaseList, TEST_SIZE);

        // prepare executor
        arguments.put("infuse.workspace-root", "workspace");
        arguments.put("infuse.jar-target-path", "cc_utils/INFUSE_ORI/INFUSE-2.1.1-test-modified.jar");
        arguments.put("infuse.bfunc-class-path", "cc_utils/INFUSE_ORI/Bfunction.class");
        arguments.put("infuse.arguments", List.of("-approach", "INFUSE", "-mg"));
        executor = new TestOnlyExecutor("infuse", arguments);

        // init core
        mrCore = new CRCCore(limit);

        // test source
        executor.executeTest(testCaseList);

        List<TestCase> afterTestCases = mrCore.generateTestCases(testCaseList);

        executor = new TestAndCheckExecutor("infuse", arguments);
        executor.executeTest(afterTestCases);

        System.out.println("done");
    }

    public TesterCore() {

    }


    public static void main(String[] args) {
        try {
            developExecute();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
