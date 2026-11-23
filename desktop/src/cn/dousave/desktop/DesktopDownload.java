package cn.dousave.desktop;

import cn.dousave.app.HttpClient;
import cn.dousave.app.ImageMedia;
import cn.dousave.app.NetworkPolicy;
import cn.dousave.app.VideoInfo;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import org.mp4parser.Container;
import org.mp4parser.IsoFile;
import org.mp4parser.muxer.Movie;
import org.mp4parser.muxer.Track;
import org.mp4parser.muxer.builder.DefaultMp4Builder;
import org.mp4parser.muxer.container.mp4.MovieCreator;

public final class DesktopDownload {
    private DesktopDownload() {}

    public static List<Path> save(VideoInfo info, int selected, boolean images, Path directory,
                                  Consumer<String> status) throws Exception {
        return saveDetailed(info, selected, images, directory, status).files;
    }

    public static final class DownloadResult {
        public final List<Path> files;
        public final int silentMotionCount;
        public final List<String> failures;

        private DownloadResult(List<Path> files, int silentMotionCount, List<String> failures) {
            this.files = files;
            this.silentMotionCount = silentMotionCount;
            this.failures = failures;
        }
    }

    public static DownloadResult saveDetailed(VideoInfo info, int selected, boolean images, Path directory,
                                              Consumer<String> status) throws Exception {
        Files.createDirectories(directory);
        List<Path> completed = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int silentMotionCount = 0;
        String base = safeName(info.title);
        if (images) {
            for (int index = 0; index < info.images.size(); index++) {
                checkCancelled();
                VideoInfo.GalleryEntry entry = info.images.get(index);
                status.accept("正在下载图集 " + (index + 1) + " / " + info.images.size());
                Path partial = null;
                try {
                    partial = Files.createTempFile(directory, ".fhd-gallery-", ".part");
                    String extension = transfer(entry.urls, info.referer, partial, status, entry);
                    boolean silent = entry.motion && !hasAudioTrack(partial);
                    String suffix = "";
                    if (entry.motion) {
                        suffix = "_motion";
                    }
                    Path target = uniquePath(directory, base + "_" + entry.position + suffix, extension);
                    checkCancelled();
                    Files.move(partial, target);
                    completed.add(target);
                    if (silent) {
                        silentMotionCount += 1;
                    }
                } catch (CancellationException error) {
                    throw error;
                } catch (Exception error) {
                    String kind = "图片";
                    if (entry.motion) {
                        kind = "实况";
                    }
                    failures.add("第 " + entry.position + " 项" + kind + "：" + error.getMessage());
                } finally {
                    if (partial != null) {
                        Files.deleteIfExists(partial);
                    }
                }
            }
            return new DownloadResult(completed, silentMotionCount, failures);
        }
        VideoInfo.Variant variant = info.variants.get(selected);
        Path video = Files.createTempFile(directory, ".fhd-video-", ".part");
        Path audio = null;
        Path merged = null;
        try {
            status.accept("正在下载视频");
            transfer(variant.urls, info.referer, video, status);
            Path finished = video;
            if (!variant.audioUrls.isEmpty()) {
                audio = Files.createTempFile(directory, ".fhd-audio-", ".part");
                merged = Files.createTempFile(directory, ".fhd-merge-", ".part");
                status.accept("正在下载音频");
                transfer(variant.audioUrls, info.referer, audio, status);
                checkCancelled();
                status.accept("正在合并音视频");
                mux(video, audio, merged);
                finished = merged;
            }
            verifyMp4(finished);
            checkCancelled();
            Path target = uniquePath(directory, base, ".mp4");
            Files.move(finished, target);
            completed.add(target);
            return new DownloadResult(completed, 0, failures);
        } finally {
            Files.deleteIfExists(video);
            if (audio != null) {
                Files.deleteIfExists(audio);
            }
            if (merged != null) {
                Files.deleteIfExists(merged);
            }
        }
    }

    private static void transfer(List<String> sources, String referer, Path destination,
                                 Consumer<String> status) throws Exception {
        transfer(sources, referer, destination, status, null);
    }

