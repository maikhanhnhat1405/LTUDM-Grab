# Delivery App — Level 1 (TCP multi-client + PostgreSQL)

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
# 1) Tạo database (PostgreSQL)
createdb delivery_app
psql -d delivery_app -f db/schema-postgres.sql

# 2) Build
mvn clean package

# 3) Chạy server (sửa thông tin DB bằng biến môi trường nếu cần)
export DB_URL=jdbc:postgresql://localhost:5432/delivery_app
export DB_USER=postgres DB_PASS=your_password
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
| Database | `db/schema-postgres.sql` + tầng DAO |

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

PostgreSQL khóa dòng khi update, transaction thứ hai phải chờ rồi đọc lại điều kiện,
thấy `status` đã đổi nên không khớp `WHERE` → `rowsAffected = 0`.
Cách này vẫn đúng kể cả khi sau này chạy nhiều instance server.

## 6. Đã chừa sẵn chỗ cho Level 2 & 3

- `Protocol.FRAME_BINARY` + `writeBinary()` → gửi ảnh / voice.
- Cột `orders.version` → optimistic locking.
- `messages.type` (`TEXT`/`IMAGE`) → chat có ảnh.
- `Database` gói riêng → thay bằng HikariCP chỉ sửa 1 file.
- Còn giữ `db/schema.sql` bản MySQL nếu cần đổi hệ quản trị.
- `SessionRegistry` → nơi gắn cache "driver đang online" và bộ đẩy vị trí GPS.
- `PING`/`PONG` đã có sẵn trong protocol → heartbeat & reconnect.


---

# Level 2 — phần 1: GPS realtime qua UDP

## Gói tin UDP (45 byte, nhị phân cố định)

```
byte 0      version   (1)
byte 1-8    driverId  (long)
byte 9-16   token     (long)   ← server cấp lúc login qua TCP
byte 17-24  timestamp (long)
byte 25-32  lat       (double)
byte 33-40  lng       (double)
byte 41-44  seq       (int)
```

Không dùng JSON như bên TCP: mỗi tài xế bắn 1 gói/2 giây, JSON tốn băng thông
gấp ~4 lần và phải parse text. Gói cố định kích thước nên đọc thẳng bằng
`ByteBuffer`, và **không cần framing** — UDP là datagram, mỗi gói đã là một đơn
vị trọn vẹn, khác hẳn TCP stream.

## Đường đi của dữ liệu

```
Driver ──UDP:5001──> UdpServer ──> LocationService ──> LocationCache (RAM)
                                          │
                                          ├─ ActiveTripRegistry: driverId → (orderId, customerId)
                                          │
                                          └──TCP:5000 push──> Customer
```

## Ba cửa ải mỗi gói phải qua (`LocationService.onPacket`)

**1. Xác thực token.** UDP không có kết nối — ai cũng gửi được gói giả mạo
`driverId` bất kỳ tới cổng 5001. Server cấp token ngẫu nhiên lúc đăng nhập qua
TCP (kênh đã xác thực), tài xế nhét vào mỗi gói UDP. Sai token thì **bỏ im
lặng**, không trả lời — trả lời là mở đường cho tấn công khuếch đại.

**2. Kiểm tra `seq`.** UDP không đảm bảo thứ tự. Gói gửi lúc 10:00:00 có thể
tới sau gói 10:00:02. Ghi đè mù thì chấm tài xế nhảy giật lùi.

**3. Có chuyến đang chạy không.** `ActiveTripRegistry` trả lời ngay trong RAM.
Query DB cho từng gói thì 100 tài xế = 50 query/giây chỉ để hỏi một câu không
bao giờ đổi.

## Vì sao chiều về lại là TCP

Driver → server dùng UDP: mất gói không sao, 2 giây sau có gói mới, cần độ trễ
thấp hơn là độ tin cậy. Server → customer **bắt buộc TCP**: khách đang nhìn xe
chạy, mất gói là màn hình đứng. Hệ thống dùng đúng giao thức cho đặc tính của
từng chiều, không phải chọn bừa một cái.

## Vị trí không ghi database

100 tài xế × 1 gói/2 giây = 4,3 triệu bản ghi/ngày, trong khi chỉ bản ghi **mới
nhất** có giá trị. `LocationCache` giữ trong RAM. Cần lưu vết hành trình thì ghi
mẫu thưa (30 giây/lần) vào bảng riêng.

## Giao diện

Giữ nguyên toàn bộ giao diện cũ (`Theme`, `UiKit`, `HeaderBar`, `ChatPanel`).
Cột phải của màn khách hàng và màn tài xế đổi thành **tab**: tab *Trò chuyện*
là khung chat cũ y nguyên, thêm tab *Bản đồ*.

- **Khách hàng**: tab Bản đồ có nút *Chọn điểm lấy* / *Chọn điểm giao*, bấm lên
  bản đồ để đặt toạ độ (sửa luôn lỗi toạ độ bằng 0 ở Level 1). Chọn đơn nào
  trong bảng thì bản đồ vẽ lộ trình đơn đó và chấm tài xế chạy realtime.
