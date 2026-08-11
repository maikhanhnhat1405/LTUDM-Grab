package com.delivery.server.service;

import com.delivery.common.Log;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.delivery.server.ClientSession;
import com.delivery.server.SessionRegistry;
import com.delivery.server.db.MessageDao;
import com.delivery.server.db.OrderDao;
import com.delivery.server.model.ChatMessage;
import com.delivery.server.model.Order;
import com.google.gson.JsonArray;

import java.sql.SQLException;
import java.util.List;

/**
 * Chat gan theo DON HANG (khong phai chat tu do): khach va tai xe cua cung 1 don
 * moi noi chuyen duoc voi nhau. Server luon kiem tra quyen truoc khi chuyen tiep.
 */
public class ChatService {

    private final MessageDao messageDao = new MessageDao();
    private final OrderDao orderDao = new OrderDao();
    private final SessionRegistry registry;

    public ChatService(SessionRegistry registry) { this.registry = registry; }

    public void send(ClientSession s, Message req) {
        long orderId = req.lng("orderId");
        String content = req.str("content");
        if (content == null || content.trim().isEmpty()) {
            s.send(Message.error(req.getRequestId(), MessageType.ERR_BAD_REQUEST, "Noi dung rong"));
            return;
        }
        try {
            Order o = orderDao.findById(orderId);
            if (o == null) {
                s.send(Message.error(req.getRequestId(), MessageType.ERR_ORDER_NOT_FOUND, "Khong tim thay don"));
                return;
            }
            if (!o.belongsTo(s.userId())) {
                s.send(Message.error(req.getRequestId(), MessageType.ERR_FORBIDDEN, "Ban khong thuoc don nay"));
                return;
            }
            ChatMessage m = messageDao.save(orderId, s.userId(), content, "TEXT");
            m.senderName = s.fullName();

            // Xac nhan cho nguoi gui (de UI biet tin da toi server)
            s.send(Message.ok(req.getRequestId()).put("message", m.toJson()));

            // Chuyen tiep cho nguoi con lai neu dang online
            Long other = o.otherParty(s.userId());
            if (other != null) {
                boolean delivered = registry.sendTo(other,
                        Message.push(MessageType.PUSH_CHAT_MESSAGE).put("message", m.toJson()));
                if (!delivered) {
                    // Offline: tin da luu DB, ho se thay khi mo lich su chat
                    Log.info("User " + other + " offline, tin nhan don #" + orderId + " luu DB");
                }
            }
        } catch (SQLException e) {
            Log.error("chat send", e);
            s.send(Message.error(req.getRequestId(), MessageType.ERR_SERVER, "Loi database"));
        }
    }

    public void history(ClientSession s, Message req) {
        long orderId = req.lng("orderId");
        try {
            Order o = orderDao.findById(orderId);
            if (o == null || !o.belongsTo(s.userId())) {
                s.send(Message.error(req.getRequestId(), MessageType.ERR_FORBIDDEN, "Khong co quyen xem"));
                return;
            }
            List<ChatMessage> list = messageDao.listByOrder(orderId, 200);
            JsonArray arr = new JsonArray();
            for (ChatMessage m : list) arr.add(m.toJson());
            s.send(Message.ok(req.getRequestId()).put("messages", arr));
        } catch (SQLException e) {
            Log.error("chat history", e);
            s.send(Message.error(req.getRequestId(), MessageType.ERR_SERVER, "Loi database"));
        }
    }
}
