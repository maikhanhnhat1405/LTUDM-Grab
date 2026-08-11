package com.delivery.client.ui;

import com.delivery.common.GeoUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * "Bản đồ giả": vẽ toạ độ lên khung lưới thay vì tải tile thật.
 *
 * Mục đích là tách bạch hai việc khi debug — chấm tài xế không nhúc nhích thì
 * lỗi nằm ở đường truyền UDP chứ không phải ở thư viện bản đồ. Sau khi luồng
 * dữ liệu chạy đúng thì thay lớp này bằng JXMapViewer2 (tile OpenStreetMap)
 * mà không phải động tới phần mạng.
 *
 * Màu sắc lấy hết từ {@link Theme} để khớp với phần còn lại của giao diện.
 */
public class MapPanel extends JPanel {

    public enum Pick { NONE, PICKUP, DROPOFF }

    public interface PickListener {
        void onPick(Pick which, double lat, double lng);
    }

    // Khung nhìn cố định quanh TP.HCM
    private static final double MIN_LAT = 10.70, MAX_LAT = 10.88;
    private static final double MIN_LNG = 106.60, MAX_LNG = 106.82;
    private static final int TRAIL_MAX = 120;

    private static final Color GRID     = new Color(0xE6, 0xEB, 0xEF);
    private static final Color DRIVER_C = new Color(0x3B, 0x7D, 0xD8);

    private double[] pickup, dropoff, driver;
    private final Deque<double[]> trail = new ArrayDeque<>();

    private Pick pickMode = Pick.NONE;
    private PickListener pickListener;
    private String statusText = "";

