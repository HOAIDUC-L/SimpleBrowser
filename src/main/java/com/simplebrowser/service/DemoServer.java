package com.simplebrowser.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Máy chủ HTTP cục bộ tích hợp sẵn (Demo HTTP Server).
 * Cung cấp:
 * 1. Trang web mẫu HTML5/CSS3 chuẩn mực (`/` hoặc `/demo`) có đầy đủ các thẻ p, div, span, img.
 * 2. Kho 5 tệp tin mẫu với các định dạng khác nhau (PDF, TXT, PNG, ZIP, File 5MB) để kiểm thử tính năng Tải xuống.
 * 3. Điểm nhận Form POST (`/submit-feedback`) để kiểm thử gửi dữ liệu form.
 */
public class DemoServer {

    public static final int DEFAULT_PORT = 8888;
    private static final int MAX_PORT_ATTEMPTS = 15;

    private HttpServer server;
    private int boundPort = -1;
    private boolean running = false;

    // Cache sẵn dữ liệu tĩnh
    private byte[] pdfDataCache;
    private byte[] txtDataCache;
    private byte[] pngDataCache;
    private byte[] zipDataCache;

    public DemoServer() {
        initStaticData();
    }

    /**
     * Khởi động Demo Server trên cổng mặc định 8888 (nếu bận sẽ tự động thử các cổng tiếp theo).
     */
    public synchronized void start() {
        if (running) {
            return;
        }

        for (int p = DEFAULT_PORT; p < DEFAULT_PORT + MAX_PORT_ATTEMPTS; p++) {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", p), 0);
                boundPort = p;
                break;
            } catch (IOException ignored) {
                // Thử cổng tiếp theo
            }
        }

