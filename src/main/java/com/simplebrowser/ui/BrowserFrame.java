package com.simplebrowser.ui;

import com.simplebrowser.model.HtmlStats;
import com.simplebrowser.model.HttpResponseData;
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
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Giao diện chính của Simple Web Browser xây dựng bằng Java Swing.
 * Tích hợp đầy đủ các tính năng: GET/POST/HEAD, thống kê thẻ HTML, quản lý headers và xử lý nền SwingWorker.
 */
public class BrowserFrame extends JFrame {

    private final HttpRequestService requestService;

    // Controls trên thanh nhập liệu
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
    private JLabel statsStatusNotice;

    // Tab Raw / Error Details
    private JTextArea errorDetailsTextArea;

    public BrowserFrame() {
        this(new HttpRequestService());
    }

    public BrowserFrame(HttpRequestService requestService) {
        this.requestService = requestService;
        initUI();
    }

    private void initUI() {
        setTitle("Simple Web Browser – Nhóm 1 (HTTP Client & HTML Analyzer)");
        setSize(1000, 750);
        setMinimumSize(new Dimension(850, 600));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel mainContainer = new JPanel(new BorderLayout(8, 8));
        mainContainer.setBorder(new EmptyBorder(12, 12, 12, 12));

        // 1. Top Panel: URL + Method + POST Body
        JPanel topContainer = new JPanel(new BorderLayout(6, 6));
        topContainer.add(createUrlBarPanel(), BorderLayout.NORTH);
        topContainer.add(createPostBodyPanel(), BorderLayout.CENTER);
        topContainer.add(createStatusAndAlertPanel(), BorderLayout.SOUTH);

        mainContainer.add(topContainer, BorderLayout.NORTH);

        // 2. Center Panel: Tabs kết quả (Body, Headers, Thống kê HTML, Chi tiết lỗi)
        mainContainer.add(createTabsPanel(), BorderLayout.CENTER);

        setContentPane(mainContainer);

        // Trạng thái ban đầu
        updateMethodUiState();
        clearResults();
    }

