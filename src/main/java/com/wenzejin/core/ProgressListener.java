package com.wenzejin.core;

public interface ProgressListener {
    void onStart(int totalSteps, String taskName);
    void onStep(int steps, String message);
    void onFinish(String message);
    void onError(String errorMessage);
}
