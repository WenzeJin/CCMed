package com.wenzejin.core;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {

    // =====================
    // Known fields
    // =====================

    // Task info
    @JsonProperty private String taskName = "DefaultTask";
    @JsonProperty private boolean timeStamp = true;
    @JsonProperty private String workspaceRoot;

    // Threading
    @JsonProperty private int threadLimit = Runtime.getRuntime().availableProcessors();

    // Stopping criteria
    @JsonProperty private String stopMode = "panic";
    @JsonProperty private int sourceLimit;
    @JsonProperty private int followUpLimit;

    // Batching
    @JsonProperty private int batchSize;
    @JsonProperty private int miniBatchSize;

    // Target and supplementary class
    @JsonProperty private String workerType;
    @JsonProperty private String targetPath;
    @JsonProperty private String bfuncPath;

    // Target arguments
    @JsonProperty private List<String> arguments;

    // Source cases generation
    @JsonProperty private String rulesPath;
    @JsonProperty private String bfuncPolicy = "enum";
    @JsonProperty private int enumLimit = Integer.MAX_VALUE;

    // MT core selection: "ART" (default) or "chain"
    @JsonProperty private String mtCore = "ART";
    // Chain length for ChainCompositionCore (2-6), only used when mtCore = "chain"
    @JsonProperty private int chainLength = 5;

    // Other options
    @JsonProperty private boolean simpleMode = false;
    @JsonProperty private String storeMode = "fail";
    @JsonProperty private boolean detailData = false;

    // =====================
    // Unknown fields (user-defined)
    // =====================
    @JsonProperty private Map<String, JsonNode> otherArgs = new HashMap<>();

    // =====================
    // Getters
    // =====================

    public String getTaskName() { return taskName; }
    public boolean isTimeStamp() { return timeStamp; }
    public String getWorkspaceRoot() { return workspaceRoot; }
    public int getThreadLimit() { return threadLimit; }
    public String getStopMode() { return stopMode; }
    public int getSourceLimit() { return sourceLimit; }
    public int getFollowUpLimit() { return followUpLimit; }
    public int getBatchSize() { return batchSize; }
    public int getMiniBatchSize() { return miniBatchSize; }
    public String getWorkerType() { return workerType; }
    public String getTargetPath() { return targetPath; }
    public String getBfuncPath() { return bfuncPath; }
    public List<String> getArguments() { return arguments; }
    public String getRulesPath() { return rulesPath; }
    public String getBfuncPolicy() { return bfuncPolicy; }
    public int getEnumLimit() { return enumLimit; }
    public String getMtCore() { return mtCore; }
    public int getChainLength() { return chainLength; }
    public boolean isSimpleMode() { return simpleMode; }
    public String getStoreMode() { return storeMode; }
    public boolean isDetailData() { return detailData; }
    public Map<String, JsonNode> getOtherArgs() { return otherArgs; }

    // =====================
    // Setters (for programmatic overrides)
    // =====================

    public void setDetailData(boolean detailData) { this.detailData = detailData; }

    // =====================
    // Serialization / Deserialization
    // =====================

    /**
     * Load configuration from JSON file.
     */
    public static Config loadFromJson(String jsonPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new File(jsonPath), Config.class);
    }

    public void saveToJson(String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(path), this);
    }

    /**
     * Automatically collects unknown JSON fields into otherArgs.
     */
    @JsonAnySetter
    public void handleUnknown(String key, JsonNode value) {
        otherArgs.put(key, value);
    }

    public boolean validate(boolean verbose) {
        // thread limit
        if (threadLimit < 1) {
            if (verbose) {
                System.out.println("FATAL: Thread limit is too small!");
            }
            return false;
        }

        if (threadLimit > Runtime.getRuntime().availableProcessors()) {
            if (verbose) {
                System.out.println("WARN: Thread limit is too large! Available processors: " + Runtime.getRuntime().availableProcessors());
            }
        }

        // enum limit
        if (enumLimit < 1) {
            if (verbose) {
                System.out.println("FATAL: Enum limit is too small!");
            }
            return false;
        }

        // rulesPath check
        if (rulesPath == null || !new File(rulesPath).exists()) {
            if (verbose) {
                System.out.println("FATAL: rulesPath is null or the file does not exist!");
            }
            return false;
        }

        // targetPath check
        if (targetPath == null || !new File(targetPath).exists()) {
            if (verbose) {
                System.out.println("FATAL: targetPath is null or the file does not exist!");
            }
            return false;
        }

        // bfuncPath check — empty string is allowed when SUT does not use Bfunction support
        if (bfuncPath != null && !bfuncPath.isEmpty() && !new File(bfuncPath).exists()) {
            if (verbose) {
                System.out.println("FATAL: bfuncPath file does not exist!");
            }
            return false;
        }

        // storeMode check
        Set<String> validStoreModes = new HashSet<>(Arrays.asList("all", "fail", "none"));
        if (!validStoreModes.contains(storeMode)) {
            if (verbose) {
                System.out.println("FATAL: storeMode must be one of " + validStoreModes);
            }
            return false;
        }

        // bfuncPolicy check
        Set<String> validBfuncPolicies = new HashSet<>(Arrays.asList("enum", "random"));
        if (!validBfuncPolicies.contains(bfuncPolicy)) {
            if (verbose) {
                System.out.println("FATAL: bfuncPolicy must be one of " + validBfuncPolicies);
            }
            return false;
        }

        return true;
    }
}
