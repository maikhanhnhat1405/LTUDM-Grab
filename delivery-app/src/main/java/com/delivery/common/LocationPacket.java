package com.delivery.common;

import java.nio.ByteBuffer;

/**
 * Goi tin GPS gui qua UDP. Dinh dang nhi phan co dinh 45 byte:
 *
 *   [1] version | [8] driverId | [8] token | [8] timestamp | [8] lat | [8] lng | [4] seq
 *
 * Vi sao khong dung JSON nhu ben TCP:
 *   - Moi tai xe ban 1 goi / 2 giay, 100 tai xe = 3000 goi/phut. JSON ton
 *     bang thong gap ~4 lan va phai parse text.
 *   - Goi co kich thuoc co dinh -> doc bang ByteBuffer, khong can framing
 *     (UDP la datagram, moi goi la mot don vi tron ven - khac han TCP stream).
 *
 * ByteBuffer mac dinh big-endian (network byte order) nen khong phai xu ly gi them.
 */
public class LocationPacket {

    public static final byte VERSION = 1;
    public static final int SIZE = 45;

    public long driverId;
    public long token;      // chong gia mao: UDP khong co ket noi nen phai tu xac thuc
    public long timestamp;
    public double lat;
    public double lng;
    public int seq;         // so thu tu tang dan -> phat hien goi den tre

    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(SIZE);
        buf.put(VERSION);
        buf.putLong(driverId);
        buf.putLong(token);
        buf.putLong(timestamp);
        buf.putDouble(lat);
        buf.putDouble(lng);
        buf.putInt(seq);
        return buf.array();
    }

    /** Tra ve null neu goi tin hong / sai phien ban -> goi tin rac bi bo qua lang le. */
    public static LocationPacket decode(byte[] data, int length) {
        if (length != SIZE) return null;
        ByteBuffer buf = ByteBuffer.wrap(data, 0, length);
        if (buf.get() != VERSION) return null;

        LocationPacket p = new LocationPacket();
        p.driverId  = buf.getLong();
        p.token     = buf.getLong();
        p.timestamp = buf.getLong();
        p.lat       = buf.getDouble();
        p.lng       = buf.getDouble();
        p.seq       = buf.getInt();
        return p;
    }

    @Override
    public String toString() {
        return String.format("GPS[driver=%d seq=%d %.5f,%.5f]", driverId, seq, lat, lng);
    }
}
