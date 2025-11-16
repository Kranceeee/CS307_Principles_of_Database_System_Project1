package fileio;

import dbms.BatchQueryTask;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class BatchConcurrencyTester {

    private static final int THREAD_COUNT = 50;
    private static final int TOTAL_QUERIES = 100000;
    private static final int BATCH_SIZE = 100;

    private static final int QUERIES_PER_THREAD = TOTAL_QUERIES / THREAD_COUNT;

    private static final int BATCHES_PER_THREAD = QUERIES_PER_THREAD / BATCH_SIZE;

    public static void main(String[] args) throws InterruptedException {

        System.out.println("--- 开始批次查询并发测试 ---");
        System.out.println("并发执行绪数: " + THREAD_COUNT);
        System.out.println("每个批次大小: " + BATCH_SIZE);
        System.out.println("每个执行绪执行批次数: " + BATCHES_PER_THREAD);
        System.out.println("总查询 ID 数: " + TOTAL_QUERIES);
        System.out.println("预计总网络请求数: " + (THREAD_COUNT * BATCHES_PER_THREAD));

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        long startTime = System.nanoTime();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(new BatchQueryTask(BATCHES_PER_THREAD));
        }

        executor.shutdown();

        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                System.err.println("部分任务在 60 秒内未能完成，测试被迫终止。");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long endTime = System.nanoTime();
        double totalTimeMs = (endTime - startTime) / 1_000_000.0;

        System.out.println("\n--- 测试结果 ---");
        System.out.printf("总耗时: %.2f ms\n", totalTimeMs);

        double tps = TOTAL_QUERIES / (totalTimeMs / 1000.0);
        System.out.printf("平均每秒处理查询数 (TPS): %.2f\n", tps);
    }
}
