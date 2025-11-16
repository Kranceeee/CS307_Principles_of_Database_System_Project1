package fileio;

import dbms.BatchQueryTask; // [!! 導入新的批次任務 !!]

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * [Bonus 任務]
 * 批次查詢併發測試啟動器
 * * 此測試器用於評估 "探索路徑三：查詢邏輯優化" 的效果。
 * 它使用與 ConcurrencyTester 相同的併發級別，但執行的是 BatchQueryTask。
 */
public class BatchConcurrencyTester {

    // --- 測試參數配置 ---
    private static final int THREAD_COUNT = 50;           // 併發執行緒數 (保持不變)
    private static final int TOTAL_QUERIES = 100000;      // 總查詢目標 (保持不變)
    private static final int BATCH_SIZE = 100;            // 每個批次查詢 100 個 ID

    // --- 計算得出 ---
    // 每個執行緒總共需要查詢多少個 ID？
    private static final int QUERIES_PER_THREAD = TOTAL_QUERIES / THREAD_COUNT; // (2000 個 ID)

    // 每個執行緒需要執行多少個「批次」？
    private static final int BATCHES_PER_THREAD = QUERIES_PER_THREAD / BATCH_SIZE; // (20 個批次)

    // --- 網路請求次數對比 ---
    // 舊方法 (ConcurrencyTester): 100,000 次網路請求
    // 新方法 (本測試): TOTAL_QUERIES / BATCH_SIZE = 1000 次網路請求 (減少 99%)
    // ----------------------------------------------------------------------

    public static void main(String[] args) throws InterruptedException {

        System.out.println("--- 開始 [Bonus] 批次查詢併發測試 (探索路徑三) ---");
        System.out.println("併發執行緒數: " + THREAD_COUNT);
        System.out.println("每個批次大小: " + BATCH_SIZE);
        System.out.println("每個執行緒執行批次數: " + BATCHES_PER_THREAD);
        System.out.println("總查詢 ID 數: " + TOTAL_QUERIES);
        System.out.println("預計總網路請求數: " + (THREAD_COUNT * BATCHES_PER_THREAD));

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        long startTime = System.nanoTime();

        // 提交任務：每個執行緒執行 BATCHES_PER_THREAD (20) 次批次任務
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(new BatchQueryTask(BATCHES_PER_THREAD));
        }

        executor.shutdown();

        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                System.err.println("部分任務在 60 秒內未能完成，測試被迫終止。");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long endTime = System.nanoTime();
        double totalTimeMs = (endTime - startTime) / 1_000_000.0;

        System.out.println("\n--- [Bonus] 測試結果 ---");
        System.out.printf("總耗時: %.2f ms\n", totalTimeMs);

        // [!! 關鍵 !!] TPS 仍然基於總查詢 ID 數 (TOTAL_QUERIES) 來計算
        // 這樣才能與之前的 TPS (45,076) 進行公平比較
        double tps = TOTAL_QUERIES / (totalTimeMs / 1000.0);
        System.out.printf("平均每秒處理查詢數 (TPS): %.2f\n", tps);
    }
}