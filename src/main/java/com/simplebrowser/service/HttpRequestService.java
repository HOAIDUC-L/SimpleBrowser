package com.simplebrowser.service;

import com.simplebrowser.model.HtmlStats;
import com.simplebrowser.model.HttpResponseData;
import com.simplebrowser.util.UrlValidator;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Dịch vụ xử lý gửi HTTP Request (GET, POST, HEAD) bằng Java HttpClient.
 * Hỗ trợ timeout, chuyển hướng (redirect), giải mã charset và xử lý lỗi mạng an toàn.
 */
public class HttpRequestService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String DEFAULT_USER_AGENT = "SimpleWebBrowser/1.0 (Java Swing; Group 1)";

    private final HttpClient httpClient;
    private final HtmlAnalyzer htmlAnalyzer;

    public HttpRequestService() {
        this(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), new HtmlAnalyzer());
    }

    public HttpRequestService(HttpClient httpClient, HtmlAnalyzer htmlAnalyzer) {
        this.httpClient = httpClient;
        this.htmlAnalyzer = htmlAnalyzer;
    }

    /**
     * Thực hiện gửi request dựa trên method, URL và postBody.
     *
     * @param method   Phương thức HTTP ("GET", "POST", "HEAD")
     * @param rawUrl   URL người dùng nhập
     * @param postBody Dữ liệu gửi kèm khi POST (nếu có)
     * @return Đối tượng HttpResponseData chứa kết quả hoặc lỗi
     */
    public HttpResponseData sendRequest(String method, String rawUrl, String postBody) {
        String normalizedUrl = UrlValidator.normalizeUrl(rawUrl);
        String validationError = UrlValidator.getValidationError(normalizedUrl);
        if (validationError != null) {
            return HttpResponseData.error(method, normalizedUrl, validationError, 0);
        }

        long startTime = System.currentTimeMillis();
        String upperMethod = method != null ? method.trim().toUpperCase() : "GET";

        try {
            URI uri = URI.create(normalizedUrl);
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

            return switch (upperMethod) {
                case "GET" -> executeGet(reqBuilder, normalizedUrl, startTime);
                case "POST" -> executePost(reqBuilder, normalizedUrl, postBody, startTime);
                case "HEAD" -> executeHead(reqBuilder, normalizedUrl, startTime);
                default -> HttpResponseData.error(
                        upperMethod,
                        normalizedUrl,
                        "Phương thức HTTP không được hỗ trợ: " + upperMethod,
                        0
                );
            };
        } catch (IllegalArgumentException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return HttpResponseData.error(upperMethod, normalizedUrl, "Tham số URL không hợp lệ: " + e.getMessage(), executionTime);
        } catch (HttpConnectTimeoutException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return HttpResponseData.error(upperMethod, normalizedUrl, "Lỗi kết nối quá hạn (Connect Timeout) sau " + CONNECT_TIMEOUT.toSeconds() + "s: Máy chủ không phản hồi.", executionTime);
        } catch (HttpTimeoutException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return HttpResponseData.error(upperMethod, normalizedUrl, "Yêu cầu quá hạn (Request Timeout) sau " + REQUEST_TIMEOUT.toSeconds() + "s: Thời gian chờ phản hồi quá lâu.", executionTime);
        } catch (UnknownHostException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return HttpResponseData.error(upperMethod, normalizedUrl, "Lỗi DNS: Không tìm thấy máy chủ '" + e.getMessage() + "'. Vui lòng kiểm tra lại tên miền hoặc kết nối Internet.", executionTime);
        } catch (ConnectException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return HttpResponseData.error(upperMethod, normalizedUrl, "Từ chối kết nối (Connection Refused): Máy chủ từ chối tiếp nhận yêu cầu hoặc cổng không mở.", executionTime);
        } catch (SSLHandshakeException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return HttpResponseData.error(upperMethod, normalizedUrl, "Lỗi chứng chỉ bảo mật SSL/TLS: " + e.getMessage(), executionTime);
        } catch (IOException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return HttpResponseData.error(upperMethod, normalizedUrl, "Lỗi I/O mạng: " + e.getMessage(), executionTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long executionTime = System.currentTimeMillis() - startTime;
            return HttpResponseData.error(upperMethod, normalizedUrl, "Yêu cầu đã bị hủy hoặc gián đoạn.", executionTime);
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return HttpResponseData.error(upperMethod, normalizedUrl, "Lỗi không xác định: " + e.getMessage(), executionTime);
        }
    }

    /**
     * Xử lý HTTP GET.
     */
    private HttpResponseData executeGet(HttpRequest.Builder reqBuilder, String url, long startTime)
            throws IOException, InterruptedException {
        HttpRequest request = reqBuilder.GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        long executionTime = System.currentTimeMillis() - startTime;

        return processResponseWithBody("GET", url, response, executionTime);
    }

    /**
     * Xử lý HTTP POST.
     */
    private HttpResponseData executePost(HttpRequest.Builder reqBuilder, String url, String postBody, long startTime)
            throws IOException, InterruptedException {
        String payload = postBody != null ? postBody : "";
        reqBuilder.header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        HttpRequest request = reqBuilder.POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        long executionTime = System.currentTimeMillis() - startTime;

        return processResponseWithBody("POST", url, response, executionTime);
    }

    /**
     * Xử lý HTTP HEAD.
     * Phương thức HEAD chỉ nhận status code và response headers, không đọc body.
     */
    private HttpResponseData executeHead(HttpRequest.Builder reqBuilder, String url, long startTime)
            throws IOException, InterruptedException {
        HttpRequest request = reqBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        long executionTime = System.currentTimeMillis() - startTime;

        int statusCode = response.statusCode();
        Map<String, List<String>> headers = response.headers().map();
        String contentType = response.headers().firstValue("Content-Type").orElse("");

        // Không phân tích HTML và không thống kê thẻ cho phương thức HEAD
        return new HttpResponseData(
                "HEAD",
                url,
                statusCode,
                null,
                headers,
                "", // HEAD không có body
                executionTime,
                contentType,
                false,
                null, // Không có thống kê HTML
                null
        );
    }

    /**
     * Xử lý phản hồi có body (GET / POST), giải mã charset và phân tích HTML nếu có.
     */
    private HttpResponseData processResponseWithBody(String method,
                                                     String url,
                                                     HttpResponse<byte[]> response,
                                                     long executionTime) {
        int statusCode = response.statusCode();
        Map<String, List<String>> headers = response.headers().map();
        String contentType = response.headers().firstValue("Content-Type").orElse("");

        // Giải mã byte array sang chuỗi ký tự theo charset máy chủ khai báo
        Charset charset = extractCharset(contentType);
        byte[] bodyBytes = response.body();
        String body = bodyBytes != null ? new String(bodyBytes, charset) : "";

        // Kiểm tra xem phản hồi có phải là HTML hay không
        boolean isHtml = HtmlAnalyzer.isHtmlContentType(contentType) || isLikelyHtmlContent(body);

        HtmlStats stats = null;
        if (isHtml && !body.isBlank()) {
            stats = htmlAnalyzer.analyze(body);
        }

        return new HttpResponseData(
                method,
                url,
                statusCode,
                null,
                headers,
                body,
                executionTime,
                contentType,
                isHtml,
                stats,
                null
        );
    }

    /**
     * Nhận diện dự phòng xem nội dung có phải là HTML khi Content-Type bị thiếu hoặc không chuẩn.
     */
    private boolean isLikelyHtmlContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String preview = content.substring(0, Math.min(content.length(), 256)).trim().toLowerCase();
        return preview.startsWith("<!doctype html") || preview.startsWith("<html");
    }

    /**
     * Trích xuất Charset từ chuỗi Content-Type header.
     */
    private Charset extractCharset(String contentType) {
        if (contentType != null && !contentType.isBlank()) {
            String lower = contentType.toLowerCase();
            int idx = lower.indexOf("charset=");
            if (idx != -1) {
                String csName = contentType.substring(idx + 8).trim();
                int semicolon = csName.indexOf(';');
                if (semicolon != -1) {
                    csName = csName.substring(0, semicolon).trim();
                }
                csName = csName.replace("\"", "").replace("'", "").trim();
                try {
                    return Charset.forName(csName);
                } catch (Exception ignored) {
                    // Nếu charset không hợp lệ, dùng fallback UTF-8
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}
