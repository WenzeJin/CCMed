package com.wenzejin.core.util;

import com.google.gson.GsonBuilder;
import com.wenzejin.core.executor.AbstractExecutor;
import com.wenzejin.core.executor.TestOnlyExecutor;
import com.wenzejin.core.mr.assertion.TestAssertion;
import com.wenzejin.core.mr.assertion.TestAssertionTypeAdapter;
import com.wenzejin.core.mr.crc.CRCCore;
import com.wenzejin.core.preprocess.Preprocessor;
import com.wenzejin.core.rule.Formula;
import com.wenzejin.core.rule.FormulaTypeAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestCaseJsonTest {

    List<TestCase> tcs;

    Gson gson = new GsonBuilder()
            .registerTypeAdapter(Formula.class, new FormulaTypeAdapter())
            .registerTypeAdapter(TestAssertion.class, new TestAssertionTypeAdapter())
            .create();

    @BeforeEach
    void generateTestCases() throws Exception{
        // prepare test cases
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("preprocessor.rules-input-directory", "rules/4_level_rule/rule_data");
        Preprocessor preprocessor = new Preprocessor(arguments);
        preprocessor.readRules();
        preprocessor.generateTestCases();
        tcs = preprocessor.getTestCases();
    }

    @Test
    void smallTest() throws Exception {
        System.out.println();
        List<TestCase> sample = RandomSubset.getRandom(tcs, 50);

        for (TestCase tc: sample) {
            String json = GzipCompress.compressString(gson.toJson(tc));
            System.out.println(json);
            // Save to a test json file. pretty format
            try (FileOutputStream fos = new FileOutputStream("test.json")) {
                fos.write(json.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
            TestCase tc1 = gson.fromJson(GzipCompress.decompressString(json), TestCase.class);
            assertEquals(tc1, tc);
        }

        AbstractExecutor executor;
        CRCCore mrCore;

        Map<String, Object> arguments = new HashMap<>();
        // prepare executor
        arguments.put("infuse.workspace-root", "workspace");
        arguments.put("infuse.jar-target-path", "cc_utils/INFUSE_ORI/INFUSE-2.1.1-test-modified.jar");
        arguments.put("infuse.bfunc-class-path", "cc_utils/INFUSE_ORI/Bfunction.class");
        arguments.put("infuse.arguments", List.of("-approach", "INFUSE", "-mg"));
        executor = new TestOnlyExecutor("infuse", arguments);

        int limit = 100;
        // init core
        mrCore = new CRCCore(limit);

        // test source
        executor.executeTest(sample);

        sample = mrCore.generateTestCases(sample);
        for (TestCase tc: sample) {
            String json = GzipCompress.compressString(gson.toJson(tc));
            System.out.println(json);
            // Save to a test json file. pretty format
            try (FileOutputStream fos = new FileOutputStream("test.json")) {
                fos.write(json.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
            TestCase tc1 = gson.fromJson(GzipCompress.decompressString(json), TestCase.class);
            assertEquals(tc1, tc);
        }
    }

    @Test
    void bigTest() throws Exception {
        try (FileOutputStream fos = new FileOutputStream("test.json")) {
            for (TestCase tc: tcs) {
                String json = GzipCompress.compressString(gson.toJson(tc));
                // Save to a test json file.
                fos.write(json.getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}