package com.delivery.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache "don dang chay": driverId -> (orderId, customerId).
 *
 * Muc dich: khi mot goi GPS bay vao, server phai biet ngay day vi tri nay
 * can gui cho AI. Neu moi goi lai query database mot lan thi voi 100 tai xe
 * la 50 query/giay chi de tra loi mot cau khong bao gio doi.
 *
 * Duoc cap nhat boi OrderService: them khi driver nhan don, xoa khi don ket thuc.
 * Day la o "CACHE / Active Orders" trong so do kien truc.
 */
public class ActiveTripRegistry {

    public static class Trip {
        public final long orderId;
        public final long customerId;
        Trip(long orderId, long customerId) {
            this.orderId = orderId;
            this.customerId = customerId;
        }
    }

    private final Map<Long, Trip> byDriver = new ConcurrentHashMap<>();

    public void start(long driverId, long orderId, long customerId) {
        byDriver.put(driverId, new Trip(orderId, customerId));
    }

    public void end(long driverId) { byDriver.remove(driverId); }

    public Trip get(long driverId) { return byDriver.get(driverId); }

    public int size() { return byDriver.size(); }
}
