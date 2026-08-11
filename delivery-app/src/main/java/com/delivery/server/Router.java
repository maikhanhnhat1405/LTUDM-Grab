package com.delivery.server;

import com.delivery.common.Log;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.delivery.server.service.AuthService;
import com.delivery.server.service.ChatService;
import com.delivery.server.service.OrderService;

/**
 * Nhan message da parse -> chon service xu ly.
 * Cung la cho dat CHOT BAO MAT: chua dang nhap thi chi duoc goi REGISTER/LOGIN/PING.
 */
public class Router {

    private final AuthService authService;
    private final OrderService orderService;
    private final ChatService chatService;

    public Router(SessionRegistry registry, ActiveTripRegistry activeTrips) {
        this.authService  = new AuthService(registry);
        this.orderService = new OrderService(registry, activeTrips);
        this.chatService  = new ChatService(registry);
    }

    public void handle(ClientSession s, Message msg) {
        String type = msg.getType();
        if (type == null) return;

        // --- vong ngoai: khong can dang nhap ---
        switch (type) {
            case MessageType.REGISTER: authService.register(s, msg); return;
            case MessageType.LOGIN:    authService.login(s, msg);    return;
            case MessageType.PING:     s.send(Message.response(MessageType.PONG, msg.getRequestId())); return;
            default: break;
        }

        // --- vong trong: bat buoc dang nhap ---
        if (!s.isAuthenticated()) {
            s.send(Message.error(msg.getRequestId(), MessageType.ERR_UNAUTHORIZED, "Chua dang nhap"));
            return;
        }

        switch (type) {
            case MessageType.ORDER_CREATE:        orderService.create(s, msg);       break;
            case MessageType.ORDER_ACCEPT:        orderService.accept(s, msg);       break;
            case MessageType.ORDER_UPDATE_STATUS: orderService.updateStatus(s, msg); break;
            case MessageType.ORDER_LIST_PENDING:  orderService.listPending(s, msg);  break;
            case MessageType.ORDER_LIST_MINE:     orderService.listMine(s, msg);     break;
            case MessageType.CHAT_SEND:           chatService.send(s, msg);          break;
            case MessageType.CHAT_HISTORY:        chatService.history(s, msg);       break;
            default:
                Log.warn("Message type la: " + type);
                s.send(Message.error(msg.getRequestId(), MessageType.ERR_BAD_REQUEST,
                        "Khong ho tro type: " + type));
        }
    }
}
