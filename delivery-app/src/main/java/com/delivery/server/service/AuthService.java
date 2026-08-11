package com.delivery.server.service;

import com.delivery.common.Log;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.delivery.server.ClientSession;
import com.delivery.server.PasswordUtil;
import com.delivery.server.SessionRegistry;
import com.delivery.server.db.UserDao;
import com.delivery.server.model.Role;
import com.delivery.server.model.User;

import java.sql.SQLException;

public class AuthService {

    private final UserDao userDao = new UserDao();
    private final SessionRegistry registry;

    public AuthService(SessionRegistry registry) { this.registry = registry; }

    public void register(ClientSession s, Message req) {
        String username = req.str("username");
        String password = req.str("password");
        String fullName = req.str("fullName");
        String roleStr  = req.str("role");

        if (isBlank(username) || isBlank(password) || isBlank(fullName) || isBlank(roleStr)) {
            s.send(Message.error(req.getRequestId(), MessageType.ERR_BAD_REQUEST,
                    "Thieu username/password/fullName/role"));
            return;
        }
        try {
            if (userDao.findByUsername(username) != null) {
                s.send(Message.error(req.getRequestId(), MessageType.ERR_USER_EXISTS,
                        "Ten dang nhap da ton tai"));
                return;
            }
            User u = new User();
            u.username = username;
            u.passwordHash = PasswordUtil.hash(password);
            u.fullName = fullName;
            u.phone = req.str("phone");
            u.role = Role.valueOf(roleStr);

            long id = userDao.create(u, req.str("vehicleType"), req.str("plateNumber"));
            u.id = id;
            Log.info("Dang ky moi: " + username + " (" + u.role + ") id=" + id);
            s.send(Message.ok(req.getRequestId()).put("user", u.toJson()));

        } catch (IllegalArgumentException e) {
            s.send(Message.error(req.getRequestId(), MessageType.ERR_BAD_REQUEST, "Role khong hop le"));
        } catch (SQLException e) {
            Log.error("register", e);
            s.send(Message.error(req.getRequestId(), MessageType.ERR_SERVER, "Loi database"));
        }
    }

    public void login(ClientSession s, Message req) {
        String username = req.str("username");
        String password = req.str("password");
        try {
            User u = userDao.findByUsername(username);
            if (u == null || !PasswordUtil.verify(password, u.passwordHash)) {
                s.send(Message.error(req.getRequestId(), MessageType.ERR_LOGIN_FAILED,
                        "Sai tai khoan hoac mat khau"));
                return;
            }
            s.authenticate(u.id, u.fullName, u.role);
            registry.register(s);
            Log.info("Dang nhap: " + s.describe() + " | online=" + registry.onlineCount());
            s.send(Message.ok(req.getRequestId()).put("user", u.toJson()));

        } catch (SQLException e) {
            Log.error("login", e);
            s.send(Message.error(req.getRequestId(), MessageType.ERR_SERVER, "Loi database"));
        }
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}
