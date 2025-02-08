package com.wenzejin.core.executor;

import com.wenzejin.core.mr.assertion.TestAssertion;
import com.wenzejin.core.util.TestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestAndCheckExecutor extends AbstractExecutor{

    public enum FailureMode {
        CONTINUE,
        EXIT,
    }

    private static final Logger logger = LogManager.getLogger("Executor (Test&Check)");

    private long startTime;
    private long endTime;

    private Map<TestCase, List<TestAssertion>> failedCases;

    public TestAndCheckExecutor(String workerType, Map<String, Object> arguments) {
        super(workerType, arguments);
        logger.info("TestAndCheckExecutor initialized. WorkerType: {}, Arguments: {}", workerType, arguments);
        failedCases = new HashMap<>();
    }

    @Override
    protected void beforeTest(List<TestCase> tcs) {
        startTime = System.currentTimeMillis();
    }

    @Override
    protected void afterTest(List<TestCase> tcs) {
        endTime = System.currentTimeMillis();
        int checked = 0;
        for (TestCase tc : tcs) {
            if(tc.isChecked()) {
                checked++;
            }
        }
        logger.info("Execution finished. Test cases: {}(checked) / {} , Time elapsed: {} ms",
                checked, tcs.size(), endTime - startTime);

        int failed = failedCases.size();
        int passed = checked - failed;
        logger.info("Report: {} cases passed, {} cases failed, pass rate: {}%.", passed, failed, (float)passed * 100 / checked);

        for (var fc: failedCases.keySet()) {
            logger.info("Failed Test Case:");
            logger.info(fc.toString());
            logger.info("failed assertions:");
            logger.info(failedCases.get(fc).toString());
        }
    }

    @Override
    protected boolean checkResult(TestCase tc) {
        List<TestAssertion> failedAssertions = new ArrayList<>();
        boolean boolRes = tc.assertTest(failedAssertions);
        if (!boolRes) {
            failedCases.put(tc, failedAssertions);
            logger.info("Test case id {} failed.", tc.getId());
            return false;
        }
        return true;
    }

    @Override
    protected void passTestCase(TestCase tc) {
        // Do nothing
    }

    @Override
    protected void failTestCase(TestCase tc) {
        // Do nothing
    }

    @Override
    protected void procedureCallback(int currentProgress, int totalTasks) {
        int onePercent = totalTasks / 100;
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
