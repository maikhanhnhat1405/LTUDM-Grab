package com.delivery.server.db;

import com.delivery.server.model.ChatMessage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageDao {

    public ChatMessage save(long orderId, long senderId, String content, String type) throws SQLException {
        String sql = "INSERT INTO messages(order_id,sender_id,content,type) VALUES (?,?,?,?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, orderId);
            ps.setLong(2, senderId);
            ps.setString(3, content);
            ps.setString(4, type);
            ps.executeUpdate();

            ChatMessage m = new ChatMessage();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                m.id = keys.getLong(1);
            }
            m.orderId = orderId;
            m.senderId = senderId;
            m.content = content;
            m.type = type;
            m.createdAt = new Timestamp(System.currentTimeMillis()).toString();
            return m;
        }
    }

    public List<ChatMessage> listByOrder(long orderId, int limit) throws SQLException {
        String sql = "SELECT m.id,m.order_id,m.sender_id,m.content,m.type,m.created_at,u.full_name " +
                "FROM messages m JOIN users u ON u.id=m.sender_id " +
                "WHERE m.order_id=? ORDER BY m.id ASC LIMIT ?";
        List<ChatMessage> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ChatMessage m = new ChatMessage();
                    m.id = rs.getLong("id");
                    m.orderId = rs.getLong("order_id");
                    m.senderId = rs.getLong("sender_id");
                    m.content = rs.getString("content");
                    m.type = rs.getString("type");
                    Timestamp ts = rs.getTimestamp("created_at");
                    m.createdAt = ts == null ? "" : ts.toString();
                    m.senderName = rs.getString("full_name");
                    list.add(m);
                }
            }
        }
        return list;
    }
}
