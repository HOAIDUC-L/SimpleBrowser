package com.simplebrowser.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Model chứa thông tin toàn diện về phản hồi HTTP và kết quả phân tích.
 */
public class HttpResponseData {
    private final String method;
    private final String url;
    private final int statusCode;
    private final String statusText;
    private final Map<String, List<String>> headers;
    private final String body;
    private final long executionTimeMs;
    private final String contentType;
    private final boolean isHtml;
    private final HtmlStats htmlStats;
    private final String errorMessage;
    private final String pageTitle;

    public HttpResponseData(String method,
                            String url,
                            int statusCode,
                            String statusText,
                            Map<String, List<String>> headers,
                            String body,
                            long executionTimeMs,
                            String contentType,
                            boolean isHtml,
                            HtmlStats htmlStats,
                            String errorMessage) {
        this(method, url, statusCode, statusText, headers, body, executionTimeMs, contentType, isHtml, htmlStats, errorMessage, "");
    }

    public HttpResponseData(String method,
                            String url,
                            int statusCode,
                            String statusText,
                            Map<String, List<String>> headers,
                            String body,
                            long executionTimeMs,
                            String contentType,
                            boolean isHtml,
                            HtmlStats htmlStats,
                            String errorMessage,
                            String pageTitle) {
        this.method = method;
        this.url = url;
        this.statusCode = statusCode;
        this.statusText = statusText != null ? statusText : getStandardStatusText(statusCode);
        this.headers = headers != null ? headers : Collections.emptyMap();
        this.body = body != null ? body : "";
        this.executionTimeMs = executionTimeMs;
        this.contentType = contentType != null ? contentType : "";
        this.isHtml = isHtml;
        this.htmlStats = htmlStats;
        this.errorMessage = errorMessage;
        this.pageTitle = pageTitle != null ? pageTitle.trim() : "";
    }

    /**
     * Tạo đối tượng HttpResponseData biểu diễn lỗi trước hoặc trong khi gửi request (Network/DNS/Timeout/SSL).
     */
    public static HttpResponseData error(String method, String url, String errorMessage, long executionTimeMs) {
        return new HttpResponseData(
                method,
                url,
                0,
                "Error",
                Collections.emptyMap(),
                "",
                executionTimeMs,
                "",
                false,
                null,
                errorMessage,
                ""
        );
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isBlank();
    }

    public String getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusText() {
        return statusText;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public String getContentType() {
        return contentType;
    }

    public boolean isHtml() {
        return isHtml;
    }

    public HtmlStats getHtmlStats() {
        return htmlStats;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getPageTitle() {
        return pageTitle != null ? pageTitle : "";
    }

    /**
     * Tra cứu mô tả HTTP Status Code chuẩn.
     */
    public static String getStandardStatusText(int code) {
        return switch (code) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 303 -> "See Other";
            case 304 -> "Not Modified";
            case 307 -> "Temporary Redirect";
            case 308 -> "Permanent Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> code > 0 ? "HTTP " + code : "Unknown";
        };
    }
}
