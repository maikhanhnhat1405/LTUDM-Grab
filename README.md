# Delivery App — Level 1 (TCP multi-client + MySQL)

Đồ án client–server đặt xe/giao hàng. Bản này hoàn thành **toàn bộ Level 1**
và đã đặt sẵn nền móng cho Level 2 & 3.

## 1. Cấu trúc

```
src/main/java/com/delivery/
├── common/                 # Dùng chung cho cả server và client
│   ├── Protocol.java       # Đóng khung gói tin [type][length][body]  ← trái tim môn mạng
│   ├── Message.java        # Envelope JSON: type / requestId / timestamp / data
│   ├── MessageType.java    # Toàn bộ tên message + mã lỗi
│   └── Log.java
├── server/
│   ├── ServerMain.java     # ServerSocket + thread pool
│   ├── ClientHandler.java  # 1 thread / 1 client, vòng lặp đọc message
│   ├── ClientSession.java  # 1 kết nối TCP đang mở
│   ├── SessionRegistry.java# userId -> session  (cơ chế đẩy tin realtime)
│   ├── Router.java         # dispatch message + chốt kiểm tra đăng nhập
│   ├── PasswordUtil.java   # SHA-256 + salt
│   ├── service/            # AuthService, OrderService, ChatService
│   ├── db/                 # Database, UserDao, OrderDao, MessageDao
│   └── model/              # User, Order, OrderStatus, ChatMessage, Role
└── client/
    ├── ClientConnection.java   # 1 thread đọc + map requestId -> CompletableFuture
    ├── ClientMain.java
    └── ui/                     # LoginFrame, RegisterDialog, CustomerFrame,
                                # DriverFrame, ChatDialog, OrderTableModel
```

## 2. Chạy

```bash
# 1) Tạo database
mysql -u root -p < db/schema.sql

# 2) Build
mvn clean package

# 3) Chạy server (sửa thông tin DB bằng biến môi trường nếu cần)
export DB_USER=root DB_PASS=your_password
java -cp target/delivery-app-1.0.0-jar-with-dependencies.jar com.delivery.server.ServerMain

# 4) Chạy client (mở nhiều cửa sổ để test multi-client)
java -cp target/delivery-app-1.0.0-jar-with-dependencies.jar com.delivery.client.ClientMain
```

## 3. Kịch bản demo (dùng luôn khi bảo vệ)

1. Mở **3 client**: đăng ký 1 CUSTOMER và 2 DRIVER.
2. Cả 2 driver đăng nhập → customer bấm **Đặt đơn**.
   → Cả 2 màn hình driver **tự động** hiện đơn mới (server PUSH, không phải bấm refresh).
3. Cả 2 driver bấm **Nhận đơn** gần như cùng lúc.
   → Đúng 1 người thắng; người kia nhận `ORDER_ALREADY_TAKEN` và đơn biến mất khỏi danh sách.
   → Customer nhận thông báo đã có tài xế.
4. Driver bấm lần lượt **Đã lấy hàng → Đang giao → Hoàn thành**.
   → Customer thấy trạng thái đổi realtime; thử bấm ngược thứ tự sẽ bị server chặn.
5. Mở **Chat** ở cả hai bên, nhắn qua lại. Tắt client rồi mở lại → lịch sử chat vẫn còn (lưu DB).

## 4. Level 1 đã đủ những gì

| Yêu cầu | Nằm ở đâu |
|---|---|
| Login / Register | `AuthService`, `PasswordUtil` (hash + salt, không lưu mật khẩu thô) |
| Customer / Driver | `Role`, phân quyền trong `Router` + từng service |
| Tạo đơn | `OrderService.create` |
| Driver nhận đơn | `OrderService.accept` + `OrderDao.tryAccept` (UPDATE có điều kiện) |
| TCP multi-client | `ServerMain` (thread pool) + `ClientHandler` (thread/connection) |
| Order status | `OrderStatus` state machine, server kiểm tra transition |
| Chat realtime | `ChatService` + `SessionRegistry.sendTo` |
| Database | `db/schema.sql` + tầng DAO |

## 5. Ba chi tiết nên nhấn mạnh khi bảo vệ

**a. Đóng khung gói tin.** TCP là byte stream, `read()` một lần có thể trả về nửa
gói hoặc hai gói dính nhau. `Protocol` dùng length-prefix + `readFully()` nên
luôn đọc đúng một message — đây là lỗi kinh điển mà đa số bài làm mắc phải.

**b. requestId.** Mọi request mang một id; response mang lại đúng id đó. Nhờ vậy
client phân biệt được đâu là *trả lời cho việc mình vừa hỏi* và đâu là
*server tự đẩy xuống* (PUSH). Không có nó thì không thể vừa gọi API vừa nhận
thông báo realtime trên cùng một socket.

**c. Chống tranh chấp đơn.** Không dùng `synchronized` (chỉ hiệu lực trong 1 JVM),
mà đẩy điều kiện vào chính câu UPDATE:

```sql
UPDATE orders SET driver_id=?, status='ACCEPTED', version=version+1
WHERE id=? AND status='PENDING' AND driver_id IS NULL
```

InnoDB khóa dòng khi update, nên chỉ một transaction thấy `rowsAffected = 1`.
Cách này vẫn đúng kể cả khi sau này chạy nhiều instance server.

## 6. Đã chừa sẵn chỗ cho Level 2 & 3

- `Protocol.FRAME_BINARY` + `writeBinary()` → gửi ảnh / voice.
- Cột `orders.version` → optimistic locking.
- `messages.type` (`TEXT`/`IMAGE`) → chat có ảnh.
- `Database` gói riêng → thay bằng HikariCP chỉ sửa 1 file.
- `SessionRegistry` → nơi gắn cache "driver đang online" và bộ đẩy vị trí GPS.
- `PING`/`PONG` đã có sẵn trong protocol → heartbeat & reconnect.
