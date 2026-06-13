package com.wenzejin.core;

import com.wenzejin.core.result.TestOutcome;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DetailDataRecorder implements Closeable {
    private static final Logger logger = LogManager.getLogger("DetailDataRecorder");

    private final BufferedWriter writer;

    public DetailDataRecorder(String outputPath) throws IOException {
        Path path = Paths.get(outputPath);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
        logger.info("detail-data enabled. output={}", path.toAbsolutePath());
    }

    public void recordFollowUpOutcomes(List<TestOutcome> followUpOutcomes, Map<Integer, String> sourceRootTypeMap) {
        for (TestOutcome outcome : followUpOutcomes) {
            String followRootType = getFollowRootType(outcome);
            String sourceRootType = getSourceRootType(outcome, sourceRootTypeMap);
            try {
                writer.write(outcome.durationMs + "\t" + sourceRootType + "\t" + followRootType);
                writer.newLine();
            } catch (IOException e) {
                logger.error("Failed to write detail-data for follow-up case {}", outcome.testCaseId, e);
            }
        }

        try {
            writer.flush();
        } catch (IOException e) {
            logger.error("Failed to flush detail-data file.", e);
        }
    }

    private static String getFollowRootType(TestOutcome outcome) {
        if (outcome.testCase != null
                && outcome.testCase.getRule() != null
                && outcome.testCase.getRule().formula != null) {
            return outcome.testCase.getRule().formula.getNodeType();
        }
        return "unknown";
    }

    private static String getSourceRootType(TestOutcome outcome, Map<Integer, String> sourceRootTypeMap) {
        if (outcome.testCase == null || outcome.testCase.getSource() == null || outcome.testCase.getSource().isEmpty()) {
            return "unknown";
        }

        LinkedHashSet<String> sourceRoots = outcome.testCase.getSource().stream()
                .map(sourceRootTypeMap::get)
                .filter(root -> root != null && !root.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (sourceRoots.isEmpty()) {
            return "unknown";
        }

        return String.join("|", sourceRoots);
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}