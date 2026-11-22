package cn.dousave.app;

public final class ResolveProbe {
    public static void main(String[] arguments) throws Exception {
        String source = arguments[0];
        if (source.equals("--stdin")) {
            java.io.ByteArrayOutputStream input = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = System.in.read(buffer)) != -1) {
                input.write(buffer, 0, count);
            }
            source = new String(input.toByteArray(), java.nio.charset.StandardCharsets.UTF_8).trim();
        }
        VideoInfo video = new VideoResolver().resolve(source);
        System.out.println("Platform: " + video.platform);
        System.out.println("ID: " + video.id);
        System.out.println("Title: " + video.title);
        System.out.println("Author: " + video.author);
        System.out.println("Images: " + video.images.size() + "; textLength=" + video.description.length());
        if (video.article) {
            System.out.println("Article body:\n" + video.description);
        }
        for (VideoInfo.Variant variant : video.variants) {
            System.out.println(variant.label() + " videoSources=" + variant.urls.size() + " audioSources=" + variant.audioUrls.size());
        }
        if (arguments.length > 1) {
            if (!video.variants.isEmpty()) {
                VideoInfo.Variant best = video.variants.get(0);
                download(best.urls, video.referer, arguments[1] + ".video.mp4");
                if (!best.audioUrls.isEmpty()) {
                    download(best.audioUrls, video.referer, arguments[1] + ".audio.m4a");
                }
            }
            for (VideoInfo.GalleryEntry entry : video.images) {
                String extension = ".image";
                if (entry.motion) {
                    extension = ".motion.mp4";
                }
                download(entry.urls, video.referer, arguments[1] + "." + entry.position + extension);
            }
        }
    }

    private static void download(java.util.List<String> urls, String referer, String destination) throws Exception {
        Exception failure = new Exception("No source");
        for (String value : urls) {
            java.net.URL next = new java.net.URL(value);
            try {
                for (int redirect = 0; redirect < 8; redirect++) {
                    NetworkPolicy.check(next, true);
                    java.net.HttpURLConnection connection = (java.net.HttpURLConnection) next.openConnection();
                    connection.setInstanceFollowRedirects(false);
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(20000);
                    connection.setRequestProperty("User-Agent", HttpClient.AGENT);
                    connection.setRequestProperty("Referer", referer);
                    try {
                        int status = connection.getResponseCode();
                        if (status >= 300 && status < 400) {
                            next = new java.net.URL(next, connection.getHeaderField("Location"));
                            continue;
                        }
                        if (status != 200) {
                            throw new Exception("HTTP " + status);
                        }
                        long total = 0;
                        try (java.io.InputStream input = connection.getInputStream(); java.io.FileOutputStream output = new java.io.FileOutputStream(destination)) {
                            byte[] buffer = new byte[65536];
                            int count;
                            while ((count = input.read(buffer)) >= 0) {
                                total += count;
                                if (total > 250 * 1024 * 1024) {
                                    throw new Exception("Probe file exceeds 250 MB");
                                }
                                output.write(buffer, 0, count);
                            }
                        }
                        if (connection.getContentLengthLong() > 0 && total != connection.getContentLengthLong()) {
                            throw new Exception("Incomplete download");
                        }
                        System.out.println("Downloaded " + total + " bytes; host=" + next.getHost());
                        return;
                    } finally {
                        connection.disconnect();
                    }
                }
            } catch (Exception error) {
                failure = error;
            }
        }
        throw failure;
    }
}
