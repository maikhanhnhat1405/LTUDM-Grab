package com.delivery.server.service;

import com.delivery.common.Log;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.delivery.server.ActiveTripRegistry;
import com.delivery.server.ClientSession;
import com.delivery.server.SessionRegistry;
import com.delivery.server.event.EventBus;
import com.delivery.server.event.OrderAcceptedEvent;
import com.delivery.server.event.OrderCreatedEvent;
import com.delivery.server.event.OrderStatusChangedEvent;
import com.delivery.server.db.OrderDao;
import com.delivery.server.model.Order;
import com.delivery.server.model.OrderStatus;
import com.delivery.server.model.Role;
import com.google.gson.JsonArray;

import java.sql.SQLException;
import java.util.List;

public class OrderService {

    private final OrderDao orderDao = new OrderDao();
    private final SessionRegistry registry;   // van giu lai cho listPending/listMine
    private final ActiveTripRegistry activeTrips;
    private final EventBus eventBus;

    public OrderService(SessionRegistry registry, ActiveTripRegistry activeTrips, EventBus eventBus) {
        this.registry = registry;
        this.activeTrips = activeTrips;
        this.eventBus = eventBus;
    }

    // ---------------- Customer tao don ----------------
    public void create(ClientSession s, Message req) {
        if (s.role() != Role.CUSTOMER) {
            s.send(Message.error(req.getRequestId(), MessageType.ERR_FORBIDDEN,
                    "Chi khach hang moi duoc tao don"));
            return;
        }
        Order o = new Order();
        o.customerId   = s.userId();
        o.pickupAddr   = req.str("pickupAddr");
        o.pickupLat    = req.dbl("pickupLat");
        o.pickupLng    = req.dbl("pickupLng");
        o.dropoffAddr  = req.str("dropoffAddr");
        o.dropoffLat   = req.dbl("dropoffLat");
        o.dropoffLng   = req.dbl("dropoffLng");
        o.note         = req.str("note");
        o.price        = req.dbl("price");

        if (o.pickupAddr == null || o.dropoffAddr == null) {
            s.send(Message.error(req.getRequestId(), MessageType.ERR_BAD_REQUEST,
                    "Thieu diem lay hoac diem giao"));
            return;
        }
        try {
            o.id = orderDao.create(o);
            Log.info("Order", req.getRequestId(), "Da tao don #" + o.id + " tu customer " + s.userId());

            // 1. Tra ve cho nguoi tao
            s.send(Message.ok(req.getRequestId()).put("order", o.toJson()));

            // 2. Publish event - NotificationListener se lo phan day PUSH cho driver.
            //    Uu diem: OrderService khong con biet gi ve "phai bao ai".
            OrderCreatedEvent ev = new OrderCreatedEvent(o);
            ev.traceId = req.getRequestId();
            eventBus.publish(ev);

        } catch (SQLException e) {
            Log.error("create order", e);
            s.send(Message.error(req.getRequestId(), MessageType.ERR_SERVER, "Loi database"));
        }
    }

    // ---------------- Driver nhan don ----------------
    public void accept(ClientSession s, Message req) {
        if (s.role() != Role.DRIVER) {
            s.send(Message.error(req.getRequestId(), MessageType.ERR_FORBIDDEN,
                    "Chi tai xe moi duoc nhan don"));
            return;
        }
        long orderId = req.lng("orderId");
        try {
            // Race condition duoc chan o DAY: UPDATE co dieu kien.
            boolean won = orderDao.tryAccept(orderId, s.userId());
            if (!won) {
                s.send(Message.error(req.getRequestId(), MessageType.ERR_ORDER_TAKEN,
                        "Don da co tai xe khac nhan"));
                return;
            }
            Order o = orderDao.findById(orderId);
            // Bat dau theo doi chuyen: tu gio moi goi GPS cua tai xe nay
            // se duoc chuyen tiep cho dung khach hang.
            activeTrips.start(s.userId(), o.id, o.customerId);
            Log.info("Driver " + s.userId() + " nhan don #" + orderId);

            s.send(Message.ok(req.getRequestId()).put("order", o.toJson()));

            // Bao cho khach: da co tai xe
            registry.sendTo(o.customerId, Message.push(MessageType.PUSH_ORDER_STATUS)
                    .put("order", o.toJson())
                    .put("driverName", s.fullName()));

            // Bao cho cac driver khac: don nay het, go khoi danh sach
            registry.broadcastToRoleExcept(Role.DRIVER, s.userId(),
                    Message.push(MessageType.PUSH_ORDER_TAKEN).put("orderId", orderId));

        } catch (SQLException e) {
            Log.error("accept order", e);
            s.send(Message.error(req.getRequestId(), MessageType.ERR_SERVER, "Loi database"));
        }
    }

