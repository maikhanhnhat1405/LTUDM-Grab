package com.delivery.server.model;

import com.google.gson.JsonObject;

public class Order {
    public long id;
    public long customerId;
    public Long driverId;          // null khi chua co tai xe
    public String pickupAddr;
    public double pickupLat, pickupLng;
    public String dropoffAddr;
    public double dropoffLat, dropoffLng;
    public String note;
    public double price;
    public OrderStatus status = OrderStatus.PENDING;
    public int version;
    public String createdAt;

    /** Nguoi con lai trong don (dung de day PUSH cho doi phuong). */
    public Long otherParty(long myUserId) {
        if (driverId == null) return null;
        return myUserId == customerId ? driverId : customerId;
    }

    public boolean belongsTo(long userId) {
        return customerId == userId || (driverId != null && driverId == userId);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("customerId", customerId);
        if (driverId != null) o.addProperty("driverId", driverId);
        o.addProperty("pickupAddr", pickupAddr);
        o.addProperty("pickupLat", pickupLat);
        o.addProperty("pickupLng", pickupLng);
        o.addProperty("dropoffAddr", dropoffAddr);
        o.addProperty("dropoffLat", dropoffLat);
        o.addProperty("dropoffLng", dropoffLng);
        o.addProperty("note", note);
        o.addProperty("price", price);
        o.addProperty("status", status.name());
        o.addProperty("version", version);
        o.addProperty("createdAt", createdAt);
        return o;
    }
}
