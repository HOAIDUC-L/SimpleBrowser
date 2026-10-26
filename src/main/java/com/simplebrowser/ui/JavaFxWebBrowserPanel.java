package com.simplebrowser.ui;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Panel nhúng JavaFX WebView vào giao diện Java Swing qua JFXPanel.
 * Hỗ trợ render đầy đủ HTML5, CSS3, JavaScript tương tự Google Chrome / Cốc Cốc.
 * Quản lý đa luồng an toàn giữa Swing Event Dispatch Thread (EDT) và JavaFX Application Thread.
 */
public class JavaFxWebBrowserPanel extends JPanel {

    private final JFXPanel jfxPanel;
    private WebView webView;
    private WebEngine webEngine;
    private boolean isFxInitialized = false;

    // Callbacks thông báo về cho Swing
    private Consumer<String> onUrlChanged;
    private Consumer<Double> onProgressChanged;
    private Consumer<String> onTitleChanged;
    private BiConsumer<String, String> onDownloadRequested;

    // Zoom level
    private double zoomFactor = 1.0;

    public JavaFxWebBrowserPanel() {
        super(new BorderLayout());
        jfxPanel = new JFXPanel();

        // Không cho phép JavaFX tự tắt khi Swing đóng cửa sổ
        Platform.setImplicitExit(false);

        // Khởi tạo WebView trên JavaFX Application Thread
        Platform.runLater(this::initFxComponents);

        // Thanh công cụ nhỏ phía trên Web Preview (Zoom, chế độ)
        JPanel topBar = createTopBar();
        add(topBar, BorderLayout.NORTH);
        add(jfxPanel, BorderLayout.CENTER);
    }

