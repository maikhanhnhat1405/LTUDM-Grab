package com.delivery.server;

import com.delivery.common.Log;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.delivery.common.Protocol;

import java.io.EOFException;
import java.io.IOException;

/**
 * Mot thread = mot client (mo hinh thread-per-connection).
 * Vong lap: doc 1 message -> dua cho Router -> lap lai, cho den khi client ngat.
 */
public class ClientHandler implements Runnable {

    private final ClientSession session;
    private final SessionRegistry registry;
    private final Router router;

    public ClientHandler(ClientSession session, SessionRegistry registry, Router router) {
        this.session = session;
        this.registry = registry;
        this.router = router;
    }

    @Override
    public void run() {
        Log.info("Client ket noi: " + session.describe());
        try {
            while (true) {
                Message msg = Protocol.readMessage(session.in());   // block cho toi khi co goi tin day du
                try {
                    router.handle(session, msg);
                } catch (Exception ex) {
                    // Loi khi xu ly 1 message KHONG duoc lam chet ca ket noi
                    Log.error("Loi xu ly " + msg.getType(), ex);
                    session.send(Message.error(msg.getRequestId(),
                            MessageType.ERR_SERVER, "Loi xu ly phia server"));
                }
            }
        } catch (EOFException e) {
            Log.info("Client dong ket noi: " + session.describe());
        } catch (IOException e) {
            Log.warn("Mat ket noi " + session.describe() + ": " + e.getMessage());
        } finally {
            registry.unregister(session);
            session.close();
            Log.info("Con lai " + registry.onlineCount() + " client online");
        }
    }
}
