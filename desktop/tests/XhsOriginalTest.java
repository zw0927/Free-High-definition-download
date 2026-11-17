package cn.dousave.desktop;

import cn.dousave.app.BiliLogin;
import cn.dousave.app.VideoInfo;
import cn.dousave.app.VideoResolver;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.imageio.ImageIO;

public final class XhsOriginalTest {
    public static void main(String[] arguments) throws Exception {
        Path output = Paths.get(arguments[0]);
        VideoInfo note = new VideoResolver().resolve("https://xhslink.cn/o/AnF55BNjiUS");
        if (!note.id.equals("6afbb492000000002a0286f8") || note.images.size() != 5) {
            throw new AssertionError("The user share link did not resolve to the expected five-image note");
        }
        List<Path> saved = DesktopDownload.save(note, 0, true, output, System.out::println);
        for (Path path : saved) {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null || image.getWidth() != 1440 || image.getHeight() != 2400) {
                throw new AssertionError("Original image is missing or has unexpected dimensions");
            }
            System.out.println("PASS: original image saved at 1440 x 2400; bytes=" + Files.size(path));
        }
        try (java.util.stream.Stream<Path> files = Files.list(output)) {
            if (files.anyMatch(path -> path.getFileName().toString().endsWith(".part"))) {
                throw new AssertionError("Temporary image file was not cleaned up");
            }
        }
        BiliLogin login = new BiliLogin();
        BiliLogin.Code code = login.generate();
        if (code.key.isEmpty() || !code.url.startsWith("https://")) {
            throw new AssertionError("Official Bilibili login QR could not be obtained");
        }
        BiliLogin.Status status = login.poll(code.key);
        if (status.code != 86101 || !status.cookie.isEmpty()) {
            throw new AssertionError("Unscanned QR must remain waiting without a login session");
        }
        System.out.println("PASS: official Bilibili QR generation and waiting-state polling");
    }
}