    // ---------------- Cap nhat trang thai ----------------
    public void updateStatus(ClientSession s, Message req) {
        long orderId = req.lng("orderId");
        OrderStatus next = OrderStatus.parse(req.str("status"));
        if (next == null) {
            s.send(Message.error(req.getRequestId(), MessageType.ERR_BAD_REQUEST, "Trang thai khong hop le"));
            return;
        }
        try {
            Order o = orderDao.findById(orderId);
            if (o == null) {
                s.send(Message.error(req.getRequestId(), MessageType.ERR_ORDER_NOT_FOUND, "Khong tim thay don"));
                return;
            }
            if (!o.belongsTo(s.userId())) {
                s.send(Message.error(req.getRequestId(), MessageType.ERR_FORBIDDEN, "Don nay khong phai cua ban"));
                return;
            }
            // Chi tai xe duoc day tien do; khach chi duoc huy
            boolean allowedActor = (next == OrderStatus.CANCELLED)
                    ? o.belongsTo(s.userId())
                    : (o.driverId != null && o.driverId == s.userId());
            if (!allowedActor) {
                s.send(Message.error(req.getRequestId(), MessageType.ERR_FORBIDDEN,
                        "Ban khong co quyen chuyen sang trang thai nay"));
                return;
            }
            if (!o.status.canGoTo(next)) {
                s.send(Message.error(req.getRequestId(), MessageType.ERR_INVALID_STATUS,
                        "Khong the chuyen " + o.status + " -> " + next));
                return;
            }
            OrderStatus prev = o.status;
            if (!orderDao.updateStatus(orderId, o.status, next)) {
                s.send(Message.error(req.getRequestId(), MessageType.ERR_INVALID_STATUS,
                        "Trang thai da bi thay doi, thu lai"));
                return;
            }
            o.status = next;
            // Chuyen ket thuc -> ngung chuyen tiep GPS cho khach
            if ((next == OrderStatus.COMPLETED || next == OrderStatus.CANCELLED) && o.driverId != null) {
                activeTrips.end(o.driverId);
            }
            Log.info("Order", req.getRequestId(),
                    "Don #" + orderId + " " + prev + " -> " + next + " boi user " + s.userId());

            s.send(Message.ok(req.getRequestId()).put("order", o.toJson()));

            // Listener se lo phan day PUSH ve doi phuong.
            OrderStatusChangedEvent ev = new OrderStatusChangedEvent(o, prev, next, s.userId());
            ev.traceId = req.getRequestId();
            eventBus.publish(ev);
        } catch (SQLException e) {
            Log.error("update status", e);
            s.send(Message.error(req.getRequestId(), MessageType.ERR_SERVER, "Loi database"));
        }
    }

    // ---------------- Danh sach ----------------
    public void listPending(ClientSession s, Message req) {
        try {
            sendList(s, req, orderDao.listPending(50));
        } catch (SQLException e) {
            Log.error("list pending", e);
            s.send(Message.error(req.getRequestId(), MessageType.ERR_SERVER, "Loi database"));
        }
    }

    public void listMine(ClientSession s, Message req) {
        try {
            sendList(s, req, orderDao.listByUser(s.userId(), 50));
        } catch (SQLException e) {
            Log.error("list mine", e);
            s.send(Message.error(req.getRequestId(), MessageType.ERR_SERVER, "Loi database"));
        }
    }

    private void sendList(ClientSession s, Message req, List<Order> orders) {
        JsonArray arr = new JsonArray();
        for (Order o : orders) arr.add(o.toJson());
        s.send(Message.ok(req.getRequestId()).put("orders", arr));
    }
}
