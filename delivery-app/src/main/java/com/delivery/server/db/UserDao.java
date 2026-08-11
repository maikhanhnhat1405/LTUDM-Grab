package com.delivery.server.db;

import com.delivery.server.model.Role;
import com.delivery.server.model.User;

import java.sql.*;

public class UserDao {

    public User findByUsername(String username) throws SQLException {
        String sql = "SELECT id,username,password_hash,full_name,phone,role FROM users WHERE username=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public User findById(long id) throws SQLException {
        String sql = "SELECT id,username,password_hash,full_name,phone,role FROM users WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Tao user; neu la DRIVER thi tao them ban ghi trong bang drivers (cung 1 transaction). */
    public long create(User u, String vehicleType, String plateNumber) throws SQLException {
        String sql = "INSERT INTO users(username,password_hash,full_name,phone,role) VALUES (?,?,?,?,?)";
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = c.prepareStatement(sql, new String[]{"id"})) {
                    ps.setString(1, u.username);
                    ps.setString(2, u.passwordHash);
                    ps.setString(3, u.fullName);
                    ps.setString(4, u.phone);
                    ps.setString(5, u.role.name());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getLong(1);
                    }
                }
                if (u.role == Role.DRIVER) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO drivers(user_id,vehicle_type,plate_number) VALUES (?,?,?)")) {
                        ps.setLong(1, id);
                        ps.setString(2, vehicleType == null ? "BIKE" : vehicleType);
                        ps.setString(3, plateNumber);
                        ps.executeUpdate();
                    }
                }
                c.commit();
                return id;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.id = rs.getLong("id");
        u.username = rs.getString("username");
        u.passwordHash = rs.getString("password_hash");
        u.fullName = rs.getString("full_name");
        u.phone = rs.getString("phone");
        u.role = Role.valueOf(rs.getString("role"));
        return u;
    }
}
