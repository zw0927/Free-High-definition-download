package cn.dousave.desktop;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.image.BufferedImage;

public final class BiliQrTest {
    public static void main(String[] arguments) throws Exception {
        String url = "https://account.bilibili.com/h5/account-h5/auth/scan-web?auth_code=sample-test-token";
        BufferedImage image = BiliLoginDialog.qrImage(url);
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        String decoded = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
        if (!url.equals(decoded)) {
            throw new AssertionError("Login QR did not decode to the exact official login URL");
        }
        System.out.println("PASS: generated Bilibili QR decodes to the exact login URL");
    }
}
