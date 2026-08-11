package com.delivery.server;

import com.delivery.common.Message;
import com.delivery.server.model.Role;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "So dia chi" cua server: userId -> session dang online.
 * Day la thanh phan cot loi cua phan MULTI-CLIENT: nho no ma server biet
 * day thong bao cho DUNG nguoi.
 *
 * Bat buoc ConcurrentHashMap vi nhieu thread client ghi/doc dong thoi.
 */
public class SessionRegistry {

    private final Map<Long, ClientSession> byUserId = new ConcurrentHashMap<>();

    public void register(ClientSession s) {
        ClientSession old = byUserId.put(s.userId(), s);
        if (old != null && old != s) old.close();   // dang nhap 2 noi -> da ket noi cu
    }

    public void unregister(ClientSession s) {
        if (s.isAuthenticated()) byUserId.remove(s.userId(), s);
    }

    public ClientSession get(long userId) { return byUserId.get(userId); }

    public boolean isOnline(long userId) { return byUserId.containsKey(userId); }

    public int onlineCount() { return byUserId.size(); }

    /** Gui rieng cho 1 user (neu dang online). */
    public boolean sendTo(long userId, Message msg) {
        ClientSession s = byUserId.get(userId);
        if (s == null) return false;
        s.send(msg);
        return true;
    }

    /** Broadcast cho toan bo user thuoc 1 role (vd: bao don moi cho tat ca driver). */
    public int broadcastToRole(Role role, Message msg) {
        int n = 0;
        for (ClientSession s : byUserId.values()) {
            if (s.role() == role) { s.send(msg); n++; }
        }
        return n;
    }

    /** Broadcast cho role nhung bo qua 1 user (vd: bao "don da bi nhan" tru driver vua nhan). */
    public int broadcastToRoleExcept(Role role, long exceptUserId, Message msg) {
        int n = 0;
        for (ClientSession s : byUserId.values()) {
            if (s.role() == role && s.userId() != exceptUserId) { s.send(msg); n++; }
        }
        return n;
    }

    public Collection<ClientSession> all() { return byUserId.values(); }
}
