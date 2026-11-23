package cn.dousave.desktop;

import cn.dousave.app.VideoInfo;
import cn.dousave.app.VideoResolver;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.mp4parser.muxer.Movie;
import org.mp4parser.muxer.Track;
import org.mp4parser.muxer.Movie;
import org.mp4parser.muxer.builder.DefaultMp4Builder;
import org.mp4parser.muxer.container.mp4.MovieCreator;

public final class DesktopTest {
    public static void main(String[] arguments) throws Exception {
        check(DesktopDownload.safeName("CON").equals("_CON"), "Reserved Windows filename");
        check(!DesktopDownload.safeName("title:/\\?*").contains(":"), "Forbidden filename characters");
        Path output = Paths.get(arguments[0]);
        Files.createDirectories(output);
        VideoInfo video = new VideoResolver().resolve("https://v.douyin.com/RQtMd_5ce00/");
        List<Path> saved = DesktopDownload.save(video, 0, false, output, System.out::println);
        check(Files.size(saved.get(0)) > 10000, "Real Douyin download");
        List<Path> images = DesktopDownload.save(video, 0, true, output, System.out::println);
        check(Files.size(images.get(0)) > 1000, "Real image download");
        VideoInfo liveGallery = new VideoResolver().resolve("https://www.douyin.com/note/7676078420824775161");
        check(liveGallery.hasMotion(), "Real Douyin gallery exposes Live Photo motion tracks");
        DesktopDownload.DownloadResult liveSaved = DesktopDownload.saveDetailed(liveGallery, 0, true, output,
            System.out::println);
        int stillFiles = 0;
        int motionFiles = 0;
        int motionWithoutAudio = 0;
        Path videoOnly = output.resolve("motion-video-only.mp4");
        for (Path path : liveSaved.files) {
            if (path.toString().endsWith(".mp4")) {
                motionFiles += 1;
                if (!DesktopDownload.hasAudioTrack(path)) {
                    motionWithoutAudio += 1;
                }
                if (videoOnly.toFile().exists()) {
                    Files.delete(videoOnly);
                }
                writeVideoOnlyCopy(path, videoOnly);
                check(!DesktopDownload.hasAudioTrack(videoOnly), "Silent motion audio detector identifies a video-only MP4");
                Files.delete(videoOnly);
            } else {
                stillFiles += 1;
            }
        }
        check(stillFiles == 4 && motionFiles == 3 && liveSaved.files.size() == 7,
            "Douyin Live Photo gallery saves all stills and matching motion clips");
        check(liveSaved.silentMotionCount == motionWithoutAudio,
            "Silent motion notice count matches actual downloaded audio tracks");
        System.out.println("Live motion clips without an audio track: " + liveSaved.silentMotionCount);
        VideoInfo bili = new VideoResolver().resolve("https://b23.tv/ootwsxT");
        List<Path> merged = DesktopDownload.save(bili, 0, false, output, System.out::println);
        Movie movie = MovieCreator.build(merged.get(0).toString());
        boolean videoTrack = false;
        boolean audioTrack = false;
        for (Track track : movie.getTracks()) {
            if (track.getHandler().equals("vide")) {
                videoTrack = true;
            }
            if (track.getHandler().equals("soun")) {
                audioTrack = true;
            }
            track.close();
        }
        check(videoTrack && audioTrack, "Bili merged MP4 contains video and audio");
        check(DesktopDownload.hasAudioTrack(merged.get(0)), "Audio-track inspection detects the merged audio");
        try (java.util.stream.Stream<Path> files = Files.list(output)) {
            check(files.noneMatch(path -> path.getFileName().toString().endsWith(".part")), "No temporary media after success");
        }
        Path cancelledDirectory = output.resolve("cancelled");
        Thread.currentThread().interrupt();
        boolean cancelled = false;
        try {
            DesktopDownload.save(video, 0, false, cancelledDirectory, text -> {});
        } catch (java.util.concurrent.CancellationException expected) {
            cancelled = true;
        } finally {
            Thread.interrupted();
        }
        check(cancelled, "Cancelled transfer stops before saving");
        try (java.util.stream.Stream<Path> files = Files.list(cancelledDirectory)) {
            check(files.count() == 0, "No partial media after cancellation");
        }
        Path screenshot = Paths.get(arguments[1]);
        DesktopApp.configureLook();
        SwingUtilities.invokeAndWait(() -> {
            try {
                DesktopApp app = new DesktopApp();
                app.setSize(940, 900);
                app.setVisible(true);
                check(!visibleButton(app, "复制文案"), "Text actions hidden before parsing");
                BufferedImage image = new BufferedImage(app.getWidth(), app.getHeight(), BufferedImage.TYPE_INT_RGB);
                app.paint(image.getGraphics());
                ImageIO.write(image, "png", screenshot.toFile());
                app.dispatchEvent(new java.awt.event.WindowEvent(app, java.awt.event.WindowEvent.WINDOW_CLOSING));
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
        System.out.println("Desktop checks passed.");
    }

    private static boolean visibleButton(Container container, String label) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton && ((JButton) component).getText().equals(label) && component.isShowing()) {
                return true;
            }
            if (component instanceof Container && visibleButton((Container) component, label)) {
                return true;
            }
        }
        return false;
    }

    private static void writeVideoOnlyCopy(Path source, Path destination) throws Exception {
        Movie sourceMovie = MovieCreator.build(source.toString());
        Movie videoMovie = new Movie();
        try {
            for (Track track : sourceMovie.getTracks()) {
                if (track.getHandler().equals("vide")) {
                    videoMovie.addTrack(track);
                } else {
                    track.close();
                }
            }
            if (videoMovie.getTracks().isEmpty()) {
                throw new AssertionError("Motion source has no video track");
            }
            try (java.nio.channels.FileChannel output = java.nio.channels.FileChannel.open(destination,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE)) {
                new DefaultMp4Builder().build(videoMovie).writeContainer(output);
            }
        } finally {
            for (Track track : videoMovie.getTracks()) {
                track.close();
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        System.out.println("PASS: " + message);
    }
}
