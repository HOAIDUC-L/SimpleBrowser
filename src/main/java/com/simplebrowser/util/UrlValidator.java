package com.simplebrowser.util;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Tiện ích chuẩn hóa và xác thực URL của người dùng.
 */
public final class UrlValidator {

    private UrlValidator() {
        // Utility class
    }

    /**
     * Chuẩn hóa URL:
     * - Loại bỏ khoảng trắng ở 2 đầu.
     * - Nếu thiếu tiền tố giao thức (không bắt đầu bằng http:// hoặc https://), tự động thêm https://.
     *
     * @param input Chuỗi URL người dùng nhập
     * @return URL đã chuẩn hóa
     */
    public static String normalizeUrl(String input) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        // Nếu đã có giao thức (chứa ://), kiểm tra xem có phải http/https không
        int schemeColon = trimmed.indexOf("://");
        if (schemeColon > 0) {
            // Đã có tiền tố giao thức (ví dụ: http://, https://, ftp://, file://)
            // Giữ nguyên để validator kiểm tra tính hợp lệ của giao thức
            return trimmed;
        }

        // Nếu thiếu tiền tố giao thức, mặc định thêm https://
        return "https://" + trimmed;
    }

    /**
     * Kiểm tra tính hợp lệ của URL.
     * URL hợp lệ phải:
     * - Không rỗng
     * - Đúng cú pháp URI
     * - Có giao thức là "http" hoặc "https"
     * - Có Host hợp lệ (không rỗng)
     *
     * @param url Chuỗi URL cần kiểm tra
     * @return true nếu URL hợp lệ, false nếu không hợp lệ
     */
    public static boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();
        if (trimmed.equalsIgnoreCase("http://") || trimmed.equalsIgnoreCase("https://")) {
            return false;
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }
            scheme = scheme.toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return false;
            }
            String host = uri.getHost();
            return host != null && !host.isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Kiểm tra URL và trả về thông điệp lỗi cụ thể nếu không hợp lệ.
     *
     * @param url Chuỗi URL đã chuẩn hóa
     * @return Thông điệp lỗi nếu URL không hợp lệ, hoặc null nếu hợp lệ
     */
    public static String getValidationError(String url) {
        if (url == null || url.isBlank()) {
            return "URL không được để trống.";
        }
        String trimmed = url.trim();
        if (trimmed.equalsIgnoreCase("http://") || trimmed.equalsIgnoreCase("https://")) {
            return "URL thiếu tên miền (host) hoặc host không hợp lệ.";
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return "URL thiếu giao thức (http:// hoặc https://).";
            }
            String lowerScheme = scheme.toLowerCase();
            if (!lowerScheme.equals("http") && !lowerScheme.equals("https")) {
                return "Giao thức không được hỗ trợ: " + scheme + ". Ứng dụng chỉ hỗ trợ http:// hoặc https://.";
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return "URL thiếu tên miền (host) hoặc host không hợp lệ.";
            }
            return null; // Hợp lệ
        } catch (URISyntaxException e) {
            if (e.getMessage() != null && e.getMessage().contains("authority")) {
                return "URL thiếu tên miền (host) hoặc host không hợp lệ.";
            }
            return "Cú pháp URL không hợp lệ: " + e.getMessage();
        }
    }
}
