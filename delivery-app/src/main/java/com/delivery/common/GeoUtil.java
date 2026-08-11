package com.delivery.common;

public final class GeoUtil {

    private static final double EARTH_RADIUS_M = 6_371_000;

    private GeoUtil() {}

    /** Khoang cach duong chim bay giua 2 toa do, don vi met (cong thuc Haversine). */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static String formatDistance(double meters) {
        return meters < 1000
                ? String.format("%.0f m", meters)
                : String.format("%.1f km", meters / 1000);
    }
}
