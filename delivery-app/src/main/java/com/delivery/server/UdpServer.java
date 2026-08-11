package com.delivery.server;

import com.delivery.common.LocationPacket;
import com.delivery.common.Log;
import com.delivery.server.service.LocationService;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

/**
 * Lang nghe GPS tren cong UDP rieng (5001), doc lap hoan toan voi TCP 5000.
 *
 * Khac biet co ban so voi TCP:
 *   - Khong accept(), khong ket noi, khong session. Chi co mot socket duy nhat
 *     nhan goi tin tu moi noi ban toi.
 *   - Moi datagram la mot don vi tron ven -> khong can framing.
 *   - Mot thread la du: xu ly moi goi chi ton vai micro giay (ghi cache + day TCP).
 *     Neu xu ly nang thi moi can day sang thread pool.
 */
public class UdpServer implements Runnable {

    public static final int UDP_PORT = 5001;

    private final LocationService locationService;
    private DatagramSocket socket;

    public UdpServer(LocationService locationService) {
        this.locationService = locationService;
    }

    public void start() throws SocketException {
        socket = new DatagramSocket(UDP_PORT);
        Thread t = new Thread(this, "udp-gps");
        t.setDaemon(true);
        t.start();
        Log.info("UDP GPS dang lang nghe tai cong " + UDP_PORT);
    }

    @Override
    public void run() {
        byte[] buffer = new byte[64];   // goi that 45 byte, chua du de bat goi rac
        while (!socket.isClosed()) {
            DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(dp);     // block cho goi tin ke tiep
                LocationPacket p = LocationPacket.decode(dp.getData(), dp.getLength());
                if (p == null) continue;             // goi hong -> bo, khong lam sap server
                locationService.onPacket(p);
            } catch (Exception e) {
                // Mot goi loi khong duoc lam chet vong lap
                Log.warn("Loi xu ly goi UDP: " + e.getMessage());
            }
        }
    }

    public void stop() {
        if (socket != null) socket.close();
    }
}
