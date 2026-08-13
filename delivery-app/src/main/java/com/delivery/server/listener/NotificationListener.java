package com.delivery.server.listener;

import com.delivery.common.Log;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.delivery.server.SessionRegistry;
import com.delivery.server.event.OrderAcceptedEvent;
import com.delivery.server.event.OrderCreatedEvent;
import com.delivery.server.event.OrderStatusChangedEvent;
import com.delivery.server.model.Order;
import com.delivery.server.model.Role;

/**
 * Nghe cac su kien don hang va day PUSH cho client tuong ung.
 *
 * Truoc day OrderService tu goi registry.broadcast/sendTo o giua nghiep vu -
 * lan lon giua "logic don hang" va "gui thong bao". Tach ra listener rieng:
 * OrderService chi lo DB + tra response; day tin la viec cua listener.
 */
public class NotificationListener {

    private final SessionRegistry registry;

    public NotificationListener(SessionRegistry registry) { this.registry = registry; }

    public void onOrderCreated(OrderCreatedEvent e) {
        Order o = e.order;
        int n = registry.broadcastToRole(Role.DRIVER,
                Message.push(MessageType.PUSH_NEW_ORDER).put("order", o.toJson()));
        Log.info("Notify", e.traceId,
                "Bao don #" + o.id + " cho " + n + " tai xe online");
    }

    public void onOrderAccepted(OrderAcceptedEvent e) {
        Order o = e.order;

        // Bao cho khach: da co tai xe
        registry.sendTo(o.customerId, Message.push(MessageType.PUSH_ORDER_STATUS)
                .put("order", o.toJson())
                .put("driverName", e.driverName));

        // Bao cho cac driver khac: don nay het, go khoi danh sach
        registry.broadcastToRoleExcept(Role.DRIVER, e.driverId,
                Message.push(MessageType.PUSH_ORDER_TAKEN).put("orderId", o.id));

        Log.info("Notify", e.traceId, "Da bao 'don duoc nhan' cho khach + driver khac");
    }

    public void onOrderStatusChanged(OrderStatusChangedEvent e) {
        Order o = e.order;
        Long other = o.otherParty(e.actorUserId);
        if (other == null) return;
        registry.sendTo(other, Message.push(MessageType.PUSH_ORDER_STATUS)
                .put("order", o.toJson()));
    }
}
