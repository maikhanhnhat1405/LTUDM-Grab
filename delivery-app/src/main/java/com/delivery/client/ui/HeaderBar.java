package com.delivery.client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Thanh tieu de tren cung cua man hinh chinh: ten ung dung, vai tro,
 * den bao ket noi va nut thoat. Dung chung cho ca khach hang lan tai xe.
 */
public class HeaderBar extends JPanel {

    private final Dot dot = new Dot();
    private final JLabel connText = new JLabel("Đã kết nối");

    public HeaderBar(String subtitle, String userName, String role, Runnable onExit) {
        super(new BorderLayout(16, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(14, 20, 14, 20));

        // ----- ben trai: ten ung dung + mo ta man hinh -----
        JLabel app = new JLabel("Delivery App");
        app.setFont(Theme.H1);
        app.setForeground(Color.WHITE);

        JLabel sub = new JLabel(subtitle);
        sub.setFont(Theme.SMALL);
        sub.setForeground(new Color(0xB8D8C6));

        JPanel left = Theme.transparent(new GridLayout(2, 1));
        left.add(app);
        left.add(sub);

        // ----- ben phai: trang thai + nguoi dung + thoat -----
        connText.setFont(Theme.SMALL);
        connText.setForeground(new Color(0xB8D8C6));

        JPanel conn = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 6, 0));
        conn.add(dot);
        conn.add(connText);

        JLabel who = new JLabel(userName);
        who.setFont(Theme.FONT_BOLD);
        who.setForeground(Color.WHITE);

        JButton exit = Theme.ghost("Thoát");
        exit.addActionListener(e -> onExit.run());

        JPanel right = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        right.add(conn);
        right.add(new RolePill(role));
        right.add(who);
        right.add(exit);

        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
    }

    public void setConnected(boolean ok) {
        dot.ok = ok;
        dot.repaint();
        connText.setText(ok ? "Đã kết nối" : "Mất kết nối");
        connText.setForeground(ok ? new Color(0xB8D8C6) : new Color(0xFFB4AE));
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setPaint(new GradientPaint(0, 0, Theme.HEADER_BG, getWidth(), 0, new Color(0x11663D)));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }

    /** Den tron bao tinh trang ket noi. */
    private static class Dot extends JComponent {
        boolean ok = true;
        Dot() { setPreferredSize(new Dimension(9, 9)); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(ok ? new Color(0x4ADE80) : Theme.DANGER);
            g2.fillOval(0, 0, 9, 9);
            g2.dispose();
        }
    }

    /** Nhan vai tro bo tron (KHÁCH HÀNG / TÀI XẾ). */
    private static class RolePill extends JLabel {
        RolePill(String text) {
            super(text.toUpperCase());
            setFont(Theme.SMALL_BOLD);
            setForeground(Color.WHITE);
            setBorder(new EmptyBorder(4, 10, 4, 10));
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.BRAND);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
