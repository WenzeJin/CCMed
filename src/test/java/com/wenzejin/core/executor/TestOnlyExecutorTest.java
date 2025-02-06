package com.wenzejin.core.executor;

import com.wenzejin.core.exception.XmlParseException;
import com.wenzejin.core.mr.crc.CRCFunc;
import com.wenzejin.core.preprocess.Preprocessor;
import com.wenzejin.core.result.Link;
import com.wenzejin.core.util.RandomSubset;
import com.wenzejin.core.util.TestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestOnlyExecutorTest {

    int TEST_SIZE = 100;

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

        for (TestCase tc : testCaseList) {
            assertTrue(tc.isChecked());
            System.out.println(tc.getRule().formula);
            List<Set<Link>> links = CRCFunc.attribution(tc);
            if (links != null) {
                Set<Link> leftLinks = links.get(0);
                Set<Link> rightLinks = links.get(1);
                System.out.println("Left links: " + leftLinks);
                System.out.println("Right links: " + rightLinks);
            }
        }
    }

}