    private static String transfer(List<String> sources, String referer, Path destination,
                                   Consumer<String> status, VideoInfo.GalleryEntry entry) throws Exception {
        Exception failure = new Exception("没有可用的下载地址");
        for (String source : sources) {
            checkCancelled();
            URL next = new URL(source);
            try {
                for (int redirect = 0; redirect < 8; redirect++) {
                    checkCancelled();
                    NetworkPolicy.check(next, true);
                    if (entry != null && entry.motion && !VideoInfo.isUnmarkedUrl(next.toString())) {
                        throw new Exception("平台返回了带水印的实况地址");
                    }
                    HttpURLConnection connection = (HttpURLConnection) next.openConnection();
                    connection.setInstanceFollowRedirects(false);
                    connection.setConnectTimeout(12000);
                    connection.setReadTimeout(15000);
                    connection.setRequestProperty("User-Agent", HttpClient.AGENT);
                    connection.setRequestProperty("Referer", referer);
                    connection.setRequestProperty("Accept-Encoding", "identity");
                    try {
                        int code = connection.getResponseCode();
                        if (code >= 300 && code < 400) {
                            String location = connection.getHeaderField("Location");
                            if (location == null) {
                                throw new Exception("下载地址跳转无效");
                            }
                            next = new URL(next, location);
                            continue;
                        }
                        if (code != 200) {
                            throw new Exception("下载服务器返回 HTTP " + code);
                        }
                        long expected = connection.getContentLengthLong();
                        long downloaded = 0;
                        long lastUpdate = 0;
                        try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(destination)) {
                            byte[] buffer = new byte[65536];
                            int count;
                            while ((count = input.read(buffer)) != -1) {
                                checkCancelled();
                                output.write(buffer, 0, count);
                                downloaded += count;
                                if (System.currentTimeMillis() - lastUpdate > 250) {
                                    String message = String.format(java.util.Locale.ROOT, "已接收 %.1f MB", downloaded / 1048576.0);
                                    if (expected > 0) {
                                        message += " · " + downloaded * 100 / expected + "%";
                                    }
                                    status.accept(message);
                                    lastUpdate = System.currentTimeMillis();
                                }
                            }
                        }
                        if (downloaded == 0 || expected > 0 && expected != downloaded) {
                            throw new Exception("下载文件不完整");
                        }
                        if (entry == null) {
                            return "";
                        }
                        if (entry.motion) {
                            verifyMp4(destination);
                            return ".mp4";
                        }
                        return imageExtension(destination);
                    } finally {
                        connection.disconnect();
                    }
                }
                throw new Exception("下载地址跳转次数过多");
            } catch (CancellationException error) {
                throw error;
            } catch (Exception error) {
                failure = error;
            }
        }
        throw failure;
    }

    public static void mux(Path video, Path audio, Path destination) throws Exception {
        Path temporaryRoot = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"));
        Files.createDirectories(temporaryRoot);
        java.util.Set<Path> existing = new java.util.HashSet<>();
        try (java.util.stream.Stream<Path> files = Files.list(temporaryRoot)) {
            files.filter(path -> path.getFileName().toString().startsWith("MediaDataBox")).forEach(existing::add);
        }
        try {
            try (FileChannel videoChannel = FileChannel.open(video); FileChannel audioChannel = FileChannel.open(audio);
                 HeapSource videoSource = new HeapSource(video); HeapSource audioSource = new HeapSource(audio)) {
                Movie videoMovie = MovieCreator.build(videoChannel, videoSource, video.toString());
                Movie audioMovie = MovieCreator.build(audioChannel, audioSource, audio.toString());
                writeMovie(videoMovie, audioMovie, destination);
            }
        } finally {
            try (java.util.stream.Stream<Path> files = Files.list(temporaryRoot)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    if (file.getFileName().toString().startsWith("MediaDataBox") && !existing.contains(file)) {
                        Files.deleteIfExists(file);
                    }
                }
            }
        }
    }

    private static void writeMovie(Movie videoMovie, Movie audioMovie, Path destination) throws Exception {
        List<Track> opened = new ArrayList<>();
        opened.addAll(videoMovie.getTracks());
        opened.addAll(audioMovie.getTracks());
        try {
            Movie output = new Movie();
            boolean hasVideo = false;
            boolean hasAudio = false;
            for (Track track : videoMovie.getTracks()) {
                if (track.getHandler().equals("vide")) {
                    output.addTrack(track);
                    hasVideo = true;
                }
            }
            for (Track track : audioMovie.getTracks()) {
                if (track.getHandler().equals("soun")) {
                    output.addTrack(track);
                    hasAudio = true;
                }
            }
            if (!hasVideo || !hasAudio) {
                throw new Exception("缺少完整的视频或音频轨道");
            }
            checkCancelled();
            Container container = new DefaultMp4Builder().build(output);
            try (FileChannel channel = FileChannel.open(destination, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                container.writeContainer(channel);
            }
            checkCancelled();
        } finally {
            for (Track track : opened) {
                track.close();
            }
        }
    }

    private static final class HeapSource implements org.mp4parser.muxer.RandomAccessSource {
        private final FileChannel channel;

        HeapSource(Path file) throws Exception {
            channel = FileChannel.open(file);
        }

        @Override
        public java.nio.ByteBuffer get(long offset, long size) throws java.io.IOException {
            if (size > Integer.MAX_VALUE) {
                throw new java.io.IOException("单次读取的数据过大");
            }
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate((int) size);
            long position = offset;
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer, position);
                if (read < 0) {
                    throw new java.io.IOException("视频轨道不完整");
                }
                position += read;
            }
            buffer.flip();
            return buffer;
        }

        @Override
        public void close() throws java.io.IOException {
            channel.close();
        }
    }

    private static void verifyMp4(Path file) throws Exception {
        byte[] header = new byte[12];
        try (InputStream input = Files.newInputStream(file)) {
            if (input.read(header) < 12 || header[4] != 'f' || header[5] != 't' || header[6] != 'y' || header[7] != 'p') {
                throw new Exception("服务器没有返回 MP4 视频，未保存为成片");
            }
        }
    }

    static boolean hasAudioTrack(Path file) throws Exception {
        boolean hasVideo = false;
        boolean hasAudio = false;
        try (IsoFile movie = new IsoFile(file.toFile())) {
            if (movie.getMovieBox() == null) {
                throw new Exception("实况文件缺少 MP4 媒体信息，未保存");
            }
            for (org.mp4parser.boxes.iso14496.part12.TrackBox track : movie.getMovieBox()
                .getBoxes(org.mp4parser.boxes.iso14496.part12.TrackBox.class)) {
                org.mp4parser.boxes.iso14496.part12.MediaBox media = track.getMediaBox();
                if (media == null || media.getHandlerBox() == null || track.getSampleTableBox() == null
                    || track.getSampleTableBox().getSampleSizeBox() == null) {
                    continue;
                }
                String handler = media.getHandlerBox().getHandlerType();
                long sampleCount = track.getSampleTableBox().getSampleSizeBox().getSampleCount();
                if ("vide".equals(handler) && sampleCount > 0) {
                    hasVideo = true;
                }
                if ("soun".equals(handler) && sampleCount > 0) {
                    hasAudio = true;
                }
            }
        }
        if (!hasVideo) {
            throw new Exception("实况文件缺少有效视频轨，未保存");
        }
        return hasAudio;
    }

    static String imageExtension(Path file) throws Exception {
        try (InputStream input = Files.newInputStream(file)) {
            return ImageMedia.detect(input).extension;
        }
    }

    public static String safeName(String title) {
        String name = title.replaceAll("[\\x00-\\x1f<>:\"/\\\\|?*]", "_").replaceAll("[. ]+$", "").trim();
        if (name.isEmpty()) {
            name = "download";
        }
        if (name.length() > 75) {
            name = name.substring(0, name.offsetByCodePoints(0, Math.min(60, name.codePointCount(0, name.length()))));
        }
        if (name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?")) {
            name = "_" + name;
        }
        return name;
    }

    public static Path uniquePath(Path directory, String base, String extension) {
        Path target = directory.resolve(base + extension);
        int suffix = 2;
        while (Files.exists(target)) {
            target = directory.resolve(base + " (" + suffix + ")" + extension);
            suffix += 1;
        }
        return target;
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("已取消");
        }
    }
}
