package dbms;

import java.sql.Array; // 注意：導入 java.sql.Array
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;

/**
 * [Bonus 任務]
 * 查詢邏輯優化：批次陣列查詢任務
 * * 此任務不再一次查詢 1 個 ID，而是一次性生成 BATCH_SIZE 個 ID，
 * 並使用 PostgreSQL 的 "ANY(?)" 語法，在單一網路請求中完成所有查詢。
 */
public class BatchQueryTask implements Runnable {

    // 每個批次要查詢的 ID 數量
    private static final int BATCH_SIZE = 100;

    // 每個執行緒要執行的「批次」數量
    private final int iterations;

    private final int maxId = 500000;
    private final Random random = new Random();

    /**
     * @param iterations 每個執行緒要執行的「批次」數量
     */
    public BatchQueryTask(int iterations) {
        this.iterations = iterations;
    }

    @Override
    public void run() {
        // 在一個執行緒的生命週期中，隨機 ID 陣列可以被重用
        Integer[] idArray = new Integer[BATCH_SIZE];

        for (int i = 0; i < iterations; i++) {

            // 1. 生成 BATCH_SIZE 個隨機 ID
            for (int j = 0; j < BATCH_SIZE; j++) {
                idArray[j] = random.nextInt(maxId) + 1;
            }

            // 2. 從最快的連接池 (C3P0) 獲取連接
            // 確保您的 C3P0Connector.java 位於 dbms 包中
            try (Connection conn = C3P0Connector.getConnection()) {

                // 3. [核心] 創建 PostgreSQL 陣列
                // 這是 JDBC 的標準方式，用於將 Java 陣列轉換為 SQL 陣列
                Array pgArray = conn.createArrayOf("int", idArray);

                // 4. [核心] 使用 "ANY" 語法進行批次查詢
                // 這條 SQL 語句會查詢 recipeid 等於陣列中任何一個元素的所有行
                String sql = "SELECT * FROM recipes WHERE recipeid = ANY(?)";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    // 5. 將陣列設置為參數
                    stmt.setArray(1, pgArray);

                    // 執行查詢
                    stmt.execute();
                }

                // 釋放 JDBC 陣列資源
                pgArray.free();

            } catch (SQLException e) {
                System.err.println("執行緒 " + Thread.currentThread().getName() + " 發生 SQL 錯誤: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("執行緒 " + Thread.currentThread().getName() + " 發生未知錯誤: " + e.getMessage());
            }
        }
    }
}