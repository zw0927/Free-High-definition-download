package cn.dousave.app;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import java.nio.ByteBuffer;

public final class MediaMux {
    public interface Cancellation {
        boolean cancelled();
    }

    private MediaMux() {}

    public static void combine(GalleryItem video, GalleryItem audio, GalleryItem output, Cancellation cancellation) throws Exception {
        MediaExtractor videoReader = new MediaExtractor();
        MediaExtractor audioReader = new MediaExtractor();
        MediaMuxer writer = null;
        boolean started = false;
        try (ParcelFileDescriptor videoFile = video.descriptor(false);
             ParcelFileDescriptor audioFile = audio.descriptor(false);
             ParcelFileDescriptor outputFile = output.descriptor(true)) {
            videoReader.setDataSource(videoFile.getFileDescriptor());
            audioReader.setDataSource(audioFile.getFileDescriptor());
            int videoIndex = find(videoReader, "video/");
            int audioIndex = find(audioReader, "audio/");
            if (videoIndex < 0 || audioIndex < 0) {
                throw new Exception("平台返回的音视频轨不完整，请重新解析");
            }
            writer = new MediaMuxer(outputFile.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaFormat videoFormat = videoReader.getTrackFormat(videoIndex);
            if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                writer.setOrientationHint(videoFormat.getInteger(MediaFormat.KEY_ROTATION));
            }
            int videoOutput = writer.addTrack(videoFormat);
            int audioOutput = writer.addTrack(audioReader.getTrackFormat(audioIndex));
            videoReader.selectTrack(videoIndex);
            audioReader.selectTrack(audioIndex);
            writer.start();
            started = true;
            copy(videoReader, writer, videoOutput, cancellation);
            copy(audioReader, writer, audioOutput, cancellation);
            writer.stop();
            started = false;
        } finally {
            videoReader.release();
            audioReader.release();
            if (writer != null) {
                if (started) {
                    try {
                        writer.stop();
                    } catch (Exception ignored) {
                        // An interrupted file is deleted by the download service.
                    }
                }
                writer.release();
            }
        }
    }

    private static int find(MediaExtractor reader, String prefix) {
        for (int index = 0; index < reader.getTrackCount(); index++) {
            String mime = reader.getTrackFormat(index).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(prefix)) {
                return index;
            }
        }
        return -1;
    }

    private static void copy(MediaExtractor reader, MediaMuxer writer, int track, Cancellation cancellation) throws Exception {
        int capacity = 16 * 1024 * 1024;
        ByteBuffer buffer = ByteBuffer.allocateDirect(capacity);
        MediaCodec.BufferInfo sample = new MediaCodec.BufferInfo();
        int count = 0;
        while (reader.getSampleTime() >= 0) {
            if (cancellation.cancelled()) {
                throw new InterruptedException();
            }
            long size = capacity;
            if (Build.VERSION.SDK_INT >= 28) {
                size = reader.getSampleSize();
            }
            if (size > 32 * 1024 * 1024) {
                throw new Exception("视频单帧过大，当前设备无法合成");
            }
            if (size > capacity) {
                capacity = (int) size;
                buffer = ByteBuffer.allocateDirect(capacity);
            }
            buffer.clear();
            int bytes = reader.readSampleData(buffer, 0);
            if (bytes < 0) {
                break;
            }
            sample.set(0, bytes, reader.getSampleTime(), 0);
            if ((reader.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                sample.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }
            writer.writeSampleData(track, buffer, sample);
            count += 1;
            reader.advance();
        }
        if (count == 0) {
            throw new Exception("平台返回了空的音视频轨");
        }
    }
}
