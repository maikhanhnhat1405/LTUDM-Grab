package com.delivery.server.service;

import com.delivery.common.GeoUtil;
import com.delivery.common.LocationPacket;
import com.delivery.common.Log;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.delivery.server.ActiveTripRegistry;
import com.delivery.server.ClientSession;
import com.delivery.server.LocationCache;
import com.delivery.server.SessionRegistry;
import com.delivery.server.model.DriverLocation;
import com.delivery.server.model.Role;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Xu ly moi goi GPS den tu UDP.
 *
 * Ba cua ai phai qua:
 *   1. Xac thuc token  -> chong gia mao (UDP khong co ket noi de tin tuong)
 *   2. Kiem tra seq    -> bo goi den tre / goi lap
 *   3. Co chuyen dang chay khong -> moi day cho khach, khong thi chi luu cache
 */
public class LocationService {

    private final SessionRegistry sessions;
    private final LocationCache cache;
    private final ActiveTripRegistry activeTrips;

    // Thong ke - rat dang dua vao bao cao de chung minh dac tinh cua UDP
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong rejectedAuth = new AtomicLong();
    private final AtomicLong droppedStale = new AtomicLong();
    private final AtomicLong pushed = new AtomicLong();

    public LocationService(SessionRegistry sessions, LocationCache cache, ActiveTripRegistry activeTrips) {
        this.sessions = sessions;
        this.cache = cache;
        this.activeTrips = activeTrips;
    }

    public void onPacket(LocationPacket p) {
        received.incrementAndGet();

        // --- Cua 1: xac thuc ---
        ClientSession session = sessions.get(p.driverId);
        if (session == null || session.role() != Role.DRIVER || session.udpToken() != p.token) {
            rejectedAuth.incrementAndGet();
            return;   // im lang bo qua, khong tra loi - tra loi la mo duong cho DoS khuech dai
        }

        // --- Cua 2: thu tu goi tin ---
        if (!cache.update(p.driverId, p.lat, p.lng, p.timestamp, p.seq)) {
            droppedStale.incrementAndGet();
            return;
        }

        // --- Cua 3: co khach nao dang doi khong ---
        ActiveTripRegistry.Trip trip = activeTrips.get(p.driverId);
        if (trip != null) {
            Message push = Message.push(MessageType.PUSH_DRIVER_LOCATION)
                    .put("driverId", p.driverId)
                    .put("orderId", trip.orderId)
                    .put("lat", p.lat)
                    .put("lng", p.lng)
                    .put("seq", p.seq)
                    .put("timestamp", p.timestamp);
            // Chieu nay BAT BUOC TCP: khach dang nhin xe chay, mat goi la man hinh dung.
            if (sessions.sendTo(trip.customerId, push)) pushed.incrementAndGet();
        }

        long n = received.get();
        if (n % 50 == 0) logStats();
    }

    /** Khoang cach tu tai xe toi mot diem - dung cho matching o buoc sau. */
    public double distanceFrom(long driverId, double lat, double lng) {
        DriverLocation loc = cache.get(driverId);
        if (loc == null) return Double.MAX_VALUE;
        return GeoUtil.distanceMeters(loc.lat, loc.lng, lat, lng);
    }

    public void logStats() {
        Log.info(String.format("UDP stats: nhan=%d | tu_choi_auth=%d | bo_goi_tre=%d | day_TCP=%d | tai_xe_co_vi_tri=%d",
                received.get(), rejectedAuth.get(), droppedStale.get(), pushed.get(), cache.size()));
    }
}
