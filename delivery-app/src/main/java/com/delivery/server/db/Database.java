package com.delivery.server.db;

import com.delivery.common.Log;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Quan ly ket noi DB. Level 1 mo/dong connection theo tung truy van cho don gian.
 * Level 3 nen thay bang connection pool (HikariCP) - chi phai sua DUY NHAT file nay.
 */
public final class Database {

    private static String url  = System.getenv().getOrDefault("DB_URL",
            "jdbc:postgresql://localhost:5432/delivery_app");
    private static String user = System.getenv().getOrDefault("DB_USER", "postgres");
    private static String pass = System.getenv().getOrDefault("DB_PASS", "postgres");

    private Database() {}

    public static void init() {
        try (Connection c = getConnection()) {
            Log.info("Ket noi database OK: " + c.getMetaData().getURL());
        } catch (SQLException e) {
            Log.error("Khong ket noi duoc database. Kiem tra DB_URL/DB_USER/DB_PASS", e);
            throw new IllegalStateException(e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }
}
