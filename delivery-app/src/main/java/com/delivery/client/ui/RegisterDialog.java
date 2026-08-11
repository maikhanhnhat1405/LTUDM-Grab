package com.delivery.client.ui;

import com.delivery.client.ClientConnection;
import com.delivery.common.Message;
import com.delivery.common.MessageType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class RegisterDialog extends JDialog {

    private final JTextField userField = Theme.field(16);
    private final JPasswordField passField = Theme.password(16);
    private final JTextField nameField = Theme.field(16);
    private final JTextField phoneField = Theme.field(16);
    private final JComboBox<String> roleBox = new JComboBox<>(new String[]{"Khách hàng", "Tài xế"});
    private final JTextField plateField = Theme.field(12);
    private final JLabel status = new JLabel(" ");

    private final JButton okBtn = Theme.primary("Tạo tài khoản");
    private final JButton cancelBtn = Theme.ghost("Huỷ");

    /** Ket noi tam de goi REGISTER. Phai dong lai du dang ky thanh cong hay khong. */
    private ClientConnection conn;
    private boolean closed;

    public RegisterDialog(LoginFrame owner) {
        super(owner, "Đăng ký tài khoản", true);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(20, 24, 20, 24));

        root.add(Theme.h1("Tạo tài khoản"), BorderLayout.NORTH);

        JPanel fields = Theme.transparent(new GridLayout(0, 1, 0, 10));
        fields.add(Theme.labeledField("TÀI KHOẢN", userField));
        fields.add(Theme.labeledField("MẬT KHẨU", passField));
        fields.add(Theme.labeledField("HỌ TÊN", nameField));
        fields.add(Theme.labeledField("SỐ ĐIỆN THOẠI", phoneField));
        fields.add(Theme.labeledField("VAI TRÒ", roleBox));
        fields.add(Theme.labeledField("BIỂN SỐ (CHỈ TÀI XẾ)", plateField));
        root.add(fields, BorderLayout.CENTER);

        roleBox.setFont(Theme.FONT);
        roleBox.addActionListener(e -> syncPlateField());
        syncPlateField();

        okBtn.addActionListener(e -> doRegister(owner));
        cancelBtn.addActionListener(e -> cancel());
        okBtn.setPreferredSize(new Dimension(0, 36));
        cancelBtn.setPreferredSize(new Dimension(0, 32));
        getRootPane().setDefaultButton(okBtn);

        JPanel buttons = Theme.transparent(new GridLayout(2, 1, 0, 8));
        buttons.add(okBtn);
        buttons.add(cancelBtn);

        status.setFont(Theme.SMALL);
        status.setForeground(Theme.DANGER);
        status.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel south = Theme.transparent(new BorderLayout(0, 8));
        south.add(buttons, BorderLayout.NORTH);
        south.add(status, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);

        // Dong dialog dang ky = huy bo: dong ket noi tam, khong dung toi LoginFrame.
        UiKit.onClosing(this, this::cancel);
        UiKit.bindEscape(getRootPane(), this::cancel);
        UiKit.lockSize(this, UiKit.REGISTER);
    }

    /** Ma vai tro gui len server, tach khoi nhan hien thi tieng Viet. */
    private String selectedRole() {
        return roleBox.getSelectedIndex() == 1 ? "DRIVER" : "CUSTOMER";
    }

    private void syncPlateField() {
        boolean driver = "DRIVER".equals(selectedRole());
        plateField.setEnabled(driver);
        plateField.setBackground(driver ? Theme.CARD : new Color(0xF1F3F5));
        if (!driver) plateField.setText("");
    }

    /** Dong theo huong huy bo - dung cho nut Huy, phim ESC va nut X. */
    private void cancel() {
        closed = true;
        if (conn != null) { conn.close(); conn = null; }
        dispose();
    }

    private void doRegister(LoginFrame owner) {
        if (conn != null) return;               // dang co request bay -> bo qua cu bam them

        String username = userField.getText().trim();
        String password = new String(passField.getPassword());
        String fullName = nameField.getText().trim();
        String phone    = phoneField.getText().trim();
        String role     = selectedRole();
        String plate    = plateField.getText().trim();

        if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            status.setText("Tài khoản, mật khẩu và họ tên không được để trống");
            return;
        }
        if ("DRIVER".equals(role) && plate.isEmpty()) {
            status.setText("Tài xế phải nhập biển số");
            return;
        }
        if (owner.port() <= 0) {
            status.setText("Cổng ở màn hình đăng nhập không hợp lệ");
            return;
        }

        ClientConnection c = new ClientConnection();
        try {
            c.connect(owner.host(), owner.port());
        } catch (Exception ex) {
            status.setText("Không kết nối được: " + ex.getMessage());
            return;
        }
        conn = c;
        setBusy(true);
        status.setForeground(Theme.MUTED);
        status.setText("Đang gửi đăng ký...");

        c.request(Message.request(MessageType.REGISTER)
                        .put("username", username)
                        .put("password", password)
                        .put("fullName", fullName)
                        .put("phone", phone)
                        .put("role", role)
                        .put("plateNumber", plate),
                resp -> {
                    if (closed) return;         // user da tat form truoc khi response ve
                    c.close();
                    conn = null;
                    closed = true;
                    JOptionPane.showMessageDialog(this, "Đăng ký thành công, mời đăng nhập.");
                    dispose();
                },
                err -> {
                    if (closed) return;
                    c.close();
                    conn = null;
                    setBusy(false);
                    status.setForeground(Theme.DANGER);
                    status.setText("Lỗi: " + err.str("message"));
                });
    }

    private void setBusy(boolean b) {
        okBtn.setEnabled(!b);
    }
}
