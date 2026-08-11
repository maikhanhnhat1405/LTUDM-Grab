package com.delivery.client;

import com.delivery.common.GeoUtil;
import com.delivery.common.LocationPacket;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Gia lap GPS cua tai xe: may tinh khong co GPS that nen ta tu di chuyen
 * mot diem tren ban do roi ban toa do do qua UDP.
 *
 * Chu y: socket UDP KHONG connect() toi server. Goi tin duoc ban di va
 * khong quan tam co toi noi hay khong - dung ban chat cua UDP.
 */
public class GpsSimulator {

    public static final int INTERVAL_MS = 2000;   // 2 giay / goi

    private final DatagramSocket socket;
    private final InetAddress serverAddress;
    private final int serverPort;
    private final long driverId;
    private final long token;

    private volatile double lat = 10.7769;        // mac dinh: cho Ben Thanh
    private volatile double lng = 106.7009;
    private volatile Double targetLat, targetLng;
    private volatile double speedMps = 11;        // ~40 km/h

    /** Gia lap mat goi de chung minh he thong van chay khi UDP roi goi. */
    private volatile int dropRatePercent = 0;

    private int seq = 0;
    private int sent = 0, dropped = 0;
    private final Random random = new Random();
    private ScheduledExecutorService exec;
    private Consumer<GpsSimulator> onTick;

    public GpsSimulator(String host, int port, long driverId, long token) throws Exception {
        this.socket = new DatagramSocket();
        this.serverAddress = InetAddress.getByName(host);
        this.serverPort = port;
        this.driverId = driverId;
        this.token = token;
    }

    public void setOnTick(Consumer<GpsSimulator> cb) { this.onTick = cb; }
    public void setDropRate(int percent) { this.dropRatePercent = percent; }
    public void setSpeedKmh(double kmh) { this.speedMps = kmh / 3.6; }

    public void setPosition(double lat, double lng) { this.lat = lat; this.lng = lng; }

    public void setTarget(Double lat, Double lng) { this.targetLat = lat; this.targetLng = lng; }

    public double lat() { return lat; }
    public double lng() { return lng; }
    public int sentCount() { return sent; }
    public int droppedCount() { return dropped; }
    public boolean isRunning() { return exec != null && !exec.isShutdown(); }

    public double distanceToTarget() {
        if (targetLat == null) return -1;
        return GeoUtil.distanceMeters(lat, lng, targetLat, targetLng);
    }

    public void start() {
        if (isRunning()) return;
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "gps-sender");
            t.setDaemon(true);
            return t;
        });
        exec.scheduleAtFixedRate(this::tick, 0, INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (exec != null) exec.shutdownNow();
        exec = null;
    }

    private void tick() {
        try {
            move();
            seq++;   // seq van tang ngay ca khi goi bi "mat" -> server phat hien duoc lo hong

            if (dropRatePercent > 0 && random.nextInt(100) < dropRatePercent) {
                dropped++;
            } else {
                send();
                sent++;
            }
            if (onTick != null) onTick.accept(this);
        } catch (Exception ignored) {
            // UDP that bai thi thoi, goi sau se bu - khong retry, khong bao loi
        }
    }

    /** Di chuyen thang ve phia dich mot doan = toc do x thoi gian. */
    private void move() {
        if (targetLat == null) return;
        double distance = GeoUtil.distanceMeters(lat, lng, targetLat, targetLng);
        double step = speedMps * (INTERVAL_MS / 1000.0);

        if (distance <= step) {          // toi noi
            lat = targetLat;
            lng = targetLng;
            return;
        }
        double ratio = step / distance;
        lat += (targetLat - lat) * ratio;
        lng += (targetLng - lng) * ratio;
    }

    private void send() throws Exception {
        LocationPacket p = new LocationPacket();
        p.driverId = driverId;
        p.token = token;
        p.timestamp = System.currentTimeMillis();
        p.lat = lat;
        p.lng = lng;
        p.seq = seq;

        byte[] data = p.encode();
        socket.send(new DatagramPacket(data, data.length, serverAddress, serverPort));
    }
}
