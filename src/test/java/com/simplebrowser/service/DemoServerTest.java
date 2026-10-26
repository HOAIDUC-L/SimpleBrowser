package com.simplebrowser.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DemoServerTest {

    private DemoServer demoServer;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        demoServer = new DemoServer();
        demoServer.start();
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (demoServer != null) {
            demoServer.stop();
        }
    }

    @Test
    void testRootPageServesHtmlAndTagElements() throws Exception {
        assertTrue(demoServer.isRunning());
        assertTrue(demoServer.getPort() > 0);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(demoServer.getDemoUrl()))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/html"));

        String body = response.body();
        assertTrue(body.contains("TRANG WEB MẪU & TẢI TỆP TIN"));
        assertTrue(body.contains("Huong-dan-Java-Web-Browser.pdf"));
        assertTrue(body.contains("Test-File-5MB.bin"));
        assertTrue(body.contains("<p"));
        assertTrue(body.contains("<div"));
        assertTrue(body.contains("<span"));
        assertTrue(body.contains("<img"));
    }

    @Test
    void testDownloadEndpointsHeadersAndContent() throws Exception {
        String baseUrl = demoServer.getDemoUrl();

        // 1. Kiểm tra tải tệp PDF
        HttpRequest pdfReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "download/Huong-dan-Java-Web-Browser.pdf"))
                .GET()
                .build();
        HttpResponse<byte[]> pdfResp = httpClient.send(pdfReq, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, pdfResp.statusCode());
        assertTrue(pdfResp.headers().firstValue("Content-Type").orElse("").contains("application/pdf"));
        assertTrue(pdfResp.headers().firstValue("Content-Disposition").orElse("").contains("Huong-dan-Java-Web-Browser.pdf"));
        assertTrue(pdfResp.body().length > 1000);
        assertTrue(new String(pdfResp.body(), 0, 8, StandardCharsets.US_ASCII).startsWith("%PDF-1.4"));

        // 2. Kiểm tra tải tệp TXT
        HttpRequest txtReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "download/Ghi-chu-bai-tap-Nhom-1.txt"))
                .GET()
                .build();
        HttpResponse<String> txtResp = httpClient.send(txtReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, txtResp.statusCode());
        assertTrue(txtResp.headers().firstValue("Content-Disposition").orElse("").contains("Ghi-chu-bai-tap-Nhom-1.txt"));
        assertTrue(txtResp.body().contains("BÀI TẬP LỚN: SIMPLE WEB BROWSER"));

        // 3. Kiểm tra tải tệp ảnh PNG
        HttpRequest pngReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "download/SimpleBrowser-Logo.png"))
                .GET()
                .build();
        HttpResponse<byte[]> pngResp = httpClient.send(pngReq, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, pngResp.statusCode());
        assertTrue(pngResp.headers().firstValue("Content-Type").orElse("").contains("image/png"));
        assertTrue(pngResp.body().length > 100);

        // 4. Kiểm tra tải tệp ZIP
        HttpRequest zipReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "download/SimpleBrowser-SourceCode.zip"))
                .GET()
                .build();
        HttpResponse<byte[]> zipResp = httpClient.send(zipReq, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, zipResp.statusCode());
        assertTrue(zipResp.headers().firstValue("Content-Type").orElse("").contains("application/zip"));
        assertTrue(zipResp.body().length > 100);

        // 5. Kiểm tra tệp lớn 5MB bằng phương thức HEAD
        HttpRequest headLargeReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "download/Test-File-5MB.bin"))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<Void> headResp = httpClient.send(headLargeReq, HttpResponse.BodyHandlers.discarding());
        assertEquals(200, headResp.statusCode());
        assertEquals("5242880", headResp.headers().firstValue("Content-Length").orElse("0"));
        assertTrue(headResp.headers().firstValue("Content-Disposition").orElse("").contains("Test-File-5MB.bin"));
    }

    @Test
    void testPostFeedbackEndpoint() throws Exception {
        String baseUrl = demoServer.getDemoUrl();

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "submit-feedback"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("name=Tester&comment=TuyetVoi"))
                .build();

        HttpResponse<String> postResp = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, postResp.statusCode());
        assertTrue(postResp.body().contains("Gửi POST Thành Công"));
        assertTrue(postResp.body().contains("Tester"));
        assertTrue(postResp.body().contains("TuyetVoi"));
    }
}
