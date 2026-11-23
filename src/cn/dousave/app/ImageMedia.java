package cn.dousave.app;

import java.io.IOException;
import java.io.InputStream;

public final class ImageMedia {
    private ImageMedia() {}

    public static final class Format {
        public final String mime;
        public final String extension;

        private Format(String mime, String extension) {
            this.mime = mime;
            this.extension = extension;
        }
    }

    public static Format detect(InputStream input) throws IOException {
        byte[] header = new byte[32];
        int length = 0;
        while (length < header.length) {
            int count = input.read(header, length, header.length - length);
            if (count < 0) {
                break;
            }
            length += count;
        }
        if (length >= 3 && unsigned(header[0]) == 0xff && unsigned(header[1]) == 0xd8 && unsigned(header[2]) == 0xff) {
            return new Format("image/jpeg", ".jpg");
        }
        if (length >= 8 && unsigned(header[0]) == 0x89
            && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
            && unsigned(header[4]) == 0x0d && unsigned(header[5]) == 0x0a
            && unsigned(header[6]) == 0x1a && unsigned(header[7]) == 0x0a) {
            return new Format("image/png", ".png");
        }
        if (length >= 6) {
            String signature = new String(header, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
            if (signature.equals("GIF87a") || signature.equals("GIF89a")) {
                return new Format("image/gif", ".gif");
            }
        }
        if (length >= 12 && fourcc(header, 0, "RIFF") && fourcc(header, 8, "WEBP")) {
            return new Format("image/webp", ".webp");
        }
        if (length >= 16 && fourcc(header, 4, "ftyp")) {
            int brandEnd = Math.min(length, 32);
            boolean avif = false;
            boolean heic = false;
            boolean heif = false;
            for (int offset = 8; offset + 4 <= brandEnd; offset += 4) {
                if (fourcc(header, offset, "avif") || fourcc(header, offset, "avis")) {
                    avif = true;
                } else if (fourcc(header, offset, "heic") || fourcc(header, offset, "heix")
                    || fourcc(header, offset, "hevc") || fourcc(header, offset, "hevx")) {
                    heic = true;
                } else if (fourcc(header, offset, "mif1") || fourcc(header, offset, "msf1")) {
                    heif = true;
                }
            }
            if (avif) {
                return new Format("image/avif", ".avif");
            }
            if (heic) {
                return new Format("image/heic", ".heic");
            }
            if (heif) {
                return new Format("image/heif", ".heif");
            }
        }
        throw new IOException("返回的数据不是支持的 JPEG、PNG、GIF、WebP、HEIC 或 AVIF 图片");
    }

    private static boolean fourcc(byte[] bytes, int offset, String value) {
        if (offset < 0 || offset + value.length() > bytes.length) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (bytes[offset + index] != (byte) value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static int unsigned(byte value) {
        return value & 255;
    }
}
