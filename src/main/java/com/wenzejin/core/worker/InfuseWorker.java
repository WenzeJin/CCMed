package com.wenzejin.core.worker;

import com.wenzejin.core.rule.Bfunc;
import com.wenzejin.core.rule.Pattern;
import com.wenzejin.core.util.TestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InfuseWorker implements TestWorker{

    // Configurable Arguments
    private final String workspaceRoot;
    private final String jarTargetPath;
    private final String bfuncClassPath;

    // UUID
    private final UUID uuid;

    private static final Logger logger = LogManager.getLogger("InfuseWorker");

    private Path workspace;
    private Path copiedJar;
    private Path copiedBfuncClass;


    public InfuseWorker(String workspaceRoot, String jarTargetPath, String bfuncClassPath) {
        this.workspaceRoot = workspaceRoot;
        this.jarTargetPath = jarTargetPath;
        this.bfuncClassPath = bfuncClassPath;
        uuid = UUID.randomUUID();
    }


    /**
     * Initialize temporary workspace for InfuseWorker
     * the workspace path will be "[workspaceRoot]/infuse_worker_[UUID]"
     * @throws IOException if initialize failed.
     */
    @Override
    public void initWorkspace() throws IOException {
        String workspacePath = getWorkspacePath();
        File workspaceDir = new File(workspacePath);
        if (!workspaceDir.exists()) {
            if (!workspaceDir.mkdirs()) {
                logger.fatal("Failed to create workspace directory: {}", workspacePath);
                throw new IOException("Failed to create workspace directory: " + workspacePath);
            }
        }
        workspace = workspaceDir.toPath();

        // 拷贝 jar 文件到工作目录
        File sourceJar = new File(jarTargetPath);
        if (!sourceJar.exists()) {
            logger.fatal("Jar target file not found at: {}", jarTargetPath);
            throw new FileNotFoundException("Jar target file not found at: " + jarTargetPath);
        }
        File destJar = new File(workspacePath, sourceJar.getName());
        Files.copy(sourceJar.toPath(), destJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        copiedJar = destJar.toPath();

        // 拷贝 bfunc class 文件到工作目录
        File sourceBfunc = new File(bfuncClassPath);
        if (!sourceBfunc.exists()) {
            logger.fatal("Bfunc class file not found at: {}", bfuncClassPath);
            throw new FileNotFoundException("Bfunc class file not found at: " + bfuncClassPath);
        }
        File destBfunc = new File(workspacePath, sourceBfunc.getName());
        Files.copy(sourceBfunc.toPath(), destBfunc.toPath(), StandardCopyOption.REPLACE_EXISTING);
        copiedBfuncClass = destBfunc.toPath();

        logger.info("Workspace created at: {}", workspace);
    }

    /**
     * Destroy temporary workspace.
     * @throws IOException if destroy failed.
     */
    @Override
    public void destroyWorkspace() throws IOException {
        if (workspace == null) {
            logger.warn("Workspace not initialized yet but trying to destroy it.");
            return;
        }

        if (Files.exists(workspace)) {
            // 使用递归删除工作目录及其内容
            Files.walk(workspace)
                    .sorted(Comparator.reverseOrder()) // 确保目录在文件后删除
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            logger.fatal("Failed to delete: {} due to: {}", path, e.getMessage());
                        }
                    });

            logger.info("Workspace destroyed at: {}", workspace);
        }

        workspace = null;
    }

    /**
     * @param tc
     */
    @Override
    public void singleRun(TestCase tc) {

    }

    /**
     * @param testCaseList
     */
    @Override
    public void batchRun(List<TestCase> testCaseList) {

    }

    private String getWorkspacePath() {
        return Paths.get(workspaceRoot, "infuse_worker_" + uuid.toString()).toString();
    }

    private void generatePatternAndContextInput(List<Pattern> patterns) {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
    }

    private void generateBfuncInput(List<Bfunc> bfuncs) {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
    }

    private void analyzeSingleResult(TestCase tc) {

    }

    private void analyzeBatchResult(List<TestCase> testCaseList) {

    }
}
