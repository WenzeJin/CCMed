package com.wenzejin.core.worker;

import java.util.Map;

public class RawExecutionResult {
    public final int exitCode;
    public final String stdout;
    public final String stderr;
    public final long durationMs;
    public final Map<String, String> artifacts;

    public RawExecutionResult(int exitCode, String stdout, String stderr, long durationMs, Map<String, String> artifacts) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.durationMs = durationMs;
        this.artifacts = artifacts;
    }
}