- **Tài xế**: nhận đơn là GPS tự bật, chấm bò từ vị trí hiện tại về điểm lấy,
  tới nơi tự quay đầu về điểm giao. Có ô **Giả lập mất 30% gói** để demo.

`MapPanel` vẽ toạ độ lên lưới thay vì tải tile thật — tách bạch khi debug: chấm
không nhúc nhích thì lỗi ở đường truyền UDP chứ không phải thư viện bản đồ. Sau
này thay bằng JXMapViewer2 (`org.jxmapviewer:jxmapviewer2:2.8`, tile
OpenStreetMap, không cần API key) mà không phải động tới phần mạng.


---

# Level 3 (đợt 1): Event Queue + Idempotency + Reconnect + Heartbeat + Logging file

## Event Queue

Trước: `OrderService.create` sau khi ghi DB xong tự gọi `registry.broadcast(...)`.
Nay: nó chỉ `eventBus.publish(new OrderCreatedEvent(o))` rồi return.
`NotificationListener` nghe event đó và lo phần đẩy PUSH. `AuditListener` cũng nghe
cùng event để ghi log audit — thêm listener mới không phải sửa OrderService.

```
publish  ─→  BlockingQueue  ─→  N worker  ─→  NotificationListener
                                           └─  AuditListener
                                           └─  (Analytics, Metrics, ... — thêm ở đây)
```

- Queue có giới hạn 10.000 event, đầy thì bỏ và log cảnh báo (mất thông báo còn
  hơn treo request).
- Listener nem exception không làm chết worker và không ảnh hưởng listener khác.
- Đây là in-process (không phải Kafka). Với đồ án là đủ; sau này thay bằng broker
  chỉ phải sửa nội bộ `EventBus`.

## Idempotency (chống retry tạo đơn trùng)

Khi retry (client), server (`Router`) kiểm `requestId`:
- Đã xử lý → trả lại response cũ, không chạy lại logic.
- Chưa → xử lý, ghi vào `IdempotencyCache`.

Chỉ áp dụng cho lệnh **ghi** thực sự (`ORDER_CREATE`, `CHAT_SEND`). Các lệnh còn
lại vốn đã idempotent nhờ điều kiện trong SQL: `ORDER_ACCEPT` retry lần 2 sẽ trả
`ORDER_ALREADY_TAKEN` (điều kiện `AND status='PENDING'`), `ORDER_UPDATE_STATUS`
retry sẽ trả "trạng thái đã đổi" (`AND status=?`).

Cách chặn ở tầng Router: wrap `ClientSession` bằng `RecordingClientSession`, bắt
lại response đầu tiên, các PUSH sau đó không bị cache.

## Heartbeat + reconnect

**Client** (`ClientConnection`):
- PING mỗi 20 giây.
- Mất kết nối → không báo lỗi UI ngay. Reconnect với exponential backoff
  (1s → 2s → 4s → 8s, trần 30s). Đăng nhập lại tự động bằng credentials nhớ tạm.
- Sau 3 lần thất bại mới gọi `onDisconnect` để header hiện đỏ.
- Khi nối lại: gọi `onReconnected` để UI làm mới danh sách.

**Server** (`ClientHandler`): đặt `soTimeout` 60 giây trên socket. Không nhận gì
trong 60s (3 chu kỳ PING liên tiếp bị mất) → coi là zombie, đóng.

Kịch bản demo: đang chạy client, `Ctrl+C` server, đợi 3 giây rồi bật lại server
— header client chuyển "Đang thử lại (lần 2)..." rồi "Đã nối lại", không cần
đăng nhập lại.

## Logging file

`Log` giờ ghi song song ra `logs/server-YYYY-MM-DD.log`, rolling theo ngày.
Thêm tham số `tag` và `requestId` để trace một yêu cầu xuyên suốt các service:

```
21:33:14.005 [INFO ] [main            ] [Order         ] [req=abc12345] Da tao don #17
21:33:14.006 [INFO ] [event-worker-1  ] [Notify        ] [req=abc12345] Bao don #17 cho 2 tai xe
21:33:14.007 [INFO ] [event-worker-2  ] [Audit         ] [req=abc12345] ORDER_CREATED id=17 customer=3
```

Nhìn `req=abc12345` là trace được toàn bộ vòng đời của request. Không cần
Logback/SLF4J — 100 dòng code Java, đủ cho quy mô đồ án.

## Tổng kết Level 3 đợt 1

| Yêu cầu đề bài | Trạng thái | Ở đâu |
|---|---|---|
| Cache | ✓ (từ L2) | `LocationCache`, `ActiveTripRegistry`, `SessionRegistry` |
| Queue / Event | ✓ | `event/`, `listener/` |
| Chống race | ✓ (từ L1) | `OrderDao.tryAccept` — UPDATE có điều kiện |
| Retry / reconnect khi mất mạng | ✓ | `ClientConnection` + `ClientHandler` heartbeat |
| Idempotency | ✓ (bổ trợ retry) | `IdempotencyCache`, `Router` |
| Logging | ✓ | `Log` — rolling file + tag + requestId |
| Tách service | Sắp | LocationService là ứng viên tách ra process riêng |
| AI support | Sắp | Sau khi chốt hướng với thầy |