    private JPanel createTopBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 2));
        bar.setBorder(new EmptyBorder(3, 8, 3, 8));
        bar.setBackground(new Color(248, 249, 250));

        JLabel engineBadge = new JLabel("🌐 WebKit Engine (HTML5, CSS3, JS)");
        engineBadge.setFont(new Font("SansSerif", Font.BOLD, 11));
        engineBadge.setForeground(new Color(25, 103, 210));

        JPanel zoomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        zoomPanel.setOpaque(false);

        JButton zoomOutBtn = new JButton("−");
        zoomOutBtn.setToolTipText("Thu nhỏ trang");
        zoomOutBtn.setPreferredSize(new Dimension(32, 22));
        zoomOutBtn.setMargin(new Insets(0, 0, 0, 0));
        zoomOutBtn.addActionListener(e -> changeZoom(-0.1));

        JLabel zoomLabel = new JLabel("100%");
        zoomLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        JButton zoomInBtn = new JButton("+");
        zoomInBtn.setToolTipText("Phóng to trang");
        zoomInBtn.setPreferredSize(new Dimension(32, 22));
        zoomInBtn.setMargin(new Insets(0, 0, 0, 0));
        zoomInBtn.addActionListener(e -> changeZoom(0.1));

        JButton zoomResetBtn = new JButton("Reset");
        zoomResetBtn.setToolTipText("Đặt lại kích thước chuẩn 100%");
        zoomResetBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        zoomResetBtn.setPreferredSize(new Dimension(50, 22));
        zoomResetBtn.setMargin(new Insets(0, 0, 0, 0));
        zoomResetBtn.addActionListener(e -> resetZoom(zoomLabel));

        zoomPanel.add(zoomOutBtn);
        zoomPanel.add(zoomLabel);
        zoomPanel.add(zoomInBtn);
        zoomPanel.add(zoomResetBtn);

        bar.add(engineBadge, BorderLayout.WEST);
        bar.add(zoomPanel, BorderLayout.EAST);

        return bar;
    }

    private void changeZoom(double delta) {
        zoomFactor = Math.max(0.3, Math.min(3.0, zoomFactor + delta));
        Platform.runLater(() -> {
            if (webView != null) {
                webView.setZoom(zoomFactor);
            }
        });
    }

    private void resetZoom(JLabel label) {
        zoomFactor = 1.0;
        label.setText("100%");
        Platform.runLater(() -> {
            if (webView != null) {
                webView.setZoom(1.0);
            }
        });
    }

    /**
     * Khởi tạo các thành phần JavaFX trên JavaFX Thread.
     */
    private void initFxComponents() {
        webView = new WebView();
        webEngine = webView.getEngine();
        webEngine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 SimpleBrowser/1.0");

        // 1. Lắng nghe sự kiện thay đổi URL khi người dùng nhấp vào link bên trong WebView
        webEngine.locationProperty().addListener((obs, oldUrl, newUrl) -> {
            if (newUrl != null && !newUrl.isBlank() && !newUrl.startsWith("about:blank") && !newUrl.startsWith("data:")) {
                // Nếu URL là một tài nguyên tệp tin tải về, hủy tải trên WebView và chuyển sang Downloader của Swing
                if (isDownloadUrl(newUrl)) {
                    Platform.runLater(() -> webEngine.getLoadWorker().cancel());
                    SwingUtilities.invokeLater(() -> {
                        if (onDownloadRequested != null) {
                            onDownloadRequested.accept(newUrl, "");
                        }
                    });
                    return;
                }

                SwingUtilities.invokeLater(() -> {
                    if (onUrlChanged != null) {
                        onUrlChanged.accept(newUrl);
                    }
                });
            }
        });

        // 2. Lắng nghe tiến độ tải trang (0.0 -> 1.0)
        webEngine.getLoadWorker().progressProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                SwingUtilities.invokeLater(() -> {
                    if (onProgressChanged != null) {
                        onProgressChanged.accept(newVal.doubleValue());
                    }
                });
            }
        });

        // 3. Lắng nghe tiêu đề trang
        webEngine.titleProperty().addListener((obs, oldTitle, newTitle) -> {
            if (newTitle != null) {
                SwingUtilities.invokeLater(() -> {
                    if (onTitleChanged != null) {
                        onTitleChanged.accept(newTitle);
                    }
                });
            }
        });

        // 4. Lắng nghe lỗi nạp trang
        webEngine.getLoadWorker().exceptionProperty().addListener((obs, oldEx, newEx) -> {
            if (newEx != null) {
                System.err.println("JavaFX WebView Exception: " + newEx.getMessage());
            }
        });

        // 5. Khi tải xong trang HTML, nhúng JavaScript Bridge bắt sự kiện nhấp các liên kết tải xuống
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                try {
                    JSObject window = (JSObject) webEngine.executeScript("window");
                    window.setMember("javaBridge", new JavaDownloadBridge());

                    webEngine.executeScript(
                        "(function() {" +
                        "  document.addEventListener('click', function(e) {" +
                        "    var a = e.target;" +
                        "    while (a && a.tagName !== 'A') { a = a.parentElement; }" +
                        "    if (a && a.href) {" +
                        "      var href = a.href;" +
                        "      var isDownload = a.hasAttribute('download') || " +
                        "                       a.classList.contains('download-btn') || " +
                        "                       href.indexOf('/download/') !== -1 || " +
                        "                       /\\.(pdf|zip|bin|exe|tar|gz|7z|rar|iso|apk|dmg|docx|xlsx|pptx)(\\?.*)?$/i.test(href);" +
                        "      if (isDownload) {" +
                        "        e.preventDefault();" +
                        "        e.stopPropagation();" +
                        "        var fileName = a.getAttribute('download') || '';" +
                        "        if (window.javaBridge) {" +
                        "          window.javaBridge.triggerDownload(href, fileName);" +
                        "        }" +
                        "      }" +
                        "    }" +
                        "  }, true);" +
                        "})();"
                    );
                } catch (Throwable ignored) {
                }
            }
        });

        // Hiển thị trang mặc định chào mừng
        showNotice(
                "Simple Web Browser",
                "Sử dụng <b>JavaFX WebView (WebKit)</b> hỗ trợ đầy đủ HTML5, CSS3, JavaScript.<br>" +
                        "Nhập địa chỉ URL (ví dụ: <code>https://www.google.com</code>) và nhấn <b>Send Request</b> để duyệt web.",
                "#1a73e8"
        );

        Scene scene = new Scene(webView);
        jfxPanel.setScene(scene);
        isFxInitialized = true;
    }

    /**
     * Chế độ 1: Tải trực tiếp URL qua WebEngine (cho phép tải CSS, JavaScript, ảnh đầy đủ từ máy chủ).
     *
     * @param urlStr Địa chỉ URL cần mở
     */
    public void loadUrl(String urlStr) {
        if (urlStr == null || urlStr.isBlank()) {
            return;
        }
        Platform.runLater(() -> {
            if (webEngine != null) {
                webEngine.load(urlStr);
            }
        });
    }

    /**
     * Chế độ 2: Nạp trực tiếp chuỗi mã nguồn HTML (dùng cho POST response hoặc HTML tự sinh).
     *
     * @param htmlContent Chuỗi mã nguồn HTML
     */
    public void loadContent(String htmlContent) {
        Platform.runLater(() -> {
            if (webEngine != null) {
                String safeContent = htmlContent != null ? htmlContent : "";
                webEngine.loadContent(safeContent, "text/html");
            }
        });
    }

    /**
     * Hiển thị trang thông báo/chỉ dẫn đẹp mắt với CSS hiện đại.
     *
     * @param title    Tiêu đề thông báo
     * @param message  Nội dung thông báo (hỗ trợ HTML)
     * @param colorHex Mã màu chủ đạo (ví dụ "#1a73e8")
     */
    public void showNotice(String title, String message, String colorHex) {
        String html = "<!DOCTYPE html>" +
                "<html><head><meta charset='utf-8'>" +
                "<style>" +
                "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; " +
                "       background-color: #f8f9fa; color: #202124; margin: 0; padding: 40px; display: flex; justify-content: center; align-items: center; min-height: 70vh; }" +
                ".card { background: #ffffff; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.08); padding: 32px 40px; max-width: 620px; width: 100%; border-top: 5px solid " + colorHex + "; }" +
                "h2 { color: " + colorHex + "; margin-top: 0; font-size: 22px; }" +
                "p { font-size: 14px; line-height: 1.6; color: #3c4043; }" +
                "code { background: #e8f0fe; color: #174ea6; padding: 2px 6px; border-radius: 4px; font-family: monospace; }" +
                "</style></head><body>" +
                "<div class='card'>" +
                "<h2>" + title + "</h2>" +
                "<p>" + message + "</p>" +
                "</div></body></html>";

        loadContent(html);
    }

    /**
     * Làm sạch nội dung WebView.
     */
    public void clear() {
        Platform.runLater(() -> {
            if (webEngine != null) {
                webEngine.loadContent("", "text/html");
            }
        });
    }

    /**
     * Thiết lập callback khi URL thay đổi (do click link).
     */
    public void setOnUrlChanged(Consumer<String> onUrlChanged) {
        this.onUrlChanged = onUrlChanged;
    }

    /**
     * Thiết lập callback khi tiến độ tải trang thay đổi.
     */
    public void setOnProgressChanged(Consumer<Double> onProgressChanged) {
        this.onProgressChanged = onProgressChanged;
    }

    /**
     * Thiết lập callback khi tiêu đề trang thay đổi.
     */
    public void setOnTitleChanged(Consumer<String> onTitleChanged) {
        this.onTitleChanged = onTitleChanged;
    }

    /**
     * Thiết lập callback khi người dùng yêu cầu tải tệp tin (nhấp link tải trên web).
     */
    public void setOnDownloadRequested(BiConsumer<String, String> onDownloadRequested) {
        this.onDownloadRequested = onDownloadRequested;
    }

    /**
     * Kiểm tra xem một URL có phải là tài nguyên tệp tải về hay không.
     */
    public static boolean isDownloadUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.contains("/download/") ||
                lower.matches(".*\\.(pdf|zip|bin|exe|dmg|iso|tar|gz|7z|rar|docx|xlsx|pptx|apk)(\\?.*)?$");
    }

    /**
     * Lớp cầu nối JavaScript Bridge nhận lệnh tải tệp từ sự kiện click trong WebView.
     * Cần là public để JavaScript Engine trong JavaFX có thể gọi được qua Reflection.
     */
    public class JavaDownloadBridge {
        public void triggerDownload(String url, String fileName) {
            SwingUtilities.invokeLater(() -> {
                if (onDownloadRequested != null) {
                    onDownloadRequested.accept(url, fileName);
                }
            });
        }
    }
}
