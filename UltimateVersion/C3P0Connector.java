package dbms;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import java.beans.PropertyVetoException;
import java.sql.Connection;
import java.sql.SQLException;

public class C3P0Connector {

    private static ComboPooledDataSource dataSource;

    static {
        try {
            dataSource = new ComboPooledDataSource();

            dataSource.setDriverClass("org.postgresql.Driver");
            dataSource.setJdbcUrl("jdbc:postgresql://localhost:5432/postgres");
            dataSource.setUser("postgres");
            dataSource.setPassword("xhb77466708");
            
            dataSource.setMinPoolSize(5);
            dataSource.setAcquireIncrement(5);
            dataSource.setMaxPoolSize(20);

        } catch (PropertyVetoException e) {
            throw new RuntimeException("C3P0 Driver Class Not Found", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

}
