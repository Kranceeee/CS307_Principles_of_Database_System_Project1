package dbms;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * 负责建立和获取 PostgreSQL 数据库连接。
 */
public class PostgreSQLConnector {
    // 确保 URL, 用户名, 和 密码与 DataGrip 设置一致
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String USER = "postgres";
    private static final String PASS = "xhb77466708"; // <<<< 请替换为您的密码

    /**
     * 获取数据库连接。
     * @return Connection 对象
     * @throws RuntimeException 如果连接失败
     */
    public static Connection getConnection() {
        try {
            // Class.forName("org.postgresql.Driver"); // Maven环境通常不需要显式调用
            return DriverManager.getConnection(DB_URL, USER, PASS);
        } catch (SQLException e) {
            // 抛出运行时异常，让 PerformanceTester 捕获并停止测试
            throw new RuntimeException("数据库连接失败，请检查URL, 用户名, 和 密码。", e);
        }
    }
}
