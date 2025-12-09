package com.wenzejin.core;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)  // 忽略未知字段，避免出错
public class Config {

    // =====================
    // 已知字段
    // =====================
    public String taskName = "DefaultTask";
    public boolean timeStamp = true;

    public int threadLimit = Runtime.getRuntime().availableProcessors();
    public int enumLimit = Integer.MAX_VALUE;

    public String stopMode = "panic";
    public int sourceLimit;
    public int followUpLimit;

    public boolean ART = true;
    public int batchSize;
    public int miniBatchSize;

    public String workspaceRoot;
    public String workerType;
    public String rulesPath;
    public String targetPath;
    public String bfuncPath;
    public List<String> arguments;

    public boolean simpleMode = false;
    public String storeMode = "fail";
    public String bfuncPolicy = "enum";

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

