package com.wenzejin.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class WorkspaceManager {
    private static final Logger logger = LogManager.getLogger("WorkspaceManager");
    private final Config config;
    private String workspacePath;

    public WorkspaceManager(Config config) {
        this.config = config;
    }

    public String setupWorkspace() {
        String taskName = config.getTaskName();
        if (config.isTimeStamp()) {
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            taskName = taskName + "_" + timestamp;
        }
        workspacePath = Paths.get(config.getWorkspaceRoot(), taskName).toString();
        try {
            Files.createDirectories(Paths.get(config.getWorkspaceRoot(), taskName));
            logger.info("Workspace created at: {}", workspacePath);
        }  catch (IOException e) {
            logger.error("Failed to create workspace directory: {}", e.getMessage());
            throw new RuntimeException("Failed to create workspace", e);
        }
        return workspacePath;
    }

    public void exportConfig() {
        if (workspacePath == null) {
            logger.warn("Workspace path not set. Cannot export config.");
            return;
        }
        try {
            String path = Paths.get(workspacePath, "config.json").toString();
            config.saveToJson(path);
            logger.info("Config exported to {}", path);
        } catch (IOException e) {
            logger.error("Failed to export config: {}", e.getMessage());
        }
    }

    public String getWorkspacePath() {
        return workspacePath;
    }
}
