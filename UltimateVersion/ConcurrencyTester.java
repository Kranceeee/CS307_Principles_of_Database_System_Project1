package fileio;

import dbms.PostgreSQLConnector; // 確保 PostgreSQLConnector 位於 dbms 包中
import dbms.QueryTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.sql.SQLException;

/**
 * ConcurrencyTester.java
 * 高併發基準測試啟動器。
 * 使用 ExecutorService 和執行緒池模擬多用戶高併發查詢。
 */
public class ConcurrencyTester {

    // --- 測試參數配置 ---
    private static final int THREAD_COUNT = 50;           // 併發執行緒數量 (模擬用戶數)
    private static final int QUERIES_PER_THREAD = 2000;   // 每個執行緒執行 2000 次查詢
    private static final int TOTAL_QUERIES = THREAD_COUNT * QUERIES_PER_THREAD;

    // ** [進階任務 2 提示] 比較連接池時，請修改 QueryTask 中呼叫的 Connector 類別 **
    // ----------------------------------------------------------------------

    public static void main(String[] args) throws InterruptedException {

        System.out.println("--- 開始高併發查詢測試 ---");
        System.out.println("執行緒數: " + THREAD_COUNT);
        System.out.println("總查詢數: " + TOTAL_QUERIES);

        // 1. 創建固定大小的執行緒池
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        long startTime = System.nanoTime();

        // 2. 提交任務到執行緒池
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(new QueryTask(QUERIES_PER_THREAD));
        }

        // 3. 關閉執行器並等待所有任務完成
        executor.shutdown();

        // 最多等待 60 秒，確保測試不會無限期運行
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                System.err.println("部分任務在 60 秒內未能完成，測試被迫終止。");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("測試被中斷。");
        }

        long endTime = System.nanoTime();
        double totalTimeMs = (endTime - startTime) / 1_000_000.0;

        // 4. 輸出結果
        System.out.println("\n--- 測試結果 ---");
        System.out.printf("總耗時: %.2f ms\n", totalTimeMs);

        // 計算 TPS (Transactions Per Second)
        double tps = TOTAL_QUERIES / (totalTimeMs / 1000.0);
        System.out.printf("平均每秒處理查詢數 (TPS): %.2f\n", tps);
    }
}