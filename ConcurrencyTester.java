package fileio;

import dbms.QueryTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConcurrencyTester {

    public static void main(String[] args) throws InterruptedException {

        final int THREAD_COUNT = 50;           // 併發執行緒數量
        final int QUERIES_PER_THREAD = 2000;   // 每個執行緒執行 2000 次查詢
        final int TOTAL_QUERIES = THREAD_COUNT * QUERIES_PER_THREAD; // 總共 100,000 次查詢

        System.out.println("--- 開始高併發查詢測試 ---");
        System.out.println("執行緒數: " + THREAD_COUNT);
        System.out.println("總查詢數: " + TOTAL_QUERIES);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        long startTime = System.nanoTime();

        // 啟動所有任務
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(new QueryTask(QUERIES_PER_THREAD));
        }

        // 關閉執行器，等待所有執行緒完成
        executor.shutdown();
        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
            System.err.println("部分任務在 60 秒內未能完成。");
        }

        long endTime = System.nanoTime();
        double totalTimeMs = (endTime - startTime) / 1_000_000.0;

        System.out.println("--- 測試結果 ---");
        System.out.printf("總耗時: %.2f ms\n", totalTimeMs);
        System.out.printf("平均每秒處理查詢數 (TPS): %.2f\n", TOTAL_QUERIES / (totalTimeMs / 1000.0));
    }
}