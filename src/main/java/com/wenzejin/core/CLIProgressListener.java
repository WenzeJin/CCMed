package com.wenzejin.core;

import me.tongfei.progressbar.ProgressBar;

public class CLIProgressListener implements ProgressListener {
    private ProgressBar pb;

    @Override
    public void onStart(int totalSteps, String taskName) {
        if (totalSteps > 0) {
            pb = new ProgressBar(taskName, totalSteps);
        } else {
            pb = new ProgressBar(taskName, -1);
        }
    }

    @Override
    public void onStep(int steps, String message) {
        if (pb != null) {
            if (message != null) {
                pb.setExtraMessage(message);
            }
            pb.stepBy(steps);
        }
    }

    @Override
    public void onFinish(String message) {
        if (pb != null) {
            pb.close();
        }
        if (message != null) {
            System.out.println(message);
        }
    }

    @Override
    public void onError(String errorMessage) {
        if (pb != null) {
            pb.close();
        }
        System.err.println("Error: " + errorMessage);
    }
}