    /**
     * Tạo thanh nhập URL, lựa chọn Method và nút Send Request.
     */
    private JPanel createUrlBarPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));

        // ComboBox Method: GET, POST, HEAD
        String[] methods = {"GET", "POST", "HEAD"};
        methodComboBox = new JComboBox<>(methods);
        methodComboBox.setFont(new Font("SansSerif", Font.BOLD, 13));
        methodComboBox.setPreferredSize(new Dimension(95, 36));
        methodComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                updateMethodUiState();
            }
        });

        // URL Input Field
        urlTextField = new JTextField("https://example.com");
        urlTextField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        urlTextField.setPreferredSize(new Dimension(400, 36));
        urlTextField.setToolTipText("Nhập URL (ví dụ: https://example.com hoặc example.com)");

        // Nhấn phím Enter trong ô URL để gửi request
        urlTextField.addActionListener(e -> onSendRequest());

        // Nút Send Request
        sendButton = new JButton("Send Request");
        sendButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        sendButton.setPreferredSize(new Dimension(140, 36));
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendButton.addActionListener(e -> onSendRequest());

        JPanel leftGroup = new JPanel(new BorderLayout(6, 0));
        leftGroup.add(new JLabel("Method: "), BorderLayout.WEST);
        leftGroup.add(methodComboBox, BorderLayout.CENTER);

        panel.add(leftGroup, BorderLayout.WEST);
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
        dismissErrorBtn.setMargin(new java.awt.Insets(0, 4, 0, 4));
        dismissErrorBtn.addActionListener(e -> errorAlertPanel.setVisible(false));

        errorAlertPanel.add(errorAlertLabel, BorderLayout.CENTER);
        errorAlertPanel.add(dismissErrorBtn, BorderLayout.EAST);

        container.add(statusBannerPanel, BorderLayout.NORTH);
        container.add(errorAlertPanel, BorderLayout.SOUTH);

        return container;
    }

    /**
     * Tạo khu vực Tab hiển thị kết quả (Response Body, Headers, HTML Statistics, Logs).
     */
    private JTabbedPane createTabsPanel() {
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("SansSerif", Font.BOLD, 12));

        tabbedPane.addTab("Response Body", createBodyTab());
        tabbedPane.addTab("Response Headers", createHeadersTab());
        tabbedPane.addTab("HTML Statistics", createHtmlStatsTab());
        tabbedPane.addTab("Error / Diagnostics", createDiagnosticsTab());

        return tabbedPane;
    }

    /**
     * Tab 1: Response Body (JTextArea kèm toolbar Copy, Word Wrap).
     */
    private JPanel createBodyTab() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(new EmptyBorder(6, 6, 6, 6));

        responseBodyTextArea = new JTextArea();
        responseBodyTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        responseBodyTextArea.setEditable(false);
        responseBodyTextArea.setBackground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(responseBodyTextArea);

        // Toolbar
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
     * Tab 2: Response Headers (JTable hiển thị cặp Header - Value).
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

        // Custom renderer để highlight headers quan trọng
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
     * Tab 3: HTML Statistics (Thẻ thống kê kích thước, <p>, <div>, <span>, <img>).
     */
    private JPanel createHtmlStatsTab() {
        htmlStatsContainer = new JPanel(new BorderLayout(10, 10));
        htmlStatsContainer.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Thông báo trạng thái thống kê
        statsStatusNotice = new JLabel("Chưa có dữ liệu thống kê. Vui lòng gửi request GET hoặc POST đến trang HTML.");
        statsStatusNotice.setFont(new Font("SansSerif", Font.ITALIC, 13));
        statsStatusNotice.setHorizontalAlignment(SwingConstants.CENTER);

        // Panel chứa các thẻ đo lường (Metric Cards)
        JPanel cardsGrid = new JPanel(new GridLayout(2, 3, 14, 14));

        statsLengthValue = new JLabel("-");
        statsPValue = new JLabel("-");
        statsDivValue = new JLabel("-");
        statsSpanValue = new JLabel("-");
        statsImgValue = new JLabel("-");

        cardsGrid.add(createMetricCard("HTML Length", statsLengthValue, "Tổng số ký tự của HTML body đã giải mã"));
        cardsGrid.add(createMetricCard("Thẻ <p>", statsPValue, "Số lượng đoạn văn bản (<p>)"));
        cardsGrid.add(createMetricCard("Thẻ <div>", statsDivValue, "Số lượng khối phân chia (<div>)"));
        cardsGrid.add(createMetricCard("Thẻ <span>", statsSpanValue, "Số lượng thành phần nội dòng (<span>)"));
        cardsGrid.add(createMetricCard("Thẻ <img>", statsImgValue, "Số lượng hình ảnh (chuẩn W3C <img> thay cho <imag>)"));

        // Thẻ ghi chú kỹ thuật W3C
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
                "• Đề bài yêu cầu thẻ &lt;imag&gt;, chuẩn HTML chính thức là <b>&lt;img&gt;</b>.<br>" +
                "• Chiều dài HTML tính bằng <b>ký tự (characters)</b>, phân biệt với số byte của Content-Length." +
                "</body></html>");
        noteCard.add(noteTitle, BorderLayout.NORTH);
        noteCard.add(noteBody, BorderLayout.CENTER);
        cardsGrid.add(noteCard);

        htmlStatsContainer.add(statsStatusNotice, BorderLayout.NORTH);
        htmlStatsContainer.add(cardsGrid, BorderLayout.CENTER);

        return htmlStatsContainer;
    }

    /**
     * Tạo một Card trực quan cho từng thông số trong Tab HTML Statistics.
     */
    private JPanel createMetricCard(String title, JLabel valueLabel, String subtitle) {
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

        JLabel subLabel = new JLabel("<html><body style='width: 170px; color:#777; font-size:10px;'>" + subtitle + "</body></html>");

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        card.add(subLabel, BorderLayout.SOUTH);

        return card;
    }

    /**
     * Tab 4: Error & Diagnostics.
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

        // Vô hiệu hóa hoặc kích hoạt panel POST
        postPanel.setVisible(isPost);
        postBodyTextArea.setEnabled(isPost);

        if ("HEAD".equalsIgnoreCase(method)) {
            // Khi chọn HEAD: Không thống kê HTML
            statsStatusNotice.setText("Phương thức HEAD chỉ nhận HTTP Headers, không có nội dung Body hay thống kê HTML.");
        }

        revalidate();
        repaint();
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
            tabbedPane.setSelectedComponent(tabbedPane.getComponentAt(3)); // Chuyển sang Tab Diagnostics
            return;
        }

        // 3. Xóa cảnh báo cũ và bắt đầu trạng thái Loading
        clearAlert();
        setLoading(true);

        // 4. Chạy tác vụ gửi HTTP trên nền bằng SwingWorker để giao diện không bị treo
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
            tabbedPane.setSelectedIndex(3); // Chuyển sang tab Diagnostics
            return;
        }

        // 3. Hiển thị Status Code
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

        // 4. Hiển thị kích thước Body
        if ("HEAD".equalsIgnoreCase(data.getMethod())) {
            sizeLabel.setText("Length: N/A (HEAD)");
        } else {
            NumberFormat nf = NumberFormat.getInstance(Locale.US);
            sizeLabel.setText("Length: " + nf.format(data.getBody().length()) + " chars");
        }

        // 5. Hiển thị Response Body
        if ("HEAD".equalsIgnoreCase(data.getMethod())) {
            responseBodyTextArea.setText("[Phương thức HEAD không trả về Response Body. Xem các HTTP Headers trong tab 'Response Headers']");
        } else if (data.getBody().isEmpty()) {
            responseBodyTextArea.setText("[Response Body rỗng từ máy chủ]");
        } else {
            responseBodyTextArea.setText(data.getBody());
            responseBodyTextArea.setCaretPosition(0);
        }

        // 6. Hiển thị Response Headers
        headersTableModel.setRowCount(0);
        for (Map.Entry<String, List<String>> entry : data.getHeaders().entrySet()) {
            String key = entry.getKey() != null ? entry.getKey() : "(Status Line)";
            String value = String.join(", ", entry.getValue());
            headersTableModel.addRow(new Object[]{key, value});
        }

        // 7. Hiển thị Thống kê HTML
        updateHtmlStatsTab(data);

        // 8. Nếu là mã lỗi HTTP 4xx hoặc 5xx, hiển thị cảnh báo nhẹ
        if (code >= 400) {
            showErrorAlert("Máy chủ phản hồi mã lỗi HTTP: " + code + " (" + data.getStatusText() + ")");
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
        postBodyTextArea.setEnabled(!loading && "POST".equalsIgnoreCase((String) methodComboBox.getSelectedItem()));
        progressBar.setVisible(loading);
        progressBar.setIndeterminate(loading);

        if (loading) {
            statusBadgeLabel.setText("Status: Đang gửi yêu cầu...");
            statusBadgeLabel.setForeground(Color.DARK_GRAY);
        }
    }

    private void clearResults() {
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