    public MapPanel() {
        setBackground(Theme.CARD);
        setPreferredSize(new Dimension(360, 360));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (pickMode == Pick.NONE || pickListener == null) return;
                double lat = yToLat(e.getY());
                double lng = xToLng(e.getX());
                if (pickMode == Pick.PICKUP) setPickup(lat, lng); else setDropoff(lat, lng);
                pickListener.onPick(pickMode, lat, lng);
                setPickMode(Pick.NONE);
            }
        });
    }

    // ---------------- API ----------------
    public void setPickMode(Pick mode) {
        this.pickMode = mode;
        setCursor(mode == Pick.NONE ? Cursor.getDefaultCursor()
                : Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        repaint();
    }

    public void setPickListener(PickListener l) { this.pickListener = l; }

    public void setPickup(double lat, double lng)  { pickup  = new double[]{lat, lng}; repaint(); }
    public void setDropoff(double lat, double lng) { dropoff = new double[]{lat, lng}; repaint(); }

    public void setDriver(double lat, double lng) {
        driver = new double[]{lat, lng};
        trail.addLast(driver);
        while (trail.size() > TRAIL_MAX) trail.removeFirst();
        repaint();
    }

    public void setStatusText(String s) { this.statusText = s == null ? "" : s; repaint(); }

    public void clearTrail() { trail.clear(); driver = null; repaint(); }

    public void clearAll() {
        pickup = dropoff = driver = null;
        trail.clear();
        statusText = "";
        repaint();
    }

    // ---------------- đổi toạ độ <-> pixel ----------------
    private int lngToX(double lng) { return (int) ((lng - MIN_LNG) / (MAX_LNG - MIN_LNG) * getWidth()); }
    private int latToY(double lat) { return (int) ((MAX_LAT - lat) / (MAX_LAT - MIN_LAT) * getHeight()); }
    private double xToLng(int x)   { return MIN_LNG + (double) x / getWidth()  * (MAX_LNG - MIN_LNG); }
    private double yToLat(int y)   { return MAX_LAT - (double) y / getHeight() * (MAX_LAT - MIN_LAT); }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawGrid(g2);
        if (pickup != null && dropoff != null) drawRouteLine(g2);
        drawTrail(g2);
        if (pickup  != null) drawMarker(g2, pickup,  Theme.BRAND,  "A");
        if (dropoff != null) drawMarker(g2, dropoff, Theme.DANGER, "B");
        if (driver  != null) drawDriver(g2);
        drawOverlay(g2);

        g2.dispose();
    }

    private void drawGrid(Graphics2D g2) {
        g2.setColor(GRID);
        for (int i = 1; i < 10; i++) {
            int x = getWidth() * i / 10, y = getHeight() * i / 10;
            g2.drawLine(x, 0, x, getHeight());
            g2.drawLine(0, y, getWidth(), y);
        }
        g2.setColor(Theme.BORDER);
        g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);

        g2.setColor(Theme.MUTED);
        g2.setFont(Theme.SMALL);
        g2.drawString(String.format("%.2f, %.2f", MAX_LAT, MIN_LNG), 6, 14);
        g2.drawString(String.format("%.2f, %.2f", MIN_LAT, MAX_LNG), getWidth() - 76, getHeight() - 8);
    }

    private void drawRouteLine(Graphics2D g2) {
        g2.setColor(Theme.MUTED);
        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1f, new float[]{6f, 6f}, 0f));
        g2.drawLine(lngToX(pickup[1]), latToY(pickup[0]), lngToX(dropoff[1]), latToY(dropoff[0]));
    }

    private void drawTrail(Graphics2D g2) {
        if (trail.size() < 2) return;
        g2.setColor(new Color(DRIVER_C.getRed(), DRIVER_C.getGreen(), DRIVER_C.getBlue(), 90));
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double[] prev = null;
        for (double[] p : trail) {
            if (prev != null) g2.drawLine(lngToX(prev[1]), latToY(prev[0]), lngToX(p[1]), latToY(p[0]));
            prev = p;
        }
    }

    private void drawMarker(Graphics2D g2, double[] pos, Color color, String label) {
        int x = lngToX(pos[1]), y = latToY(pos[0]);
        g2.setColor(color);
        g2.fillOval(x - 9, y - 9, 18, 18);
        g2.setColor(Color.WHITE);
        g2.setFont(Theme.SMALL_BOLD);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(label, x - fm.stringWidth(label) / 2, y + 4);
    }

    private void drawDriver(Graphics2D g2) {
        int x = lngToX(driver[1]), y = latToY(driver[0]);
        g2.setColor(new Color(DRIVER_C.getRed(), DRIVER_C.getGreen(), DRIVER_C.getBlue(), 55));
        g2.fillOval(x - 16, y - 16, 32, 32);
        g2.setColor(DRIVER_C);
        g2.fillOval(x - 7, y - 7, 14, 14);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(x - 7, y - 7, 14, 14);
    }

    private void drawOverlay(Graphics2D g2) {
        if (pickMode != Pick.NONE) {
            String hint = pickMode == Pick.PICKUP
                    ? "Bấm lên bản đồ để chọn ĐIỂM LẤY"
                    : "Bấm lên bản đồ để chọn ĐIỂM GIAO";
            g2.setFont(Theme.SMALL_BOLD);
            g2.setColor(Theme.BRAND);
            g2.drawString(hint, 8, getHeight() - 26);
        }

        StringBuilder sb = new StringBuilder();
        if (driver != null && dropoff != null) {
            sb.append("Còn ")
              .append(GeoUtil.formatDistance(GeoUtil.distanceMeters(
                      driver[0], driver[1], dropoff[0], dropoff[1])))
              .append(" tới điểm giao");
        }
        if (!statusText.isEmpty()) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(statusText);
        }
        String text = sb.toString();
        if (text.isEmpty()) return;

        g2.setFont(Theme.SMALL_BOLD);
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(text) + 16;
        g2.setColor(new Color(255, 255, 255, 235));
        g2.fillRoundRect(6, getHeight() - 24, w, 20, 8, 8);
        g2.setColor(Theme.TEXT);
        g2.drawString(text, 14, getHeight() - 10);
    }
}
