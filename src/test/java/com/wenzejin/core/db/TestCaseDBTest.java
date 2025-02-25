package com.wenzejin.core.db;

import com.wenzejin.core.preprocess.Preprocessor;
import com.wenzejin.core.util.TestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestCaseDBTest {

    List<TestCase> tcs;

    @BeforeEach
    void setUp() throws Exception {
        TestCaseDB.clearDB();
        TestCaseDB.initializeDB();
    }

    @BeforeEach
    void prepareTestCases() throws Exception {
        // prepare test cases
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("preprocessor.rules-input-directory", "rules/4_level_rule/rule_data");
        Preprocessor preprocessor = new Preprocessor(arguments);
        preprocessor.readRules();
        preprocessor.generateTestCases();
        tcs = preprocessor.getTestCases();
    }

    @Test
    void testStoreTestCase() throws Exception {
        List<TestCase> sample = tcs.subList(0, 1000);
        TestCaseDB.storeTestCases(sample);
        List<TestCase> res = new ArrayList<>();
        for (TestCase tc: sample) {
            TestCase tc1 = TestCaseDB.getTestCaseById(tc.getId());
            res.add(tc1);
        }
        for (int i = 0; i < 1000; i++) {
            if (!sample.get(i).equals(res.get(i))) {
                System.out.println("Sample: " + sample.get(i));
                System.out.println("Result: " + res.get(i));
            }
        }
    }

}