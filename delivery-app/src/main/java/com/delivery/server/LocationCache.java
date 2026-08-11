package com.delivery.server;

import com.delivery.server.model.DriverLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vi tri tai xe song trong RAM, KHONG ghi database.
 *
 * Ly do: moi tai xe ban 1 goi / 2 giay. 100 tai xe = 4.3 trieu ban ghi/ngay
 * trong khi chi co ban ghi MOI NHAT la co gia tri. Ghi DB o day la tu sat.
 * Neu can luu vet hanh trinh thi ghi mau thua (vd 30 giay/lan) vao bang rieng.
 *
 * Day chinh la o "CACHE / Driver Location" trong so do kien truc.
 */
public class LocationCache {

    private final Map<Long, DriverLocation> byDriver = new ConcurrentHashMap<>();

    /**
     * @return true neu cap nhat duoc; false neu goi den TRE (seq nho hon cai da co).
     *
     * Day la cho xu ly dac tinh cot loi cua UDP: goi tin co the den sai thu tu.
     * Neu ghi de mu quang thi cham tai xe se nhay giat lui tren ban do.
     */
    public boolean update(long driverId, double lat, double lng, long timestamp, int seq) {
        DriverLocation loc = byDriver.computeIfAbsent(driverId, DriverLocation::new);
        synchronized (loc) {
            if (seq <= loc.lastSeq) return false;   // goi cu / goi lap -> bo
            loc.lat = lat;
            loc.lng = lng;
            loc.timestamp = timestamp;
            loc.lastSeq = seq;
            return true;
        }
    }

    public DriverLocation get(long driverId) { return byDriver.get(driverId); }

    public void remove(long driverId) { byDriver.remove(driverId); }

    public int size() { return byDriver.size(); }
}
