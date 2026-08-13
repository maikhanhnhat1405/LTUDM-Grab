package com.delivery.server.event;

import com.delivery.server.model.Order;

public class OrderCreatedEvent extends Event {
    public final Order order;
    public OrderCreatedEvent(Order order) { this.order = order; }
}
