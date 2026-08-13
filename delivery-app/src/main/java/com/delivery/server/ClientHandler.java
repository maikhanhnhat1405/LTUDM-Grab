package com.delivery.server;

import com.delivery.common.Log;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.delivery.common.Protocol;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * Mot thread = mot client (mo hinh thread-per-connection).
 * Vong lap: doc 1 message -> dua cho Router -> lap lai, cho den khi client ngat.
 *
 * Heartbeat (Level 3):
 *   Server dat soTimeout 60s tren socket. Client bat buoc gui PING moi 20s.
 *   Neu 60s khong nghe gi (3 chu ky PING lien tiep bi mat) thi coi la client
 *   zombie -> dong. Neu khong co co che nay, mot client mat mang khong sach
 *   se giu socket den khi HDH thu doc (co the hang gio) va ChatService/
 *   OrderService cu tuong ho van online.
 */
public class ClientHandler implements Runnable {

    public static final int READ_TIMEOUT_MS = 60_000;

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
        Log.info("Conn", null, "Client ket noi: " + session.describe());
        try {
            session.setReadTimeout(READ_TIMEOUT_MS);
            while (true) {
                Message msg = Protocol.readMessage(session.in());   // block cho goi day du hoac timeout
                try {
                    router.handle(session, msg);
                } catch (Exception ex) {
                    // Loi khi xu ly 1 message KHONG duoc lam chet ca ket noi
                    Log.error("Router", msg.getRequestId(), "Loi xu ly " + msg.getType(), ex);
                    session.send(Message.error(msg.getRequestId(),
                            MessageType.ERR_SERVER, "Loi xu ly phia server"));
                }
            }
        } catch (SocketTimeoutException e) {
            Log.warn("Conn", null, "Client " + session.describe() + " im lang qua " + READ_TIMEOUT_MS + "ms -> dong");
        } catch (EOFException e) {
            Log.info("Conn", null, "Client dong ket noi: " + session.describe());
        } catch (IOException e) {
            Log.warn("Conn", null, "Mat ket noi " + session.describe() + ": " + e.getMessage());
        } finally {
            registry.unregister(session);
            session.close();
            Log.info("Conn", null, "Con lai " + registry.onlineCount() + " client online");
        }
    }
}
