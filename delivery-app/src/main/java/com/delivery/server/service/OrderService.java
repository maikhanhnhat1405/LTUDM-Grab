package com.delivery.server.service;

import com.delivery.common.Log;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.delivery.server.ClientSession;
import com.delivery.server.SessionRegistry;
import com.delivery.server.db.OrderDao;
import com.delivery.server.model.Order;
import com.delivery.server.model.OrderStatus;
import com.delivery.server.model.Role;
import com.google.gson.JsonArray;

import java.sql.SQLException;
import java.util.List;

public class OrderService {

    private final OrderDao orderDao = new OrderDao();
    private final SessionRegistry registry;

    public OrderService(SessionRegistry registry) { this.registry = registry; }

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
            Log.info("Don moi #" + o.id + " tu customer " + s.userId());

            // 1. Tra ve cho nguoi tao
            s.send(Message.ok(req.getRequestId()).put("order", o.toJson()));

            // 2. PUSH cho toan bo driver dang online -> day la "realtime"
            int n = registry.broadcastToRole(Role.DRIVER,
                    Message.push(MessageType.PUSH_NEW_ORDER).put("order", o.toJson()));
            Log.info("Da bao don #" + o.id + " cho " + n + " driver online");

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
            if (!orderDao.updateStatus(orderId, o.status, next)) {
                s.send(Message.error(req.getRequestId(), MessageType.ERR_INVALID_STATUS,
                        "Trang thai da bi thay doi, thu lai"));
                return;
            }
            o.status = next;
            Log.info("Don #" + orderId + " -> " + next + " boi user " + s.userId());

            s.send(Message.ok(req.getRequestId()).put("order", o.toJson()));

            Long other = o.otherParty(s.userId());
            if (other != null) {
                registry.sendTo(other, Message.push(MessageType.PUSH_ORDER_STATUS)
                        .put("order", o.toJson()));
            }
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
