package com.delivery.client;

/**
 * Quản lý cấu hình client: host/port từ ENV.
 * Mặc định: localhost:5050.
 */
public final class Config {
    private Config() {}

    public static String host() {
        String env = System.getenv("CLIENT_HOST");
        return env != null && !env.isBlank() ? env : "localhost";
    }

    public static int port() {
        String env = System.getenv("CLIENT_PORT");
        try {
            return env != null && !env.isBlank() ? Integer.parseInt(env.trim()) : 5050;
        } catch (NumberFormatException e) {
            return 5050;
        }
    }
}
