package com.simplebrowser.model;

/**
 * Model chứa kết quả thống kê các phần tử HTML và độ dài nội dung.
 * Hỗ trợ cả thẻ <img> chuẩn W3C lẫn thẻ <imag> theo đề bài.
 */
public class HtmlStats {
    private final int htmlLength;        // Chiều dài nội dung HTML tính bằng số lượng ký tự (characters)
    private final int pCount;            // Số lượng thẻ <p>
    private final int divCount;          // Số lượng thẻ <div>
    private final int spanCount;         // Số lượng thẻ <span>
    private final int imgCount;          // Tổng số thẻ <img> và <imag>
    private final int standardImgCount;  // Số lượng thẻ <img> chuẩn W3C
    private final int customImagCount;   // Số lượng thẻ <imag> theo đề bài

    public HtmlStats(int htmlLength, int pCount, int divCount, int spanCount, int standardImgCount, int customImagCount) {
        this.htmlLength = htmlLength;
        this.pCount = pCount;
        this.divCount = divCount;
        this.spanCount = spanCount;
        this.standardImgCount = standardImgCount;
        this.customImagCount = customImagCount;
        this.imgCount = standardImgCount + customImagCount;
    }

    public HtmlStats(int htmlLength, int pCount, int divCount, int spanCount, int imgCount) {
        this(htmlLength, pCount, divCount, spanCount, imgCount, 0);
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

    /**
     * Trả về tổng số lượng thẻ hình ảnh (bao gồm cả <img> và <imag>).
     */
    public int getImgCount() {
        return imgCount;
    }

    public int getStandardImgCount() {
        return standardImgCount;
    }

    public int getCustomImagCount() {
        return customImagCount;
    }

    @Override
    public String toString() {
        return "HtmlStats{" +
                "htmlLength=" + htmlLength + " characters" +
                ", pCount=" + pCount +
                ", divCount=" + divCount +
                ", spanCount=" + spanCount +
                ", imgCount=" + imgCount +
                " (standardImg=" + standardImgCount + ", customImag=" + customImagCount + ")" +
                '}';
    }
}
