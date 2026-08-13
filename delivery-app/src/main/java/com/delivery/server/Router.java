package com.delivery.server;

import com.delivery.common.Log;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.delivery.server.event.EventBus;
import com.delivery.server.service.AuthService;
import com.delivery.server.service.ChatService;
import com.delivery.server.service.OrderService;

/**
 * Dinh tuyen message + hai chot chan:
 *
 *   1. IDEMPOTENCY: xem requestId da xu ly chua. Neu roi -> tra lai response cu.
 *      Chi ap dung cho lenh SUA DU LIEU (ORDER_CREATE, CHAT_SEND). Lenh chi doc
 *      (LIST) khong can vi lam lai khong hai gi.
 *
 *      ORDER_ACCEPT khong can idempotency o tang nay: tryAccept da idempotent nho
 *      dieu kien "status='PENDING' AND driver_id IS NULL" nen retry lan 2 se tra
 *      "ORDER_ALREADY_TAKEN" - dung y nghia; client phai xu ly ca 2 truong hop.
 *
 *      ORDER_UPDATE_STATUS cung idempotent tu nhien nho dieu kien "AND status=?"
 *      trong UPDATE.
 *
 *   2. XAC THUC: chua dang nhap thi chi duoc goi REGISTER / LOGIN / PING.
 */
public class Router {

    private final AuthService authService;
    private final OrderService orderService;
    private final ChatService chatService;
    private final IdempotencyCache idempotency = new IdempotencyCache();

    public Router(SessionRegistry registry, ActiveTripRegistry activeTrips, EventBus eventBus) {
        this.authService  = new AuthService(registry);
        this.orderService = new OrderService(registry, activeTrips, eventBus);
        this.chatService  = new ChatService(registry);
    }

    private static boolean needsIdempotency(String type) {
        return MessageType.ORDER_CREATE.equals(type)
            || MessageType.CHAT_SEND.equals(type);
    }

    public void handle(ClientSession session, Message msg) {
        String type = msg.getType();
        if (type == null) return;
        String requestId = msg.getRequestId();

        // Chan retry tu ban dem
        if (needsIdempotency(type)) {
            Message cached = idempotency.get(requestId);
            if (cached != null) {
                Log.info("Idem", requestId, "Retry cua " + type + " -> tra lai response cu");
                session.send(cached);
                return;
            }
        }

        // Wrap session de bat response dau tien
        RecordingSession recorder = needsIdempotency(type) ? new RecordingSession(session) : null;
        ClientSession sessionForService = recorder == null ? session : new RecordingClientSession(session, recorder);

        route(sessionForService, msg);

        if (recorder != null && recorder.firstResponse() != null) {
            idempotency.put(requestId, recorder.firstResponse());
        }
    }

    private void route(ClientSession s, Message msg) {
        String type = msg.getType();
        String requestId = msg.getRequestId();
        switch (type) {
            case MessageType.REGISTER: authService.register(s, msg); return;
            case MessageType.LOGIN:    authService.login(s, msg);    return;
            case MessageType.PING:     s.send(Message.response(MessageType.PONG, requestId)); return;
            default: break;
        }
        if (!s.isAuthenticated()) {
            s.send(Message.error(requestId, MessageType.ERR_UNAUTHORIZED, "Chua dang nhap"));
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
                Log.warn("Router", requestId, "Type khong ho tro: " + type);
                s.send(Message.error(requestId, MessageType.ERR_BAD_REQUEST,
                        "Khong ho tro type: " + type));
        }
    }
}
