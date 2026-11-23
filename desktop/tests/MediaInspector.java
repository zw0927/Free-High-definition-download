package cn.dousave.desktop;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.mp4parser.muxer.Movie;
import org.mp4parser.muxer.Track;
import org.mp4parser.muxer.container.mp4.MovieCreator;
import org.mp4parser.boxes.sampleentry.SampleEntry;

public final class MediaInspector {
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Pass exactly one MP4 path.");
        }
        Path media = Paths.get(arguments[0]);
        Movie movie = MovieCreator.build(media.toString());
        boolean hasAudio = false;
        boolean hasVideo = false;
        try {
            System.out.println("file=" + media.toAbsolutePath());
            System.out.println("bytes=" + java.nio.file.Files.size(media));
            for (Track track : movie.getTracks()) {
                List<String> sampleEntries = new ArrayList<>();
                for (SampleEntry entry : track.getSampleEntries()) {
                    sampleEntries.add(entry.getType());
                }
                int sampleCount = track.getSamples().size();
                if ("soun".equals(track.getHandler()) && sampleCount > 0) {
                    hasAudio = true;
                }
                if ("vide".equals(track.getHandler()) && sampleCount > 0) {
                    hasVideo = true;
                }
                System.out.println("track=" + track.getHandler() + " samples=" + sampleCount
                    + " timescale=" + track.getTrackMetaData().getTimescale()
                    + " sampleEntries=" + sampleEntries);
            }
            System.out.println("hasVideo=" + hasVideo);
            System.out.println("hasAudio=" + hasAudio);
            System.out.println("desktopDetectorHasAudio=" + DesktopDownload.hasAudioTrack(media));
        } finally {
            for (Track track : movie.getTracks()) {
                track.close();
            }
        }
    }
}
