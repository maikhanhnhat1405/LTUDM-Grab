package com.delivery.client.ui;

import com.delivery.client.ClientConnection;
import com.delivery.client.GpsSimulator;
import com.delivery.common.GeoUtil;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Man hinh tai xe.
 *
 * Cot trai: don dang cho (nhan don) va don cua toi (day trang thai).
 * Cot phai: khung chat nhung san, bam theo don dang chon o bang "Đơn của tôi".
 * Cac nut trang thai tu bat/tat theo dung state machine cua don, tai xe khong
 * con bam bua roi cho server tra loi loi nua.
 */
public class DriverFrame extends JFrame {

    private final ClientConnection conn;

    private final OrderTableModel pendingModel = new OrderTableModel();
    private final OrderTableModel myModel = new OrderTableModel();
    private final JTable pendingTable = new JTable(pendingModel);
    private final JTable myTable = new JTable(myModel);
    private final JTextArea logArea = new JTextArea(4, 20);

    private final JButton acceptBtn = Theme.primary("Nhận đơn");
    private final JButton pickedBtn = Theme.ghost("Đã lấy hàng");
    private final JButton deliverBtn = Theme.ghost("Đang giao");
    private final JButton doneBtn = Theme.primary("Hoàn thành");

    private final ChatPanel chat;
    private final HeaderBar header;

    // ----- Level 2: bản đồ + GPS -----
    private final MapPanel map = new MapPanel();
    private final JTabbedPane rightTabs = new JTabbedPane();
    private final JButton gpsBtn = Theme.primary("Bật GPS");
    private final JCheckBox dropBox = new JCheckBox("Giả lập mất 30% gói");
    private final JLabel gpsLabel = Theme.muted("GPS: tắt");
    private GpsSimulator gps;
    private double[] currentPickup, currentDropoff;
    private boolean towardPickup = true;
    private long watchingOrderId = -1;

    public DriverFrame(ClientConnection conn) {
        super("Delivery App — Tài xế · " + conn.fullName);
        this.conn = conn;
        this.chat = new ChatPanel(conn);
        this.header = new HeaderBar("Nhận đơn và cập nhật hành trình", conn.fullName, "Tài xế", this::exitApp);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(header, BorderLayout.NORTH);

        JPanel body = Theme.transparent(new BorderLayout(14, 0));
        body.setBorder(new EmptyBorder(14, 16, 16, 16));
        body.add(buildLeft(), BorderLayout.CENTER);

        // Cột phải giữ nguyên khung chat cũ, thêm tab bản đồ bên cạnh.
        rightTabs.setFont(Theme.FONT_BOLD);
        rightTabs.addTab("Trò chuyện", Theme.card("Trò chuyện với khách", chat));
        rightTabs.addTab("Bản đồ", Theme.card("Vị trí của tôi (gửi qua UDP)", buildMapTab()));
        rightTabs.setPreferredSize(new Dimension(UiKit.CHAT_WIDTH, 0));
        body.add(rightTabs, BorderLayout.EAST);

        root.add(body, BorderLayout.CENTER);
        setContentPane(root);

        conn.setPushListener(this::onPush);
        conn.setOnDisconnect(() -> {
            header.setConnected(false);
            log("Mất kết nối tới server");
        });

        initGps();
        UiKit.onClosing(this, this::exitApp);
        UiKit.lockSize(this, UiKit.MAIN);
        loadPending();
        loadMine();
    }

    // ------------------------------------------------------------------
    // Dung giao dien
    // ------------------------------------------------------------------

    private JComponent buildLeft() {
        JPanel tables = Theme.transparent(new GridLayout(2, 1, 0, 14));
        tables.add(Theme.card("Đơn đang chờ", buildPending()));
        tables.add(Theme.card("Đơn của tôi", buildMine()));

        JPanel col = Theme.transparent(new BorderLayout(0, 14));
        col.add(tables, BorderLayout.CENTER);
        col.add(Theme.card("Nhật ký hoạt động", buildLog()), BorderLayout.SOUTH);
        return col;
    }