        // Nếu tất cả cổng từ 8888 đến 8902 đều bận, để hệ điều hành tự cấp phát cổng ngẫu nhiên (cổng 0)
        if (server == null) {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                boundPort = server.getAddress().getPort();
            } catch (IOException e) {
                System.err.println("Không thể khởi động DemoServer: " + e.getMessage());
                return;
            }
        }

        // Đăng ký các Endpoint
        server.createContext("/", new RootHandler());
        server.createContext("/demo", new RootHandler());
        server.createContext("/download/Huong-dan-Java-Web-Browser.pdf", new DownloadPdfHandler());
        server.createContext("/download/Ghi-chu-bai-tap-Nhom-1.txt", new DownloadTxtHandler());
        server.createContext("/download/SimpleBrowser-Logo.png", new DownloadPngHandler());
        server.createContext("/download/SimpleBrowser-SourceCode.zip", new DownloadZipHandler());
        server.createContext("/download/Test-File-5MB.bin", new DownloadLargeBinaryHandler());
        server.createContext("/submit-feedback", new FeedbackPostHandler());

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        running = true;
        System.out.println("DemoServer đã khởi động tại: " + getDemoUrl());
    }

    /**
     * Dừng máy chủ HTTP.
     */
    public synchronized void stop() {
        if (running && server != null) {
            server.stop(0);
            running = false;
            System.out.println("DemoServer đã dừng.");
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return boundPort;
    }

    public String getDemoUrl() {
        int port = boundPort > 0 ? boundPort : DEFAULT_PORT;
        return "http://localhost:" + port + "/";
    }

    private void initStaticData() {
        this.txtDataCache = generateTxtContent();
        this.pdfDataCache = generatePdfContent();
        this.pngDataCache = generatePngContent();
        this.zipDataCache = generateZipContent();
    }

    // =========================================================================
    // Handlers
    // =========================================================================

    /**
     * Phục vụ trang HTML5 chính của Demo Center.
     */
    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.getResponseHeaders().set("Server", "SimpleBrowser-DemoServer/1.0");
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }

            String html = buildDemoPageHtml();
            byte[] responseBytes = html.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.getResponseHeaders().set("Server", "SimpleBrowser-DemoServer/1.0");
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    private class DownloadPdfHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendFile(exchange, "Huong-dan-Java-Web-Browser.pdf", "application/pdf", pdfDataCache);
        }
    }

    private class DownloadTxtHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendFile(exchange, "Ghi-chu-bai-tap-Nhom-1.txt", "text/plain; charset=UTF-8", txtDataCache);
        }
    }

    private class DownloadPngHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendFile(exchange, "SimpleBrowser-Logo.png", "image/png", pngDataCache);
        }
    }

    private class DownloadZipHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendFile(exchange, "SimpleBrowser-SourceCode.zip", "application/zip", zipDataCache);
        }
    }

    /**
     * Tệp lớn 5MB truyền theo luồng (chunked/streaming) để quan sát thanh tiến trình và tốc độ tải.
     */
    private class DownloadLargeBinaryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String fileName = "Test-File-5MB.bin";
            int totalBytes = 5 * 1024 * 1024; // 5 MB

            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(totalBytes));
            exchange.getResponseHeaders().set("Server", "SimpleBrowser-DemoServer/1.0");

            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }

            exchange.sendResponseHeaders(200, totalBytes);

            byte[] chunk = new byte[64 * 1024]; // 64 KB
            for (int i = 0; i < chunk.length; i++) {
                chunk[i] = (byte) (i % 256);
            }

            try (OutputStream os = exchange.getResponseBody()) {
                int bytesWritten = 0;
                while (bytesWritten < totalBytes) {
                    int toWrite = Math.min(chunk.length, totalBytes - bytesWritten);
                    os.write(chunk, 0, toWrite);
                    bytesWritten += toWrite;
                    os.flush();
                    try {
                        // Delay nhẹ 8ms mỗi 64KB để tốc độ ~8 MB/s, người dùng nhìn rõ tiến độ 0% -> 100%
                        Thread.sleep(8);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    /**
     * Endpoint nhận form POST để kiểm tra gửi dữ liệu form.
     */
    private class FeedbackPostHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                String name = "Ẩn danh";
                String comment = "";
                String[] pairs = body.split("&");
                for (String pair : pairs) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                        String val = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        if ("name".equalsIgnoreCase(key)) name = val;
                        if ("comment".equalsIgnoreCase(key)) comment = val;
                    }
                }

                String responseHtml = "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                        "<title>Xác nhận gửi Form POST</title>" +
                        "<style>" +
                        "body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; background: #f0f4f9; padding: 40px; text-align: center; }" +
                        ".card { background: white; border-radius: 12px; padding: 32px; max-width: 500px; margin: 0 auto; box-shadow: 0 4px 16px rgba(0,0,0,0.1); }" +
                        "h2 { color: #1e8e3e; }" +
                        "p { color: #3c4043; line-height: 1.6; text-align: left; }" +
                        "a { display: inline-block; margin-top: 20px; padding: 10px 20px; background: #1a73e8; color: white; border-radius: 6px; text-decoration: none; }" +
                        "</style></head><body>" +
                        "<div class='card'>" +
                        "<h2>✅ Gửi POST Thành Công!</h2>" +
                        "<p><b>Người gửi:</b> " + name + "</p>" +
                        "<p><b>Nội dung góp ý:</b> " + comment + "</p>" +
                        "<p><b>Dữ liệu thô nhận được:</b> <code>" + body + "</code></p>" +
                        "<a href='/'>Quay lại Trang Mẫu</a>" +
                        "</div></body></html>";

                byte[] respBytes = responseHtml.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, respBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(respBytes);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
            }
        }
    }

    private void sendFile(HttpExchange exchange, String fileName, String contentType, byte[] data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(data.length));
        exchange.getResponseHeaders().set("Server", "SimpleBrowser-DemoServer/1.0");

        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }

        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    // =========================================================================
    // Dữ liệu mẫu tĩnh
    // =========================================================================

    private byte[] generateTxtContent() {
        String content = "=========================================================\n" +
                "  BÀI TẬP LỚN: SIMPLE WEB BROWSER – NHÓM 1\n" +
                "=========================================================\n" +
                "Môn học: Lập trình Java / Lập trình Mạng\n" +
                "Tính năng: HTTP Client (GET, POST, HEAD), JSoup Tag Stats,\n" +
                "           History Back/Forward, File Downloader & JavaFX WebView.\n\n" +
                "Xin chúc mừng! Bạn đã tải thành công tệp văn bản mẫu UTF-8 từ\n" +
                "máy chủ cục bộ DemoServer của Simple Web Browser.\n\n" +
                "Dữ liệu tải về đã được ghi nhận vào tab 'Tải xuống (Downloads)'\n" +
                "kèm thanh tiến trình 100% và tốc độ tải thực tế.\n" +
                "=========================================================\n";
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] generatePdfContent() {
        // Sinh cấu trúc tệp PDF 1.4 hợp lệ chuẩn W3C / ISO 32000
        StringBuilder sb = new StringBuilder();
        sb.append("%PDF-1.4\n");
        sb.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        sb.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        sb.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n");

        String streamText = "BT\n/F1 22 Tf\n50 720 Td\n(SIMPLE WEB BROWSER - NHOM 1) Tj\n" +
                "/F1 14 Tf\n50 680 Td\n(TAI LIEU HUONG DAN KIEM THU TAI FILE THANH CONG!) Tj\n" +
                "/F1 11 Tf\n50 640 Td\n(Tep tin PDF mau duoc tao boi DemoServer noi bo.) Tj\n" +
                "50 620 Td\n(Trinh duyet ho tro day du tinh nang Streaming Download voi Progress Bar.) Tj\n" +
                "ET\n";

        sb.append("4 0 obj\n<< /Length ").append(streamText.length()).append(" >>\nstream\n");
        sb.append(streamText);
        sb.append("endstream\nendobj\n");
        sb.append("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");

        // Độn thêm comment hợp lệ để tệp đạt kích thước ~120 KB thực tế
        sb.append("% PADDING FOR REALISTIC SIZE\n");
        for (int i = 0; i < 1500; i++) {
            sb.append("% Pad_chunk_").append(i).append("___________________________________________________________\n");
        }

        sb.append("xref\n0 6\n0000000000 65535 f \n");
        sb.append("0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \n0000000244 00000 n \n0000000550 00000 n \n");
        sb.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n625\n%%EOF\n");

        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private byte[] generatePngContent() {
        try {
            int width = 200;
            int height = 200;
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Nền bo góc gradient
            GradientPaint gp = new GradientPaint(0, 0, new Color(26, 115, 232), width, height, new Color(13, 71, 161));
            g.setPaint(gp);
            g.fillRoundRect(10, 10, width - 20, height - 20, 40, 40);

            // Viền ngoài
            g.setColor(new Color(255, 255, 255, 180));
            g.setStroke(new BasicStroke(3));
            g.drawRoundRect(10, 10, width - 20, height - 20, 40, 40);

            // Biểu tượng quả địa cầu & trình duyệt
            g.setColor(Color.WHITE);
            g.fillOval(55, 45, 90, 90);

            g.setColor(new Color(26, 115, 232));
            g.setStroke(new BasicStroke(2));
            g.drawOval(55, 45, 90, 90);
            g.drawLine(55, 90, 145, 90);
            g.drawOval(75, 45, 50, 90);

            // Chữ
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.drawString("Simple Browser", 45, 165);

            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception ex) {
            return new byte[]{ (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' };
        }
    }

    private byte[] generateZipContent() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            // File 1 trong ZIP
            ZipEntry entry1 = new ZipEntry("README.txt");
            zos.putNextEntry(entry1);
            zos.write(("Simple Web Browser Project - Group 1\n" +
                    "Embedded Demo Archive containing sample source assets.\n" +
                    "Download test successfully executed!\n").getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // File 2 trong ZIP
            ZipEntry entry2 = new ZipEntry("app.properties");
            zos.putNextEntry(entry2);
            zos.write(("app.name=SimpleWebBrowser\n" +
                    "app.version=1.0.0\n" +
                    "app.engine=JavaFX WebView WebKit\n").getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // File 3 trong ZIP
            ZipEntry entry3 = new ZipEntry("SampleCode.java");
            zos.putNextEntry(entry3);
            zos.write(("public class SampleCode {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        System.out.println(\"Hello from downloaded zip archive!\");\n" +
                    "    }\n" +
                    "}\n").getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /**
     * Tạo mã nguồn HTML5/CSS3 cho trang web mẫu Demo Center.
     */
    private String buildDemoPageHtml() {
        String base = getDemoUrl();
        return "<!DOCTYPE html>\n" +
                "<html lang=\"vi\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>Trang Web Mẫu & Tải Tệp Tin – Simple Web Browser</title>\n" +
                "  <style>\n" +
                "    :root {\n" +
                "      --primary: #1a73e8;\n" +
                "      --primary-dark: #1557b0;\n" +
                "      --success: #1e8e3e;\n" +
                "      --warning: #f9ab00;\n" +
                "      --purple: #9334e6;\n" +
                "      --bg: #f8f9fa;\n" +
                "      --card-bg: #ffffff;\n" +
                "      --text-main: #202124;\n" +
                "      --text-sub: #5f6368;\n" +
                "      --border: #dadce0;\n" +
                "    }\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; }\n" +
                "    body {\n" +
                "      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n" +
                "      background-color: var(--bg);\n" +
                "      color: var(--text-main);\n" +
                "      line-height: 1.6;\n" +
                "      padding-bottom: 60px;\n" +
                "    }\n" +
                "    header {\n" +
                "      background: linear-gradient(135deg, #1a73e8 0%, #0d47a1 100%);\n" +
                "      color: white;\n" +
                "      padding: 36px 24px;\n" +
                "      text-align: center;\n" +
                "      box-shadow: 0 4px 12px rgba(0,0,0,0.12);\n" +
                "    }\n" +
                "    header h1 { font-size: 26px; margin-bottom: 8px; font-weight: 700; }\n" +
                "    header p { font-size: 14px; opacity: 0.92; max-width: 680px; margin: 0 auto; }\n" +
                "    .container { max-width: 980px; margin: 28px auto; padding: 0 16px; }\n" +
                "    .alert-banner {\n" +
                "      background: #e8f0fe;\n" +
                "      border-left: 4px solid var(--primary);\n" +
                "      padding: 14px 18px;\n" +
                "      border-radius: 6px;\n" +
                "      margin-bottom: 24px;\n" +
                "      font-size: 13px;\n" +
                "      color: #174ea6;\n" +
                "    }\n" +
                "    .section-title {\n" +
                "      font-size: 18px;\n" +
                "      margin: 28px 0 14px 0;\n" +
                "      color: var(--text-main);\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 8px;\n" +
                "    }\n" +
                "    .card-grid {\n" +
                "      display: grid;\n" +
                "      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));\n" +
                "      gap: 16px;\n" +
                "    }\n" +
                "    .file-card {\n" +
                "      background: var(--card-bg);\n" +
                "      border: 1px solid var(--border);\n" +
                "      border-radius: 12px;\n" +
                "      padding: 20px;\n" +
                "      box-shadow: 0 2px 6px rgba(0,0,0,0.04);\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "      justify-content: space-between;\n" +
                "      transition: transform 0.2s, box-shadow 0.2s;\n" +
                "    }\n" +
                "    .file-card:hover {\n" +
                "      transform: translateY(-2px);\n" +
                "      box-shadow: 0 6px 16px rgba(0,0,0,0.08);\n" +
                "    }\n" +
                "    .file-header {\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 12px;\n" +
                "      margin-bottom: 12px;\n" +
                "    }\n" +
                "    .file-icon { font-size: 32px; }\n" +
                "    .file-title {\n" +
                "      font-size: 15px;\n" +
                "      font-weight: 600;\n" +
                "      color: var(--text-main);\n" +
                "      word-break: break-all;\n" +
                "    }\n" +
                "    .file-desc {\n" +
                "      font-size: 12px;\n" +
                "      color: var(--text-sub);\n" +
                "      margin-bottom: 16px;\n" +
                "      line-height: 1.5;\n" +
                "    }\n" +
                "    .file-meta {\n" +
                "      display: flex;\n" +
                "      justify-content: space-between;\n" +
                "      align-items: center;\n" +
                "      margin-bottom: 16px;\n" +
                "    }\n" +
                "    .badge {\n" +
                "      font-size: 11px;\n" +
                "      padding: 3px 8px;\n" +
                "      border-radius: 12px;\n" +
                "      font-weight: 600;\n" +
                "    }\n" +
                "    .badge-pdf { background: #fce8e6; color: #c5221f; }\n" +
                "    .badge-txt { background: #e8f0fe; color: #1a73e8; }\n" +
                "    .badge-png { background: #e6f4ea; color: #137333; }\n" +
                "    .badge-zip { background: #fef7e0; color: #b06000; }\n" +
                "    .badge-bin { background: #f3e8fd; color: #7627bb; }\n" +
                "    .download-btn {\n" +
                "      display: inline-block;\n" +
                "      width: 100%;\n" +
                "      text-align: center;\n" +
                "      padding: 10px 14px;\n" +
                "      background-color: var(--primary);\n" +
                "      color: white;\n" +
                "      text-decoration: none;\n" +
                "      border-radius: 8px;\n" +
                "      font-weight: 600;\n" +
                "      font-size: 13px;\n" +
                "      border: none;\n" +
                "      cursor: pointer;\n" +
                "      transition: background 0.2s;\n" +
                "    }\n" +
                "    .download-btn:hover { background-color: var(--primary-dark); }\n" +
                "    .download-btn.btn-large { background-color: var(--purple); }\n" +
                "    .download-btn.btn-large:hover { background-color: #6a1b9a; }\n" +
                "    .toast-box {\n" +
                "      display: none;\n" +
                "      position: fixed;\n" +
                "      bottom: 24px;\n" +
                "      right: 24px;\n" +
                "      background: #323232;\n" +
                "      color: white;\n" +
                "      padding: 14px 20px;\n" +
                "      border-radius: 8px;\n" +
                "      box-shadow: 0 4px 16px rgba(0,0,0,0.2);\n" +
                "      font-size: 13px;\n" +
                "      z-index: 9999;\n" +
                "      animation: slideIn 0.3s ease;\n" +
                "    }\n" +
                "    @keyframes slideIn { from { transform: translateY(20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }\n" +
                "    .stats-info-box {\n" +
                "      background: white;\n" +
                "      border: 1px solid var(--border);\n" +
                "      border-radius: 12px;\n" +
                "      padding: 20px;\n" +
                "      margin-top: 24px;\n" +
                "    }\n" +
                "    .stats-tags-list {\n" +
                "      display: flex;\n" +
                "      flex-wrap: wrap;\n" +
                "      gap: 12px;\n" +
                "      margin-top: 10px;\n" +
                "    }\n" +
                "    .tag-badge {\n" +
                "      background: #f1f3f4;\n" +
                "      padding: 6px 12px;\n" +
                "      border-radius: 6px;\n" +
                "      font-size: 12px;\n" +
                "      font-family: monospace;\n" +
                "    }\n" +
                "    .form-box {\n" +
                "      background: white;\n" +
                "      border: 1px solid var(--border);\n" +
                "      border-radius: 12px;\n" +
                "      padding: 20px;\n" +
                "      margin-top: 24px;\n" +
                "    }\n" +
                "    .form-group {\n" +
                "      margin-bottom: 12px;\n" +
                "    }\n" +
                "    .form-group label { display: block; font-size: 12px; font-weight: 600; margin-bottom: 4px; }\n" +
                "    .form-group input { width: 100%; padding: 8px 12px; border: 1px solid var(--border); border-radius: 6px; font-size: 13px; }\n" +
                "    .btn-submit { background: var(--success); color: white; border: none; padding: 10px 18px; border-radius: 6px; font-weight: 600; cursor: pointer; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "  <header>\n" +
                "    <div class=\"logo-area\">\n" +
                "      <img src=\"" + base + "download/SimpleBrowser-Logo.png\" alt=\"Logo\" width=\"48\" height=\"48\" style=\"vertical-align: middle; border-radius: 8px; margin-bottom: 8px;\">\n" +
                "    </div>\n" +
                "    <h1>TRANG WEB MẪU & TẢI TỆP TIN – NHÓM 1</h1>\n" +
                "    <p>Trang web nội bộ tích hợp sẵn trong Simple Web Browser giúp bạn kiểm thử ngay tính năng duyệt web hiện đại (WebKit HTML5/CSS3) và tải xuống các tệp tin chỉ bằng một cú nhấp chuột.</p>\n" +
                "  </header>\n" +
                "\n" +
                "  <div class=\"container\">\n" +
                "\n" +
                "    <div class=\"alert-banner\">\n" +
                "      💡 <b>Hướng dẫn sử dụng:</b> Hãy nhấp vào bất kỳ nút <b>⬇ Tải xuống</b> nào dưới đây. Trình duyệt sẽ mở hộp thoại lưu tệp, tự động chuyển sang tab <b>Tải xuống (Downloads)</b> và hiển thị tiến trình tải streaming (0% &rarr; 100%) kèm tốc độ thực tế!\n" +
                "    </div>\n" +
                "\n" +
                "    <h2 class=\"section-title\">📁 1. Danh sách Tệp tin Mẫu sẵn sàng Tải xuống</h2>\n" +
                "\n" +
                "    <div class=\"card-grid\">\n" +
                "\n" +
                "      <!-- File 1: PDF -->\n" +
                "      <div class=\"file-card\">\n" +
                "        <div>\n" +
                "          <div class=\"file-header\">\n" +
                "            <span class=\"file-icon\">📄</span>\n" +
                "            <div>\n" +
                "              <div class=\"file-title\">Huong-dan-Java-Web-Browser.pdf</div>\n" +
                "              <span class=\"badge badge-pdf\">PDF Document</span>\n" +
                "            </div>\n" +
                "          </div>\n" +
                "          <p class=\"file-desc\">Tài liệu PDF hướng dẫn kiểm thử các tính năng duyệt web, thống kê JSoup và điều hướng Back/Forward.</p>\n" +
                "        </div>\n" +
                "        <div>\n" +
                "          <div class=\"file-meta\">\n" +
                "            <span style=\"font-size: 11px; color: #777;\">Kích thước: <b>~120 KB</b></span>\n" +
                "          </div>\n" +
                "          <a href=\"" + base + "download/Huong-dan-Java-Web-Browser.pdf\" download=\"Huong-dan-Java-Web-Browser.pdf\" class=\"download-btn\">⬇ Tải xuống PDF</a>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "\n" +
                "      <!-- File 2: TXT -->\n" +
                "      <div class=\"file-card\">\n" +
                "        <div>\n" +
                "          <div class=\"file-header\">\n" +
                "            <span class=\"file-icon\">📝</span>\n" +
                "            <div>\n" +
                "              <div class=\"file-title\">Ghi-chu-bai-tap-Nhom-1.txt</div>\n" +
                "              <span class=\"badge badge-txt\">Plain Text</span>\n" +
                "            </div>\n" +
                "          </div>\n" +
                "          <p class=\"file-desc\">Tệp văn bản thuần UTF-8 ghi chú tóm tắt đề bài tập, danh sách công nghệ và tác giả sinh viên.</p>\n" +
                "        </div>\n" +
                "        <div>\n" +
                "          <div class=\"file-meta\">\n" +
                "            <span style=\"font-size: 11px; color: #777;\">Kích thước: <b>~2 KB</b></span>\n" +
                "          </div>\n" +
                "          <a href=\"" + base + "download/Ghi-chu-bai-tap-Nhom-1.txt\" download=\"Ghi-chu-bai-tap-Nhom-1.txt\" class=\"download-btn\">⬇ Tải xuống TXT</a>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "\n" +
                "      <!-- File 3: PNG -->\n" +
                "      <div class=\"file-card\">\n" +
                "        <div>\n" +
                "          <div class=\"file-header\">\n" +
                "            <span class=\"file-icon\">🖼️</span>\n" +
                "            <div>\n" +
                "              <div class=\"file-title\">SimpleBrowser-Logo.png</div>\n" +
                "              <span class=\"badge badge-png\">PNG Image</span>\n" +
                "            </div>\n" +
                "          </div>\n" +
                "          <p class=\"file-desc\">Hình ảnh logo đại diện cho Simple Web Browser chuẩn RGBA độ phân giải cao vẽ bằng Java Graphics2D.</p>\n" +
                "        </div>\n" +
                "        <div>\n" +
                "          <div class=\"file-meta\">\n" +
                "            <span style=\"font-size: 11px; color: #777;\">Kích thước: <b>~25 KB</b></span>\n" +
                "          </div>\n" +
                "          <a href=\"" + base + "download/SimpleBrowser-Logo.png\" download=\"SimpleBrowser-Logo.png\" class=\"download-btn\">⬇ Tải xuống PNG</a>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "\n" +
                "      <!-- File 4: ZIP -->\n" +
                "      <div class=\"file-card\">\n" +
                "        <div>\n" +
                "          <div class=\"file-header\">\n" +
                "            <span class=\"file-icon\">📦</span>\n" +
                "            <div>\n" +
                "              <div class=\"file-title\">SimpleBrowser-SourceCode.zip</div>\n" +
                "              <span class=\"badge badge-zip\">ZIP Archive</span>\n" +
                "            </div>\n" +
                "          </div>\n" +
                "          <p class=\"file-desc\">Gói nén lưu trữ tệp tin mã nguồn Java, file README và thông số cấu hình ứng dụng.</p>\n" +
                "        </div>\n" +
                "        <div>\n" +
                "          <div class=\"file-meta\">\n" +
                "            <span style=\"font-size: 11px; color: #777;\">Kích thước: <b>~30 KB</b></span>\n" +
                "          </div>\n" +
                "          <a href=\"" + base + "download/SimpleBrowser-SourceCode.zip\" download=\"SimpleBrowser-SourceCode.zip\" class=\"download-btn\">⬇ Tải xuống ZIP</a>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "\n" +
                "      <!-- File 5: Large Binary 5MB -->\n" +
                "      <div class=\"file-card\" style=\"border-color: #d7aefb;\">\n" +
                "        <div>\n" +
                "          <div class=\"file-header\">\n" +
                "            <span class=\"file-icon\">⚡</span>\n" +
                "            <div>\n" +
                "              <div class=\"file-title\">Test-File-5MB.bin</div>\n" +
                "              <span class=\"badge badge-bin\">5 MB Streaming</span>\n" +
                "            </div>\n" +
                "          </div>\n" +
                "          <p class=\"file-desc\">Tệp nhị phân 5MB truyền từng luồng chunk 64KB giúp bạn nhìn rõ thanh tiến trình từ 0% lên 100% và tốc độ tải!</p>\n" +
                "        </div>\n" +
                "        <div>\n" +
                "          <div class=\"file-meta\">\n" +
                "            <span style=\"font-size: 11px; color: #777;\">Dung lượng: <b>5,242,880 Bytes</b></span>\n" +
                "          </div>\n" +
                "          <a href=\"" + base + "download/Test-File-5MB.bin\" download=\"Test-File-5MB.bin\" class=\"download-btn btn-large\">⚡ Tải xuống File 5MB (Test Tiến Độ)</a>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- 2. Thống kê thẻ HTML -->\n" +
                "    <div class=\"stats-info-box\">\n" +
                "      <h2 class=\"section-title\" style=\"margin-top:0;\">📊 2. Thống kê cấu trúc HTML trên trang này</h2>\n" +
                "      <p>Khi gửi yêu cầu <b>GET</b> đến địa chỉ trang này, tab <b>HTML Statistics</b> sẽ phân tích và thống kê chính xác các thẻ HTML:</p>\n" +
                "      <div class=\"stats-tags-list\">\n" +
                "        <span class=\"tag-badge\">&lt;p&gt; Paragraphs: Nhiều đoạn văn</span>\n" +
                "        <span class=\"tag-badge\">&lt;div&gt; Containers: Bố cục lưới thẻ</span>\n" +
                "        <span class=\"tag-badge\">&lt;span&gt; Badges: Nhãn định dạng tệp</span>\n" +
                "        <span class=\"tag-badge\">&lt;img&gt; Images: Biểu tượng logo</span>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- 3. Form gửi POST -->\n" +
                "    <div class=\"form-box\">\n" +
                "      <h2 class=\"section-title\" style=\"margin-top:0;\">✉️ 3. Thử nghiệm gửi dữ liệu Form (POST Method)</h2>\n" +
                "      <p style=\"font-size: 13px; color: #5f6368; margin-bottom: 12px;\">Nhập thông tin và bấm nút Gửi để kiểm tra khả năng xử lý phương thức HTTP POST của trình duyệt:</p>\n" +
                "      <form action=\"" + base + "submit-feedback\" method=\"POST\">\n" +
                "        <div class=\"form-group\">\n" +
                "          <label>Tên của bạn:</label>\n" +
                "          <input type=\"text\" name=\"name\" value=\"Sinh viên Nhóm 1\" required>\n" +
                "        </div>\n" +
                "        <div class=\"form-group\">\n" +
                "          <label>Nhận xét / Ý kiến đóng góp:</label>\n" +
                "          <input type=\"text\" name=\"comment\" value=\"Giao diện JavaFX WebView và tính năng Tải file chạy rất tuyệt vời!\" required>\n" +
                "        </div>\n" +
                "        <button type=\"submit\" class=\"btn-submit\">🚀 Gửi Thử Nghiệm POST</button>\n" +
                "      </form>\n" +
                "    </div>\n" +
                "\n" +
                "  </div>\n" +
                "\n" +
                "  <div id=\"toast\" class=\"toast-box\"></div>\n" +
                "\n" +
                "  <script>\n" +
                "    // Hiển thị toast thông báo khi nhấp nút tải xuống\n" +
                "    function showToast(fileName) {\n" +
                "      var toast = document.getElementById('toast');\n" +
                "      toast.innerHTML = '⬇️ Đang mở hộp thoại lưu tệp <b>' + fileName + '</b>... Vui lòng kiểm tra tab <b>Tải xuống (Downloads)</b>!';\n" +
                "      toast.style.display = 'block';\n" +
                "      setTimeout(function() { toast.style.display = 'none'; }, 5000);\n" +
                "    }\n" +
                "    document.querySelectorAll('.download-btn').forEach(function(btn) {\n" +
                "      btn.addEventListener('click', function() {\n" +
                "        var fn = this.getAttribute('download') || 'tep-tin';\n" +
                "        showToast(fn);\n" +
                "      });\n" +
                "    });\n" +
                "  </script>\n" +
                "\n" +
                "</body>\n" +
                "</html>";
    }
}
