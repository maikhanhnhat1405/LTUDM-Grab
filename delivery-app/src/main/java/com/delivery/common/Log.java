package com.delivery.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Logger toi gian: co timestamp + ten thread (rat quan trong khi debug multi-thread). */
public final class Log {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private Log() {}

    public static void info(String msg) { print("INFO ", msg); }
    public static void warn(String msg) { print("WARN ", msg); }

    public static void error(String msg, Throwable t) {
        print("ERROR", msg + (t != null ? " -> " + t : ""));
    }

    private static void print(String level, String msg) {
        System.out.printf("%s [%s] %-16s %s%n",
                LocalDateTime.now().format(FMT), level, Thread.currentThread().getName(), msg);
    }
}
