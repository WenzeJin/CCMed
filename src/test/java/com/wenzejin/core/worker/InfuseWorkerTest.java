package com.wenzejin.core.worker;

import com.wenzejin.core.exception.XmlParseException;
import com.wenzejin.core.preprocess.Preprocessor;
import com.wenzejin.core.result.Link;
import com.wenzejin.core.util.RandomSubset;
import com.wenzejin.core.util.TestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InfuseWorkerTest {

    InfuseWorker worker;

    @BeforeEach
    void initializeWorker() {
        worker = new InfuseWorker("workspace",
                "cc_utils/INFUSE_ORI/INFUSE-2.1.1-test-modified.jar",
                "cc_utils/INFUSE_ORI/Bfunction.class", List.of("-approach", "INFUSE", "-mg"));
    }

    @Test
    void testInitWorkspace() {
        try {
            worker.initWorkspace();
            worker.destroyWorkspace();
        } catch (IOException e) {
            fail("Init Workspace Failed.");
        }
    }

    @Test
    void testSingleRun() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("preprocessor.rules-input-directory", "rules/4_level_rule/rule_data");

        Preprocessor preprocessor = new Preprocessor(arguments);
        try {
            preprocessor.readRules();
        } catch (XmlParseException e) {
            e.printStackTrace();
        }
        preprocessor.generateTestCases();


        try {
            worker.initWorkspace();

        } catch (IOException e) {
            fail("Init Workspace Failed.");
        }

        List<TestCase> tcs = preprocessor.getTestCases();
        tcs = RandomSubset.getRandom(tcs, 1000);

        worker.singleRun(tcs.getFirst());

        try {
            worker.destroyWorkspace();
        } catch (IOException e) {
            fail("Destroy Workspace Failed.");
        }

    }

    @Test
    void testSingleRunParallel() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("preprocessor.rules-input-directory", "rules/4_level_rule/rule_data");

        Preprocessor preprocessor = new Preprocessor(arguments);
        try {
            preprocessor.readRules();
        } catch (XmlParseException e) {
            e.printStackTrace();
        }
        preprocessor.generateTestCases();

        List<TestCase> tcs = preprocessor.getTestCases();
        tcs = RandomSubset.getRandom(tcs, 100);

        int totalTasks = tcs.size();
        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads); // 创建线程池

        AtomicInteger progress = new AtomicInteger(0); // 用于跟踪进度
        List<Integer> failedIds = Collections.synchronizedList(new ArrayList<>()); // 线程安全的失败列表

        // 初始化线程本地 Worker，每个线程管理一个 Worker
        ThreadLocal<InfuseWorker> threadLocalWorker = ThreadLocal.withInitial(() -> {
            InfuseWorker worker = new InfuseWorker("workspace",
                    "cc_utils/INFUSE_ORI/INFUSE-2.1.1-test-modified.jar",
                    "cc_utils/INFUSE_ORI/Bfunction.class", List.of("-approach", "INFUSE", "-mg"));
            try {
                worker.initWorkspace(); // 每个线程的 Worker 只初始化一次工作区
            } catch (IOException e) {
                fail("Init Workspace Failed.");
            }
            return worker;
        });


        List<Callable<Object>> tasks = new ArrayList<>();
        for (TestCase tc : tcs) {
            tasks.add(() -> {
                InfuseWorker worker = threadLocalWorker.get(); // 获取线程本地 Worker

                try {
                    worker.singleRun(tc); // 执行测试用例
                } catch (Exception e) {
                    synchronized (failedIds) {
                        failedIds.add(tc.getId());
                    }
                }

                int currentProgress = progress.incrementAndGet();
                if (currentProgress % 50 == 0 || currentProgress == totalTasks) {
                    System.out.println("Test progress: " + currentProgress + "/" + totalTasks);
                }
                return null;
            });
        }

        try {
            executor.invokeAll(tasks); // 执行所有任务
        } catch (InterruptedException e) {
            fail("Execution interrupted.");
        }

        // 销毁线程本地 Worker 的工作区
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                InfuseWorker worker = threadLocalWorker.get();
                try {
                    worker.destroyWorkspace(); // 每个线程的 Worker 只销毁一次工作区
                } catch (IOException e) {
                    fail("Destroy Workspace Failed.");
                }
                return null;
            });
        }


        executor.shutdown();

        // 检查未完成的用例
        for (TestCase tc : tcs) {
            if (!tc.isChecked() && !failedIds.contains(tc.getId())) {
                failedIds.add(tc.getId());
            }
        }

        if (!failedIds.isEmpty()) {
            System.out.println("Failed Test Cases: " + failedIds);
            fail("Some test cases failed.");
        }

        System.out.println("Empty link cases:");
        for (TestCase tc : tcs) {
            assertTrue(tc.isChecked());

            if (tc.getResult().links.isEmpty()) {
                System.out.println(tc);
            }
        }

        System.out.println("All satisfied cases:");
        for (TestCase tc : tcs) {

            if (!tc.getResult().links.isEmpty() && tc.getResult().truth) {
                System.out.println(tc);
            }
        }

        System.out.println("Both satisfied and violated cases:");
        for (TestCase tc : tcs) {
            if (!tc.getResult().links.isEmpty() && !tc.getResult().truth) {
                boolean hasSatisfied = false;
                for (var link : tc.getResult().links) {
                    if (link.type == Link.Type.SATISFIED) {
                        hasSatisfied = true;
                        break;
                    }
                }
                if (hasSatisfied) {
                    System.out.println(tc);
                }
            }
        }


    }

}