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
            assertEquals(3, stats.getStandardImgCount());
            assertEquals(0, stats.getCustomImagCount());
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
        assertEquals(0, nullStats.getStandardImgCount());
        assertEquals(0, nullStats.getCustomImagCount());

        HtmlStats emptyStats = analyzer.analyze("");
        assertEquals(0, emptyStats.getHtmlLength());
        assertEquals(0, emptyStats.getPCount());
        assertEquals(0, emptyStats.getDivCount());
        assertEquals(0, emptyStats.getSpanCount());
        assertEquals(0, emptyStats.getImgCount());
        assertEquals(0, emptyStats.getStandardImgCount());
        assertEquals(0, emptyStats.getCustomImagCount());
    }

    @Test
    @DisplayName("Kiểm tra phân tích HTML có cả thẻ <img> chuẩn W3C và thẻ <imag> theo đề bài")
    void testAnalyzeBothImgAndImagTags() {
        String htmlWithBoth = "<div>" +
                "<p>Trang kiểm thử hình ảnh</p>" +
                "<img src='standard.jpg' alt='W3C Image'/>" +
                "<imag src='custom1.png' alt='Assignment Image 1'></imag>" +
                "<imag src='custom2.png' alt='Assignment Image 2'/>" +
                "<span>Chú thích ảnh</span>" +
                "</div>";

        HtmlStats stats = analyzer.analyze(htmlWithBoth);

        assertEquals(htmlWithBoth.length(), stats.getHtmlLength());
        assertEquals(1, stats.getPCount());
        assertEquals(1, stats.getDivCount());
        assertEquals(1, stats.getSpanCount());

        // Kiểm tra chi tiết thẻ hình ảnh
        assertEquals(1, stats.getStandardImgCount(), "Có 1 thẻ <img> chuẩn W3C");
        assertEquals(2, stats.getCustomImagCount(), "Có 2 thẻ <imag> theo đề bài");
        assertEquals(3, stats.getImgCount(), "Tổng số thẻ hình ảnh (img + imag) phải là 3");
    }

    @Test
    @DisplayName("Kiểm tra phân tích văn bản thuần không có thẻ HTML nào")
    void testAnalyzeHtmlWithoutTags() {
        String plainText = "Đây là văn bản thuần túy không chứa bất kỳ thẻ HTML nào.";
        HtmlStats stats = analyzer.analyze(plainText);

        assertEquals(plainText.length(), stats.getHtmlLength());
        assertEquals(0, stats.getPCount());
        assertEquals(0, stats.getDivCount());
        assertEquals(0, stats.getSpanCount());
        assertEquals(0, stats.getImgCount());
        assertEquals(0, stats.getStandardImgCount());
        assertEquals(0, stats.getCustomImagCount());
    }

    @Test
    @DisplayName("Kiểm tra phân tích chuỗi không phải HTML (chuỗi JSON)")
    void testAnalyzeNonHtmlJsonString() {
        String jsonString = "{\"name\": \"SimpleBrowser\", \"version\": \"1.0.0\", \"active\": true}";
        HtmlStats stats = analyzer.analyze(jsonString);

        // Chiều dài ký tự vẫn được tính cho chuỗi
        assertEquals(jsonString.length(), stats.getHtmlLength());
        // Số lượng thẻ phải bằng 0 vì JSON không có thẻ HTML
        assertEquals(0, stats.getPCount());
        assertEquals(0, stats.getDivCount());
        assertEquals(0, stats.getSpanCount());
        assertEquals(0, stats.getImgCount());
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