    private JComponent buildPending() {
        prepare(pendingTable, 60, 110, 200, 200, 110, 0);
        pendingTable.getColumnModel().removeColumn(
                pendingTable.getColumnModel().getColumn(OrderTableModel.COL_DRIVER));  // don cho thi chua co tai xe

        pendingTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                acceptBtn.setEnabled(pendingModel.at(pendingTable.getSelectedRow()) != null);
            }
        });
        acceptBtn.setEnabled(false);
        acceptBtn.addActionListener(e -> acceptOrder());

        JButton reload = Theme.ghost("Làm mới");
        reload.addActionListener(e -> loadPending());

        JPanel actions = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(reload);
        actions.add(acceptBtn);

        JPanel p = Theme.transparent(new BorderLayout(0, 10));
        p.add(Theme.scroll(pendingTable), BorderLayout.CENTER);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    private JComponent buildMine() {
        prepare(myTable, 60, 110, 200, 200, 110, 0);
        myTable.getColumnModel().removeColumn(
                myTable.getColumnModel().getColumn(OrderTableModel.COL_DRIVER));       // tai xe chinh la minh

        myTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) syncSelection();
        });

        pickedBtn.addActionListener(e -> updateStatus("PICKED_UP"));
        deliverBtn.addActionListener(e -> updateStatus("DELIVERING"));
        doneBtn.addActionListener(e -> updateStatus("COMPLETED"));
        syncSelection();

        JPanel actions = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(pickedBtn);
        actions.add(deliverBtn);
        actions.add(doneBtn);

        JPanel p = Theme.transparent(new BorderLayout(0, 10));
        p.add(Theme.scroll(myTable), BorderLayout.CENTER);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    private void prepare(JTable t, int... widths) {
        Theme.styleTable(t);
        t.getColumnModel().getColumn(OrderTableModel.COL_STATUS)
         .setCellRenderer(new Theme.StatusRenderer());
        for (int i = 0; i < widths.length && i < t.getColumnCount(); i++) {
            if (widths[i] > 0) t.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private JComponent buildLog() {
        logArea.setEditable(false);
        logArea.setFont(Theme.SMALL);
        logArea.setForeground(Theme.MUTED);
        logArea.setBackground(Theme.CARD);
        logArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane sp = Theme.scroll(logArea);
        sp.setPreferredSize(new Dimension(0, 66));
        return sp;
    }

    private JComponent buildMapTab() {
        gpsBtn.addActionListener(e -> toggleGps());
        dropBox.setOpaque(false);
        dropBox.setFont(Theme.SMALL);
        dropBox.setForeground(Theme.MUTED);
        dropBox.addActionListener(e -> {
            if (gps != null) gps.setDropRate(dropBox.isSelected() ? 30 : 0);
        });

        JPanel bar = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.add(gpsBtn);
        bar.add(dropBox);

        JPanel south = Theme.transparent(new BorderLayout(0, 6));
        south.add(bar, BorderLayout.NORTH);
        south.add(gpsLabel, BorderLayout.SOUTH);

        JPanel p = Theme.transparent(new BorderLayout(0, 10));
        p.add(map, BorderLayout.CENTER);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    // ------------------------------------------------------------------
    // GPS (Level 2)
    // ------------------------------------------------------------------

    private void initGps() {
        try {
            gps = new GpsSimulator(conn.host, conn.udpPort, conn.userId, conn.udpToken);
            gps.setOnTick(sim -> SwingUtilities.invokeLater(() -> onGpsTick(sim)));
            map.setDriver(gps.lat(), gps.lng());
            log("Sẵn sàng bắn GPS tới " + conn.host + ":" + conn.udpPort
                    + " (mỗi " + GpsSimulator.INTERVAL_MS / 1000 + "s một gói)");
        } catch (Exception e) {
            log("Không tạo được socket UDP: " + e.getMessage());
            gpsBtn.setEnabled(false);
        }
    }

    private void toggleGps() {
        if (gps == null) return;
        if (gps.isRunning()) {
            gps.stop();
            gpsBtn.setText("Bật GPS");
            gpsLabel.setText("GPS: tắt");
        } else {
            gps.start();
            gpsBtn.setText("Tắt GPS");
        }
    }

    /** Chạy sau mỗi lần giả lập bắn xong một gói UDP. */
    private void onGpsTick(GpsSimulator sim) {
        map.setDriver(sim.lat(), sim.lng());
        gpsLabel.setText(String.format("Đã gửi %d gói · mất %d gói",
                sim.sentCount(), sim.droppedCount()));

        // Tới điểm lấy rồi thì tự đổi đích sang điểm giao
        if (towardPickup && currentDropoff != null
                && sim.distanceToTarget() >= 0 && sim.distanceToTarget() < 30) {
            towardPickup = false;
            sim.setTarget(currentDropoff[0], currentDropoff[1]);
            log("Đã tới điểm lấy, chuyển hướng về điểm giao");
        }
    }

    /** Nạp lộ trình của một đơn vào bản đồ và vào bộ giả lập GPS. */
    private void routeFrom(JsonObject o) {
        if (o == null) return;
        long id = o.get("id").getAsLong();
        boolean changed = id != watchingOrderId;
        watchingOrderId = id;

        currentPickup  = new double[]{o.get("pickupLat").getAsDouble(),  o.get("pickupLng").getAsDouble()};
        currentDropoff = new double[]{o.get("dropoffLat").getAsDouble(), o.get("dropoffLng").getAsDouble()};

        if (changed) {
            towardPickup = true;
            map.clearTrail();
            map.setPickup(currentPickup[0], currentPickup[1]);
            map.setDropoff(currentDropoff[0], currentDropoff[1]);
            if (gps != null) {
                gps.setTarget(currentPickup[0], currentPickup[1]);
                map.setDriver(gps.lat(), gps.lng());
                log(String.format("Lộ trình đơn #%d: còn %s tới điểm lấy", id,
                        GeoUtil.formatDistance(gps.distanceToTarget())));
            }
        }
        map.setStatusText("Đơn #" + id + " · " + Theme.statusLabel(o.get("status").getAsString()));
    }

    // ------------------------------------------------------------------
    // Nghiep vu
    // ------------------------------------------------------------------

    /** Bat/tat nut theo dung state machine + doi khung chat sang don dang chon. */
    private void syncSelection() {
        JsonObject o = myModel.at(myTable.getSelectedRow());
        String status = o == null ? null : o.get("status").getAsString();

        pickedBtn.setEnabled("ACCEPTED".equals(status));
        deliverBtn.setEnabled("PICKED_UP".equals(status));
        doneBtn.setEnabled("DELIVERING".equals(status));

        chat.showOrder(o, o == null ? null : "Khách hàng #" + o.get("customerId").getAsLong());
        routeFrom(o);
    }

    private void updateStatus(String status) {
        JsonObject o = myModel.at(myTable.getSelectedRow());
        if (o == null) return;
        long id = o.get("id").getAsLong();

        conn.request(Message.request(MessageType.ORDER_UPDATE_STATUS)
                        .put("orderId", id)
                        .put("status", status),
                resp -> {
                    myModel.upsert(resp.getData().getAsJsonObject("order"));
                    syncSelection();
                    log("Đơn #" + id + " → " + Theme.statusLabel(status));
                    if ("COMPLETED".equals(status) && gps != null) {
                        gps.setTarget(null, null);
                        log("Đã hoàn thành, ngừng bám theo lộ trình");
                    }
                },
                err -> log("Không đổi được trạng thái: " + err.str("message")));
    }

    private void acceptOrder() {
        JsonObject o = pendingModel.at(pendingTable.getSelectedRow());
        if (o == null) return;
        long orderId = o.get("id").getAsLong();

        conn.request(Message.request(MessageType.ORDER_ACCEPT).put("orderId", orderId),
                resp -> {
                    JsonObject order = resp.getData().getAsJsonObject("order");
                    pendingModel.removeById(orderId);
                    myModel.upsert(order);
                    select(myTable, myModel, orderId);
                    log("Đã nhận đơn #" + orderId);
                    routeFrom(order);
                    if (gps != null && !gps.isRunning()) {
                        toggleGps();                 // nhận đơn là tự động bật GPS
                        log("Tự động bật GPS");
                    }
                },
                err -> {
                    // Truong hop kinh dien: 2 tai xe bam cung luc, ta la nguoi thua
                    pendingModel.removeById(orderId);
                    log("Không nhận được đơn #" + orderId + ": " + err.str("message"));
                });
    }

    private void select(JTable t, OrderTableModel m, long orderId) {
        int row = m.rowOf(orderId);
        if (row >= 0) t.setRowSelectionInterval(row, row);
    }

    private void loadPending() {
        conn.request(Message.request(MessageType.ORDER_LIST_PENDING),
                resp -> pendingModel.setAll(resp.getData().getAsJsonArray("orders")),
                err -> log("Lỗi tải đơn chờ: " + err.str("message")));
    }

    private void loadMine() {
        conn.request(Message.request(MessageType.ORDER_LIST_MINE),
                resp -> {
                    myModel.setAll(resp.getData().getAsJsonArray("orders"));
                    if (myModel.getRowCount() > 0 && myTable.getSelectedRow() < 0) {
                        myTable.setRowSelectionInterval(0, 0);
                    }
                },
                err -> log("Lỗi tải đơn của tôi: " + err.str("message")));
    }

    private void onPush(Message push) {
        switch (push.getType()) {
            case MessageType.PUSH_NEW_ORDER: {
                JsonObject o = push.getData().getAsJsonObject("order");
                pendingModel.upsert(o);
                log("CÓ ĐƠN MỚI #" + o.get("id").getAsLong() + ": " +
                        o.get("pickupAddr").getAsString() + " → " + o.get("dropoffAddr").getAsString());
                break;
            }
            case MessageType.PUSH_ORDER_TAKEN: {
                long id = push.lng("orderId");
                pendingModel.removeById(id);
                log("Đơn #" + id + " đã có tài xế khác nhận");
                break;
            }
            case MessageType.PUSH_ORDER_STATUS: {
                JsonObject o = push.getData().getAsJsonObject("order");
                myModel.upsert(o);
                if (chat.currentOrderId() == o.get("id").getAsLong()) syncSelection();
                log("Đơn #" + o.get("id").getAsLong() + " → " + Theme.statusLabel(o.get("status").getAsString()));
                break;
            }
            case MessageType.PUSH_CHAT_MESSAGE: {
                JsonObject m = push.getData().getAsJsonObject("message");
                if (!chat.handlePush(m)) {
                    log("Tin nhắn mới (đơn #" + m.get("orderId").getAsLong() + "): " + m.get("content").getAsString());
                }
                break;
            }
            default:
                log("PUSH: " + push.getType());
        }
    }

    private void log(String s) {
        logArea.append(s + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void exitApp() {
        if (!UiKit.confirm(this, "Thoát ứng dụng?")) return;
        conn.close();
        dispose();
        System.exit(0);
    }
}
