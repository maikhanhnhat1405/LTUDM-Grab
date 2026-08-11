package com.delivery.client.ui;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collections;
import java.util.List;

/**
 * Quy uoc CUA SO dung chung cho toan bo client (mau sac nam o {@link Theme}).
 *
 * Hai muc tieu:
 *
 *  1. GIAO DIEN CO DINH. Truoc day client dung system look-and-feel nen mo tren
 *     Windows / macOS / Linux ra ba kieu khac nhau, cua so lai resize duoc nen
 *     layout xo lech. Bay gio ep cung mot L&F (Nimbus - co san trong JDK), cung
 *     mot font, cung mot kich thuoc, khoa resize va maximize.
 *
 *  2. DONG FORM CHUAN. Moi loai cua so co mot cach dong rieng (xem
 *     {@link #onClosing}). Khong con cho nao tu goi EXIT_ON_CLOSE nua.
 */
public final class UiKit {
    private UiKit() {}

    // ----- kich thuoc co dinh cua tung man hinh (pixel logic) -----
    public static final Dimension LOGIN    = new Dimension(860, 520);
    public static final Dimension REGISTER = new Dimension(480, 580);
    public static final Dimension MAIN     = new Dimension(1100, 700);

    /** Be ngang cot chat nam ben phai man hinh chinh. */
    public static final int CHAT_WIDTH = 400;

    /** Goi DUY NHAT 1 lan, trong ClientMain, truoc khi tao bat ky cua so nao. */
    public static void applyTheme() {
        // Cac key "nimbus*" phai dat TRUOC setLookAndFeel vi Nimbus suy ra toan bo
        // bang mau cua no ngay luc khoi tao.
        UIManager.put("control", Theme.BG);
        UIManager.put("background", Theme.BG);
        UIManager.put("nimbusBase", Theme.BRAND_DARK);
        UIManager.put("nimbusBlueGrey", new Color(0xD5DBE1));
        UIManager.put("nimbusSelectionBackground", Theme.BRAND);
        UIManager.put("nimbusSelection", Theme.BRAND);
        UIManager.put("nimbusFocus", Theme.BRAND);
        UIManager.put("text", Theme.TEXT);
        UIManager.put("nimbusLightBackground", Theme.CARD);

        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            // May nao khong co Nimbus thi dung Metal - van la L&F cross-platform,
            // van giong nhau moi noi, chi khac o cho xau hon mot chut.
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {}
        }

        setGlobalFont(Theme.FONT);
        UIManager.put("ToolTip.background", Theme.CARD);
        UIManager.put("OptionPane.background", Theme.BG);
        UIManager.put("Panel.background", Theme.BG);
        JFrame.setDefaultLookAndFeelDecorated(false);
        JDialog.setDefaultLookAndFeelDecorated(false);
    }

    private static void setGlobalFont(Font font) {
        FontUIResource f = new FontUIResource(font);
        UIManager.getLookAndFeelDefaults().put("defaultFont", f);   // Nimbus doc key nay
        List<Object> keys = Collections.list(UIManager.getDefaults().keys());
        for (Object key : keys) {                                    // cac L&F con lai
            if (UIManager.get(key) instanceof FontUIResource) UIManager.put(key, f);
        }
    }

    /**
     * Khoa cua so o dung mot kich thuoc: khong resize, khong maximize,
     * va luon nam giua man hinh (hoac giua cua so cha neu la dialog).
     */
    public static void lockSize(Window w, Dimension size) {
        w.setPreferredSize(size);
        w.setMinimumSize(size);
        w.setMaximumSize(size);
        w.setSize(size);
        if (w instanceof Frame) {
            Frame f = (Frame) w;
            f.setResizable(false);
            f.setExtendedState(Frame.NORMAL);
        } else if (w instanceof Dialog) {
            ((Dialog) w).setResizable(false);
        }
        w.setLocationRelativeTo(w.getOwner());   // owner == null -> giua man hinh
    }

    /**
     * Gan hanh dong dong CHUAN cho cua so: bam nut X se chay {@code action}
     * chu khong lam gi khac. Nho DO_NOTHING_ON_CLOSE nen quyen quyet dinh
     * dong hay khong nam hoan toan o {@code action}.
     */
    public static void onClosing(Window w, Runnable action) {
        if (w instanceof JFrame) {
            ((JFrame) w).setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        } else if (w instanceof JDialog) {
            ((JDialog) w).setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        }
        w.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { action.run(); }
        });
    }

    /** Phim ESC = dong form. Chi dung cho dialog, khong dung cho cua so chinh. */
    public static void bindEscape(JRootPane root, Runnable action) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "uikit-close");
        root.getActionMap().put("uikit-close", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    public static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "Xác nhận",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }
}
