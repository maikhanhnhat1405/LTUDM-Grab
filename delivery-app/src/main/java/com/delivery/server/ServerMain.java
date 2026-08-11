package com.delivery.server;

import com.delivery.common.Log;
import com.delivery.server.db.Database;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Diem khoi dong server.
 *
 * Mo hinh: 1 thread accept + thread pool xu ly client (thread-per-connection).
 * Voi quy mo do an (vai chuc client) day la lua chon dung: don gian, de debug.
 * Neu can hang nghin ket noi thi moi phai chuyen sang NIO/Netty - nen ghi
 * dieu nay vao bao cao phan "danh gia & huong phat trien".
 */
public class ServerMain {

    public static final int DEFAULT_PORT = 5000;

    /** Cong lay tu tham so dong lenh, roi den bien moi truong PORT, cuoi cung la mac dinh. */
    private static int resolvePort(String[] args) {
        String v = args.length > 0 ? args[0] : System.getenv("PORT");
        try {
            return (v == null || v.isBlank()) ? DEFAULT_PORT : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            Log.warn("Cong khong hop le: " + v + " -> dung " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        Database.init();

        SessionRegistry registry = new SessionRegistry();
        Router router = new Router(registry);

        // Thread pool co gioi han -> tranh client rac lam server tao vo han thread
        ExecutorService pool = Executors.newFixedThreadPool(200);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            Log.info("TCP server dang lang nghe tai cong " + port);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Log.info("Dang tat server...");
                pool.shutdownNow();
            }));

            while (true) {
                Socket socket = serverSocket.accept();      // block cho client moi
                socket.setTcpNoDelay(true);                 // tat Nagle -> chat/GPS bot tre
                try {
                    ClientSession session = new ClientSession(socket);
                    pool.execute(new ClientHandler(session, registry, router));
                } catch (IOException e) {
                    Log.error("Khong tao duoc session", e);
                    socket.close();
                }
            }
        }
    }
}
