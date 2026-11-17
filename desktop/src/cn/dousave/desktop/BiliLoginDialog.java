package cn.dousave.desktop;

import cn.dousave.app.BiliLogin;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public final class BiliLoginDialog extends JDialog {
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final BiliLogin login = new BiliLogin();
    private final JLabel status = new JLabel("正在获取B站登录二维码…", SwingConstants.CENTER);
    private final JLabel image = new JLabel("", SwingConstants.CENTER);
    private volatile boolean closed;

    public BiliLoginDialog(JFrame owner, Consumer<String> completed) {
        super(owner, "B站扫码登录", false);
        setLayout(new BorderLayout(12, 12));
        add(new JLabel("使用哔哩哔哩手机 App 扫码并确认登录", SwingConstants.CENTER), BorderLayout.NORTH);
        add(image, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        setSize(390, 430);
        setResizable(false);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        worker.execute(() -> {
            try {
                BiliLogin.Code code = login.generate();
                BufferedImage bitmap = qrImage(code.url);
                SwingUtilities.invokeLater(() -> {
                    if (!closed) {
                        image.setIcon(new ImageIcon(bitmap));
                        status.setText("等待扫码");
                    }
                });
                if (!closed) {
                    worker.scheduleWithFixedDelay(() -> poll(code.key, completed), 2, 2, TimeUnit.SECONDS);
                }
            } catch (Exception error) {
                showStatus("获取二维码失败，请关闭后重试");
                worker.shutdown();
            }
        });
    }

    public static BufferedImage qrImage(String text) throws Exception {
        BitMatrix matrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 310, 310);
        BufferedImage bitmap = new BufferedImage(310, 310, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 310; y++) {
            for (int x = 0; x < 310; x++) {
                int color = 0xffffff;
                if (matrix.get(x, y)) {
                    color = 0x111111;
                }
                bitmap.setRGB(x, y, color);
            }
        }
        return bitmap;
    }

    private void poll(String key, Consumer<String> completed) {
        if (closed) {
            return;
        }
        try {
            BiliLogin.Status result = login.poll(key);
            if (result.code == 0) {
                worker.shutdown();
                SwingUtilities.invokeLater(() -> {
                    if (!closed) {
                        completed.accept(result.cookie);
                        dispose();
                    }
                });
            } else if (result.code == 86038) {
                showStatus("二维码已过期，请关闭后重新登录");
                worker.shutdown();
            } else if (result.code == 86090) {
                showStatus("已扫码，请在手机上确认登录");
            } else if (result.code == 86101) {
                showStatus("等待扫码");
            } else {
                showStatus("登录状态异常，请关闭后重试");
                worker.shutdown();
            }
        } catch (Exception error) {
            showStatus("网络连接失败，请关闭后重试");
            worker.shutdown();
        }
    }

    private void showStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            if (!closed) {
                status.setText(message);
            }
        });
    }

    @Override
    public void dispose() {
        closed = true;
        worker.shutdownNow();
        super.dispose();
    }
}
