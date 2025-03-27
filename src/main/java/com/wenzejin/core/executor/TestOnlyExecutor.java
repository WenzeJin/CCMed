package com.wenzejin.core.executor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wenzejin.core.TestCase;

import java.util.List;
import java.util.Map;

public class TestOnlyExecutor extends AbstractExecutor {

    private static final Logger logger = LogManager.getLogger("Executor (TestOnly)");

    private long startTime;
    private long endTime;

    public TestOnlyExecutor(String workerType, Map<String, Object> arguments) {
        super(workerType, arguments);
    }

    public TestOnlyExecutor(String workerType, Map<String, Object> arguments, int threadNum) {
        super(workerType, arguments, threadNum);
    }

    @Override
    protected void beforeTest(List<TestCase> tcs) {
        super.beforeTest(tcs);
        startTime = System.currentTimeMillis();
    }

    @Override
    protected void afterTest(List<TestCase> tcs) {
        super.afterTest(tcs);
        endTime = System.currentTimeMillis();
        logger.info("Execution finished. Test cases: {}(checked) / {} , Time elapsed: {} ms",
                checked, tcs.size(), endTime - startTime);
    }

    @Override
    protected boolean checkResult(TestCase tc) {
        // Do nothing
        return true;
    }

    @Override
    protected void procedureCallback(int currentProgress, int totalTasks) {
        int onePercent = totalTasks / 20;
        if (onePercent == 0) {
            onePercent = 1;
        }
        long currentTime = System.currentTimeMillis();
        if (currentProgress % onePercent == 0) {
            long elapsedTime = currentTime - startTime;
            long eta = elapsedTime * (totalTasks - currentProgress) / currentProgress;

            // 格式化 ETA
            String formattedETA = formatETA(eta);

            logger.info("Progress: {} / {}, {}%, ETA: {}", currentProgress, totalTasks,
                    currentProgress * 100 / totalTasks,
                    formattedETA);
        }
    }

    @Override
    protected void uncheckTestCase(TestCase tc) {
        // Do nothing
    }

    /**
     * 格式化时间为 xx h xx m xx s xx ms 格式
     * @param eta 时间，单位为毫秒
     * @return 格式化后的字符串
     */
    private String formatETA(long eta) {
        long hours = eta / (3600 * 1000);
        eta %= 3600 * 1000;

        long minutes = eta / (60 * 1000);
        eta %= 60 * 1000;

        long seconds = eta / 1000;
        long milliseconds = eta % 1000;

        StringBuilder formattedETA = new StringBuilder();
        if (hours > 0) {
            formattedETA.append(hours).append(" h ");
        }
        if (minutes > 0) {
            formattedETA.append(minutes).append(" m ");
        }
        if (seconds > 0) {
            formattedETA.append(seconds).append(" s ");
        }
        if (milliseconds > 0 || formattedETA.isEmpty()) {
            formattedETA.append(milliseconds).append(" ms");
        }

        return formattedETA.toString().trim();
    }
}
