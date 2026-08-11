package com.delivery.client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Bang mau + bo component dung chung cho toan bo client.
 *
 * Moi mau, moi font, moi kieu nut deu khai bao o day. Cac frame chi viec lap
 * ghep -> doi mot mau la ca ung dung doi theo, khong phai di sua tung file.
 */
public final class Theme {
    private Theme() {}

    // ---------------- Bang mau ----------------
    public static final Color BRAND       = new Color(0x00B14F);   // xanh chu dao
    public static final Color BRAND_DARK  = new Color(0x00913F);
    public static final Color BRAND_SOFT  = new Color(0xE4F6EC);   // nen nhat khi chon dong
    public static final Color BG          = new Color(0xF2F5F7);   // nen cua so
    public static final Color CARD        = Color.WHITE;
    public static final Color BORDER      = new Color(0xE0E5EA);
    public static final Color TEXT        = new Color(0x1B2430);
    public static final Color MUTED       = new Color(0x78838F);
    public static final Color DANGER      = new Color(0xE0473E);
    public static final Color HEADER_BG   = new Color(0x0F2F22);   // thanh tieu de dam
    public static final Color BUBBLE_MINE = BRAND;
    public static final Color BUBBLE_OTHER= new Color(0xEDF1F4);
    public static final Color ROW_ALT     = new Color(0xFAFBFC);

    // ---------------- Font ----------------
    private static final String FAMILY = "SansSerif";
    public static final Font FONT       = new Font(FAMILY, Font.PLAIN, 13);
    public static final Font FONT_BOLD  = new Font(FAMILY, Font.BOLD, 13);
    public static final Font H1         = new Font(FAMILY, Font.BOLD, 19);
    public static final Font H2         = new Font(FAMILY, Font.BOLD, 14);
    public static final Font SMALL      = new Font(FAMILY, Font.PLAIN, 11);
    public static final Font SMALL_BOLD = new Font(FAMILY, Font.BOLD, 11);

