package com.delivery.client;

import com.delivery.common.Log;
import com.delivery.common.Message;
import com.delivery.common.Protocol;

import javax.swing.SwingUtilities;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Lop ket noi phia client. Diem quan trong nhat cua thiet ke nay:
 *
 *   - CO DUY NHAT 1 thread doc socket (readerLoop).
 *   - Message co requestId -> tra ve cho dung CompletableFuture dang cho.
 *   - Message khong co requestId (PUSH) -> day sang pushListener.
 *
 * Nho vay giao dien khong bao gio bi treo: moi thu deu bat dong bo,
 * va callback luon duoc chay tren EDT (SwingUtilities.invokeLater).
 */
public class ClientConnection {

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private Thread reader;

    private final Map<String, CompletableFuture<Message>> pending = new ConcurrentHashMap<>();
    private volatile Consumer<Message> pushListener = m -> {};
    private volatile Runnable onDisconnect = () -> {};

    // Thong tin phien sau khi dang nhap
    public long userId;
    public String fullName;
    public String role;
    public String host;
    public int port;
    public long udpToken;      // dùng để ký mỗi gói GPS gửi qua UDP
    public int udpPort = 5001;

    public void connect(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setTcpNoDelay(true);
        in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

        reader = new Thread(this::readerLoop, "reader");
        reader.setDaemon(true);
        reader.start();
    }

    public void setPushListener(Consumer<Message> listener) {
        this.pushListener = listener == null ? m -> {} : listener;
    }

    public void setOnDisconnect(Runnable r) { this.onDisconnect = r; }

    /** Gui request, nhan ve Future cua response tuong ung. */
    public CompletableFuture<Message> send(Message req) {
        CompletableFuture<Message> future = new CompletableFuture<>();
        if (req.getRequestId() != null) pending.put(req.getRequestId(), future);
        try {
            Protocol.writeMessage(out, req);
        } catch (IOException e) {
            pending.remove(req.getRequestId());
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * Ban tien loi cho Swing: ket qua duoc tra ve tren EDT nen callback
     * duoc phep dung thang toi component giao dien.
     */
    public void request(Message req, Consumer<Message> onOk, Consumer<Message> onError) {
        send(req).whenComplete((resp, err) -> SwingUtilities.invokeLater(() -> {
            if (err != null) {
                if (onError != null) onError.accept(
                        Message.error(req.getRequestId(), "NETWORK", err.getMessage()));
            } else if (resp.isError()) {
                if (onError != null) onError.accept(resp);
            } else {
                if (onOk != null) onOk.accept(resp);
            }
        }));
    }

    private void readerLoop() {
        try {
            while (true) {
                Message msg = Protocol.readMessage(in);
                String rid = msg.getRequestId();
                CompletableFuture<Message> waiting = (rid == null) ? null : pending.remove(rid);
                if (waiting != null) {
                    waiting.complete(msg);                       // day la RESPONSE
                } else {
                    Message finalMsg = msg;
                    SwingUtilities.invokeLater(() -> pushListener.accept(finalMsg));  // day la PUSH
                }
            }
        } catch (IOException e) {
            Log.warn("Mat ket noi toi server: " + e.getMessage());
        } finally {
            pending.values().forEach(f -> f.completeExceptionally(new IOException("Ket noi da dong")));
            pending.clear();
            SwingUtilities.invokeLater(onDisconnect);
        }
    }

    public void close() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }
}
