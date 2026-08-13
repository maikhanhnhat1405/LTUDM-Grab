package com.delivery.common;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logger toi gian nhung du dung cho do an:
 *
 *   - In ra console (co timestamp + level + ten thread).
 *   - Ghi song song ra file trong thu muc logs/, ten file la logs/server-YYYY-MM-DD.log
 *     (roll theo ngay - dung ngay hien tai lam ten). Khong can Logback.
 *   - Ho tro tag requestId de trace 1 yeu cau xuyen suot cac service:
 *
 *       Log.info("ORDER_ACCEPT", requestId, "driver=42 order=17");
 *
 *     Log se ra:
 *       21:33:14.005 [INFO ] [main            ] [ORDER_ACCEPT   ] [req=abc123] driver=42 order=17
 *
 * Vi sao khong dung SLF4J/Logback: them 2 dependency + 1 file cau hinh XML cho mot
 * yeu cau don gian la thua. Neu sau nay can log co cau truc thi thay noi dung
 * file nay, khong sua goi ben ngoai.
 */
public final class Log {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter DAY  = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Path LOG_DIR = Paths.get("logs");
    private static final Object FILE_LOCK = new Object();

    private static volatile PrintWriter fileOut;
    private static volatile String currentDay;
    private static volatile boolean fileEnabled = true;

    private Log() {}

    // ---------- API ngan (khong requestId) ----------
    public static void info(String msg)             { log("INFO ", null, null, msg, null); }
    public static void warn(String msg)             { log("WARN ", null, null, msg, null); }
    public static void error(String msg, Throwable t) { log("ERROR", null, null, msg, t); }

    // ---------- API day du (co tag + requestId) ----------
    public static void info(String tag, String requestId, String msg)  { log("INFO ", tag, requestId, msg, null); }
    public static void warn(String tag, String requestId, String msg)  { log("WARN ", tag, requestId, msg, null); }
    public static void error(String tag, String requestId, String msg, Throwable t) { log("ERROR", tag, requestId, msg, t); }

    /** Tat ghi file - dung trong test hoac khi khong co quyen ghi thu muc. */
    public static void setFileEnabled(boolean enabled) { fileEnabled = enabled; }

    private static void log(String level, String tag, String requestId, String msg, Throwable t) {
        String line = format(level, tag, requestId, msg, t);
        System.out.println(line);
        if (t != null) t.printStackTrace(System.out);
        if (fileEnabled) writeToFile(line, t);
    }

    private static String format(String level, String tag, String requestId, String msg, Throwable t) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(LocalDateTime.now().format(TIME))
          .append(" [").append(level).append("]")
          .append(" [").append(pad(Thread.currentThread().getName(), 14)).append("]");
        if (tag != null)       sb.append(" [").append(pad(tag, 14)).append("]");
        if (requestId != null) sb.append(" [req=").append(shortId(requestId)).append("]");
        sb.append(' ').append(msg);
        if (t != null)         sb.append(" -> ").append(t);
        return sb.toString();
    }

    private static String pad(String s, int width) {
        if (s == null) s = "";
        return s.length() >= width ? s.substring(0, width) : s + " ".repeat(width - s.length());
    }

    private static String shortId(String id) {
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private static void writeToFile(String line, Throwable t) {
        try {
            synchronized (FILE_LOCK) {
                String today = LocalDate.now().format(DAY);
                if (fileOut == null || !today.equals(currentDay)) {
                    if (fileOut != null) fileOut.close();
                    Files.createDirectories(LOG_DIR);
                    Path file = LOG_DIR.resolve("server-" + today + ".log");
                    fileOut = new PrintWriter(Files.newBufferedWriter(file,
                            StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND), true);
                    currentDay = today;
                }
                fileOut.println(line);
                if (t != null) t.printStackTrace(fileOut);
            }
        } catch (IOException ignored) {
            // Ghi file that bai khong duoc lam sap ung dung
        }
    }
}
