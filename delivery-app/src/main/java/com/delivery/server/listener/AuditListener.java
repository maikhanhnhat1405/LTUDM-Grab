package com.delivery.server.listener;

import com.delivery.common.Log;
import com.delivery.server.event.OrderAcceptedEvent;
import com.delivery.server.event.OrderCreatedEvent;
import com.delivery.server.event.OrderStatusChangedEvent;

/**
 * Ghi lai moi bien co quan trong vao log audit.
 * Vi du ve "listener thu 2 nghe cung 1 event" - minh chung cho loi ich cua EventBus:
 * them tinh nang moi khong dong den code cu.
 */
public class AuditListener {

    public void onOrderCreated(OrderCreatedEvent e) {
        Log.info("Audit", e.traceId,
                "ORDER_CREATED id=" + e.order.id
                + " customer=" + e.order.customerId
                + " price=" + e.order.price);
    }

    public void onOrderAccepted(OrderAcceptedEvent e) {
        Log.info("Audit", e.traceId,
                "ORDER_ACCEPTED id=" + e.order.id
                + " driver=" + e.driverId + "(" + e.driverName + ")");
    }

    public void onOrderStatusChanged(OrderStatusChangedEvent e) {
        Log.info("Audit", e.traceId,
                "ORDER_STATUS id=" + e.order.id
                + " " + e.from + " -> " + e.to + " actor=" + e.actorUserId);
    }
}
