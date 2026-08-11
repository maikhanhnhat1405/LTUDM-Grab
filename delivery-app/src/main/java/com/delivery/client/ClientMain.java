package com.delivery.client;

import com.delivery.client.ui.LoginFrame;
import com.delivery.client.ui.UiKit;

import javax.swing.SwingUtilities;

public class ClientMain {
    public static void main(String[] args) {
        UiKit.applyTheme();   // ep look-and-feel + font dung chung cho moi may
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
