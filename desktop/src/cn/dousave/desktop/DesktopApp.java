package cn.dousave.desktop;

import cn.dousave.app.VideoInfo;
import cn.dousave.app.VideoResolver;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.json.JSONArray;
import org.json.JSONObject;

public final class DesktopApp extends JFrame {
    private static final Color GREEN = new Color(8, 127, 114);
    private final JTextArea input = new JTextArea(4, 40);
    private final JTextArea body = new JTextArea(6, 40);
    private final JLabel status = new JLabel("等待分享链接或抖音文章口令");
    private final JLabel title = new JLabel();
    private final JLabel metadata = new JLabel();
    private final JTextField directory = new JTextField();
    private final JComboBox<String> quality = new JComboBox<>();
    private final JButton analyze = button("解析", true);
    private final JButton videoButton = button("保存视频", true);
    private final JButton imageButton = button("保存图片", true);
    private final JButton cancelButton = button("取消", false);
    private final JButton biliAccount = button("B站登录", false);
    private BiliLoginDialog loginDialog;
    private final JPanel result = new JPanel();
    private final JProgressBar progress = new JProgressBar();
    private final DefaultListModel<String> historyModel = new DefaultListModel<>();
    private final JList<String> historyList = new JList<>(historyModel);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final java.util.Properties settings = new java.util.Properties();
    private final Path dataDirectory;
    private final Timer debounce;
    private JSONArray history = new JSONArray();
    private Future<?> task;
    private VideoInfo info;
    private int generation;
    private boolean busy;
    private boolean downloading;

    public static void main(String[] arguments) throws Exception {
        configureLook();
        SwingUtilities.invokeLater(() -> {
            DesktopApp app = new DesktopApp();
            app.setVisible(true);
            if (arguments.length > 0) {
                app.input.setText(String.join(" ", arguments));
            }
        });
    }

    public static void configureLook() {
        System.setProperty("flatlaf.useNativeLibrary", "false");
        com.formdev.flatlaf.FlatLightLaf.setup();
        UIManager.put("Button.arc", 6);
        UIManager.put("Component.arc", 6);
        Font font = new Font("Microsoft YaHei UI", Font.PLAIN, 14);
        for (Object key : UIManager.getDefaults().keySet()) {
            if (UIManager.get(key) instanceof javax.swing.plaf.FontUIResource) {
                UIManager.put(key, new javax.swing.plaf.FontUIResource(font));
            }
        }
    }

