package com.simplebrowser.model;

/**
 * Model chứa kết quả thống kê các phần tử HTML và độ dài nội dung.
 */
public class HtmlStats {
    private final int htmlLength; // Chiều dài nội dung HTML tính bằng số lượng ký tự (characters)
    private final int pCount;     // Số lượng thẻ <p>
    private final int divCount;   // Số lượng thẻ <div>
    private final int spanCount;  // Số lượng thẻ <span>
    private final int imgCount;   // Số lượng thẻ <img> (chuẩn W3C thay cho <imag>)

    public HtmlStats(int htmlLength, int pCount, int divCount, int spanCount, int imgCount) {
        this.htmlLength = htmlLength;
        this.pCount = pCount;
        this.divCount = divCount;
        this.spanCount = spanCount;
        this.imgCount = imgCount;
    }

    public int getHtmlLength() {
        return htmlLength;
    }

    public int getPCount() {
        return pCount;
    }

    public int getDivCount() {
        return divCount;
    }

    public int getSpanCount() {
        return spanCount;
    }

    public int getImgCount() {
        return imgCount;
    }

    @Override
    public String toString() {
        return "HtmlStats{" +
                "htmlLength=" + htmlLength + " characters" +
                ", pCount=" + pCount +
                ", divCount=" + divCount +
                ", spanCount=" + spanCount +
                ", imgCount=" + imgCount +
                '}';
    }
}
