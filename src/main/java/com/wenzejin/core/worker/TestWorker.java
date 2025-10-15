package com.wenzejin.core.worker;

import java.io.IOException;
import java.nio.file.Path;

import com.wenzejin.core.cases.TestCase;

/**
 * The TestWorker interface defines the contract for a worker that can execute test cases
 * within a managed workspace. It provides methods to initialize and destroy the workspace,
 * as well as to execute a single test case.
 */
public interface TestWorker {

    /**
     * Initializes the workspace required for the test worker.
     * This method sets up any necessary files, directories, or configurations
     * needed for the proper functioning of the worker.
     *
     * @throws IOException if an I/O error occurs during the initialization process.
     */
    void initWorkspace() throws IOException;

    /**
     * Cleans up and removes the workspace used by the worker.
     * This method is responsible for ensuring that any temporary files
     * or directories created during the worker's execution are properly
     * deleted to free up resources.
     *
     * @throws IOException if an I/O error occurs during the cleanup process.
     */
    void destroyWorkspace() throws IOException;

    /**
     * Executes a single run of the given test case.
     *
     * @param tc the test case to be executed
     * @return an integer representing the result of the test case execution
     */
    int execute(TestCase tc, boolean source);


    void logTestCase(Path logDir);
}
