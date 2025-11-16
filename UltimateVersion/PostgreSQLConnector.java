package dbms;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class PostgreSQLConnector {
    // 關鍵變數：靜態的 HikariDataSource
    private static HikariDataSource dataSource;

    static {
        // 這段代碼在類載入時執行，只會初始化一次連接池
        HikariConfig config = new HikariConfig();

        // 您的 PostgreSQL 連接參數
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/postgres");
        config.setUsername("postgres");
        config.setPassword("xhb77466708"); // 替換為您的實際密碼

        // 連接池配置優化
        config.setMaximumPoolSize(20); // 最大連接數，可根據需求調整（例如：執行緒數量的 1.5 倍）
        config.setMinimumIdle(5);     // 保持的最小空閒連接數
        config.setConnectionTimeout(30000); // 30秒超時

        // 使用 HikariCP 提供的驅動名稱 (如果需要)
        config.setDriverClassName("org.postgresql.Driver");

        dataSource = new HikariDataSource(config);
    }

    // 獲取連接的方法：現在它是從池中快速借用一個連接
    public static Connection getConnection() throws SQLException {
        // 從連接池中獲取連接
        return dataSource.getConnection();
    }
}
// 註：您的 PostgreSQLOperations.java 中獲取連接的代碼保持不變，但現在速度會極快。