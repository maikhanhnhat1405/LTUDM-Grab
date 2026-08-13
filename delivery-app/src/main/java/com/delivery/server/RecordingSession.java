package com.delivery.server;

import com.delivery.common.Message;

/**
 * Wrapper dung composition: gui thang qua session that, dong thoi nho
 * response DAU TIEN de Router luu vao IdempotencyCache.
 *
 * Khong the ke thua ClientSession vi constructor cua no yeu cau Socket that
 * (throw IOException). Composition cung dung hon: RecordingSession chi
 * quan tam den .send(), khong phai la mot ket noi mang.
 *
 * Vi sao chi nho response dau tien: cac PUSH sau nay (cho driver khac,
 * cho khach hang) khong phai "response cua request nay" - chung khong
 * nen bi replay khi client retry.
 */
public class RecordingSession {
    private final ClientSession delegate;
    private volatile Message firstResponse;

    public RecordingSession(ClientSession delegate) { this.delegate = delegate; }

    public void send(Message m) {
        if (firstResponse == null && m.getRequestId() != null) firstResponse = m;
        delegate.send(m);
    }

    public Message firstResponse() { return firstResponse; }
}
