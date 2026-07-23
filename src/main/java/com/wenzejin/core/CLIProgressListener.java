package com.wenzejin.core;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;

public class CLIProgressListener implements ProgressListener {
    private ProgressBar pb;

    private static int detectTerminalWidth() {
        // 1. COLUMNS 环境变量（最可靠）
        String columns = System.getenv("COLUMNS");
        if (columns != null) {
            try { return Integer.parseInt(columns); } catch (NumberFormatException ignored) {}
        }
        // 2. 通过 stty 获取（Unix）
        try {
            Process p = new ProcessBuilder("stty", "size").redirectInput(ProcessBuilder.Redirect.INHERIT).start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            // stty size 输出格式: "rows columns"
            String[] parts = output.split(" ");
            if (parts.length >= 2) {
                return Integer.parseInt(parts[1]);
            }
        } catch (Exception ignored) {}
        // 3. 通过 tput 获取
        try {
            Process p = new ProcessBuilder("tput", "cols").redirectInput(ProcessBuilder.Redirect.INHERIT).start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            if (p.waitFor() == 0 && !output.isEmpty()) {
                return Integer.parseInt(output);
            }
        } catch (Exception ignored) {}
        // 4. 默认宽度
        return 120;
    }

    @Override
    public synchronized void onStart(int totalSteps, String taskName) {
        int width = detectTerminalWidth();
        if (totalSteps > 0) {
            pb = new ProgressBarBuilder()
                    .setTaskName(taskName)
                    .setInitialMax(totalSteps)
                    .setMaxRenderedLength(width)
                    .build();
        } else {
            pb = new ProgressBarBuilder()
                    .setTaskName(taskName)
                    .setInitialMax(-1)
                    .setMaxRenderedLength(width)
                    .build();
        }
    }

    @Override
    public synchronized void onStep(int steps, String message) {
        if (pb != null) {
            if (message != null) {
                pb.setExtraMessage(message);
            }
            pb.stepBy(steps);
        }
    }

    @Override
    public synchronized void onFinish(String message) {
        if (pb != null) {
            pb.close();
        }
        if (message != null) {
            System.out.println(message);
        }
    }

    @Override
    public synchronized void onError(String errorMessage) {
        if (pb != null) {
            pb.close();
        }
        System.err.println("Error: " + errorMessage);
    }
}
