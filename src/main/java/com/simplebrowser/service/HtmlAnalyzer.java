package com.simplebrowser.service;

import com.simplebrowser.model.HtmlStats;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Dịch vụ phân tích cú pháp HTML và thống kê các thẻ theo yêu cầu đề bài.
 * Sử dụng thư viện JSoup chuẩn W3C, tuyệt đối không dùng Regular Expression.
 */
public class HtmlAnalyzer {

    /**
     * Kiểm tra Content-Type trả về có phải là tài liệu HTML hay không.
     *
     * @param contentType Chuỗi Content-Type từ HTTP Header (ví dụ: "text/html; charset=UTF-8")
     * @return true nếu là HTML, false nếu là định dạng khác (JSON, XML, hình ảnh, nhị phân...)
     */
    public static boolean isHtmlContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String lower = contentType.toLowerCase();
        return lower.contains("text/html") || lower.contains("application/xhtml+xml");
    }

    /**
     * Phân tích nội dung HTML và thống kê độ dài cùng số lượng các thẻ: <p>, <div>, <span>, <img>.
     *
     * @param htmlContent Chuỗi HTML response body đã được giải mã
     * @return Đối tượng HtmlStats chứa kết quả thống kê
     */
    public HtmlStats analyze(String htmlContent) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return new HtmlStats(0, 0, 0, 0, 0);
        }

        // 1. Tính chiều dài nội dung HTML trả về (số lượng ký tự)
        // Lưu ý: Đây là số ký tự của chuỗi sau khi giải mã, không phải số byte (Content-Length)
        int htmlLength = htmlContent.length();

        // 2. Phân tích HTML bằng JSoup
        Document document = Jsoup.parse(htmlContent);

        // 3. Đếm số lượng các thẻ bằng JSoup CSS selector
        int pCount = document.select("p").size();
        int divCount = document.select("div").size();
        int spanCount = document.select("span").size();

        // Chuẩn W3C HTML quy định thẻ hiển thị hình ảnh là <img> (đề bài ghi <imag>)
        int imgCount = document.select("img").size();

        return new HtmlStats(htmlLength, pCount, divCount, spanCount, imgCount);
    }
}
