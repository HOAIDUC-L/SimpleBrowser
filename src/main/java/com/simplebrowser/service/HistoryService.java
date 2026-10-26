package com.simplebrowser.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.simplebrowser.model.HistoryItem;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Dịch vụ quản lý lịch sử duyệt web và điều hướng Back/Forward (tương tự Chrome/Cốc Cốc).
 * Tự động đồng bộ lịch sử vào file JSON để dữ liệu không bị mất khi đóng ứng dụng.
 */
public class HistoryService {

    private static final Path DEFAULT_STORAGE_PATH = Paths.get("history.json");

    private final Path storagePath;
    private final Gson gson;
    private final List<HistoryItem> historyList;
    private final Deque<String> backStack;
    private final Deque<String> forwardStack;

    public HistoryService() {
        this(DEFAULT_STORAGE_PATH);
    }

    public HistoryService(Path storagePath) {
        this.storagePath = storagePath;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.historyList = new ArrayList<>();
        this.backStack = new ArrayDeque<>();
        this.forwardStack = new ArrayDeque<>();
        loadHistory();
    }

    /**
     * Ghi nhận việc điều hướng đến URL mới.
     * Lưu URL hiện tại vào ngăn xếp Back và xóa sạch ngăn xếp Forward.
     */
    public synchronized void recordNavigation(String newUrl, String currentUrl) {
        if (newUrl == null || newUrl.isBlank()) {
            return;
        }
        if (currentUrl != null && !currentUrl.isBlank() && !currentUrl.equalsIgnoreCase(newUrl)) {
            backStack.push(currentUrl);
            forwardStack.clear();
        }
    }

    /**
     * Thêm một bản ghi vào danh sách lịch sử duyệt web và lưu xuống tệp JSON.
     */
    public synchronized void recordVisit(String url, String method, int statusCode, String title) {
        if (url == null || url.isBlank()) {
            return;
        }
        HistoryItem item = new HistoryItem(url, method, statusCode, title);
        // Thêm vào đầu danh sách để lịch sử mới nhất hiển thị trên cùng
        historyList.add(0, item);
        saveHistory();
    }

    /**
     * Kiểm tra có thể thực hiện thao tác Back hay không.
     */
    public synchronized boolean canGoBack() {
        return !backStack.isEmpty();
    }

    /**
     * Kiểm tra có thể thực hiện thao tác Forward hay không.
     */
    public synchronized boolean canGoForward() {
        return !forwardStack.isEmpty();
    }

    /**
     * Thực hiện thao tác Back:
     * - Lấy URL trước đó từ backStack
     * - Đẩy currentUrl vào forwardStack
     */
    public synchronized String goBack(String currentUrl) {
        if (!canGoBack()) {
            return currentUrl;
        }
        String previousUrl = backStack.pop();
        if (currentUrl != null && !currentUrl.isBlank()) {
            forwardStack.push(currentUrl);
        }
        return previousUrl;
    }

    /**
     * Thực hiện thao tác Forward:
     * - Lấy URL tiếp theo từ forwardStack
     * - Đẩy currentUrl vào backStack
     */
    public synchronized String goForward(String currentUrl) {
        if (!canGoForward()) {
            return currentUrl;
        }
        String nextUrl = forwardStack.pop();
        if (currentUrl != null && !currentUrl.isBlank()) {
            backStack.push(currentUrl);
        }
        return nextUrl;
    }

    /**
     * Lấy danh sách lịch sử duyệt web (chỉ đọc).
     */
    public synchronized List<HistoryItem> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(historyList));
    }

    /**
     * Xóa toàn bộ lịch sử duyệt web và các ngăn xếp điều hướng.
     */
    public synchronized void clearHistory() {
        historyList.clear();
        backStack.clear();
        forwardStack.clear();
        saveHistory();
    }

    /**
     * Lưu danh sách lịch sử vào file JSON.
     */
    public synchronized void saveHistory() {
        try {
            if (storagePath.getParent() != null) {
                Files.createDirectories(storagePath.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(storagePath, StandardCharsets.UTF_8)) {
                gson.toJson(historyList, writer);
            }
        } catch (IOException e) {
            System.err.println("Không thể lưu lịch sử duyệt web: " + e.getMessage());
        }
    }

    /**
     * Tải danh sách lịch sử từ file JSON.
     */
    public synchronized void loadHistory() {
        if (!Files.exists(storagePath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(storagePath, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<ArrayList<HistoryItem>>() {}.getType();
            List<HistoryItem> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                historyList.clear();
                historyList.addAll(loaded);
            }
        } catch (Exception e) {
            System.err.println("Không thể đọc lịch sử duyệt web từ file: " + e.getMessage());
        }
    }
}
