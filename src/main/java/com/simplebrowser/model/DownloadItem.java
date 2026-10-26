package com.simplebrowser.model;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Model quản lý thông tin tiến trình của một tệp tin đang/đã tải về.
 */
public class DownloadItem {

    public enum Status {
        DOWNLOADING("Đang tải"),
        COMPLETED("Hoàn tất"),
        FAILED("Lỗi"),
        CANCELLED("Đã hủy");

        private final String displayName;

        Status(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final String id;
    private final String url;
    private final String fileName;
    private final Path destinationPath;
    private long totalBytes;
    private long downloadedBytes;
    private int progress;
    private String speedText;
    private Status status;
    private String errorMessage;

    public DownloadItem(String url, String fileName, Path destinationPath, long totalBytes) {
        this.id = UUID.randomUUID().toString();
        this.url = url;
        this.fileName = fileName;
        this.destinationPath = destinationPath;
        this.totalBytes = totalBytes;
        this.downloadedBytes = 0;
        this.progress = 0;
        this.speedText = "0 KB/s";
        this.status = Status.DOWNLOADING;
        this.errorMessage = null;
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getFileName() {
        return fileName;
    }

    public Path getDestinationPath() {
        return destinationPath;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    public void setDownloadedBytes(long downloadedBytes) {
        this.downloadedBytes = downloadedBytes;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = Math.min(100, Math.max(0, progress));
    }

    public String getSpeedText() {
        return speedText;
    }

    public void setSpeedText(String speedText) {
        this.speedText = speedText;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Chuyển đổi số byte sang chuỗi kích thước dễ đọc (KB, MB, GB).
     */
    public static String formatBytes(long bytes) {
        if (bytes < 0) return "Unknown";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
