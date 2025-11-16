package fileio;

import dbms.PostgreSQLConnector;
import dbms.QueryTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.sql.SQLException;

public class ConcurrencyTester {

    private static final int THREAD_COUNT = 50;
    private static final int QUERIES_PER_THREAD = 2000;
    private static final int TOTAL_QUERIES = THREAD_COUNT * QUERIES_PER_THREAD;


    public static void main(String[] args) throws InterruptedException {

        System.out.println("--- 开始高并发查询测试 ---");
        System.out.println("执行绪数: " + THREAD_COUNT);
        System.out.println("总查询数: " + TOTAL_QUERIES);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        long startTime = System.nanoTime();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(new QueryTask(QUERIES_PER_THREAD));
        }

        executor.shutdown();

        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                System.err.println("部分任务在 60 秒内未能完成，测试被迫终止。");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("测试被中断。");
        }

        long endTime = System.nanoTime();
        double totalTimeMs = (endTime - startTime) / 1_000_000.0;
        
        System.out.println("\n--- 测试结果 ---");
        System.out.printf("总耗时: %.2f ms\n", totalTimeMs);
        
        double tps = TOTAL_QUERIES / (totalTimeMs / 1000.0);
        System.out.printf("平均每秒处理查询数 (TPS): %.2f\n", tps);
    }
}
