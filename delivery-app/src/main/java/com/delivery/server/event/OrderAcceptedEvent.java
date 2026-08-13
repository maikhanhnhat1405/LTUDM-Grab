package com.delivery.server.event;

import com.delivery.server.model.Order;

public class OrderAcceptedEvent extends Event {
    public final Order order;
    public final long driverId;
    public final String driverName;
    public OrderAcceptedEvent(Order order, long driverId, String driverName) {
        this.order = order;
        this.driverId = driverId;
        this.driverName = driverName;
    }
}
