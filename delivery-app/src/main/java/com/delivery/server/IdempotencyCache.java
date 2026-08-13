package com.delivery.server;

import com.delivery.common.Message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ban dem cua co che retry an toan.
 *
 * TINH HUONG:
 *   Client gui ORDER_ACCEPT, server xu ly xong, dang gui response ve thi
 *   mang rot. Client khong biet la thanh cong hay that bai. Neu retry ma
 *   server xu ly lai tu dau thi:
 *     - Voi ORDER_ACCEPT: khong sao (tryAccept da idempotent nho version).
 *     - Voi ORDER_CREATE: RA 2 DON. Hong.
 *     - Voi CHAT_SEND: gui 2 tin nhac trung nhau.
 *
 * GIAI PHAP:
 *   Moi request co requestId (san co tu Level 1). Server nho requestId da
 *   xu ly xong + response da tra ra. Neu thay requestId cu -> tra lai
 *   response da luu, khong xu ly lai.
 *
 * Cache thuong (khong loai bo) se pho phach RAM sau vai ngay chay lien tuc,
 * nen dat gioi han va don theo LRU tu che rat don gian.
 */
public class IdempotencyCache {

    private static final int MAX_ENTRIES = 5_000;

    private final Map<String, Message> byRequestId = new ConcurrentHashMap<>();

    public Message get(String requestId) {
        return requestId == null ? null : byRequestId.get(requestId);
    }

    public void put(String requestId, Message response) {
        if (requestId == null) return;
        // Don qua tay khi vuot nguong - "chi dam bao chong retup trong thoi gian gan".
        // Voi mang thuc te thi retry xay ra trong vai giay den vai phut, cache 5k la du.
        if (byRequestId.size() >= MAX_ENTRIES) byRequestId.clear();
        byRequestId.put(requestId, response);
    }

    public int size() { return byRequestId.size(); }
}
