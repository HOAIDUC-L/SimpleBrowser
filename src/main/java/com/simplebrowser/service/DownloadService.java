package com.simplebrowser.service;

import com.simplebrowser.model.DownloadItem;
import com.simplebrowser.model.HttpResponseData;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dịch vụ tải tệp tin chạy nền (File Downloader) sử dụng Java HttpClient.
 * Hỗ trợ nhận diện tệp, trích xuất tên tệp, báo cáo tiến trình (0-100%) và tốc độ tải.
 */
public class DownloadService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final String DEFAULT_USER_AGENT = "SimpleWebBrowser/1.0 (Downloader; Group 1)";

    private final HttpClient httpClient;
    private final List<DownloadItem> downloadList;

    public DownloadService() {
        this(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public DownloadService(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.downloadList = new CopyOnWriteArrayList<>();
    }

    /**
     * Nhận diện xem phản hồi HTTP có phải là tài nguyên tệp tin cần tải về hay không.
     *
     * @param response Đối tượng HttpResponseData đã nhận
     * @return true nếu tài nguyên là tệp tải về
     */
    public static boolean isDownloadable(HttpResponseData response) {
        if (response == null || response.hasError()) {
            return false;
        }

        // 1. Kiểm tra header Content-Disposition
        List<String> dispositions = response.getHeaders().get("content-disposition");
        if (dispositions == null) {
            dispositions = response.getHeaders().get("Content-Disposition");
        }
        if (dispositions != null && !dispositions.isEmpty()) {
            String disp = dispositions.get(0).toLowerCase();
            if (disp.contains("attachment") || disp.contains("filename=")) {
                return true;
            }
        }

        // 2. Kiểm tra Content-Type dạng nhị phân hoặc media
        String contentType = response.getContentType().toLowerCase();
        if (contentType.contains("application/octet-stream") ||
                contentType.contains("application/pdf") ||
                contentType.contains("application/zip") ||
                contentType.contains("application/x-zip") ||
                contentType.contains("application/x-tar") ||
                contentType.contains("application/gzip") ||
                contentType.contains("application/x-rar") ||
                contentType.contains("application/x-7z") ||
                contentType.contains("video/") ||
                contentType.contains("audio/")) {
            return true;
        }

        return false;
    }

    /**
     * Trích xuất tên tệp tin từ Header Content-Disposition, URL path hoặc Content-Type.
     *
     * @param urlStr             URL tệp tin
     * @param contentDisposition Giá trị header Content-Disposition (nếu có)
     * @param contentType        Giá trị header Content-Type (nếu có)
     * @return Tên tệp tin gợi ý an toàn
     */
    public static String extractFileName(String urlStr, String contentDisposition, String contentType) {
        String fileName = null;

        // 1. Thử trích xuất từ header Content-Disposition
        if (contentDisposition != null && !contentDisposition.isBlank()) {
            // Định dạng: filename*=UTF-8''filename.ext (RFC 5987)
            Pattern rfc5987 = Pattern.compile("filename\\*=(?:UTF-8|utf-8)''([^;\\r\\n]+)", Pattern.CASE_INSENSITIVE);
            Matcher m1 = rfc5987.matcher(contentDisposition);
            if (m1.find()) {
                try {
                    fileName = URLDecoder.decode(m1.group(1).trim(), StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                }
            }

            // Định dạng: filename="filename.ext"
            if (fileName == null) {
                Pattern quoted = Pattern.compile("filename=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
                Matcher m2 = quoted.matcher(contentDisposition);
                if (m2.find()) {
                    fileName = m2.group(1).trim();
                }
            }

            // Định dạng: filename=filename.ext
            if (fileName == null) {
                Pattern simple = Pattern.compile("filename=([^;\\r\\n\\s]+)", Pattern.CASE_INSENSITIVE);
                Matcher m3 = simple.matcher(contentDisposition);
                if (m3.find()) {
                    fileName = m3.group(1).trim().replace("\"", "").replace("'", "");
                }
            }
        }

        // 2. Thử trích xuất từ URL path
        if (fileName == null && urlStr != null && !urlStr.isBlank()) {
            try {
                URI uri = URI.create(urlStr);
                String path = uri.getPath();
                if (path != null && !path.isBlank()) {
                    String lastSegment = Paths.get(path).getFileName() != null ?
                            Paths.get(path).getFileName().toString() : "";
                    if (!lastSegment.isBlank()) {
                        fileName = lastSegment;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 3. Fallback mặc định nếu không tìm thấy tên
        if (fileName == null || fileName.isBlank()) {
            fileName = "download_" + System.currentTimeMillis();
            if (contentType != null) {
                String lowerType = contentType.toLowerCase();
                if (lowerType.contains("pdf")) fileName += ".pdf";
                else if (lowerType.contains("zip")) fileName += ".zip";
                else if (lowerType.contains("json")) fileName += ".json";
                else if (lowerType.contains("jpeg") || lowerType.contains("jpg")) fileName += ".jpg";
                else if (lowerType.contains("png")) fileName += ".png";
                else if (lowerType.contains("mp3")) fileName += ".mp3";
                else if (lowerType.contains("mp4")) fileName += ".mp4";
                else fileName += ".bin";
            } else {
                fileName += ".bin";
            }
        }

        // Loại bỏ các ký tự nguy hiểm hoặc đường dẫn tương đối (Path Traversal Prevention)
        fileName = fileName.replace("\\", "/");
        int lastSlash = fileName.lastIndexOf('/');
        if (lastSlash != -1) {
            fileName = fileName.substring(lastSlash + 1);
        }
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    /**
     * Bắt đầu tải tệp tin chạy nền (Non-blocking).
     *
     * @param fileUrl         Địa chỉ URL của tệp
     * @param destinationPath Đường dẫn lưu tệp trên máy tính
     * @param listener        Callback nhận tiến trình
     * @return Đối tượng DownloadItem đại diện cho tiến trình tải
     */
    public DownloadItem startDownload(String fileUrl, Path destinationPath, DownloadListener listener) {
        String fileName = destinationPath.getFileName().toString();
        DownloadItem item = new DownloadItem(fileUrl, fileName, destinationPath, -1);
        downloadList.add(0, item);

        CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            long lastSpeedCheckTime = startTime;
            long lastSpeedCheckBytes = 0;

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(fileUrl))
                        .timeout(Duration.ofMinutes(10))
                        .header("User-Agent", DEFAULT_USER_AGENT)
                        .GET()
                        .build();

                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() >= 400) {
                    throw new RuntimeException("Máy chủ từ chối tải file, mã HTTP: " + response.statusCode());
                }

                long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                item.setTotalBytes(contentLength);

                if (destinationPath.getParent() != null) {
                    Files.createDirectories(destinationPath.getParent());
                }

                try (InputStream in = response.body();
                     OutputStream out = Files.newOutputStream(destinationPath)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalDownloaded = 0;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalDownloaded += bytesRead;
                        item.setDownloadedBytes(totalDownloaded);

                        if (contentLength > 0) {
                            int pct = (int) ((totalDownloaded * 100) / contentLength);
                            item.setProgress(pct);
                        }

                        // Tính tốc độ tải mỗi 500ms
                        long now = System.currentTimeMillis();
                        if (now - lastSpeedCheckTime >= 500) {
                            long bytesDiff = totalDownloaded - lastSpeedCheckBytes;
                            double secondsDiff = (now - lastSpeedCheckTime) / 1000.0;
                            double speedBps = secondsDiff > 0 ? bytesDiff / secondsDiff : 0;
                            item.setSpeedText(DownloadItem.formatBytes((long) speedBps) + "/s");

                            lastSpeedCheckTime = now;
                            lastSpeedCheckBytes = totalDownloaded;

                            if (listener != null) {
                                listener.onProgress(item);
                            }
                        }
                    }

                    // Tải hoàn tất
                    item.setProgress(100);
                    item.setStatus(DownloadItem.Status.COMPLETED);
                    item.setSpeedText("Hoàn thành");

                    if (listener != null) {
                        listener.onProgress(item);
                        listener.onCompleted(item);
                    }
                }
            } catch (Exception e) {
                item.setStatus(DownloadItem.Status.FAILED);
                item.setErrorMessage(e.getMessage());
                item.setSpeedText("Lỗi");
                if (listener != null) {
                    listener.onFailed(item, e);
                }
            }
        });

        return item;
    }

    /**
     * Lấy danh sách các tệp tin đã/đang tải về.
     */
    public List<DownloadItem> getDownloads() {
        return Collections.unmodifiableList(new ArrayList<>(downloadList));
    }

    /**
     * Xóa danh sách tải về trong bộ nhớ.
     */
    public void clearDownloads() {
        downloadList.clear();
    }
}
