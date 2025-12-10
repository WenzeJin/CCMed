package com.wenzejin.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wenzejin.core.mr.assertion.TestAssertion;
import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.result.TestOutcome;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ResultReporter {
    private static final Logger logger = LogManager.getLogger("ResultReporter");
    private final String workspace;

    public ResultReporter(String workspace) {
        this.workspace = workspace;
    }

    public void dumpJsonResult(boolean failed, boolean sourceFailed, List<TestOutcome> failedOutcomes, int firstFailure) {
        JsonObject result = new JsonObject();
        result.addProperty("passed", !failed && !sourceFailed);
        result.addProperty("source-failed", sourceFailed);
        result.addProperty("first-failure", firstFailure);
        JsonArray failedArray = new JsonArray();
        for (TestOutcome outcome : failedOutcomes) {
            JsonObject failedCase = new JsonObject();
            TestCase tc = outcome.testCase;
            failedCase.addProperty("case_id", tc.getId());
            failedCase.addProperty("status", outcome.status.toString());
            
            JsonArray sourceArray = new JsonArray();
            for (Integer srcId : tc.getSource()) {
                sourceArray.add(srcId);
            }
            failedCase.add("source", sourceArray);

            JsonArray assertionArray = new JsonArray();
            for (TestAssertion assertion : outcome.failedAssertions) {
                assertionArray.add(assertion.toString());
            }
            failedCase.add("failed_assertions", assertionArray);

            if (outcome.errorMessage != null) {
                failedCase.addProperty("error_message", outcome.errorMessage);
            }
            
            failedArray.add(failedCase);
        }
        result.add("failed_cases", failedArray);

        Path resultPath = Paths.get(workspace, "result.json");

        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.write(resultPath, gson.toJson(result).getBytes());
            logger.info("Result dumped to {}", resultPath);
        } catch (IOException e) {
            logger.error("Failed to dump result to file: {}", e.getMessage());
        }
    }
}
