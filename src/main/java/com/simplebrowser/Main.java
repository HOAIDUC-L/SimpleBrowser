package com.simplebrowser;

import com.formdev.flatlaf.FlatLightLaf;
import com.simplebrowser.ui.BrowserFrame;

import javax.swing.*;

/**
 * Điểm khởi chạy ứng dụng Simple Web Browser (Nhóm 1).
 */
public class Main {

    public static void main(String[] args) {
        // 1. Thiết lập Look and Feel hiện đại (FlatLaf) với fallback sang System Look and Feel
        setupLookAndFeel();

        // 2. Khởi tạo và hiển thị giao diện trên luồng Swing Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            BrowserFrame frame = new BrowserFrame();
            frame.setVisible(true);
        });
    }

    private static void setupLookAndFeel() {
        try {
            // Thử nạp FlatLaf Light
            FlatLightLaf.setup();
        } catch (Throwable t) {
            // Nếu có lỗi, sử dụng Look & Feel mặc định của hệ điều hành
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Giữ Look & Feel mặc định của Java Swing
            }
        }
    }
}
