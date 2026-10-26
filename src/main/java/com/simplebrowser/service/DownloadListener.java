package com.simplebrowser.service;

import com.simplebrowser.model.DownloadItem;

/**
 * Interface nhận thông báo về tiến trình tải tệp tin.
 */
public interface DownloadListener {

    /**
     * Được gọi khi có dữ liệu mới được tải về (cập nhật phần trăm tiến độ, dung lượng, tốc độ).
     */
    void onProgress(DownloadItem item);

    /**
     * Được gọi khi tệp tin đã tải hoàn tất thành công.
     */
    void onCompleted(DownloadItem item);

    /**
     * Được gọi khi xảy ra lỗi trong quá trình tải.
     */
    void onFailed(DownloadItem item, Exception e);
}
