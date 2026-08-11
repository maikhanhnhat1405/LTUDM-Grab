package com.delivery.client.ui;

import com.delivery.client.ClientConnection;
import com.delivery.client.Config;
import com.delivery.common.Message;
import com.delivery.common.MessageType;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Man hinh dang nhap: nua trai la banner thuong hieu, nua phai la form.
 */
public class LoginFrame extends JFrame {

    private final JTextField userField = Theme.field(18);
    private final JPasswordField passField = Theme.password(16);
    private final JLabel statusLabel = new JLabel(" ");

    private final JButton loginBtn = Theme.primary("Đăng nhập");
    private final JButton regBtn = Theme.ghost("Tạo tài khoản mới");

    /** Ket noi dang cho response LOGIN - giu lai de con dong khi form bi tat. */
    private ClientConnection pending;
    private boolean busy;

    public LoginFrame() {
        super("Delivery App — Đăng nhập");

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(buildBanner(), BorderLayout.WEST);
        root.add(buildForm(), BorderLayout.CENTER);
        setContentPane(root);

        getRootPane().setDefaultButton(loginBtn);

        // Dong man hinh dang nhap = thoat han ung dung.
        UiKit.onClosing(this, this::exitApp);
        UiKit.lockSize(this, UiKit.LOGIN);
    }

    // ------------------------------------------------------------------
    // Giao dien
    // ------------------------------------------------------------------

    private JComponent buildBanner() {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, Theme.HEADER_BG, 0, getHeight(), new Color(0x11884E)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        p.setPreferredSize(new Dimension(360, 0));
        p.setBorder(new EmptyBorder(46, 36, 36, 36));

        JLabel title = new JLabel("Delivery App");
        title.setFont(new Font("SansSerif", Font.BOLD, 30));
        title.setForeground(Color.WHITE);

        JLabel tagline = new JLabel("<html>Đặt đơn, nhận đơn và trò chuyện<br>theo thời gian thực.</html>");
        tagline.setFont(Theme.FONT);
        tagline.setForeground(new Color(0xC7E7D5));
        tagline.setBorder(new EmptyBorder(12, 0, 0, 0));

        JPanel head = Theme.transparent(new BorderLayout());
        head.add(title, BorderLayout.NORTH);
        head.add(tagline, BorderLayout.CENTER);

        JPanel bullets = Theme.transparent(new GridLayout(3, 1, 0, 12));
        bullets.add(bullet("Đơn hàng đẩy thẳng tới tài xế"));
        bullets.add(bullet("Chat ngay trong màn hình chính"));
        bullets.add(bullet("Trạng thái cập nhật tức thì"));

        // Dat ca khoi trong NORTH de noi dung bam sat tren, khong bi keo gian ra
        // het chieu cao banner.
        JPanel stack = Theme.transparent(new BorderLayout(0, 34));
        stack.add(head, BorderLayout.NORTH);
        stack.add(bullets, BorderLayout.CENTER);
        p.add(stack, BorderLayout.NORTH);
        return p;
    }

    private JComponent bullet(String text) {
        JPanel row = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JLabel dotLabel = new JLabel("●");
        dotLabel.setForeground(new Color(0x4ADE80));
        dotLabel.setFont(Theme.SMALL);
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT);
        l.setForeground(new Color(0xE6F3EB));
        row.add(dotLabel);
        row.add(l);
        return row;
    }

    private JComponent buildForm() {
        JPanel wrap = Theme.transparent(new GridBagLayout());
        wrap.setBorder(new EmptyBorder(0, 30, 0, 30));

        JPanel fields = Theme.transparent(new GridLayout(0, 1, 0, 12));
        fields.add(Theme.labeledField("TÀI KHOẢN", userField));
        fields.add(Theme.labeledField("MẬT KHẨU", passField));

        loginBtn.addActionListener(e -> doLogin());
        regBtn.addActionListener(e -> new RegisterDialog(this).setVisible(true));
        loginBtn.setPreferredSize(new Dimension(0, 38));
        regBtn.setPreferredSize(new Dimension(0, 34));

        JPanel buttons = Theme.transparent(new GridLayout(2, 1, 0, 8));
        buttons.add(loginBtn);
        buttons.add(regBtn);

        statusLabel.setFont(Theme.SMALL);
        statusLabel.setForeground(Theme.DANGER);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel card = Theme.transparent(new BorderLayout(0, 16));
        JLabel heading = Theme.h1("Chào mừng trở lại");
        card.add(heading, BorderLayout.NORTH);
        card.add(fields, BorderLayout.CENTER);

        JPanel south = Theme.transparent(new BorderLayout(0, 8));
        south.add(buttons, BorderLayout.NORTH);
        south.add(statusLabel, BorderLayout.CENTER);
        card.add(south, BorderLayout.SOUTH);

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        wrap.add(card, g);
        return wrap;
    }

    // ------------------------------------------------------------------
    // Nghiep vu
    // ------------------------------------------------------------------

    private void exitApp() {
        if (pending != null) pending.close();
        dispose();
        System.exit(0);
    }

    private void doLogin() {
        if (busy) return;                       // chan bam lien tuc -> moi lan bam mo 1 socket moi

        String username = userField.getText().trim();
        String password = new String(passField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Nhập đầy đủ tài khoản và mật khẩu");
            return;
        }
        statusLabel.setText("Đang kết nối...");

        ClientConnection conn = new ClientConnection();
        try {
            conn.connect(Config.host(), Config.port());
        } catch (Exception ex) {
            statusLabel.setText("Không kết nối được máy chủ: " + ex.getMessage());
            return;
        }
        setBusy(true, conn);

        conn.request(Message.request(MessageType.LOGIN)
                        .put("username", username)
                        .put("password", password),
                resp -> {
                    JsonObject u = resp.getData().getAsJsonObject("user");
                    conn.userId = u.get("id").getAsLong();
                    conn.fullName = u.get("fullName").getAsString();
                    conn.role = u.get("role").getAsString();
                    conn.udpToken = resp.lng("udpToken");
                    if (resp.getData().has("udpPort")) conn.udpPort = (int) resp.lng("udpPort");

                    JFrame next;
                    switch (conn.role) {
                        case "CUSTOMER": next = new CustomerFrame(conn); break;
                        case "DRIVER":   next = new DriverFrame(conn);   break;
                        default:
                            JOptionPane.showMessageDialog(this, "Màn hình ADMIN sẽ làm ở Level 2.");
                            conn.close();
                            setBusy(false, null);
                            return;
                    }
                    setBusy(false, null);
                    next.setVisible(true);
                    dispose();          // khong System.exit: connection da chuyen sang frame moi
                },
                err -> {
                    statusLabel.setText("Đăng nhập thất bại: " + err.str("message"));
                    conn.close();
                    setBusy(false, null);
                });
    }

    private void setBusy(boolean b, ClientConnection conn) {
        busy = b;
        pending = conn;
        loginBtn.setEnabled(!b);
        regBtn.setEnabled(!b);
    }
}
