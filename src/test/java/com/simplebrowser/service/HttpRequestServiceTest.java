package com.simplebrowser.service;

import com.simplebrowser.model.HttpResponseData;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử HttpRequestService với máy chủ HTTP cục bộ")
class HttpRequestServiceTest {

    private static HttpServer localServer;
    private static int serverPort;
    private static HttpRequestService requestService;

    @BeforeAll
    static void startLocalServer() throws IOException {
        // Khởi tạo máy chủ HTTP cục bộ trên cổng ngẫu nhiên để kiểm thử hermetic
        localServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverPort = localServer.getAddress().getPort();

        // 1. Endpoint trả về HTML chuẩn
        localServer.createContext("/html", exchange -> {
            String html = "<html><body><div><p>Chào bạn <span>Nhóm 1</span></p><img src='pic.jpg'></div></body></html>";
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });

        // 2. Endpoint xử lý POST echo
        localServer.createContext("/post-echo", exchange -> {
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            String receivedBody = new String(bodyBytes, StandardCharsets.UTF_8);
            String resp = "<html><body><div><p>Echo: " + receivedBody + "</p></div></body></html>";
            byte[] respBytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, respBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(respBytes);
            }
        });

        // 3. Endpoint JSON (không phải HTML)
        localServer.createContext("/json", exchange -> {
            String json = "{\"status\": \"success\", \"message\": \"Hello World\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        // 4. Endpoint lỗi 404
        localServer.createContext("/not-found", exchange -> {
            String notFound = "<html><body><p>404 Not Found</p></body></html>";
            byte[] bytes = notFound.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(404, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        localServer.start();
        requestService = new HttpRequestService();
    }

    @AfterAll
    static void stopLocalServer() {
        if (localServer != null) {
            localServer.stop(0);
        }
    }

    private String getBaseUrl() {
        return "http://127.0.0.1:" + serverPort;
    }

    @Test
    @DisplayName("Kiểm tra gửi GET và phân tích thẻ HTML")
    void testGetHtmlRequest() {
        String url = getBaseUrl() + "/html";
        HttpResponseData data = requestService.sendRequest("GET", url, null);

        assertNotNull(data);
        assertFalse(data.hasError(), "Không được có lỗi mạng: " + data.getErrorMessage());
        assertEquals(200, data.getStatusCode());
        assertTrue(data.isHtml(), "Phản hồi phải được nhận diện là HTML");
        assertNotNull(data.getHtmlStats(), "Phải có đối tượng HtmlStats");

        // Verify HTML tags count
        // "<html><body><div><p>Chào bạn <span>Nhóm 1</span></p><img src='pic.jpg'></div></body></html>"
        assertEquals(1, data.getHtmlStats().getPCount());
        assertEquals(1, data.getHtmlStats().getDivCount());
        assertEquals(1, data.getHtmlStats().getSpanCount());
        assertEquals(1, data.getHtmlStats().getImgCount());
        assertTrue(data.getExecutionTimeMs() >= 0);
    }

    @Test
    @DisplayName("Kiểm tra gửi POST mang theo body và nhận kết quả HTML")
    void testPostRequest() {
        String url = getBaseUrl() + "/post-echo";
        String postPayload = "name=duc&age=20";
        HttpResponseData data = requestService.sendRequest("POST", url, postPayload);

        assertNotNull(data);
        assertFalse(data.hasError());
        assertEquals(200, data.getStatusCode());
        assertTrue(data.getBody().contains("Echo: name=duc&age=20"));
        assertTrue(data.isHtml());
        assertNotNull(data.getHtmlStats());
        assertEquals(1, data.getHtmlStats().getPCount());
    }

    @Test
    @DisplayName("Kiểm tra gửi HEAD: chỉ nhận headers, không có body và không thống kê HTML")
    void testHeadRequest() {
        String url = getBaseUrl() + "/html";
        HttpResponseData data = requestService.sendRequest("HEAD", url, null);

        assertNotNull(data);
        assertFalse(data.hasError());
        assertEquals(200, data.getStatusCode());
        assertEquals("", data.getBody(), "HEAD không được có body");
        assertNull(data.getHtmlStats(), "HEAD không được thống kê HTML");
        assertTrue(data.getHeaders().containsKey("Content-type") || data.getHeaders().containsKey("Content-Type"));
    }

    @Test
    @DisplayName("Kiểm tra nhận diện phản hồi không phải HTML (JSON)")
    void testJsonResponseNotHtml() {
        String url = getBaseUrl() + "/json";
        HttpResponseData data = requestService.sendRequest("GET", url, null);

        assertNotNull(data);
        assertEquals(200, data.getStatusCode());
        assertFalse(data.isHtml(), "Phản hồi JSON không phải là HTML");
        assertNull(data.getHtmlStats(), "Phản hồi JSON không được tính thống kê thẻ HTML");
        assertTrue(data.getBody().contains("Hello World"));
    }

    @Test
    @DisplayName("Kiểm tra xử lý mã phản hồi 404 Not Found")
    void test404NotFoundResponse() {
        String url = getBaseUrl() + "/not-found";
        HttpResponseData data = requestService.sendRequest("GET", url, null);

        assertNotNull(data);
        assertFalse(data.hasError(), "404 là mã HTTP hợp lệ từ máy chủ, không phải lỗi kết nối");
        assertEquals(404, data.getStatusCode());
        assertEquals("Not Found", data.getStatusText());
    }

    @Test
    @DisplayName("Kiểm tra từ chối URL không hợp lệ")
    void testInvalidUrlHandling() {
        HttpResponseData data = requestService.sendRequest("GET", "invalid://host", null);
        assertNotNull(data);
        assertTrue(data.hasError());
        assertTrue(data.getErrorMessage().contains("Giao thức"));
    }

    @Test
    @DisplayName("Kiểm tra từ chối phương thức HTTP không được hỗ trợ")
    void testUnsupportedMethodHandling() {
        String url = getBaseUrl() + "/html";
        HttpResponseData data = requestService.sendRequest("DELETE", url, null);
        assertNotNull(data);
        assertTrue(data.hasError());
        assertTrue(data.getErrorMessage().contains("không được hỗ trợ"));
    }
}
