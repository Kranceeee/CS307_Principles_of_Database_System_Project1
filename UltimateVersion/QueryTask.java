package dbms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;

/**
 * 併發測試的單一執行緒任務。
 * [已修正] SQL 語句中的欄位名已從 recipe_id 改為 recipeid。
 */
public class QueryTask implements Runnable {
    private final int iterations; // 每個執行緒要執行的查詢次數

    // 假設您的 Recipes 數據 ID 範圍是 1 到 500000 (請根據您的數據調整)
    private final int maxId = 500000;
    private final Random random = new Random();

    public QueryTask(int iterations) {
        this.iterations = iterations;
    }

    @Override
    public void run() {
        for (int i = 0; i < iterations; i++) {
            // 隨機生成一個 ID 進行查詢
            int randomId = random.nextInt(maxId) + 1;

            // 關鍵：使用 try-with-resources 從連接池借用並自動歸還連接
            try (Connection conn = PostgreSQLConnector.getConnection()) {

                // [!! 關鍵修正 !!]
                // 確保使用您數據庫中全小寫的表名 (recipes) 和欄位名 (recipeid)
                String sql = "SELECT * FROM recipes WHERE recipeid = ?";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, randomId);

                    // 執行查詢。對於基準測試，我們不需要處理 ResultSet。
                    stmt.execute();
                }

            } catch (SQLException e) {
                // 在高併發下，這裡可能會捕獲到連接超時或死鎖
                System.err.println("執行緒 " + Thread.currentThread().getName() + " 發生 SQL 錯誤: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("執行緒 " + Thread.currentThread().getName() + " 發生未知錯誤: " + e.getMessage());
            }
        }
    }
}