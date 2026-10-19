package com.simplebrowser.service;

import com.simplebrowser.model.HtmlStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử HtmlAnalyzer - Phân tích HTML và thống kê thẻ")
class HtmlAnalyzerTest {

    private HtmlAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new HtmlAnalyzer();
    }

    @Test
    @DisplayName("Kiểm tra thống kê chính xác trên file HTML Fixture cục bộ")
    void testAnalyzeSamplePageFixture() throws IOException {
        String fixturePath = "/fixtures/sample_page.html";
        try (InputStream is = getClass().getResourceAsStream(fixturePath)) {
            assertNotNull(is, "Không tìm thấy file fixture " + fixturePath);
            String htmlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            HtmlStats stats = analyzer.analyze(htmlContent);

            assertNotNull(stats);
            // Chiều dài HTML tính bằng số lượng ký tự (characters) của chuỗi giải mã
            assertEquals(htmlContent.length(), stats.getHtmlLength(), "Chiều dài HTML phải bằng độ dài chuỗi ký tự");

            // Kiểm tra số lượng các thẻ theo nội dung fixture
            assertEquals(5, stats.getPCount(), "Số lượng thẻ <p> phải là 5");
            assertEquals(4, stats.getDivCount(), "Số lượng thẻ <div> phải là 4");
            assertEquals(6, stats.getSpanCount(), "Số lượng thẻ <span> phải là 6");
            assertEquals(3, stats.getImgCount(), "Số lượng thẻ <img> phải là 3 (chuẩn W3C)");
        }
    }

    @Test
    @DisplayName("Kiểm tra xử lý an toàn khi HTML rỗng hoặc null")
    void testAnalyzeEmptyAndNullHtml() {
        HtmlStats nullStats = analyzer.analyze(null);
        assertEquals(0, nullStats.getHtmlLength());
        assertEquals(0, nullStats.getPCount());
        assertEquals(0, nullStats.getDivCount());
        assertEquals(0, nullStats.getSpanCount());
        assertEquals(0, nullStats.getImgCount());

        HtmlStats emptyStats = analyzer.analyze("");
        assertEquals(0, emptyStats.getHtmlLength());
        assertEquals(0, emptyStats.getPCount());
        assertEquals(0, emptyStats.getDivCount());
        assertEquals(0, emptyStats.getSpanCount());
        assertEquals(0, emptyStats.getImgCount());
    }

    @Test
    @DisplayName("Kiểm tra phân tích đoạn HTML nhỏ và thẻ lồng nhau")
    void testAnalyzeSimpleSnippet() {
        String snippet = "<div><p>Đoạn 1 <span>chữ nhỏ</span></p><p>Đoạn 2 <img src='test.jpg'/></p></div>";
        HtmlStats stats = analyzer.analyze(snippet);

        assertEquals(snippet.length(), stats.getHtmlLength());
        assertEquals(2, stats.getPCount());
        assertEquals(1, stats.getDivCount());
        assertEquals(1, stats.getSpanCount());
        assertEquals(1, stats.getImgCount());
    }

    @Test
    @DisplayName("Kiểm tra phân tích HTML khuyết thiếu thẻ đóng (Malformed HTML)")
    void testAnalyzeMalformedHtml() {
        // JSoup tự động cân bằng DOM theo chuẩn W3C
        String malformed = "<p>Đoạn mở không đóng <div>Khối <span>Nội dung <img>";
        HtmlStats stats = analyzer.analyze(malformed);

        assertEquals(malformed.length(), stats.getHtmlLength());
        assertEquals(1, stats.getPCount());
        assertEquals(1, stats.getDivCount());
        assertEquals(1, stats.getSpanCount());
        assertEquals(1, stats.getImgCount());
    }

    @Test
    @DisplayName("Kiểm tra nhận diện Content-Type có phải là HTML")
    void testIsHtmlContentType() {
        assertTrue(HtmlAnalyzer.isHtmlContentType("text/html"));
        assertTrue(HtmlAnalyzer.isHtmlContentType("text/html; charset=UTF-8"));
        assertTrue(HtmlAnalyzer.isHtmlContentType("TEXT/HTML; CHARSET=ISO-8859-1"));
        assertTrue(HtmlAnalyzer.isHtmlContentType("application/xhtml+xml"));

        assertFalse(HtmlAnalyzer.isHtmlContentType("application/json"));
        assertFalse(HtmlAnalyzer.isHtmlContentType("application/xml"));
        assertFalse(HtmlAnalyzer.isHtmlContentType("image/png"));
        assertFalse(HtmlAnalyzer.isHtmlContentType("text/plain"));
        assertFalse(HtmlAnalyzer.isHtmlContentType(null));
        assertFalse(HtmlAnalyzer.isHtmlContentType(""));
    }
}
