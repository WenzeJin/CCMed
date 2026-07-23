package com.wenzejin.core;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)  // 忽略未知字段，避免出错
public class Config {

    // =====================
    // 已知字段
    // =====================

    // Task info
    public String taskName = "DefaultTask";
    public boolean timeStamp = true;
    public String workspaceRoot;

    // Threading
    public int threadLimit = Runtime.getRuntime().availableProcessors();

    // Stopping criteria
    public String stopMode = "panic";
    public int sourceLimit;
    public int followUpLimit;

    // Batching
    public int batchSize;
    public int miniBatchSize;

    // Target and supplementary class
    public String workerType;
    public String targetPath;
    public String bfuncPath;

    // Target arguments
    public List<String> arguments;

    // Source cases generation
    public String rulesPath;
    public String bfuncPolicy = "enum";
    public int enumLimit = Integer.MAX_VALUE;
    
    // MT core selection: "ART" (default) or "chain"
    public String mtCore = "ART";
    // Chain length for ChainCompositionCore (2-6), only used when mtCore = "chain"
    public int chainLength = 5;

    // Other options
    public boolean simpleMode = false;
    public String storeMode = "fail";
    public boolean detailData = false;

    // =====================
    // 未知字段（用户自定义）
    // =====================
    public Map<String, JsonNode> otherArgs = new HashMap<>();

    /**
     * 自动解析 JSON 配置
     */
    public static Config loadFromJson(String jsonPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new File(jsonPath), Config.class);
    }

    public void saveToJson(String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().withDefaultPrettyPrinter();
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(path), this);
    }

    /**
     * 将未知字段（未映射字段）自动放到 otherArgs 中
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

        // bfuncPath check
        if (bfuncPath == null || !new File(bfuncPath).exists()) {
            if (verbose) {
                System.out.println("FATAL: bfuncPath is null or the file does not exist!");
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

