package com.delivery.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;

/**
 * Quản lý cấu hình client: host/port từ ENV, username/password cuối cùng lưu vào file.
 */
public final class Config {
    private static final String CONFIG_FILE = System.getProperty("user.home") + "/.delivery-app";

    public static String host() {
        String env = System.getenv("CLIENT_HOST");
        return env != null && !env.isBlank() ? env : "localhost";
    }

    public static int port() {
        String env = System.getenv("CLIENT_PORT");
        try {
            return env != null && !env.isBlank() ? Integer.parseInt(env.trim()) : 5000;
        } catch (NumberFormatException e) {
            return 5000;
        }
    }

    public static String lastUsername() {
        JsonObject obj = loadJson();
        return obj.has("username") ? obj.get("username").getAsString() : "";
    }

    public static void saveCredentials(String username) {
        try {
            JsonObject obj = loadJson();
            obj.addProperty("username", username);
            new FileWriter(CONFIG_FILE, StandardCharsets.UTF_8).append(obj.toString()).close();
        } catch (Exception ignored) {}
    }

    private static JsonObject loadJson() {
        try {
            File f = new File(CONFIG_FILE);
            if (!f.exists()) return new JsonObject();
            JsonObject obj = JsonParser.parseReader(new FileReader(f)).getAsJsonObject();
            return obj != null ? obj : new JsonObject();
        } catch (JsonSyntaxException | IllegalStateException ignored) {
            return new JsonObject();
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }
}
