package com.simplebrowser.ui;

import com.simplebrowser.model.DownloadItem;
import com.simplebrowser.model.HistoryItem;
import com.simplebrowser.model.HtmlStats;
import com.simplebrowser.model.HttpResponseData;
import com.simplebrowser.service.DemoServer;
import com.simplebrowser.service.DownloadListener;
import com.simplebrowser.service.DownloadService;
import com.simplebrowser.service.HistoryService;
import com.simplebrowser.service.HttpRequestService;
import com.simplebrowser.util.UrlValidator;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Giao diện chính của Simple Web Browser xây dựng bằng Java Swing.
 * Tích hợp đầy đủ:
 * - GET / POST / HEAD
 * - Điều hướng: Back (◀), Forward (▶), Reload (🔄), Trang Mẫu (🏠), Download (⬇)
 * - Quản lý lịch sử duyệt web (History) tự động lưu file JSON
 * - Trình quản lý tải tệp tin (File Downloader) chạy nền kèm thanh tiến trình
 * - Web Preview (HTML5, CSS3, JavaScript) sử dụng JavaFX WebView qua JFXPanel
 * - Thống kê thẻ HTML (<p>, <div>, <span>, <img>, <imag>) bằng JSoup
 * - Đa luồng SwingWorker chống treo giao diện
 */
public class BrowserFrame extends JFrame {

    private final HttpRequestService requestService;
    private final HistoryService historyService;
    private final DownloadService downloadService;
    private final DemoServer demoServer;

    // Trạng thái điều hướng
    private String currentActiveUrl = "";
    private boolean isNavigatingHistory = false;

    // Controls trên thanh điều hướng & nhập liệu
    private JButton backButton;
    private JButton forwardButton;
    private JButton reloadButton;
    private JButton demoButton;
    private JButton downloadQuickButton;
    private JComboBox<String> methodComboBox;
    private JTextField urlTextField;
    private JButton sendButton;
    private JPanel postPanel;
    private JTextArea postBodyTextArea;

    // Thanh trạng thái & tiến trình
    private JPanel statusBannerPanel;
    private JLabel statusBadgeLabel;
    private JLabel latencyLabel;
    private JLabel contentTypeLabel;
    private JLabel sizeLabel;
    private JProgressBar progressBar;

    // Khu vực cảnh báo lỗi
    private JPanel errorAlertPanel;
    private JLabel errorAlertLabel;

    // Tabs hiển thị kết quả
    private JTabbedPane tabbedPane;
    private JavaFxWebBrowserPanel webBrowserPanel;
    private JTextArea responseBodyTextArea;
    private JTable headersTable;
    private DefaultTableModel headersTableModel;

    // Tab HTML Statistics components
    private JPanel htmlStatsContainer;
    private JLabel statsLengthValue;
    private JLabel statsPValue;
    private JLabel statsDivValue;
    private JLabel statsSpanValue;
    private JLabel statsImgValue;
    private JLabel statsImgSubLabel;
    private JLabel statsStatusNotice;

    // Tab Lịch sử (History)
    private JTable historyTable;
    private DefaultTableModel historyTableModel;

    // Tab Tải xuống (Downloads)
    private JTable downloadsTable;
    private DefaultTableModel downloadsTableModel;

    // Tab Raw / Error Details
    private JTextArea errorDetailsTextArea;

    public BrowserFrame() {
        this(new HttpRequestService(), new HistoryService(), new DemoServer());
    }

    public BrowserFrame(HttpRequestService requestService, HistoryService historyService) {
        this(requestService, historyService, new DemoServer());
    }

    public BrowserFrame(HttpRequestService requestService, HistoryService historyService, DemoServer demoServer) {
        this.requestService = requestService;
        this.historyService = historyService;
        this.downloadService = requestService.getDownloadService();
        this.demoServer = demoServer;
        if (this.demoServer != null) {
            this.demoServer.start();
        }
        initUI();
    }

