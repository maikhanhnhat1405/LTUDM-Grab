package com.delivery.server.model;

public class DriverLocation {
    public final long driverId;
    public volatile double lat;
    public volatile double lng;
    public volatile long timestamp;
    public volatile int lastSeq = -1;

    public DriverLocation(long driverId) { this.driverId = driverId; }
}
