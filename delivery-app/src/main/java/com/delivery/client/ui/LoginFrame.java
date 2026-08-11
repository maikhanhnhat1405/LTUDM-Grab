package com.delivery.client.ui;

import com.delivery.client.ClientConnection;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;

public class LoginFrame extends JFrame {

    private final JTextField hostField = new JTextField("localhost", 10);
    private final JTextField portField = new JTextField("5000", 5);
    private final JTextField userField = new JTextField(14);
    private final JPasswordField passField = new JPasswordField(14);
    private final JLabel statusLabel = new JLabel(" ");

    public LoginFrame() {
        super("Delivery App - Dang nhap");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(form, g, row++, "Server:", panelOf(hostField, new JLabel(":"), portField));
        addRow(form, g, row++, "Tai khoan:", userField);
        addRow(form, g, row++, "Mat khau:", passField);

        JButton loginBtn = new JButton("Dang nhap");
        JButton regBtn = new JButton("Dang ky");
        loginBtn.addActionListener(e -> doLogin());
        regBtn.addActionListener(e -> new RegisterDialog(this).setVisible(true));
        getRootPane().setDefaultButton(loginBtn);

        JPanel buttons = new JPanel();
        buttons.add(loginBtn);
        buttons.add(regBtn);

        statusLabel.setForeground(new Color(180, 30, 30));

        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        add(statusLabel, BorderLayout.NORTH);

        pack();
        setLocationRelativeTo(null);
        setResizable(false);
    }

    String host() { return hostField.getText().trim(); }
    int port() { return Integer.parseInt(portField.getText().trim()); }

    private void doLogin() {
        String username = userField.getText().trim();
        String password = new String(passField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Nhap day du tai khoan va mat khau");
            return;
        }
        statusLabel.setText("Dang ket noi...");

        ClientConnection conn = new ClientConnection();
        try {
            conn.connect(host(), port());
        } catch (Exception ex) {
            statusLabel.setText("Khong ket noi duoc server: " + ex.getMessage());
            return;
        }

        Message req = Message.request(MessageType.LOGIN)
                .put("username", username)
                .put("password", password);

        conn.request(req,
                resp -> {
                    JsonObject u = resp.getData().getAsJsonObject("user");
                    conn.userId = u.get("id").getAsLong();
                    conn.fullName = u.get("fullName").getAsString();
                    conn.role = u.get("role").getAsString();

                    JFrame next;
                    switch (conn.role) {
                        case "CUSTOMER": next = new CustomerFrame(conn); break;
                        case "DRIVER":   next = new DriverFrame(conn);   break;
                        default:
                            JOptionPane.showMessageDialog(this,
                                    "Man hinh ADMIN se lam o Level 2.");
                            conn.close();
                            return;
                    }
                    next.setVisible(true);
                    dispose();
                },
                err -> {
                    statusLabel.setText("Dang nhap that bai: " + err.str("message"));
                    conn.close();
                });
    }

    private JPanel panelOf(Component... comps) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        for (Component c : comps) p.add(c);
        return p;
    }

    private void addRow(JPanel form, GridBagConstraints g, int row, String label, Component field) {
        g.gridx = 0; g.gridy = row;
        form.add(new JLabel(label), g);
        g.gridx = 1;
        form.add(field, g);
    }
}
