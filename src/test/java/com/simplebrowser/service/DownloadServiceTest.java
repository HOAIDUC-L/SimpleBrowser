package com.simplebrowser.service;

import com.simplebrowser.model.DownloadItem;
import com.simplebrowser.model.HttpResponseData;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử DownloadService - Nhận diện, trích xuất tên tệp và tải streaming")
class DownloadServiceTest {

    private static HttpServer localServer;
    private static int serverPort;

    @BeforeAll
    static void startServer() throws IOException {
        localServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverPort = localServer.getAddress().getPort();

        // Endpoint phục vụ tải file nhị phân mẫu
        localServer.createContext("/download-test.zip", exchange -> {
            byte[] fileData = "SAMPLE_ZIP_BINARY_DATA_CONTENT_12345".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"sample_archive.zip\"");
            exchange.sendResponseHeaders(200, fileData.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileData);
            }
        });

        localServer.start();
    }

    @AfterAll
    static void stopServer() {
        if (localServer != null) {
            localServer.stop(0);
        }
    }

    @Test
    @DisplayName("Kiểm tra trích xuất tên tệp tin từ Header Content-Disposition và URL")
    void testExtractFileName() {
        // 1. Quoted filename
        assertEquals("document.pdf",
                DownloadService.extractFileName("https://example.com/download", "attachment; filename=\"document.pdf\"", "application/pdf"));

        // 2. RFC 5987 filename* (UTF-8 URL-encoded)
        assertEquals("báo cáo 2026.docx",
                DownloadService.extractFileName("https://example.com/download", "attachment; filename*=UTF-8''b%C3%A1o%20c%C3%A1o%202026.docx", "application/msword"));

        // 3. Simple unquoted filename
        assertEquals("setup.exe",
                DownloadService.extractFileName("https://example.com/files", "attachment; filename=setup.exe", "application/octet-stream"));

        // 4. Trích xuất từ URL path khi header không có tên
        assertEquals("archive.tar.gz",
                DownloadService.extractFileName("https://example.com/files/archive.tar.gz", null, "application/gzip"));

        // 5. Ngăn chặn tấn công Path Traversal
        assertEquals("passwd",
                DownloadService.extractFileName("https://example.com/download", "attachment; filename=\"../../../../etc/passwd\"", "text/plain"));
    }

    @Test
    @DisplayName("Kiểm tra nhận diện tài nguyên tệp tin cần tải về (isDownloadable)")
    void testIsDownloadable() {
        // Phản hồi có header Content-Disposition: attachment
        Map<String, List<String>> headersWithDisp = Map.of(
                "Content-Disposition", List.of("attachment; filename=\"report.pdf\"")
        );
        HttpResponseData respAttachment = new HttpResponseData("GET", "https://example.com/file", 200, "OK",
                headersWithDisp, "", 100, "application/pdf", false, null, "");
        assertTrue(DownloadService.isDownloadable(respAttachment));

        // Phản hồi có Content-Type là application/zip
        HttpResponseData respZip = new HttpResponseData("GET", "https://example.com/data.zip", 200, "OK",
                Collections.emptyMap(), "", 100, "application/zip", false, null, "");
        assertTrue(DownloadService.isDownloadable(respZip));

        // Phản hồi HTML thông thường -> không phải tệp tải về
        HttpResponseData respHtml = new HttpResponseData("GET", "https://example.com/index.html", 200, "OK",
                Collections.emptyMap(), "<html><body>Test</body></html>", 100, "text/html; charset=UTF-8", true, null, "");
        assertFalse(DownloadService.isDownloadable(respHtml));
    }

    @Test
    @DisplayName("Kiểm tra tải tệp tin chạy nền và callback listener")
    void testDownloadFileExecution(@TempDir Path tempDir) throws Exception {
        DownloadService downloadService = new DownloadService();
        String fileUrl = "http://127.0.0.1:" + serverPort + "/download-test.zip";
        Path destFile = tempDir.resolve("downloaded_sample.zip");

        CountDownLatch latch = new CountDownLatch(1);

        DownloadItem item = downloadService.startDownload(fileUrl, destFile, new DownloadListener() {
            @Override
            public void onProgress(DownloadItem item) {
                // callback tiến độ
            }

            @Override
            public void onCompleted(DownloadItem item) {
                latch.countDown();
            }

            @Override
            public void onFailed(DownloadItem item, Exception e) {
                latch.countDown();
            }
        });

        assertNotNull(item);
        assertEquals("downloaded_sample.zip", item.getFileName());

        // Chờ tải xong trong tối đa 5 giây
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "Tiến trình tải tệp không hoàn tất trong thời gian cho phép");

        // Kiểm tra kết quả
        assertEquals(DownloadItem.Status.COMPLETED, item.getStatus());
        assertEquals(100, item.getProgress());
        assertTrue(Files.exists(destFile));
        assertTrue(Files.size(destFile) > 0);

        String downloadedContent = Files.readString(destFile);
        assertEquals("SAMPLE_ZIP_BINARY_DATA_CONTENT_12345", downloadedContent);
    }
}
