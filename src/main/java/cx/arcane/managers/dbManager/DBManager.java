package cx.arcane.managers.dbManager;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import cx.arcane.utils.Log;

import java.sql.Connection;
import java.sql.SQLException;

public class DBManager {

    private static HikariDataSource dataSource;

    public static void onEnable() {
        String host = "";
        int port = 1025;
        String database= "";
        String username = "";
        String password = "";

        Log.info("[DBManager] Initializing HikariCP...");
        Log.info("[DBManager] Target: {}:{}/{}", host, port, database);

        long tTotal = System.currentTimeMillis();

        try {
            long tConfig = System.currentTimeMillis();

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false"
                    + "&rewriteBatchedStatements=true"
                    + "&useServerPrepStmts=false"
                    + "&characterEncoding=utf8"
                    + "&useUnicode=true");
            config.setUsername(username);
            config.setPassword(password);
            config.addDataSourceProperty("cachePrepStmts",        "true");
            config.addDataSourceProperty("prepStmtCacheSize",     "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            Log.info("[DBManager] HikariConfig built in {}ms", System.currentTimeMillis() - tConfig);

            long tPool = System.currentTimeMillis();
            dataSource = new HikariDataSource(config);
            Log.info("[DBManager] HikariDataSource initialized in {}ms", System.currentTimeMillis() - tPool);

            long tConn = System.currentTimeMillis();
            try (Connection con = dataSource.getConnection()) {
                Log.info("[DBManager] Test connection acquired in {}ms — DB is reachable.", System.currentTimeMillis() - tConn);
                Log.info("[DBManager] JDBC URL: {}", con.getMetaData().getURL());
                Log.info("[DBManager] Driver:   {}", con.getMetaData().getDriverVersion());
            }

            Log.info("[DBManager] onEnable() completed in {}ms", System.currentTimeMillis() - tTotal);

        } catch (Exception e) {
            Log.error("[DBManager] Failed to initialize after {}ms", System.currentTimeMillis() - tTotal);
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null)
            throw new IllegalStateException("Database hasn't been initialized.");

        return dataSource.getConnection();
    }

    public static void onDisable() {
        Log.info("[DBManager] Shutting down connection pool...");
        long t = System.currentTimeMillis();

        if (dataSource != null && !dataSource.isClosed()) {
            Log.info("[DBManager] Active connections: {}, Idle: {}, Total: {}",
                    dataSource.getHikariPoolMXBean().getActiveConnections(),
                    dataSource.getHikariPoolMXBean().getIdleConnections(),
                    dataSource.getHikariPoolMXBean().getTotalConnections());

            dataSource.close();
            Log.info("[DBManager] Pool closed in {}ms", System.currentTimeMillis() - t);
        } else {
            Log.info("[DBManager] Pool was already closed or null.");
        }
    }

    public static void onSave() {

    }
}