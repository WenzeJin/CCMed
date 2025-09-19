package com.wenzejin.core.worker;

import java.util.Map;

public class TestWorkerFactory {
    public static TestWorker getTestWorker(String workerType, Map<String, Object> arguments) {
        if (workerType.equalsIgnoreCase("infuse")) {
            return new InfuseWorker(arguments);
        } else if (workerType.equalsIgnoreCase("infuse-old")) {
            return new InfuseOldWorker(arguments);
        } else if (workerType.equalsIgnoreCase("mg")) {
            return new MGWorker(arguments);
        } else {
            return null;
        }

    }
}
