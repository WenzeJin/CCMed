package com.wenzejin.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wenzejin.core.mr.assertion.TestAssertion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class ResultReporter {
    private static final Logger logger = LogManager.getLogger("ResultReporter");
    private final String workspace;

    public ResultReporter(String workspace) {
        this.workspace = workspace;
    }

    public void dumpJsonResult(boolean failed, boolean sourceFailed, Map<Integer, List<TestAssertion>> failedCases, int firstFailure) {
        JsonObject result = new JsonObject();
        result.addProperty("passed", !failed && !sourceFailed);
        result.addProperty("source-failed", sourceFailed);
        result.addProperty("first-failure", firstFailure);
        JsonArray failedArray = new JsonArray();
        for (Map.Entry<Integer, List<TestAssertion>> entry: failedCases.entrySet()) {
            JsonObject failedCase = new JsonObject();
            failedCase.addProperty("case_id", entry.getKey());
            failedCase.addProperty("failed_assertions", entry.getValue().toString());
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
