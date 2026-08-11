package com.delivery.server;

import com.delivery.common.Log;
import com.delivery.common.Message;
import com.delivery.common.Protocol;
import com.delivery.server.model.Role;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Dai dien 1 ket noi TCP dang mo. Moi client = 1 session = 1 thread doc rieng.
 * Doi tuong nay la thu duy nhat cho phep server "goi nguoc" ve client.
 */
public class ClientSession {

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    // Gan sau khi dang nhap thanh cong
    private volatile long userId = -1;
    private volatile String fullName;
    private volatile Role role;
    private volatile long udpToken;   // cap luc dang nhap, dung xac thuc goi UDP

    public ClientSession(Socket socket) throws IOException {
        this.socket = socket;
        this.in  = new DataInputStream(new java.io.BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new java.io.BufferedOutputStream(socket.getOutputStream()));
    }

    public DataInputStream in() { return in; }

    public void send(Message msg) {
        try {
            Protocol.writeMessage(out, msg);
        } catch (IOException e) {
            Log.warn("Gui that bai cho " + describe() + ": " + e.getMessage());
            close();
        }
    }

    public boolean isAuthenticated() { return userId > 0; }

    public void authenticate(long userId, String fullName, Role role, long udpToken) {
        this.userId = userId;
        this.fullName = fullName;
        this.role = role;
        this.udpToken = udpToken;
    }

    public long udpToken() { return udpToken; }

    public long userId()     { return userId; }
    public String fullName() { return fullName; }
    public Role role()       { return role; }

    public String describe() {
        return (isAuthenticated() ? userId + "/" + fullName + "/" + role : "guest")
                + "@" + socket.getRemoteSocketAddress();
    }

    public void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }
}