    public DesktopApp() {
        super("无水印下载 · Free HD Download");
        String appData = System.getenv("LOCALAPPDATA");
        if (appData == null || appData.isEmpty()) {
            appData = System.getProperty("user.home");
        }
        Path storage = Paths.get(appData, "FreeHDDownload");
        String override = System.getProperty("freehd.dataDirectory", "");
        if (!override.isEmpty()) {
            storage = Paths.get(override);
        }
        try {
            Files.createDirectories(storage);
            Path check = Files.createTempFile(storage, ".write-test-", ".tmp");
            Files.delete(check);
        } catch (Exception error) {
            storage = Paths.get(System.getProperty("user.dir"), "FreeHDDownload-data");
        }
        dataDirectory = storage;
        try {
            Files.createDirectories(dataDirectory.resolve("temp"));
            System.setProperty("java.io.tmpdir", dataDirectory.resolve("temp").toString());
            Path settingsFile = dataDirectory.resolve("settings.properties");
            if (Files.exists(settingsFile)) {
                try (java.io.InputStream stream = Files.newInputStream(settingsFile)) {
                    settings.load(stream);
                }
            }
        } catch (Exception error) {
            status.setText("无法读取设置，将使用默认目录");
        }
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(780, 680));
        setSize(940, 860);
        setLocationRelativeTo(null);
        debounce = new Timer(900, event -> resolve());
        debounce.setRepeats(false);
        buildUi();
        readHistory();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (downloading) {
                    int answer = JOptionPane.showConfirmDialog(DesktopApp.this, "下载尚未结束，取消下载并退出？", "退出", JOptionPane.YES_NO_OPTION);
                    if (answer != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
                debounce.stop();
                if (loginDialog != null) {
                    loginDialog.dispose();
                }
                worker.shutdownNow();
                dispose();
            }
        });
    }

    private void buildUi() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        content.setBackground(new Color(246, 248, 250));
        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        JLabel brand = new JLabel("无水印下载");
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 27));
        brand.setForeground(GREEN);
        heading.add(brand, BorderLayout.WEST);
        heading.add(new JLabel("WINDOWS  /  1.2.2", JLabel.RIGHT), BorderLayout.EAST);
        content.add(heading);
        content.add(Box.createVerticalStrut(22));
        content.add(new JLabel("分享链接 / 文章口令"));
        content.add(Box.createVerticalStrut(8));
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setMargin(new Insets(12, 12, 12, 12));
        input.setName("shareInput");
        input.setToolTipText("粘贴抖音、小红书、B站完整分享内容");
        content.add(new JScrollPane(input));
        JPanel actions = flow();
        JButton paste = button("粘贴", false);
        paste.addActionListener(event -> {
            try {
                Object value = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
                input.setText(value.toString());
            } catch (Exception error) {
                message("剪贴板没有可用的文字");
            }
        });
        JButton clear = button("清空", false);
        clear.addActionListener(event -> {
            if (downloading) {
                return;
            }
            input.setText("");
            status.setText("等待分享链接或抖音文章口令");
        });
        analyze.addActionListener(event -> resolve());
        actions.add(paste);
        actions.add(clear);
        actions.add(analyze);
        biliAccount.addActionListener(event -> manageBiliAccount());
        updateBiliAccount();
        actions.add(biliAccount);
        content.add(actions);
        directory.setEditable(false);
        directory.setText(settings.getProperty("directory", Paths.get(System.getProperty("user.home"), "Downloads", "无水印下载").toString()));
        JPanel location = new JPanel(new BorderLayout(10, 0));
        location.setOpaque(false);
        location.add(new JLabel("保存到"), BorderLayout.WEST);
        location.add(directory, BorderLayout.CENTER);
        JButton choose = button("选择目录", false);
        choose.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser(directory.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                directory.setText(chooser.getSelectedFile().getAbsolutePath());
                settings.setProperty("directory", directory.getText());
                try (java.io.OutputStream stream = Files.newOutputStream(dataDirectory.resolve("settings.properties"))) {
                    settings.store(stream, "Free HD Download");
                } catch (Exception error) {
                    message("无法保存目录设置：" + error.getMessage());
                }
            }
        });
        location.add(choose, BorderLayout.EAST);
        content.add(location);
        content.add(Box.createVerticalStrut(12));
        status.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        content.add(status);
        result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));
        result.setOpaque(false);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18));
        title.setAlignmentX(0);
        title.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        metadata.setAlignmentX(0);
        result.add(title);
        result.add(metadata);
        result.add(Box.createVerticalStrut(10));
        body.setEditable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setMargin(new Insets(10, 10, 10, 10));
        result.add(new JScrollPane(body));
        result.add(Box.createVerticalStrut(10));
        result.add(quality);
        JPanel saveActions = flow();
        videoButton.addActionListener(event -> download(false));
        imageButton.addActionListener(event -> download(true));
        JButton copy = button("复制文案", false);
        copy.addActionListener(event -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(exportText()), null);
            status.setText("文案已复制");
        });
        JButton text = button("保存 TXT", false);
        text.addActionListener(event -> saveText());
        saveActions.add(videoButton);
        saveActions.add(imageButton);
        saveActions.add(copy);
        saveActions.add(text);
        result.add(saveActions);
        for (Component component : result.getComponents()) {
            if (component instanceof javax.swing.JComponent) {
                ((javax.swing.JComponent) component).setAlignmentX(0);
            }
        }
        result.setVisible(false);
        content.add(result);
        JPanel transfer = new JPanel(new BorderLayout(10, 0));
        transfer.setOpaque(false);
        progress.setVisible(false);
        cancelButton.setVisible(false);
        cancelButton.addActionListener(event -> {
            if (task != null && downloading) {
                task.cancel(true);
                status.setText("正在取消，请等待临时文件清理");
                cancelButton.setEnabled(false);
                worker.submit(() -> SwingUtilities.invokeLater(() -> {
                    if (downloading) {
                        finishDownload("已取消");
                    }
                }));
            }
        });
        transfer.add(progress, BorderLayout.CENTER);
        transfer.add(cancelButton, BorderLayout.EAST);
        content.add(transfer);
        content.add(Box.createVerticalStrut(16));
        JPanel recent = new JPanel(new BorderLayout());
        recent.setOpaque(false);
        recent.add(new JLabel("最近下载"), BorderLayout.WEST);
        JButton clearHistory = button("清空记录", false);
        clearHistory.addActionListener(event -> {
            history = new JSONArray();
            writeHistory();
            renderHistory();
            if (!busy) {
                status.setText("记录已清空，本地文件保留");
            }
        });
        recent.add(clearHistory, BorderLayout.EAST);
        content.add(recent);
        historyList.setVisibleRowCount(3);
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        content.add(new JScrollPane(historyList));
        JPanel files = flow();
        JButton open = button("打开文件", false);
        open.addActionListener(event -> openHistory());
        JButton folder = button("打开下载目录", false);
        folder.addActionListener(event -> {
            try {
                Path path = Paths.get(directory.getText());
                Files.createDirectories(path);
                Desktop.getDesktop().open(path.toFile());
            } catch (Exception error) {
                message("无法打开目录：" + error.getMessage());
            }
        });
        files.add(open);
        files.add(folder);
        content.add(files);
        JLabel footer = new JLabel("抖音 · 小红书 · B站     /     Free HD Download · 开源桌面版");
        footer.setForeground(new Color(105, 115, 124));
        content.add(footer);
        for (java.awt.Component component : content.getComponents()) {
            if (component instanceof javax.swing.JComponent) {
                ((javax.swing.JComponent) component).setAlignmentX(0);
                Dimension preferred = component.getPreferredSize();
                component.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
            }
        }
        result.setMaximumSize(new Dimension(Integer.MAX_VALUE, 370));
        content.add(Box.createVerticalGlue());
        JScrollPane page = new JScrollPane(content);
        page.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        page.getVerticalScrollBar().setUnitIncrement(18);
        setContentPane(page);
        input.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { changed(); }
            public void removeUpdate(DocumentEvent event) { changed(); }
            public void changedUpdate(DocumentEvent event) { changed(); }
        });
    }

    private void changed() {
        if (downloading) {
            return;
        }
        generation += 1;
        if (task != null) {
            task.cancel(true);
        }
        info = null;
        result.setVisible(false);
        status.setText("等待分享链接或抖音文章口令");
        busy = false;
        analyze.setEnabled(true);
        debounce.stop();
        if (!input.getText().trim().isEmpty()) {
            debounce.restart();
        }
        revalidate();
    }

    private void resolve() {
        if (downloading || input.getText().trim().isEmpty()) {
            return;
        }
        debounce.stop();
        generation += 1;
        int token = generation;
        if (task != null) {
            task.cancel(true);
        }
        String share = input.getText();
        String cookie = settings.getProperty("biliCookie", "");
        busy = true;
        info = null;
        result.setVisible(false);
        analyze.setEnabled(false);
        status.setText("正在解析作品…");
        task = worker.submit(() -> {
            try {
                VideoInfo parsed = new VideoResolver(cookie).resolve(share);
                SwingUtilities.invokeLater(() -> {
                    if (generation != token) {
                        return;
                    }
                    info = parsed;
                    title.setText("<html>" + html(parsed.title) + "</html>");
                    String type = "视频";
                    if (parsed.variants.isEmpty()) {
                        type = "图文";
                    }
                    if (parsed.article) {
                        type = "文章";
                    }
                    metadata.setText(parsed.platform + " · " + parsed.author + " · " + type);
                    body.setText(parsed.description);
                    body.setCaretPosition(0);
                    quality.removeAllItems();
                    for (VideoInfo.Variant variant : parsed.variants) {
                        quality.addItem(variant.label());
                    }
                    quality.setVisible(!parsed.variants.isEmpty());
                    videoButton.setVisible(!parsed.variants.isEmpty());
                    imageButton.setVisible(!parsed.images.isEmpty());
                    imageButton.setText("保存图片（" + parsed.images.size() + "）");
                    result.setVisible(true);
                    busy = false;
                    analyze.setEnabled(true);
                    status.setText("已获取作品");
                    if (!parsed.qualityMessage.isEmpty()) {
                        status.setText(parsed.qualityMessage);
                    }
                    revalidate();
                });
            } catch (Exception error) {
                SwingUtilities.invokeLater(() -> {
                    if (generation == token) {
                        busy = false;
                        analyze.setEnabled(true);
                        status.setText("解析失败：" + error.getMessage());
                    }
                });
            }
        });
        Timer timeout = new Timer(60000, event -> {
            if (generation == token && busy && !downloading) {
                generation += 1;
                task.cancel(true);
                busy = false;
                analyze.setEnabled(true);
                status.setText("解析超时，请检查网络后重试");
            }
        });
        timeout.setRepeats(false);
        timeout.start();
    }

    private void download(boolean images) {
        if (info == null || busy) {
            return;
        }
        VideoInfo selected = info;
        int selectedQuality = quality.getSelectedIndex();
        Path destination = Paths.get(directory.getText());
        busy = true;
        downloading = true;
        input.setEditable(false);
        analyze.setEnabled(false);
        videoButton.setEnabled(false);
        imageButton.setEnabled(false);
        progress.setVisible(true);
        progress.setIndeterminate(true);
        cancelButton.setEnabled(true);
        cancelButton.setVisible(true);
        task = worker.submit(() -> {
            String outcome;
            try {
                List<Path> saved = DesktopDownload.save(selected, selectedQuality, images, destination,
                    text -> SwingUtilities.invokeLater(() -> status.setText(text)));
                SwingUtilities.invokeLater(() -> {
                    for (Path path : saved) {
                        addHistory(selected.title, path);
                    }
                });
                outcome = "已保存到本地 · " + saved.size() + " 个文件";
            } catch (CancellationException error) {
                outcome = "已取消；已完成的图片保留";
            } catch (Exception error) {
                outcome = "下载失败：" + error.getMessage() + "；已完成的图片保留";
            }
            String message = outcome;
            SwingUtilities.invokeLater(() -> {
                finishDownload(message);
            });
        });
    }

    private void updateBiliAccount() {
        biliAccount.setText("B站登录");
        if (!settings.getProperty("biliCookie", "").isEmpty()) {
            biliAccount.setText("B站已登录");
        }
    }

    private void manageBiliAccount() {
        if (busy) {
            message("请等待当前任务完成");
            return;
        }
        if (!settings.getProperty("biliCookie", "").isEmpty()) {
            Object[] options = new Object[] {"重新登录", "退出登录", "取消"};
            int choice = JOptionPane.showOptionDialog(this, "管理B站登录状态", "B站账号",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            if (choice < 0 || choice == 2) {
                return;
            }
            if (!storeBiliCookie("")) {
                return;
            }
            changed();
            if (choice == 1) {
                return;
            }
        }
        if (loginDialog != null && loginDialog.isDisplayable()) {
            loginDialog.toFront();
            return;
        }
        loginDialog = new BiliLoginDialog(this, cookie -> {
            if (storeBiliCookie(cookie)) {
                status.setText("B站登录成功，正在刷新可用画质");
                if (!input.getText().trim().isEmpty()) {
                    resolve();
                }
            }
        });
        loginDialog.setVisible(true);
    }

    private boolean storeBiliCookie(String cookie) {
        settings.setProperty("biliCookie", cookie);
        updateBiliAccount();
        try (java.io.OutputStream stream = Files.newOutputStream(dataDirectory.resolve("settings.properties"))) {
            settings.store(stream, "Free HD Download");
            return true;
        } catch (Exception error) {
            message("无法保存B站登录状态，请检查应用数据目录权限");
            return false;
        }
    }

    private void finishDownload(String message) {
        busy = false;
        downloading = false;
        input.setEditable(true);
        analyze.setEnabled(true);
        videoButton.setEnabled(true);
        imageButton.setEnabled(true);
        progress.setVisible(false);
        cancelButton.setVisible(false);
        status.setText(message);
    }

    private String exportText() {
        if (info == null) {
            return "";
        }
        if (info.title.equals(info.description)) {
            return info.title;
        }
        return info.title + "\n\n" + info.description;
    }

    private void saveText() {
        if (info == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser(directory.getText());
        chooser.setSelectedFile(new java.io.File(DesktopDownload.safeName(info.title) + ".txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        if (Files.exists(target)) {
            if (JOptionPane.showConfirmDialog(this, "文件已存在，覆盖？", "保存 TXT", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            Files.write(target, exportText().getBytes(StandardCharsets.UTF_8));
            addHistory(info.title, target);
            status.setText("文字已保存");
        } catch (Exception error) {
            message("保存失败：" + error.getMessage());
        }
    }

    private void addHistory(String title, Path path) {
        JSONArray updated = new JSONArray();
        updated.put(new JSONObject().put("title", title).put("path", path.toAbsolutePath().toString()));
        for (int index = 0; index < Math.min(history.length(), 49); index++) {
            updated.put(history.optJSONObject(index));
        }
        history = updated;
        writeHistory();
        renderHistory();
    }

    private void readHistory() {
        try {
            Path path = dataDirectory.resolve("history.json");
            if (Files.exists(path)) {
                history = new JSONArray(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            status.setText("下载记录无法读取，仍可正常下载");
        }
        renderHistory();
    }

    private void writeHistory() {
        try {
            Files.createDirectories(dataDirectory);
            Path temporary = dataDirectory.resolve("history.tmp");
            Files.write(temporary, history.toString(2).getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, dataDirectory.resolve("history.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            message("无法保存下载记录：" + error.getMessage());
        }
    }

    private void renderHistory() {
        historyModel.clear();
        for (int index = 0; index < history.length(); index++) {
            JSONObject item = history.optJSONObject(index);
            if (item != null) {
                historyModel.addElement(item.optString("title").replace('\n', ' ') + "  ·  " + item.optString("path"));
            }
        }
    }

    private void openHistory() {
        int selected = historyList.getSelectedIndex();
        if (selected < 0) {
            message("请先选择一条下载记录");
            return;
        }
        try {
            Desktop.getDesktop().open(Paths.get(history.getJSONObject(selected).getString("path")).toFile());
        } catch (Exception error) {
            message("文件已移动、删除或没有关联的打开程序");
        }
    }

    private void message(String text) {
        JOptionPane.showMessageDialog(this, text, "无水印下载", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    private static JPanel flow() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        panel.setOpaque(false);
        return panel;
    }

    private static JButton button(String label, boolean primary) {
        JButton button = new JButton(label);
        button.setFocusPainted(false);
        button.setMargin(new Insets(9, 16, 9, 16));
        if (primary) {
            button.setForeground(GREEN);
            button.setFont(button.getFont().deriveFont(Font.BOLD));
        }
        return button;
    }
}
