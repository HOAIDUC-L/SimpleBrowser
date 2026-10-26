package com.simplebrowser.service;

import com.simplebrowser.model.HistoryItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử HistoryService - Quản lý lịch sử và điều hướng Back/Forward")
class HistoryServiceTest {

    @TempDir
    Path tempDir;

    private Path tempHistoryFile;
    private HistoryService historyService;

    @BeforeEach
    void setUp() {
        tempHistoryFile = tempDir.resolve("test_history.json");
        historyService = new HistoryService(tempHistoryFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(tempHistoryFile);
    }

    @Test
    @DisplayName("Kiểm tra ngăn xếp điều hướng Back và Forward")
    void testNavigationStacks() {
        // Ban đầu chưa có lịch sử điều hướng
        assertFalse(historyService.canGoBack());
        assertFalse(historyService.canGoForward());

        // Người dùng bắt đầu từ trang A và điều hướng sang trang B
        historyService.recordNavigation("https://site-b.com", "https://site-a.com");
        assertTrue(historyService.canGoBack());
        assertFalse(historyService.canGoForward());

        // Người dùng tiếp tục điều hướng sang trang C
        historyService.recordNavigation("https://site-c.com", "https://site-b.com");
        assertTrue(historyService.canGoBack());
        assertFalse(historyService.canGoForward());

        // Bấm Back từ trang C -> phải quay về trang B
        String backUrl = historyService.goBack("https://site-c.com");
        assertEquals("https://site-b.com", backUrl);
        assertTrue(historyService.canGoBack()); // Vẫn còn trang A
        assertTrue(historyService.canGoForward()); // Có thể Forward sang C

        // Bấm Back tiếp từ B -> quay về trang A
        String backUrl2 = historyService.goBack("https://site-b.com");
        assertEquals("https://site-a.com", backUrl2);
        assertFalse(historyService.canGoBack()); // Hết lịch sử Back
        assertTrue(historyService.canGoForward()); // Có thể Forward sang B

        // Bấm Forward từ A -> tiến tới B
        String forwardUrl = historyService.goForward("https://site-a.com");
        assertEquals("https://site-b.com", forwardUrl);
        assertTrue(historyService.canGoBack());
        assertTrue(historyService.canGoForward());

        // Bấm Forward tiếp từ B -> tiến tới C
        String forwardUrl2 = historyService.goForward("https://site-b.com");
        assertEquals("https://site-c.com", forwardUrl2);
        assertTrue(historyService.canGoBack());
        assertFalse(historyService.canGoForward()); // Đã tới trang mới nhất
    }

    @Test
    @DisplayName("Kiểm tra ghi nhận lịch sử và lưu/đọc file JSON")
    void testRecordVisitAndPersistence() {
        historyService.recordVisit("https://example.com", "GET", 200, "Example Domain");
        historyService.recordVisit("https://httpbin.org/post", "POST", 200, "HttpBin Post");

        List<HistoryItem> history = historyService.getHistory();
        assertEquals(2, history.size());
        // Lịch sử mới nhất phải ở trên cùng
        assertEquals("https://httpbin.org/post", history.get(0).getUrl());
        assertEquals("POST", history.get(0).getMethod());
        assertEquals("HttpBin Post", history.get(0).getTitle());

        // Kiểm tra file JSON đã được tạo trên đĩa
        assertTrue(Files.exists(tempHistoryFile));

        // Khởi tạo một HistoryService mới trỏ vào cùng file để kiểm tra nạp lại
        HistoryService reloadedService = new HistoryService(tempHistoryFile);
        List<HistoryItem> reloadedHistory = reloadedService.getHistory();
        assertEquals(2, reloadedHistory.size());
        assertEquals("https://httpbin.org/post", reloadedHistory.get(0).getUrl());
        assertEquals("https://example.com", reloadedHistory.get(1).getUrl());
    }

    @Test
    @DisplayName("Kiểm tra xóa toàn bộ lịch sử duyệt web")
    void testClearHistory() {
        historyService.recordNavigation("https://b.com", "https://a.com");
        historyService.recordVisit("https://a.com", "GET", 200, "Site A");
        historyService.recordVisit("https://b.com", "GET", 200, "Site B");

        assertEquals(2, historyService.getHistory().size());
        assertTrue(historyService.canGoBack());

        historyService.clearHistory();

        assertEquals(0, historyService.getHistory().size());
        assertFalse(historyService.canGoBack());
        assertFalse(historyService.canGoForward());

        // Kiểm tra file được làm trống
        HistoryService reloaded = new HistoryService(tempHistoryFile);
        assertEquals(0, reloaded.getHistory().size());
    }
}