    private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));

    public static String money(double v) { return MONEY.format(Math.round(v)) + " đ"; }

    // ---------------- Trang thai don ----------------
    public static String statusLabel(String status) {
        if (status == null) return "—";
        switch (status) {
            case "PENDING":    return "Chờ tài xế";
            case "ACCEPTED":   return "Đã nhận";
            case "PICKED_UP":  return "Đã lấy hàng";
            case "DELIVERING": return "Đang giao";
            case "COMPLETED":  return "Hoàn thành";
            case "CANCELLED":  return "Đã huỷ";
            default:           return status;
        }
    }

    public static Color statusColor(String status) {
        if (status == null) return MUTED;
        switch (status) {
            case "PENDING":    return new Color(0xE8A317);
            case "ACCEPTED":   return new Color(0x3B7DD8);
            case "PICKED_UP":  return new Color(0x8256D0);
            case "DELIVERING": return new Color(0x0E9BC4);
            case "COMPLETED":  return BRAND;
            case "CANCELLED":  return DANGER;
            default:           return MUTED;
        }
    }

    // ---------------- Panel bo goc ----------------
    /** Panel nen trang, bo goc tron, vien mong - "the" chua noi dung. */
    public static class Card extends JPanel {
        private final int arc;
        public Card(LayoutManager lm) { this(lm, 14); }
        public Card(LayoutManager lm, int arc) {
            super(lm);
            this.arc = arc;
            setOpaque(false);
            setBackground(CARD);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** The co tieu de o tren, noi dung o giua. */
    public static Card card(String title, JComponent content) {
        Card c = new Card(new BorderLayout(0, 10));
        c.setBorder(new EmptyBorder(12, 14, 12, 14));
        if (title != null) c.add(sectionTitle(title), BorderLayout.NORTH);
        content.setOpaque(false);
        c.add(content, BorderLayout.CENTER);
        return c;
    }

    public static JComponent sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(H2);
        l.setForeground(TEXT);
        l.setBorder(new EmptyBorder(0, 0, 8, 0));
        JPanel p = transparent(new BorderLayout());
        p.add(l, BorderLayout.WEST);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        return p;
    }

    // ---------------- Nut bam ----------------
    /** Nut bo goc tu ve - khong phu thuoc look-and-feel nen moi may hien giong nhau. */
    public static class PillButton extends JButton {
        private final Color base;
        private final boolean outline;
        PillButton(String text, Color base, Color fg, boolean outline) {
            super(text);
            this.base = base;
            this.outline = outline;
            setForeground(fg);
            setFont(FONT_BOLD);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(new EmptyBorder(8, 16, 8, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = base;
            if (!isEnabled())                  c = blend(base, CARD, 0.55f);
            else if (getModel().isPressed())   c = blend(base, Color.BLACK, 0.18f);
            else if (getModel().isRollover())  c = blend(base, Color.WHITE, 0.12f);
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            if (outline) {
                g2.setColor(isEnabled() ? BORDER : blend(BORDER, CARD, 0.5f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            }
            g2.dispose();
            super.paintComponent(g);
        }
        @Override public void setEnabled(boolean b) {
            super.setEnabled(b);
            setCursor(Cursor.getPredefinedCursor(b ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }
    }

    public static JButton primary(String text) { return new PillButton(text, BRAND, Color.WHITE, false); }
    public static JButton danger(String text)  { return new PillButton(text, DANGER, Color.WHITE, false); }
    public static JButton ghost(String text)   { return new PillButton(text, CARD, TEXT, true); }

    // ---------------- Nhan & o nhap ----------------
    public static JLabel label(String text)  { return styled(new JLabel(text), FONT, TEXT); }
    public static JLabel bold(String text)   { return styled(new JLabel(text), FONT_BOLD, TEXT); }
    public static JLabel muted(String text)  { return styled(new JLabel(text), SMALL, MUTED); }
    public static JLabel h1(String text)     { return styled(new JLabel(text), H1, TEXT); }

    private static JLabel styled(JLabel l, Font f, Color c) {
        l.setFont(f);
        l.setForeground(c);
        return l;
    }

    public static JTextField field(int cols) {
        JTextField t = new JTextField(cols);
        decorate(t);
        return t;
    }

    public static JPasswordField password(int cols) {
        JPasswordField t = new JPasswordField(cols);
        decorate(t);
        return t;
    }

    private static void decorate(JTextField t) {
        t.setFont(FONT);
        t.setForeground(TEXT);
        t.setBackground(CARD);
        t.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(6, 8, 6, 8)));
    }

    public static JPanel transparent(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setOpaque(false);
        return p;
    }

    /** Hang "nhan : o nhap" xep doc, dung cho cac form. */
    public static JPanel labeledField(String label, JComponent field) {
        JPanel p = transparent(new BorderLayout(0, 4));
        JLabel l = muted(label);
        l.setFont(SMALL_BOLD);
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    // ---------------- Bang ----------------
    public static void styleTable(JTable t) {
        t.setFont(FONT);
        t.setRowHeight(30);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setFillsViewportHeight(true);
        t.setBackground(CARD);
        t.setForeground(TEXT);
        t.setSelectionBackground(BRAND_SOFT);
        t.setSelectionForeground(TEXT);
        t.setDefaultRenderer(Object.class, new CellRenderer());

        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setResizingAllowed(false);
        h.setFont(SMALL_BOLD);
        h.setBackground(new Color(0xF6F8F9));
        h.setForeground(MUTED);
        h.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        h.setPreferredSize(new Dimension(0, 30));
        ((DefaultTableCellRenderer) h.getDefaultRenderer()).setHorizontalAlignment(SwingConstants.LEFT);
    }

    /** O binh thuong: chi them padding + ke soc nhe cho de doc. */
    private static class CellRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                 boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, false, row, col);
            setBorder(new EmptyBorder(0, 10, 0, 10));
            setFont(col == 0 ? FONT_BOLD : FONT);
            if (!sel) setBackground(row % 2 == 0 ? CARD : ROW_ALT);
            return this;
        }
    }

    /** O trang thai: ve mot vien thuoc mau theo trang thai. */
    public static class StatusRenderer extends DefaultTableCellRenderer {
        private Color pill = MUTED;
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                 boolean focus, int row, int col) {
            String raw = String.valueOf(v);
            super.getTableCellRendererComponent(t, statusLabel(raw), sel, false, row, col);
            pill = statusColor(raw);
            setFont(SMALL_BOLD);
            setForeground(Color.WHITE);
            setHorizontalAlignment(CENTER);
            setOpaque(false);
            setBorder(new EmptyBorder(0, 6, 0, 6));
            return this;
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(pill);
            g2.fillRoundRect(6, 5, getWidth() - 12, getHeight() - 10, 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static JScrollPane scroll(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        sp.getViewport().setBackground(CARD);
        sp.setBackground(CARD);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    // ---------------- Tien ich ----------------
    public static Color blend(Color a, Color b, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        return new Color(
                Math.round(a.getRed()   * (1 - r) + b.getRed()   * r),
                Math.round(a.getGreen() * (1 - r) + b.getGreen() * r),
                Math.round(a.getBlue()  * (1 - r) + b.getBlue()  * r));
    }

    /** "2026-08-11 17:07:47.638" -> "17:07". Chuoi la se tra ve nguyen ven. */
    public static String shortTime(String timestamp) {
        if (timestamp == null || timestamp.length() < 16) return "";
        String hhmm = timestamp.substring(11, 16);
        return hhmm.charAt(2) == ':' ? hhmm : "";
    }
}
