package com.wenzejin.core.worker;

import java.util.Map;

public class TestWorkerFactory {
    public static TestWorker getTestWorker(String workerType, String workspace, Map<String, Object> arguments) {
        if (workerType.equalsIgnoreCase("infuse")) {
            return new InfuseWorker(workspace, arguments);
        } else if (workerType.equalsIgnoreCase("infuse-old")) {
            return new InfuseOldWorker(workspace, arguments);
        } else if (workerType.equalsIgnoreCase("mg")) {
            return new MGWorker(workspace, arguments);
        } else {
            return null;
        }
    }
}
