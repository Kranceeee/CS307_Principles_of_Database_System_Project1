package dbms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;

public class QueryTask implements Runnable {
    private final int iterations; 
    
    private final int maxId = 500000;
    private final Random random = new Random();

    public QueryTask(int iterations) {
        this.iterations = iterations;
    }

    @Override
    public void run() {
        for (int i = 0; i < iterations; i++) {
            int randomId = random.nextInt(maxId) + 1;

            try (Connection conn = PostgreSQLConnector.getConnection()) {
                
                String sql = "SELECT * FROM recipes WHERE recipeid = ?";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, randomId);

                    stmt.execute();
                }

            } catch (SQLException e) {
                System.err.println("执行绪" + Thread.currentThread().getName() + " 发生 SQL 错误: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("执行绪 " + Thread.currentThread().getName() + " 发生未知错误: " + e.getMessage());
            }
        }
    }
}
