package com.wenzejin.core.executor;

import com.wenzejin.core.exception.XmlParseException;
import com.wenzejin.core.preprocess.Preprocessor;
import com.wenzejin.core.util.RandomSubset;
import com.wenzejin.core.util.TestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestOnlyExecutorTest {

    int TEST_SIZE = 1000;

    List<TestCase> testCaseList;

    TestOnlyExecutor executor;

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

    @Test
    void testExecuteTest() {
        executor.executeTest(testCaseList);
    }

}