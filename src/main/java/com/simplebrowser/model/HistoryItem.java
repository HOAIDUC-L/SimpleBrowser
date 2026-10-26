package com.simplebrowser.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Model biểu diễn một bản ghi trong lịch sử duyệt web.
 */
public class HistoryItem {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String timestamp;
    private final String url;
    private final String method;
    private final int statusCode;
    private final String title;

    public HistoryItem(String url, String method, int statusCode, String title) {
        this(LocalDateTime.now().format(FORMATTER), url, method, statusCode, title);
    }

    public HistoryItem(String timestamp, String url, String method, int statusCode, String title) {
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now().format(FORMATTER);
        this.url = url != null ? url : "";
        this.method = method != null ? method : "GET";
        this.statusCode = statusCode;
        this.title = (title != null && !title.isBlank()) ? title.trim() : url;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HistoryItem that = (HistoryItem) o;
        return statusCode == that.statusCode &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(url, that.url) &&
                Objects.equals(method, that.method) &&
                Objects.equals(title, that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, url, method, statusCode, title);
    }

    @Override
    public String toString() {
        return "[" + timestamp + "] " + method + " (" + statusCode + ") " + title + " - " + url;
    }
}
