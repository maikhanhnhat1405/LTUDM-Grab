package com.delivery.common;

/**
 * Tap trung TAT CA ten message cua protocol o mot cho.
 * Quy uoc dat ten:
 *   - REQUEST : client -> server            (LOGIN, ORDER_CREATE, ...)
 *   - RESPONSE: server -> client, co requestId trung voi request
 *   - PUSH    : server -> client, KHONG co requestId (server tu dong bao)
 */
public final class MessageType {
    private MessageType() {}

    // ---------- REQUEST: Auth ----------
    public static final String REGISTER = "REGISTER";
    public static final String LOGIN    = "LOGIN";
    public static final String LOGOUT   = "LOGOUT";

    // ---------- REQUEST: Order ----------
    public static final String ORDER_CREATE        = "ORDER_CREATE";
    public static final String ORDER_LIST_PENDING  = "ORDER_LIST_PENDING";  // driver lay danh sach don cho
    public static final String ORDER_LIST_MINE     = "ORDER_LIST_MINE";     // don cua chinh minh
    public static final String ORDER_ACCEPT        = "ORDER_ACCEPT";        // driver nhan don
    public static final String ORDER_UPDATE_STATUS = "ORDER_UPDATE_STATUS";
    public static final String ORDER_CANCEL        = "ORDER_CANCEL";

    // ---------- REQUEST: Chat ----------
    public static final String CHAT_SEND    = "CHAT_SEND";
    public static final String CHAT_HISTORY = "CHAT_HISTORY";

    // ---------- REQUEST: he thong ----------
    public static final String PING = "PING";

    // ---------- RESPONSE ----------
    public static final String OK    = "OK";     // thanh cong, ket qua nam trong data
    public static final String ERROR = "ERROR";  // that bai: data.code + data.message

    // ---------- PUSH ----------
    public static final String PUSH_NEW_ORDER      = "PUSH_NEW_ORDER";      // -> tat ca driver online
    public static final String PUSH_ORDER_TAKEN    = "PUSH_ORDER_TAKEN";    // -> driver khac: don da bi nhan
    public static final String PUSH_ORDER_STATUS   = "PUSH_ORDER_STATUS";   // -> doi phuong trong don
    public static final String PUSH_DRIVER_LOCATION = "PUSH_DRIVER_LOCATION"; // -> khach: vi tri tai xe (nguon UDP)
    public static final String PUSH_CHAT_MESSAGE   = "PUSH_CHAT_MESSAGE";   // -> nguoi con lai trong don
    public static final String PONG                = "PONG";

    // ---------- Ma loi ----------
    public static final String ERR_UNAUTHORIZED    = "UNAUTHORIZED";
    public static final String ERR_BAD_REQUEST     = "BAD_REQUEST";
    public static final String ERR_LOGIN_FAILED    = "LOGIN_FAILED";
    public static final String ERR_USER_EXISTS     = "USER_EXISTS";
    public static final String ERR_ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    public static final String ERR_ORDER_TAKEN     = "ORDER_ALREADY_TAKEN";
    public static final String ERR_INVALID_STATUS  = "INVALID_STATUS_TRANSITION";
    public static final String ERR_FORBIDDEN       = "FORBIDDEN";
    public static final String ERR_SERVER          = "SERVER_ERROR";
}
