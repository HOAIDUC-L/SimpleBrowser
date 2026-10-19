package com.simplebrowser.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử UrlValidator - Chuẩn hóa và xác thực URL")
class UrlValidatorTest {

    @Test
    @DisplayName("Kiểm tra tự động thêm tiền tố https:// khi thiếu giao thức")
    void testNormalizeUrlMissingProtocol() {
        assertEquals("https://example.com", UrlValidator.normalizeUrl("example.com"));
        assertEquals("https://www.google.com/search?q=java", UrlValidator.normalizeUrl("www.google.com/search?q=java"));
        assertEquals("https://localhost:8080/api", UrlValidator.normalizeUrl("localhost:8080/api"));
    }

    @Test
    @DisplayName("Kiểm tra giữ nguyên khi URL đã có http:// hoặc https://")
    void testNormalizeUrlWithProtocol() {
        assertEquals("http://example.com", UrlValidator.normalizeUrl("http://example.com"));
        assertEquals("https://example.com", UrlValidator.normalizeUrl("https://example.com"));
        assertEquals("http://localhost:8080", UrlValidator.normalizeUrl("  http://localhost:8080  "));
    }

    @Test
    @DisplayName("Kiểm tra xử lý chuỗi rỗng và null trong normalizeUrl")
    void testNormalizeUrlNullAndEmpty() {
        assertEquals("", UrlValidator.normalizeUrl(null));
        assertEquals("", UrlValidator.normalizeUrl(""));
        assertEquals("", UrlValidator.normalizeUrl("   "));
    }

    @Test
    @DisplayName("Kiểm tra nhận diện URL hợp lệ")
    void testIsValidUrlValidCases() {
        assertTrue(UrlValidator.isValidUrl("https://example.com"));
        assertTrue(UrlValidator.isValidUrl("http://example.com"));
        assertTrue(UrlValidator.isValidUrl("http://localhost:8080/test?param=1"));
        assertTrue(UrlValidator.isValidUrl("https://sub.domain.org/path/to/resource"));
    }

    @Test
    @DisplayName("Kiểm tra từ chối các URL không hợp lệ")
    void testIsValidUrlInvalidCases() {
        assertFalse(UrlValidator.isValidUrl(null));
        assertFalse(UrlValidator.isValidUrl(""));
        assertFalse(UrlValidator.isValidUrl("   "));
        assertFalse(UrlValidator.isValidUrl("ftp://files.example.com"));
        assertFalse(UrlValidator.isValidUrl("file:///home/user/test.html"));
        assertFalse(UrlValidator.isValidUrl("not a valid url"));
        assertFalse(UrlValidator.isValidUrl("http://"));
        assertFalse(UrlValidator.isValidUrl("https://"));
    }

    @Test
    @DisplayName("Kiểm tra thông báo lỗi cụ thể qua getValidationError")
    void testGetValidationError() {
        assertNotNull(UrlValidator.getValidationError(null));
        assertTrue(UrlValidator.getValidationError(null).contains("trống"));

        assertNotNull(UrlValidator.getValidationError("ftp://ftp.test.com"));
        assertTrue(UrlValidator.getValidationError("ftp://ftp.test.com").contains("Giao thức không được hỗ trợ"));

        assertNotNull(UrlValidator.getValidationError("http://"));
        assertTrue(UrlValidator.getValidationError("http://").contains("tên miền"));

        assertNull(UrlValidator.getValidationError("https://example.com"));
    }
}
