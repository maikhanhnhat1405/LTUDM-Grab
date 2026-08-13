package com.delivery.client;

import com.delivery.common.Log;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Ket noi TCP tu client. Ba diem cot loi:
 *
 *   1. DUY NHAT 1 thread doc socket (readerLoop). Message co requestId -> ket
 *      thuc CompletableFuture dang cho; message khong co requestId (PUSH) ->
 *      day sang pushListener. Nho vay UI khong bao gio bi treo.
 *
 *   2. HEARTBEAT: cu {@link #HEARTBEAT_MS} 1 lan gui PING. Server dat
 *      soTimeout 60s nen neu client zombie thi server tu don. Con neu nga la
 *      SERVER thi PING that bai -> reader thoat -> co che reconnect kich hoat.
 *
 *   3. RECONNECT: mat ket noi khong bao loi ra UI ngay; thay vao do quay thu
 *      lai voi exponential backoff (1s, 2s, 4s, 8s, cap 30s). Dang nhap lai
 *      tu dong bang username/password nho tam thoi. Khi noi lai, UI biet qua
 *      onReconnected -> lam moi danh sach.
 */
public class ClientConnection {

    private static final long HEARTBEAT_MS = 20_000;
    private static final int  CONNECT_TIMEOUT_MS = 5_000;
    private static final long BACKOFF_START_MS = 1_000;
    private static final long BACKOFF_MAX_MS = 30_000;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private final Map<String, CompletableFuture<Message>> pending = new ConcurrentHashMap<>();
    private volatile Consumer<Message> pushListener = m -> {};
    private volatile Runnable onDisconnect = () -> {};
    private volatile Runnable onReconnected = () -> {};
    private volatile Consumer<String> onConnectionStatus = s -> {};   // "connecting" / "connected" / "retry(3)"

    // Thong tin phien
    public long userId;
    public String fullName;
    public String role;
    public String host;
    public int port;
    public long udpToken;
    public int udpPort = 5001;

    // Luu de dang nhap lai khi reconnect
    private String username;
    private String password;

    private ScheduledExecutorService heartbeat;
    private volatile boolean closed = false;
    private volatile boolean reconnecting = false;

    // ---------- API ----------
    public void connect(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        openSocket();
        startReader();
        startHeartbeat();
    }

    /** Goi ngay sau khi LOGIN thanh cong de reconnect co the tu dang nhap lai. */
    public void rememberCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public void setPushListener(Consumer<Message> listener) {
        this.pushListener = listener == null ? m -> {} : listener;
    }
    public void setOnDisconnect(Runnable r)       { this.onDisconnect = r; }
    public void setOnReconnected(Runnable r)      { this.onReconnected = r; }
    public void setOnConnectionStatus(Consumer<String> c) { this.onConnectionStatus = c; }

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

    /** Tien loi cho Swing: callback chay tren EDT, an toan cham vao UI. */
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

    public void close() {
        closed = true;
        stopHeartbeat();
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    // ---------- noi bo ----------
    private void openSocket() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        socket.setTcpNoDelay(true);
        in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    private void startReader() {
        Thread reader = new Thread(this::readerLoop, "conn-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "conn-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeat.scheduleAtFixedRate(this::sendPing, HEARTBEAT_MS, HEARTBEAT_MS, TimeUnit.MILLISECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeat != null) heartbeat.shutdownNow();
        heartbeat = null;
    }

    private void sendPing() {
        try {
            send(Message.request(MessageType.PING));
        } catch (Exception ignored) {
            // reader se phat hien mat ket noi va lo tiep
        }
    }

    private void readerLoop() {
        try {
            while (true) {
                Message msg = Protocol.readMessage(in);
                String rid = msg.getRequestId();
                CompletableFuture<Message> waiting = (rid == null) ? null : pending.remove(rid);
                if (waiting != null) {
                    waiting.complete(msg);
                } else {
                    Message finalMsg = msg;
                    SwingUtilities.invokeLater(() -> pushListener.accept(finalMsg));
                }
            }
        } catch (IOException e) {
            Log.warn("Conn", null, "Mat ket noi: " + e.getMessage());
        } finally {
            // Huy moi request dang cho
            pending.values().forEach(f -> f.completeExceptionally(new IOException("Ket noi da dong")));
            pending.clear();
            stopHeartbeat();
            if (!closed) tryReconnect();
        }
    }

    private void tryReconnect() {
        if (reconnecting || closed) return;
        if (username == null || password == null) {
            // Chua kip login thanh cong -> khong con thong tin de auto login lai
            SwingUtilities.invokeLater(onDisconnect);
            return;
        }
        reconnecting = true;
        Thread t = new Thread(this::reconnectLoop, "conn-reconnect");
        t.setDaemon(true);
        t.start();
    }

    private void reconnectLoop() {
        long backoff = BACKOFF_START_MS;
        int attempt = 0;
        while (!closed) {
            attempt++;
            final int a = attempt;
            SwingUtilities.invokeLater(() -> onConnectionStatus.accept("Dang thu lai (lan " + a + ")..."));
            try {
                Thread.sleep(backoff);
                openSocket();
                startReader();
                // Dang nhap lai bang tam thoi
                Message login = Message.request(MessageType.LOGIN)
                        .put("username", username)
                        .put("password", password);
                Message resp = send(login).get(5, TimeUnit.SECONDS);
                if (resp.isError()) {
                    Log.warn("Conn", null, "Reconnect login that bai: " + resp.str("message"));
                    try { socket.close(); } catch (IOException ignored) {}
                    // Login sai la ket thuc, khong retry
                    reconnecting = false;
                    SwingUtilities.invokeLater(onDisconnect);
                    return;
                }
                // Cap nhat udpToken moi (server sinh token moi moi lan login)
                udpToken = resp.lng("udpToken");
                startHeartbeat();
                reconnecting = false;
                Log.info("Conn", null, "Da noi lai server sau " + attempt + " lan thu");
                SwingUtilities.invokeLater(() -> {
                    onConnectionStatus.accept("Da noi lai");
                    onReconnected.run();
                });
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                Log.warn("Conn", null, "Reconnect lan " + attempt + " that bai: " + e.getMessage());
                backoff = Math.min(BACKOFF_MAX_MS, backoff * 2);
                // sau vai lan that bai thi bao UI biet la dang co su co
                if (attempt == 3) SwingUtilities.invokeLater(onDisconnect);
            }
        }
        reconnecting = false;
    }
}
