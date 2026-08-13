package com.delivery.server.event;

import com.delivery.server.model.Order;
import com.delivery.server.model.OrderStatus;

public class OrderStatusChangedEvent extends Event {
    public final Order order;
    public final OrderStatus from;
    public final OrderStatus to;
    public final long actorUserId;
    public OrderStatusChangedEvent(Order order, OrderStatus from, OrderStatus to, long actorUserId) {
        this.order = order;
        this.from = from;
        this.to = to;
        this.actorUserId = actorUserId;
    }
}
