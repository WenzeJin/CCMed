package com.wenzejin.core.mr.crc;

import com.wenzejin.core.executor.TestOnlyExecutor;
import com.wenzejin.core.preprocess.Preprocessor;
import com.wenzejin.core.result.Link;
import com.wenzejin.core.util.RandomSubset;
import com.wenzejin.core.util.TestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CRCCoreTest {
    int TEST_SIZE = 100;

    List<TestCase> testCaseList;

    TestOnlyExecutor executor;

    CRCCore mrCore;

    @BeforeEach
    void prepareTestCases() throws Exception {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("preprocessor.rules-input-directory", "rules/4_level_rule/rule_data");
        Preprocessor preprocessor = new Preprocessor(arguments);
        preprocessor.readRules();
        preprocessor.generateTestCases();
        testCaseList = preprocessor.getTestCases();
        testCaseList = RandomSubset.getRandom(testCaseList, TEST_SIZE);
    }

    @BeforeEach
    void prepareExecutor() throws Exception {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("infuse.workspace-root", "workspace");
        arguments.put("infuse.jar-target-path", "cc_utils/INFUSE_ORI/INFUSE-2.1.1-test-modified.jar");
        arguments.put("infuse.bfunc-class-path", "cc_utils/INFUSE_ORI/Bfunction.class");
        arguments.put("infuse.arguments", List.of("-approach", "INFUSE", "-mg"));
        executor = new TestOnlyExecutor("infuse", arguments);
    }

    @BeforeEach
    void initCore() {
        mrCore = new CRCCore();
        mrCore.init();
    }

    @Test
    void testInitPools() {
        executor.executeTest(testCaseList);

        mrCore.initPools(testCaseList);
    }

}