package cn.dousave.desktop;

import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public final class DesktopUiTest {
    public static void main(String[] arguments) throws Exception {
        DesktopApp.configureLook();
        AtomicReference<DesktopApp> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                DesktopApp app = new DesktopApp();
                reference.set(app);
                app.setSize(940, 840);
                app.setVisible(true);
                ((JTextField) field(app, "directory")).setText("E:\\Downloads\\无水印下载");
                ((JTextArea) field(app, "input")).setText("https://v.douyin.com/RQtMd_5ce00/");
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
        DesktopApp app = reference.get();
        try {
            long deadline = System.currentTimeMillis() + 65000;
            boolean parsed = false;
            while (System.currentTimeMillis() < deadline) {
                AtomicReference<Boolean> visible = new AtomicReference<>(false);
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        visible.set(((JPanel) field(app, "result")).isShowing());
                    } catch (Exception error) {
                        throw new RuntimeException(error);
                    }
                });
                if (visible.get()) {
                    parsed = true;
                    break;
                }
                Thread.sleep(100);
            }
            if (!parsed) {
                throw new AssertionError("Desktop UI did not display parsed result");
            }
            SwingUtilities.invokeAndWait(() -> {
                try {
                    BufferedImage image = new BufferedImage(app.getWidth(), app.getHeight(), BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics graphics = image.getGraphics();
                    app.paint(graphics);
                    graphics.dispose();
                    ImageIO.write(image, "png", Paths.get(arguments[0]).toFile());
                    button(app, "清空").doClick();
                    if (((JPanel) field(app, "result")).isShowing()) {
                        throw new AssertionError("Results still visible after clearing");
                    }
                    if (!((JTextArea) field(app, "input")).getText().isEmpty()) {
                        throw new AssertionError("Input still contains text after clearing");
                    }
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
            System.out.println("PASS: UI automatic real-link parse, result controls and clear reset");
        } finally {
            SwingUtilities.invokeAndWait(() -> app.dispatchEvent(new java.awt.event.WindowEvent(app, java.awt.event.WindowEvent.WINDOW_CLOSING)));
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = DesktopApp.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static JButton button(Container parent, String label) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JButton && ((JButton) component).getText().equals(label)) {
                return (JButton) component;
            }
            if (component instanceof Container) {
                JButton found = button((Container) component, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
