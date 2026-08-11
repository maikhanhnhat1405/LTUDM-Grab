package com.delivery.client.ui;

import com.delivery.client.ClientConnection;
import com.delivery.common.Message;
import com.delivery.common.MessageType;

import javax.swing.*;
import java.awt.*;

public class RegisterDialog extends JDialog {

    private final JTextField userField = new JTextField(14);
    private final JPasswordField passField = new JPasswordField(14);
    private final JTextField nameField = new JTextField(14);
    private final JTextField phoneField = new JTextField(14);
    private final JComboBox<String> roleBox = new JComboBox<>(new String[]{"CUSTOMER", "DRIVER"});
    private final JTextField plateField = new JTextField(10);
    private final JLabel status = new JLabel(" ");

    public RegisterDialog(LoginFrame owner) {
        super(owner, "Dang ky tai khoan", true);
        setLayout(new BorderLayout(6, 6));

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Tai khoan:"));  form.add(userField);
        form.add(new JLabel("Mat khau:"));   form.add(passField);
        form.add(new JLabel("Ho ten:"));     form.add(nameField);
        form.add(new JLabel("So dien thoai:")); form.add(phoneField);
        form.add(new JLabel("Vai tro:"));    form.add(roleBox);
        form.add(new JLabel("Bien so (driver):")); form.add(plateField);

        JButton ok = new JButton("Tao tai khoan");
        ok.addActionListener(e -> doRegister(owner));

        JPanel south = new JPanel();
        south.add(ok);

        add(status, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    private void doRegister(LoginFrame owner) {
        ClientConnection conn = new ClientConnection();
        try {
            conn.connect(owner.host(), owner.port());
        } catch (Exception ex) {
            status.setText("Khong ket noi duoc: " + ex.getMessage());
            return;
        }
        Message req = Message.request(MessageType.REGISTER)
                .put("username", userField.getText().trim())
                .put("password", new String(passField.getPassword()))
                .put("fullName", nameField.getText().trim())
                .put("phone", phoneField.getText().trim())
                .put("role", (String) roleBox.getSelectedItem())
                .put("plateNumber", plateField.getText().trim());

        conn.request(req,
                resp -> {
                    JOptionPane.showMessageDialog(this, "Dang ky thanh cong, moi dang nhap.");
                    conn.close();
                    dispose();
                },
                err -> {
                    status.setText("Loi: " + err.str("message"));
                    conn.close();
                });
    }
}
