package com.delivery.server.db;

import com.delivery.server.model.Order;
import com.delivery.server.model.OrderStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderDao {

    public long create(Order o) throws SQLException {
        String sql = "INSERT INTO orders(customer_id,pickup_addr,pickup_lat,pickup_lng," +
                "dropoff_addr,dropoff_lat,dropoff_lng,note,price,status) VALUES (?,?,?,?,?,?,?,?,?, 'PENDING')";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, new String[]{"id"})) {
            ps.setLong(1, o.customerId);
            ps.setString(2, o.pickupAddr);
            ps.setDouble(3, o.pickupLat);
            ps.setDouble(4, o.pickupLng);
            ps.setString(5, o.dropoffAddr);
            ps.setDouble(6, o.dropoffLat);
            ps.setDouble(7, o.dropoffLng);
            ps.setString(8, o.note);
            ps.setDouble(9, o.price);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public Order findById(long id) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_BASE + " WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public List<Order> listPending(int limit) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     SELECT_BASE + " WHERE status='PENDING' ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            return mapAll(ps);
        }
    }

    /** Danh sach don cua 1 user, bat ke user do la khach hay tai xe. */
    public List<Order> listByUser(long userId, int limit) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     SELECT_BASE + " WHERE customer_id=? OR driver_id=? ORDER BY id DESC LIMIT ?")) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ps.setInt(3, limit);
            return mapAll(ps);
        }
    }

    /**
     * DIEM MAU CHOT chong race condition khi nhieu driver bam nhan cung 1 don.
     *
     * Dieu kien "status='PENDING' AND driver_id IS NULL" nam NGAY TRONG cau UPDATE,
     * nen InnoDB khoa dong khi update: chi dung 1 transaction thang, cac transaction
     * con lai thay rowsAffected = 0.
     *
     * @return true neu gianh duoc don, false neu driver khac da nhan truoc.
     */
    public boolean tryAccept(long orderId, long driverId) throws SQLException {
        String sql = "UPDATE orders SET driver_id=?, status='ACCEPTED', version=version+1, updated_at=CURRENT_TIMESTAMP " +
                     "WHERE id=? AND status='PENDING' AND driver_id IS NULL";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, driverId);
            ps.setLong(2, orderId);
            return ps.executeUpdate() == 1;
        }
    }

    /** Update co dieu kien trang thai cu -> tranh 2 lenh update dam nhau. */
    public boolean updateStatus(long orderId, OrderStatus expected, OrderStatus next) throws SQLException {
        String sql = "UPDATE orders SET status=?, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE id=? AND status=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, next.name());
            ps.setLong(2, orderId);
            ps.setString(3, expected.name());
            return ps.executeUpdate() == 1;
        }
    }

    private static final String SELECT_BASE =
            "SELECT id,customer_id,driver_id,pickup_addr,pickup_lat,pickup_lng," +
            "dropoff_addr,dropoff_lat,dropoff_lng,note,price,status,version,created_at FROM orders";

    private List<Order> mapAll(PreparedStatement ps) throws SQLException {
        List<Order> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    private Order map(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.id = rs.getLong("id");
        o.customerId = rs.getLong("customer_id");
        long d = rs.getLong("driver_id");
        o.driverId = rs.wasNull() ? null : d;
        o.pickupAddr = rs.getString("pickup_addr");
        o.pickupLat = rs.getDouble("pickup_lat");
        o.pickupLng = rs.getDouble("pickup_lng");
        o.dropoffAddr = rs.getString("dropoff_addr");
        o.dropoffLat = rs.getDouble("dropoff_lat");
        o.dropoffLng = rs.getDouble("dropoff_lng");
        o.note = rs.getString("note");
        o.price = rs.getDouble("price");
        o.status = OrderStatus.valueOf(rs.getString("status"));
        o.version = rs.getInt("version");
        Timestamp ts = rs.getTimestamp("created_at");
        o.createdAt = ts == null ? "" : ts.toString();
        return o;
    }
}
