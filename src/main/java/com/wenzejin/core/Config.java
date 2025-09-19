package com.wenzejin.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 专门负责配置的加载、解析和管理。
 */
public class Config {
    private static final Logger logger = LogManager.getLogger("TesterConfig");

    public String mode;
    public String stopMode;
    public int sourceLimit;
    public int followUpLimit;
    public int threadLimit;
    public int batchSize;
    public int miniBatchSize;
    public String databaseUrl;
    public String workerType;
    public String targetPath;
    public String bfuncPath;
    public String rulesPath;
    public String workspaceRoot;
    public List<String> arguments;
    public Map<String, Object> otherArgs;
    public String resultPath;
    public boolean ART;
    public boolean enableUndetermined;
    public boolean simpleMode;
    public String storeMode; // "all" or "fail"

    public Config() {
        loadDefaultSettings();
    }

    public void loadDefaultSettings() {
        mode = "normal";
        stopMode = "panic";
        sourceLimit = 500;
        followUpLimit = 2000;
        batchSize = 500;
        miniBatchSize = 200;
        threadLimit = 8;
        databaseUrl = "data/testcase.db";
        workerType = "infuse";
        workspaceRoot = "workspace";
        targetPath = "cc_utils/INFUSE_ORI/std.jar";
        bfuncPath = "cc_utils/INFUSE_ORI/Bfunction.class";
        rulesPath = "rules/4_level_rule/rule_data";
        arguments = List.of("-approach", "ECC+IMD", "-mg");
        otherArgs = new HashMap<>();
        resultPath = "./result.txt";
        ART = true;
        enableUndetermined = false;
        simpleMode = false;
        storeMode = "fail";
        logger.info("Default settings loaded successfully.");
    }

    public void loadSettingsFromYaml(String yamlPath) throws IOException {
        loadDefaultSettings();
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try (FileInputStream fis = new FileInputStream(yamlPath)) {
            Object data = yaml.load(fis);
            if (!(data instanceof Map<?, ?> mapData)) {
                throw new IllegalArgumentException("Invalid YAML format: Root must be a mapping.");
            }
            parseYaml(mapData, "");
        } catch (IOException e) {
            logger.fatal("Failed to load settings from YAML: {}, check if the file exists.", yamlPath);
            throw e;
        }
    }

    private void parseYaml(Map<?, ?> yamlMap, String prefix) {
        for (Map.Entry<?, ?> entry : yamlMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nestedMap) {
                parseYaml(nestedMap, fullKey);
            } else {
                assignSetting(fullKey, value);
            }
        }
    }

    private void assignSetting(String key, Object value) {
        switch (key) {
            case "mode" -> this.mode = value.toString();
            case "stopMode" -> this.stopMode = value.toString();
            case "threadLimit" -> this.threadLimit = Integer.parseInt(value.toString());
            case "sourceLimit" -> this.sourceLimit = Integer.parseInt(value.toString());
            case "followUpLimit" -> this.followUpLimit = Integer.parseInt(value.toString());
            case "batchSize" -> this.batchSize = Integer.parseInt(value.toString());
            case "miniBatchSize" -> this.miniBatchSize = Integer.parseInt(value.toString());
            case "databaseUrl" -> this.databaseUrl = value.toString();
            case "workerType" -> this.workerType = value.toString();
            case "workspaceRoot" -> this.workspaceRoot = value.toString();
            case "targetPath" -> this.targetPath = value.toString();
            case "bfuncPath" -> this.bfuncPath = value.toString();
            case "rulesPath" -> this.rulesPath = value.toString();
            case "arguments" -> this.arguments = (value instanceof List<?> list) ?
                    list.stream().map(Object::toString).toList() : List.of();
            case "resultPath" -> this.resultPath = value.toString();
            case "ART" -> this.ART = Boolean.parseBoolean(value.toString());
            case "enableUndetermined" -> this.enableUndetermined = Boolean.parseBoolean(value.toString());
            case "simpleMode" -> this.simpleMode = Boolean.parseBoolean(value.toString());
            case "storeMode" -> this.storeMode = value.toString();
            default -> this.otherArgs.put(key, value);
        }
    }
}