    private void initUI() {
        setTitle("Simple Web Browser – Nhóm 1 (HTTP Client, History & File Downloader)");
        setSize(1120, 800);
        setMinimumSize(new Dimension(920, 640));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (demoServer != null) {
                    demoServer.stop();
                }
            }
        });

        JPanel mainContainer = new JPanel(new BorderLayout(8, 8));
        mainContainer.setBorder(new EmptyBorder(12, 12, 12, 12));

        // 1. Top Panel: Navigation Bar + URL + Method + POST Body + Status Alert
        JPanel topContainer = new JPanel(new BorderLayout(6, 6));
        topContainer.add(createUrlBarPanel(), BorderLayout.NORTH);
        topContainer.add(createPostBodyPanel(), BorderLayout.CENTER);
        topContainer.add(createStatusAndAlertPanel(), BorderLayout.SOUTH);

        mainContainer.add(topContainer, BorderLayout.NORTH);

        // 2. Center Panel: Các Tabs kết quả (Preview, Body, Headers, Thống kê, Lịch sử, Tải xuống, Logs)
        mainContainer.add(createTabsPanel(), BorderLayout.CENTER);

        setContentPane(mainContainer);

        // Nạp dữ liệu ban đầu
        updateMethodUiState();
        updateNavigationButtons();
        refreshHistoryTable();
        refreshDownloadsTable();
        clearResults();
    }

    /**
     * Tạo thanh điều hướng (Back, Forward, Reload, Download), ComboBox Method, URL TextField và nút Send.
     */
    private JPanel createUrlBarPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 0));

        // Nhóm nút điều hướng bên trái
        JPanel navGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        backButton = new JButton("◀");
        backButton.setToolTipText("Quay lại trang trước (Back)");
        backButton.setPreferredSize(new Dimension(36, 36));
        backButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        backButton.setEnabled(false);
        backButton.addActionListener(e -> onBack());

        forwardButton = new JButton("▶");
        forwardButton.setToolTipText("Tiến tới trang sau (Forward)");
        forwardButton.setPreferredSize(new Dimension(36, 36));
        forwardButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        forwardButton.setEnabled(false);
        forwardButton.addActionListener(e -> onForward());

        reloadButton = new JButton("🔄");
        reloadButton.setToolTipText("Tải lại trang hiện tại (Reload)");
        reloadButton.setPreferredSize(new Dimension(36, 36));
        reloadButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        reloadButton.addActionListener(e -> onReload());

        demoButton = new JButton("🏠 Trang Mẫu");
        demoButton.setToolTipText("Mở trang web mẫu tích hợp sẵn để kiểm thử duyệt web và tải file");
        demoButton.setPreferredSize(new Dimension(110, 36));
        demoButton.setFont(new Font("SansSerif", Font.BOLD, 11));
        demoButton.addActionListener(e -> onOpenDemoPage());

        downloadQuickButton = new JButton("⬇ Tải về");
        downloadQuickButton.setToolTipText("Tải tài nguyên tại URL này về máy");
        downloadQuickButton.setPreferredSize(new Dimension(88, 36));
        downloadQuickButton.setFont(new Font("SansSerif", Font.BOLD, 11));
        downloadQuickButton.addActionListener(e -> onDownloadCurrentUrl());

        // ComboBox Method: GET, POST, HEAD
        String[] methods = {"GET", "POST", "HEAD"};
        methodComboBox = new JComboBox<>(methods);
        methodComboBox.setFont(new Font("SansSerif", Font.BOLD, 12));
        methodComboBox.setPreferredSize(new Dimension(85, 36));
        methodComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                updateMethodUiState();
            }
        });

        navGroup.add(backButton);
        navGroup.add(forwardButton);
        navGroup.add(reloadButton);
        navGroup.add(demoButton);
        navGroup.add(downloadQuickButton);
        navGroup.add(new JSeparator(SwingConstants.VERTICAL));
        navGroup.add(new JLabel("Method: "));
        navGroup.add(methodComboBox);

        // URL Input Field (Mặc định mở ngay trang web mẫu tích hợp)
        String defaultUrl = (demoServer != null) ? demoServer.getDemoUrl() : "https://example.com";
        urlTextField = new JTextField(defaultUrl);
        urlTextField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        urlTextField.setPreferredSize(new Dimension(380, 36));
        urlTextField.setToolTipText("Nhập URL (ví dụ: " + defaultUrl + " hoặc https://www.google.com)");
        urlTextField.addActionListener(e -> onSendRequest());

        // Nút Send Request
        sendButton = new JButton("Send Request");
        sendButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        sendButton.setPreferredSize(new Dimension(135, 36));
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendButton.addActionListener(e -> onSendRequest());

        panel.add(navGroup, BorderLayout.WEST);
        panel.add(urlTextField, BorderLayout.CENTER);
        panel.add(sendButton, BorderLayout.EAST);

        return panel;
    }

    /**
     * Tạo panel nhập dữ liệu POST (cho phép bật/tắt tùy theo HTTP Method).
     */
    private JPanel createPostBodyPanel() {
        postPanel = new JPanel(new BorderLayout(4, 4));
        postPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                "POST Data (Content-Type: application/x-www-form-urlencoded; charset=UTF-8)",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 11)
        ));

        postBodyTextArea = new JTextArea(3, 20);
        postBodyTextArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        postBodyTextArea.setText("name=duc&age=20");
        postBodyTextArea.setToolTipText("Nhập dữ liệu POST theo định dạng x-www-form-urlencoded (ví dụ: key1=value1&key2=value2)");

        JScrollPane scrollPane = new JScrollPane(postBodyTextArea);
        scrollPane.setPreferredSize(new Dimension(100, 75));
        postPanel.add(scrollPane, BorderLayout.CENTER);

        return postPanel;
    }

    /**
     * Tạo cụm Status Banner hiển thị mã HTTP, thời gian chạy và thông báo lỗi.
     */
    private JPanel createStatusAndAlertPanel() {
        JPanel container = new JPanel(new BorderLayout(4, 4));

        // Status Banner
        statusBannerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 4));
        statusBannerPanel.setBorder(new CompoundBorder(
                new LineBorder(new Color(220, 220, 220), 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));
        statusBannerPanel.setBackground(new Color(248, 249, 250));

        statusBadgeLabel = new JLabel("Status: Ready");
        statusBadgeLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

        latencyLabel = new JLabel("⏱ Latency: --");
        latencyLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        contentTypeLabel = new JLabel("Type: --");
        contentTypeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        sizeLabel = new JLabel("Length: --");
        sizeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(140, 16));
        progressBar.setVisible(false);

        statusBannerPanel.add(statusBadgeLabel);
        statusBannerPanel.add(new JSeparator(SwingConstants.VERTICAL));
        statusBannerPanel.add(latencyLabel);
        statusBannerPanel.add(new JSeparator(SwingConstants.VERTICAL));
        statusBannerPanel.add(contentTypeLabel);
        statusBannerPanel.add(new JSeparator(SwingConstants.VERTICAL));
        statusBannerPanel.add(sizeLabel);
        statusBannerPanel.add(progressBar);

        // Error Alert Panel (mặc định ẩn)
        errorAlertPanel = new JPanel(new BorderLayout(8, 0));
        errorAlertPanel.setBorder(new CompoundBorder(
                new LineBorder(new Color(220, 53, 69), 1, true),
                new EmptyBorder(6, 12, 6, 12)
        ));
        errorAlertPanel.setBackground(new Color(255, 235, 238));
        errorAlertPanel.setVisible(false);

        errorAlertLabel = new JLabel();
        errorAlertLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        errorAlertLabel.setForeground(new Color(176, 0, 32));

        JButton dismissErrorBtn = new JButton("✕");
        dismissErrorBtn.setMargin(new Insets(0, 4, 0, 4));
        dismissErrorBtn.addActionListener(e -> errorAlertPanel.setVisible(false));

        errorAlertPanel.add(errorAlertLabel, BorderLayout.CENTER);
        errorAlertPanel.add(dismissErrorBtn, BorderLayout.EAST);

        container.add(statusBannerPanel, BorderLayout.NORTH);
        container.add(errorAlertPanel, BorderLayout.SOUTH);

        return container;
    }

    /**
     * Tạo khu vực Tab hiển thị kết quả.
     */
    private JTabbedPane createTabsPanel() {
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("SansSerif", Font.BOLD, 12));

        tabbedPane.addTab("Web Preview (HTML Render)", createWebPreviewTab());
        tabbedPane.addTab("Response Body", createBodyTab());
        tabbedPane.addTab("Response Headers", createHeadersTab());
        tabbedPane.addTab("HTML Statistics", createHtmlStatsTab());
        tabbedPane.addTab("Lịch sử (History)", createHistoryTab());
        tabbedPane.addTab("Tải xuống (Downloads)", createDownloadsTab());
        tabbedPane.addTab("Error / Diagnostics", createDiagnosticsTab());

        return tabbedPane;
    }

    /**
     * Tab 1: Web Preview (HTML Render bằng JavaFX WebView qua JFXPanel).
     * Hỗ trợ render đầy đủ HTML5, CSS3, JavaScript tương tự Google Chrome / Cốc Cốc.
     */
    private JPanel createWebPreviewTab() {
        JPanel panel = new JPanel(new BorderLayout());
        webBrowserPanel = new JavaFxWebBrowserPanel();

        // 1. Đồng bộ thanh địa chỉ khi người dùng click vào liên kết bên trong WebView
        webBrowserPanel.setOnUrlChanged(newUrl -> {
            urlTextField.setText(newUrl);
        });

        // 2. Cập nhật thanh tiến trình của Swing khi JavaFX WebView tải trang
        webBrowserPanel.setOnProgressChanged(pct -> {
            if (pct >= 1.0) {
                progressBar.setVisible(false);
            } else if (pct > 0.0) {
                progressBar.setVisible(true);
                progressBar.setIndeterminate(false);
                progressBar.setValue((int) (pct * 100));
            }
        });

        // 3. Cập nhật tiêu đề cửa sổ khi trang web thay đổi title
        webBrowserPanel.setOnTitleChanged(title -> {
            if (title != null && !title.isBlank()) {
                setTitle(title + " – Simple Web Browser");
            }
        });

        // 4. Bắt sự kiện tải tệp tin khi người dùng bấm nút tải xuống trên trang web
        webBrowserPanel.setOnDownloadRequested((fileUrl, suggestedFileName) -> {
            promptAndStartDownload(fileUrl, null, null);
        });

        panel.add(webBrowserPanel, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Tab 2: Response Body.
     */
    private JPanel createBodyTab() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(new EmptyBorder(6, 6, 6, 6));

        responseBodyTextArea = new JTextArea();
        responseBodyTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        responseBodyTextArea.setEditable(false);
        responseBodyTextArea.setBackground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(responseBodyTextArea);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        JButton copyBtn = new JButton("Copy Body");
        copyBtn.addActionListener(e -> {
            String text = responseBodyTextArea.getText();
            if (!text.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
                JOptionPane.showMessageDialog(this, "Đã sao chép Response Body vào Clipboard!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JCheckBox wrapCheckbox = new JCheckBox("Word Wrap");
        wrapCheckbox.addActionListener(e -> responseBodyTextArea.setLineWrap(wrapCheckbox.isSelected()));

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> responseBodyTextArea.setText(""));

        toolbar.add(wrapCheckbox);
        toolbar.add(copyBtn);
        toolbar.add(clearBtn);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Tab 3: Response Headers.
     */
    private JPanel createHeadersTab() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(new EmptyBorder(6, 6, 6, 6));

        String[] columnNames = {"Header Name", "Header Value"};
        headersTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        headersTable = new JTable(headersTableModel);
        headersTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        headersTable.setRowHeight(22);
        headersTable.getColumnModel().getColumn(0).setPreferredWidth(220);
        headersTable.getColumnModel().getColumn(1).setPreferredWidth(600);

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String headerName = (String) table.getValueAt(row, 0);
                if (isImportantHeader(headerName)) {
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                    if (!isSelected) {
                        c.setBackground(new Color(240, 248, 255));
                    }
                } else {
                    c.setFont(c.getFont().deriveFont(Font.PLAIN));
                    if (!isSelected) {
                        c.setBackground(Color.WHITE);
                    }
                }
                return c;
            }
        };
        headersTable.getColumnModel().getColumn(0).setCellRenderer(renderer);

        JScrollPane scrollPane = new JScrollPane(headersTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Tab 4: HTML Statistics.
     */
    private JPanel createHtmlStatsTab() {
        htmlStatsContainer = new JPanel(new BorderLayout(10, 10));
        htmlStatsContainer.setBorder(new EmptyBorder(16, 16, 16, 16));

        statsStatusNotice = new JLabel("Chưa có dữ liệu thống kê. Vui lòng gửi request GET hoặc POST đến trang HTML.");
        statsStatusNotice.setFont(new Font("SansSerif", Font.ITALIC, 13));
        statsStatusNotice.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel cardsGrid = new JPanel(new GridLayout(2, 3, 14, 14));

        statsLengthValue = new JLabel("-");
        statsPValue = new JLabel("-");
        statsDivValue = new JLabel("-");
        statsSpanValue = new JLabel("-");
        statsImgValue = new JLabel("-");

        cardsGrid.add(createMetricCard("HTML Length", statsLengthValue, null, "Tổng số ký tự của HTML body đã giải mã"));
        cardsGrid.add(createMetricCard("Thẻ <p>", statsPValue, null, "Số lượng đoạn văn bản (<p>)"));
        cardsGrid.add(createMetricCard("Thẻ <div>", statsDivValue, null, "Số lượng khối phân chia (<div>)"));
        cardsGrid.add(createMetricCard("Thẻ <span>", statsSpanValue, null, "Số lượng thành phần nội dòng (<span>)"));

        statsImgSubLabel = new JLabel("<html><body style='width: 190px; color:#777; font-size:10px;'>Bao gồm cả thẻ &lt;img&gt; chuẩn và &lt;imag&gt;</body></html>");
        cardsGrid.add(createMetricCard("Thẻ <img> / <imag>", statsImgValue, statsImgSubLabel, null));

        JPanel noteCard = new JPanel(new BorderLayout(4, 4));
        noteCard.setBorder(new CompoundBorder(
                new LineBorder(new Color(220, 220, 220), 1, true),
                new EmptyBorder(10, 10, 10, 10)
        ));
        noteCard.setBackground(new Color(250, 250, 250));
        JLabel noteTitle = new JLabel("Lưu ý kỹ thuật");
        noteTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
        JLabel noteBody = new JLabel("<html><body style='font-size:10px; color:#555;'>" +
                "• Sử dụng thư viện <b>JSoup</b> để parse DOM theo chuẩn W3C.<br>" +
                "• Đề bài yêu cầu thẻ &lt;imag&gt;, chuẩn W3C là <b>&lt;img&gt;</b> (hệ thống đếm và hỗ trợ cả 2).<br>" +
                "• Chiều dài HTML tính bằng <b>ký tự (characters)</b>, phân biệt với số byte của Content-Length." +
                "</body></html>");
        noteCard.add(noteTitle, BorderLayout.NORTH);
        noteCard.add(noteBody, BorderLayout.CENTER);
        cardsGrid.add(noteCard);

        htmlStatsContainer.add(statsStatusNotice, BorderLayout.NORTH);
        htmlStatsContainer.add(cardsGrid, BorderLayout.CENTER);

        return htmlStatsContainer;
    }

    private JPanel createMetricCard(String title, JLabel valueLabel, JLabel customSubLabel, String subtitle) {
        JPanel card = new JPanel(new BorderLayout(6, 6));
        card.setBorder(new CompoundBorder(
                new LineBorder(new Color(200, 215, 235), 1, true),
                new EmptyBorder(12, 14, 12, 14)
        ));
        card.setBackground(Color.WHITE);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(new Color(60, 64, 67));

        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        valueLabel.setForeground(new Color(26, 115, 232));
        valueLabel.setHorizontalAlignment(SwingConstants.LEFT);

        JLabel subLabel = customSubLabel != null ? customSubLabel :
                new JLabel("<html><body style='width: 190px; color:#777; font-size:10px;'>" + subtitle + "</body></html>");

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        card.add(subLabel, BorderLayout.SOUTH);

        return card;
    }

    /**
     * Tab 5: Lịch sử duyệt web (History) dạng bảng JTable, hỗ trợ nhấp đúp để mở lại và xóa lịch sử.
     */
    private JPanel createHistoryTab() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        String[] columns = {"Thời gian", "Phương thức", "Mã HTTP", "Tiêu đề trang", "URL"};
        historyTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        historyTable = new JTable(historyTableModel);
        historyTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        historyTable.setRowHeight(24);
        historyTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(70);
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(250);
        historyTable.getColumnModel().getColumn(4).setPreferredWidth(450);

        // Nhấp đúp vào dòng để nạp lại URL lên thanh địa chỉ và điều hướng
        historyTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = historyTable.getSelectedRow();
                    if (row != -1) {
                        String method = (String) historyTableModel.getValueAt(row, 1);
                        String url = (String) historyTableModel.getValueAt(row, 4);
                        methodComboBox.setSelectedItem(method);
                        urlTextField.setText(url);
                        tabbedPane.setSelectedIndex(0); // Chuyển về Web Preview
                        onSendRequest();
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(historyTable);

        // Toolbar lịch sử
        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        JLabel tipLabel = new JLabel("💡 Nhấp đúp vào dòng lịch sử để mở lại URL. Dữ liệu được tự động lưu vào tệp history.json.");
        tipLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        tipLabel.setForeground(new Color(100, 100, 100));

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton refreshBtn = new JButton("Làm mới");
        refreshBtn.addActionListener(e -> refreshHistoryTable());

        JButton clearBtn = new JButton("Xóa tất cả lịch sử");
        clearBtn.setForeground(new Color(176, 0, 32));
        clearBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Bạn có chắc chắn muốn xóa toàn bộ lịch sử duyệt web không?",
                    "Xác nhận xóa lịch sử",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION) {
                historyService.clearHistory();
                refreshHistoryTable();
                updateNavigationButtons();
                JOptionPane.showMessageDialog(this, "Đã xóa toàn bộ lịch sử duyệt web thành công!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        btnGroup.add(refreshBtn);
        btnGroup.add(clearBtn);

        toolbar.add(tipLabel, BorderLayout.WEST);
        toolbar.add(btnGroup, BorderLayout.EAST);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Tab 6: Quản lý tải xuống (Downloads) hiển thị tệp, dung lượng, tiến độ và nút mở thư mục.
     */
    private JPanel createDownloadsTab() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        String[] columns = {"Tên tệp", "Tiến độ", "Kích thước", "Tốc độ", "Trạng thái", "Đường dẫn lưu"};
        downloadsTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        downloadsTable = new JTable(downloadsTableModel);
        downloadsTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        downloadsTable.setRowHeight(24);
        downloadsTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        downloadsTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        downloadsTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        downloadsTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        downloadsTable.getColumnModel().getColumn(4).setPreferredWidth(90);
        downloadsTable.getColumnModel().getColumn(5).setPreferredWidth(350);

        JScrollPane scrollPane = new JScrollPane(downloadsTable);

        // Toolbar tải xuống
        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        JLabel tipLabel = new JLabel("💡 Chọn tệp và bấm 'Mở thư mục' để xem tệp đã tải về máy.");
        tipLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        tipLabel.setForeground(new Color(100, 100, 100));

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton openFolderBtn = new JButton("Mở thư mục tệp");
        openFolderBtn.addActionListener(e -> {
            int row = downloadsTable.getSelectedRow();
            if (row != -1) {
                List<DownloadItem> downloads = downloadService.getDownloads();
                if (row < downloads.size()) {
                    openFileLocation(downloads.get(row).getDestinationPath());
                }
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn một tệp trong danh sách để mở thư mục.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JButton clearBtn = new JButton("Xóa danh sách tải");
        clearBtn.addActionListener(e -> {
            downloadService.clearDownloads();
            refreshDownloadsTable();
        });

        btnGroup.add(openFolderBtn);
        btnGroup.add(clearBtn);

        toolbar.add(tipLabel, BorderLayout.WEST);
        toolbar.add(btnGroup, BorderLayout.EAST);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Tab 7: Error & Diagnostics.
     */
    private JPanel createDiagnosticsTab() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        errorDetailsTextArea = new JTextArea();
        errorDetailsTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        errorDetailsTextArea.setEditable(false);
        errorDetailsTextArea.setText("Không có lỗi phát sinh. Mọi thứ hoạt động bình thường.");

        JScrollPane scrollPane = new JScrollPane(errorDetailsTextArea);
        panel.add(new JLabel("Chi tiết lỗi kết nối / HTTP Diagnostics:"), BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Cập nhật trạng thái giao diện theo Method (Ẩn/Hiện/Vô hiệu hóa POST panel).
     */
    private void updateMethodUiState() {
        String method = (String) methodComboBox.getSelectedItem();
        boolean isPost = "POST".equalsIgnoreCase(method);

        postPanel.setVisible(isPost);
        postBodyTextArea.setEnabled(isPost);

        if ("HEAD".equalsIgnoreCase(method)) {
            statsStatusNotice.setText("Phương thức HEAD chỉ nhận HTTP Headers, không có nội dung Body hay thống kê HTML.");
        }

        revalidate();
        repaint();
    }

    /**
     * Xử lý khi nhấn nút Back (◀).
     */
    private void onBack() {
        if (historyService.canGoBack()) {
            isNavigatingHistory = true;
            String targetUrl = historyService.goBack(currentActiveUrl);
            urlTextField.setText(targetUrl);
            updateNavigationButtons();
            onSendRequest();
        }
    }

    /**
     * Xử lý khi nhấn nút Forward (▶).
     */
    private void onForward() {
        if (historyService.canGoForward()) {
            isNavigatingHistory = true;
            String targetUrl = historyService.goForward(currentActiveUrl);
            urlTextField.setText(targetUrl);
            updateNavigationButtons();
            onSendRequest();
        }
    }

    /**
     * Xử lý khi nhấn nút Reload (🔄).
     */
    private void onReload() {
        String targetUrl = urlTextField.getText();
        if (targetUrl != null && !targetUrl.isBlank()) {
            onSendRequest();
        }
    }

    /**
     * Mở trang web mẫu tích hợp sẵn để kiểm thử duyệt web và tải file.
     */
    private void onOpenDemoPage() {
        if (demoServer != null) {
            urlTextField.setText(demoServer.getDemoUrl());
            methodComboBox.setSelectedItem("GET");
            tabbedPane.setSelectedIndex(0); // Chuyển sang Web Preview
            onSendRequest();
        }
    }

    /**
     * Cập nhật trạng thái kích hoạt của nút Back và Forward.
     */
    private void updateNavigationButtons() {
        backButton.setEnabled(historyService.canGoBack());
        forwardButton.setEnabled(historyService.canGoForward());
    }

    /**
     * Xử lý sự kiện nhấn nút Send Request.
     */
    private void onSendRequest() {
        String method = (String) methodComboBox.getSelectedItem();
        String rawUrl = urlTextField.getText();
        String postBody = postBodyTextArea.getText();

        // 1. Chuẩn hóa URL (thêm https:// nếu thiếu)
        String normalizedUrl = UrlValidator.normalizeUrl(rawUrl);
        urlTextField.setText(normalizedUrl);

        // 2. Xác thực tính hợp lệ của URL
        String validationError = UrlValidator.getValidationError(normalizedUrl);
        if (validationError != null) {
            showErrorAlert(validationError);
            errorDetailsTextArea.setText("[LỖI URL KHÔNG HỢP LỆ]\n" + validationError);
            tabbedPane.setSelectedIndex(6); // Chuyển sang Tab Diagnostics
            isNavigatingHistory = false;
            return;
        }

        // 3. Quản lý ngăn xếp điều hướng (nếu người dùng nhập mới hoặc click liên kết)
        if (!isNavigatingHistory) {
            historyService.recordNavigation(normalizedUrl, currentActiveUrl);
        }
        isNavigatingHistory = false;
        updateNavigationButtons();

        // 4. Xóa cảnh báo cũ và bắt đầu trạng thái Loading
        clearAlert();
        setLoading(true);

        // 5. Chạy tác vụ gửi HTTP trên nền bằng SwingWorker
        SwingWorker<HttpResponseData, Void> worker = new SwingWorker<>() {
            @Override
            protected HttpResponseData doInBackground() {
                return requestService.sendRequest(method, normalizedUrl, postBody);
            }

            @Override
            protected void done() {
                try {
                    HttpResponseData responseData = get();
                    displayResponse(responseData);
                } catch (Exception ex) {
                    showErrorAlert("Lỗi ngoài dự kiến trong quá trình xử lý: " + ex.getMessage());
                    errorDetailsTextArea.setText("Stack Trace:\n" + ex);
                } finally {
                    setLoading(false);
                    updateNavigationButtons();
                }
            }
        };

        worker.execute();
    }

    /**
     * Hiển thị toàn bộ dữ liệu phản hồi lên giao diện Swing.
     */
    private void displayResponse(HttpResponseData data) {
        clearResults();

        // Cập nhật URL hiện tại
        currentActiveUrl = data.getUrl();

        // 1. Cập nhật thời gian phản hồi
        latencyLabel.setText("⏱ Latency: " + data.getExecutionTimeMs() + " ms");
        contentTypeLabel.setText("Type: " + (data.getContentType().isBlank() ? "N/A" : data.getContentType()));

        // 2. Kiểm tra nếu có lỗi mạng / DNS / Timeout
        if (data.hasError()) {
            statusBadgeLabel.setText("Status: Error");
            statusBadgeLabel.setForeground(new Color(176, 0, 32));
            showErrorAlert(data.getErrorMessage());
            errorDetailsTextArea.setText("[CHI TIẾT LỖI MẠNG]\n" +
                    "URL: " + data.getUrl() + "\n" +
                    "Method: " + data.getMethod() + "\n" +
                    "Thời gian chạy: " + data.getExecutionTimeMs() + " ms\n" +
                    "Nguyên nhân: " + data.getErrorMessage());
            if (webBrowserPanel != null) {
                webBrowserPanel.showNotice(
                        "⚠️ Lỗi kết nối mạng",
                        "Không thể kết nối đến URL: <code>" + data.getUrl() + "</code><br>" +
                                "Nguyên nhân: <b>" + data.getErrorMessage() + "</b>",
                        "#d93025"
                );
            }
            tabbedPane.setSelectedIndex(6); // Chuyển sang tab Diagnostics
            return;
        }

        // 3. Lưu lịch sử duyệt web
        historyService.recordVisit(data.getUrl(), data.getMethod(), data.getStatusCode(), data.getPageTitle());
        refreshHistoryTable();

        // 4. Hiển thị Status Code
        int code = data.getStatusCode();
        statusBadgeLabel.setText("Status: " + code + " " + data.getStatusText());
        if (code >= 200 && code < 300) {
            statusBadgeLabel.setForeground(new Color(40, 167, 69)); // Xanh lá cây
        } else if (code >= 300 && code < 400) {
            statusBadgeLabel.setForeground(new Color(23, 162, 184)); // Xanh dương
        } else if (code >= 400 && code < 500) {
            statusBadgeLabel.setForeground(new Color(255, 140, 0)); // Cam
        } else {
            statusBadgeLabel.setForeground(new Color(220, 53, 69)); // Đỏ
        }

        // 5. Hiển thị kích thước Body
        if ("HEAD".equalsIgnoreCase(data.getMethod())) {
            sizeLabel.setText("Length: N/A (HEAD)");
        } else {
            NumberFormat nf = NumberFormat.getInstance(Locale.US);
            sizeLabel.setText("Length: " + nf.format(data.getBody().length()) + " chars");
        }

        // 6. Cập nhật Web Preview Tab (HTML Render)
        updateWebPreviewTab(data);

        // 7. Hiển thị Response Body
        if ("HEAD".equalsIgnoreCase(data.getMethod())) {
            responseBodyTextArea.setText("[Phương thức HEAD không trả về Response Body. Xem các HTTP Headers trong tab 'Response Headers']");
        } else if (data.getBody().isEmpty()) {
            responseBodyTextArea.setText("[Response Body rỗng từ máy chủ]");
        } else {
            responseBodyTextArea.setText(data.getBody());
            responseBodyTextArea.setCaretPosition(0);
        }

        // 8. Hiển thị Response Headers
        headersTableModel.setRowCount(0);
        for (Map.Entry<String, List<String>> entry : data.getHeaders().entrySet()) {
            String key = entry.getKey() != null ? entry.getKey() : "(Status Line)";
            String value = String.join(", ", entry.getValue());
            headersTableModel.addRow(new Object[]{key, value});
        }

        // 9. Hiển thị Thống kê HTML
        updateHtmlStatsTab(data);

        // 10. Nếu là mã lỗi HTTP 4xx hoặc 5xx, hiển thị cảnh báo nhẹ
        if (code >= 400) {
            showErrorAlert("Máy chủ phản hồi mã lỗi HTTP: " + code + " (" + data.getStatusText() + ")");
        }

        // 11. Tự động nhận diện tài nguyên tệp tin tải về (File Download Detection)
        if (DownloadService.isDownloadable(data)) {
            SwingUtilities.invokeLater(() -> {
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "Phát hiện tài nguyên tệp tin (Content-Type: " + data.getContentType() + ").\n" +
                                "Bạn có muốn tải tệp này về máy tính không?",
                        "Tải tệp tin",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );
                if (confirm == JOptionPane.YES_OPTION) {
                    String cd = data.getHeaders().getOrDefault("content-disposition",
                                    data.getHeaders().getOrDefault("Content-Disposition", Collections.emptyList()))
                            .stream().findFirst().orElse("");
                    promptAndStartDownload(data.getUrl(), cd, data.getContentType());
                }
            });
        }
    }

    /**
     * Xử lý khi người dùng chủ động nhấn nút "⬇ Tải về" trên thanh công cụ.
     */
    private void onDownloadCurrentUrl() {
        String rawUrl = urlTextField.getText();
        String normalizedUrl = UrlValidator.normalizeUrl(rawUrl);
        if (!UrlValidator.isValidUrl(normalizedUrl)) {
            showErrorAlert("URL không hợp lệ để tải tệp tin: " + rawUrl);
            return;
        }
        promptAndStartDownload(normalizedUrl, null, null);
    }

    /**
     * Mở JFileChooser để người dùng chọn vị trí lưu và bắt đầu tiến trình tải tệp tin.
     */
    private void promptAndStartDownload(String fileUrl, String contentDisposition, String contentType) {
        String suggestedFileName = DownloadService.extractFileName(fileUrl, contentDisposition, contentType);

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Chọn thư mục lưu tệp tải về");
        fileChooser.setSelectedFile(new File(suggestedFileName));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            Path destinationPath = fileChooser.getSelectedFile().toPath();
            tabbedPane.setSelectedIndex(5); // Chuyển sang Tab Downloads

            requestService.downloadFile(fileUrl, destinationPath, new DownloadListener() {
                @Override
                public void onProgress(DownloadItem item) {
                    SwingUtilities.invokeLater(() -> refreshDownloadsTable());
                }

                @Override
                public void onCompleted(DownloadItem item) {
                    SwingUtilities.invokeLater(() -> {
                        refreshDownloadsTable();
                        int openChoice = JOptionPane.showConfirmDialog(
                                BrowserFrame.this,
                                "Tệp đã được tải về hoàn tất:\n" + item.getFileName() +
                                        "\n\nBạn có muốn mở thư mục chứa tệp này ngay không?",
                                "Tải về thành công",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.INFORMATION_MESSAGE
                        );
                        if (openChoice == JOptionPane.YES_OPTION) {
                            openFileLocation(item.getDestinationPath());
                        }
                    });
                }

                @Override
                public void onFailed(DownloadItem item, Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        refreshDownloadsTable();
                        showErrorAlert("Lỗi trong quá trình tải tệp: " + e.getMessage());
                    });
                }
            });

            refreshDownloadsTable();
        }
    }

    /**
     * Mở thư mục chứa tệp tin trên máy tính người dùng.
     */
    private void openFileLocation(Path path) {
        try {
            if (path != null && Desktop.isDesktopSupported()) {
                Path folder = path.getParent() != null ? path.getParent() : path;
                Desktop.getDesktop().open(folder.toFile());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể mở thư mục: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Làm mới dữ liệu hiển thị trong bảng Lịch sử (History).
     */
    private void refreshHistoryTable() {
        if (historyTableModel == null) return;
        historyTableModel.setRowCount(0);
        for (HistoryItem item : historyService.getHistory()) {
            historyTableModel.addRow(new Object[]{
                    item.getTimestamp(),
                    item.getMethod(),
                    item.getStatusCode() > 0 ? item.getStatusCode() : "N/A",
                    item.getTitle(),
                    item.getUrl()
            });
        }
    }

    /**
     * Làm mới dữ liệu hiển thị trong bảng Tải xuống (Downloads).
     */
    private void refreshDownloadsTable() {
        if (downloadsTableModel == null) return;
        downloadsTableModel.setRowCount(0);
        for (DownloadItem item : downloadService.getDownloads()) {
            String sizeStr = item.getTotalBytes() > 0 ?
                    DownloadItem.formatBytes(item.getDownloadedBytes()) + " / " + DownloadItem.formatBytes(item.getTotalBytes()) :
                    DownloadItem.formatBytes(item.getDownloadedBytes());

            downloadsTableModel.addRow(new Object[]{
                    item.getFileName(),
                    item.getProgress() + "%",
                    sizeStr,
                    item.getSpeedText(),
                    item.getStatus().getDisplayName(),
                    item.getDestinationPath().toString()
            });
        }
    }

    /**
     * Cập nhật nội dung cho Tab Web Preview (JavaFX WebView).
     */
    private void updateWebPreviewTab(HttpResponseData data) {
        if ("HEAD".equalsIgnoreCase(data.getMethod())) {
            webBrowserPanel.showNotice(
                    "ℹ️ Phương thức HEAD",
                    "Phương thức HEAD chỉ nhận HTTP Headers từ máy chủ, không tải nội dung Response Body.<br>" +
                            "Vui lòng xem các thông số Header trong tab <b>Response Headers</b>.",
                    "#0d47a1"
            );
            return;
        }

        if (!data.isHtml()) {
            String typeStr = data.getContentType().isBlank() ? "Không xác định" : data.getContentType();
            webBrowserPanel.showNotice(
                    "ℹ️ Phản hồi không phải là HTML",
                    "Content-Type nhận được: <code>" + typeStr + "</code>.<br>" +
                            "Để xem nội dung dữ liệu (ví dụ JSON, văn bản thuần, hình ảnh), vui lòng chuyển sang tab <b>Response Body</b> hoặc sử dụng tính năng <b>Tải xuống (Downloads)</b>.",
                    "#e65100"
            );
            return;
        }

        if (data.getBody().isBlank()) {
            webBrowserPanel.showNotice(
                    "ℹ️ Phản hồi HTML có nội dung rỗng",
                    "Máy chủ đã phản hồi mã trạng thái <code>" + data.getStatusCode() + "</code> nhưng phần thân HTML rỗng.",
                    "#5f6368"
            );
            return;
        }

        if ("POST".equalsIgnoreCase(data.getMethod())) {
            // Chế độ 2: POST request - Nạp trực tiếp chuỗi HTML trả về từ kết quả POST
            webBrowserPanel.loadContent(data.getBody());
        } else {
            // Chế độ 1: GET request - Tải trực tiếp URL qua WebEngine để WebKit nạp đầy đủ CSS, JS, ảnh
            webBrowserPanel.loadUrl(data.getUrl());
        }
    }

    /**
     * Cập nhật thông số thống kê HTML trong Tab HTML Statistics.
     */
    private void updateHtmlStatsTab(HttpResponseData data) {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);

        if ("HEAD".equalsIgnoreCase(data.getMethod())) {
            statsStatusNotice.setText("ℹ️ Phương thức HEAD không tải Response Body nên không phân tích thống kê HTML.");
            resetStatsValues();
            return;
        }

        if (!data.isHtml()) {
            statsStatusNotice.setText("ℹ️ Phản hồi không phải là HTML (Content-Type: '" + data.getContentType() + "'). Không thống kê thẻ.");
            resetStatsValues();
            return;
        }

        if (data.getBody().isBlank()) {
            statsStatusNotice.setText("ℹ️ Phản hồi HTML có nội dung rỗng.");
            resetStatsValues();
            return;
        }

        HtmlStats stats = data.getHtmlStats();
        if (stats != null) {
            statsStatusNotice.setText("✅ Phân tích thành công từ JSoup Document (chuẩn W3C):");
            statsLengthValue.setText(nf.format(stats.getHtmlLength()) + " ký tự");
            statsPValue.setText(nf.format(stats.getPCount()));
            statsDivValue.setText(nf.format(stats.getDivCount()));
            statsSpanValue.setText(nf.format(stats.getSpanCount()));
            statsImgValue.setText(nf.format(stats.getImgCount()));

            if (stats.getCustomImagCount() > 0) {
                statsImgSubLabel.setText("<html><body style='width: 190px; color:#2e7d32; font-size:10px;'>" +
                        "Gồm <b>" + stats.getStandardImgCount() + "</b> thẻ &lt;img&gt; và <b>" + stats.getCustomImagCount() + "</b> thẻ &lt;imag&gt;</body></html>");
            } else {
                statsImgSubLabel.setText("<html><body style='width: 190px; color:#777; font-size:10px;'>" +
                        "Số lượng hình ảnh (chuẩn W3C &lt;img&gt; và đề bài &lt;imag&gt;)</body></html>");
            }
        } else {
            statsStatusNotice.setText("Không thể phân tích HTML.");
            resetStatsValues();
        }
    }

    private void resetStatsValues() {
        statsLengthValue.setText("-");
        statsPValue.setText("-");
        statsDivValue.setText("-");
        statsSpanValue.setText("-");
        statsImgValue.setText("-");
        statsImgSubLabel.setText("<html><body style='width: 190px; color:#777; font-size:10px;'>Bao gồm cả thẻ &lt;img&gt; chuẩn và &lt;imag&gt;</body></html>");
    }

    private boolean isImportantHeader(String header) {
        if (header == null) return false;
        String h = header.toLowerCase();
        return h.equals("content-type") ||
                h.equals("content-length") ||
                h.equals("date") ||
                h.equals("server") ||
                h.equals("location");
    }

    private void setLoading(boolean loading) {
        sendButton.setEnabled(!loading);
        methodComboBox.setEnabled(!loading);
        urlTextField.setEnabled(!loading);
        downloadQuickButton.setEnabled(!loading);
        backButton.setEnabled(!loading && historyService.canGoBack());
        forwardButton.setEnabled(!loading && historyService.canGoForward());
        reloadButton.setEnabled(!loading);
        if (demoButton != null) {
            demoButton.setEnabled(!loading);
        }
        postBodyTextArea.setEnabled(!loading && "POST".equalsIgnoreCase((String) methodComboBox.getSelectedItem()));
        progressBar.setVisible(loading);
        progressBar.setIndeterminate(loading);

        if (loading) {
            statusBadgeLabel.setText("Status: Đang gửi yêu cầu...");
            statusBadgeLabel.setForeground(Color.DARK_GRAY);
        }
    }

    private void clearResults() {
        if (webBrowserPanel != null) {
            webBrowserPanel.clear();
        }
        responseBodyTextArea.setText("");
        headersTableModel.setRowCount(0);
        resetStatsValues();
        errorDetailsTextArea.setText("Không có lỗi.");
    }

    private void showErrorAlert(String message) {
        errorAlertLabel.setText("⚠️ " + message);
        errorAlertPanel.setVisible(true);
    }

    private void clearAlert() {
        errorAlertPanel.setVisible(false);
        errorAlertLabel.setText("");
    }
}
