package com.wenzejin.core.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Shared file utilities for test workers.
 */
public final class WorkerFileUtils {

    private static final Logger logger = LogManager.getLogger("WorkerFileUtils");

    private WorkerFileUtils() {}

    /**
     * Copies a file or directory into the given workspace directory.
     *
     * @param sourcePath     path to the source file or directory
     * @param workspace      destination workspace directory
     * @param fileDescription human-readable description for error messages
     * @return the destination path inside the workspace
     * @throws IOException if the source is not found or copy fails
     */
    public static Path copyFileToWorkspace(String sourcePath, Path workspace, String fileDescription) throws IOException {
        Path sourceFile = Paths.get(sourcePath);
        if (!Files.exists(sourceFile)) {
            logger.fatal("{} not found at: {}", fileDescription, sourcePath);
            throw new FileNotFoundException(fileDescription + " not found at: " + sourcePath);
        }

        Path destinationFile = workspace.resolve(sourceFile.getFileName());
        if (Files.isDirectory(sourceFile)) {
            try (var paths = Files.walk(sourceFile)) {
                paths.forEach(path -> {
                    Path destPath = destinationFile.resolve(sourceFile.relativize(path));
                    try {
                        if (Files.isDirectory(path)) {
                            Files.createDirectories(destPath);
                        } else {
                            Files.copy(path, destPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        logger.fatal("Failed to copy {} to workspace: {}", fileDescription, destPath, e);
                        throw new UncheckedIOException(e);
                    }
                });
            }
        } else {
            try {
                Files.copy(sourceFile, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                logger.fatal("Failed to copy {} to workspace: {}", fileDescription, destinationFile, e);
                throw new IOException("Failed to copy " + fileDescription + " to workspace: " + destinationFile, e);
            }
        }

        return destinationFile;
    }
}
