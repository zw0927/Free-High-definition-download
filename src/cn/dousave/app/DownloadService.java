package cn.dousave.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DownloadService extends Service {
    public static final String EVENT = "cn.dousave.app.DOWNLOAD_EVENT";
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final String CHANNEL = "downloads";
    private static final int NOTIFICATION_ID = 21;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;
    private volatile boolean running;
    private volatile HttpURLConnection connection;
    private String currentTitle = "视频下载";
    private long lastUpdate;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "视频下载", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        GalleryItem.recover(this);
        for (String name : new String[] {"video-download.part", "audio-download.part", "combined-download.mp4"}) {
            new File(getCacheDir(), name).delete();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if ("cancel".equals(intent.getAction())) {
            cancelled = true;
            HttpURLConnection active = connection;
            if (active != null) {
                active.disconnect();
            }
            if (!running) {
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        if (running) {
            return START_NOT_STICKY;
        }
        cancelled = false;
        running = true;
        currentTitle = intent.getStringExtra("title");
        if (currentTitle == null || currentTitle.trim().isEmpty()) {
            currentTitle = "视频下载";
        }
        Notification notification = notification("准备下载", -1, true, null);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        publish("running", "准备下载", -1, "");
        worker.execute(new Runnable() {
            @Override
            public void run() {
                download(intent);
            }
        });
        return START_NOT_STICKY;
    }

    private void download(Intent intent) {
        GalleryItem temporary = null;
        GalleryItem audio = null;
        GalleryItem combined = null;
        Uri saved = null;
        try {
            String baseName = currentTitle.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", " ").trim();
            if (baseName.length() > 45) {
                baseName = baseName.substring(0, 45);
            }
            baseName += "_" + System.currentTimeMillis();
            String referer = intent.getStringExtra("referer");
            if (referer == null) {
                referer = "https://www.douyin.com/";
            }
            String imageGroups = intent.getStringExtra("images");
            if (imageGroups != null) {
                saveImages(new JSONArray(imageGroups), referer, baseName);
                return;
            }
            String[] urls = intent.getStringArrayExtra("urls");
            if (urls == null || urls.length == 0) {
                throw new Exception("视频地址为空，请重新解析");
            }
            temporary = new GalleryItem(this, baseName + ".mp4", "video/mp4");
            receiveCandidates(urls, temporary, referer, "视频");
            GalleryItem ready = temporary;
            String[] audioUrls = intent.getStringArrayExtra("audioUrls");
            if (audioUrls != null && audioUrls.length > 0) {
                audio = new GalleryItem(this, baseName + ".m4a", "audio/mp4");
                combined = new GalleryItem(this, baseName + "_merged.mp4", "video/mp4");
                receiveCandidates(audioUrls, audio, referer, "音频");
                publish("running", "正在合并音视频", -1, "");
                MediaMux.combine(temporary, audio, combined, new MediaMux.Cancellation() {
                    @Override
                    public boolean cancelled() {
                        return cancelled;
                    }
                });
                ready = combined;
            }
            if (cancelled) {
                throw new InterruptedException();
            }
            publish("running", "正在校验视频", 100, "");
            MediaMetadataRetriever metadata = new MediaMetadataRetriever();
            int width;
            int height;
            try (android.os.ParcelFileDescriptor file = ready.descriptor(false)) {
                metadata.setDataSource(file.getFileDescriptor());
                String widthValue = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String heightValue = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                String durationValue = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (widthValue == null || heightValue == null || durationValue == null) {
                    throw new Exception("下载内容不是完整的视频文件，请重新解析");
                }
                width = Integer.parseInt(widthValue);
                height = Integer.parseInt(heightValue);
                if (width <= 0 || height <= 0 || Long.parseLong(durationValue) <= 0) {
                    throw new Exception("视频文件校验失败，请重新解析");
                }
            } finally {
                metadata.release();
            }
            ready.commit();
            saved = ready.uri;
            remember(saved, currentTitle, width, height, "video/mp4");
            String message = "已保存到相册 · " + width + " × " + height;
            publish("complete", message, 100, saved.toString());
            stopForeground(STOP_FOREGROUND_REMOVE);
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(message, 100, false, saved));
        } catch (Exception error) {
            String message = "下载失败，请重新解析后重试";
            String state = "error";
            if (cancelled || error instanceof InterruptedException) {
                message = "已取消下载";
                if (error.getMessage() != null && error.getMessage().startsWith("已取消，已保存")) {
                    message = error.getMessage();
                }
                state = "cancelled";
            } else if (error.getMessage() != null) {
                message = error.getMessage();
            }
            publish(state, message, 0, "");
            stopForeground(STOP_FOREGROUND_REMOVE);
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(message, 0, false, null));
        } finally {
            if (temporary != null) {
                temporary.discard();
            }
            if (audio != null) {
                audio.discard();
            }
            if (combined != null) {
                combined.discard();
            }
            GalleryItem.recover(this);
            connection = null;
            running = false;
            stopSelf();
        }
    }

    private void receiveCandidates(String[] urls, GalleryItem temporary, String referer, String kind) throws Exception {
        Exception lastError = new Exception("没有可用的下载线路");
        for (String url : urls) {
            if (cancelled) {
                throw new InterruptedException();
            }
            try {
                receive(url, temporary, referer, kind);
                validateTrack(temporary, kind);
                return;
            } catch (Exception error) {
                lastError = error;
                if (cancelled) {
                    throw new InterruptedException();
                }
            }
        }
        throw lastError;
    }

    private void validateTrack(GalleryItem item, String kind) throws Exception {
        if (kind.equals("图片")) {
            try (android.os.ParcelFileDescriptor file = item.descriptor(false)) {
                android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeFileDescriptor(file.getFileDescriptor(), null, options);
                if (options.outWidth <= 0 || options.outHeight <= 0 || options.outMimeType == null) {
                    throw new Exception("平台未返回有效图片");
                }
            }
            return;
        }
        android.media.MediaExtractor reader = new android.media.MediaExtractor();
        String prefix = "video/";
        if (kind.equals("音频")) {
            prefix = "audio/";
        }
        try (android.os.ParcelFileDescriptor file = item.descriptor(false)) {
            reader.setDataSource(file.getFileDescriptor());
            for (int index = 0; index < reader.getTrackCount(); index++) {
                String mime = reader.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith(prefix)) {
                    reader.selectTrack(index);
                    if (reader.getSampleTime() >= 0) {
                        return;
                    }
                }
            }
            throw new Exception("平台未返回有效的" + kind + "文件，请重新解析");
        } finally {
            reader.release();
        }
    }

    private void receive(String url, GalleryItem temporary, String referer, String kind) throws Exception {
        URL next = new URL(url);
        int redirects = 0;
        HttpURLConnection active = null;
        try {
            while (redirects <= 8) {
                if (cancelled) {
                    throw new InterruptedException();
                }
                NetworkPolicy.check(next, true);
                if (kind.equals("实况") && !VideoInfo.isUnmarkedUrl(next.toString())) {
                    throw new Exception("平台返回了带水印的实况地址");
                }
                active = (HttpURLConnection) next.openConnection();
                connection = active;
                active.setInstanceFollowRedirects(false);
                active.setConnectTimeout(20000);
                active.setReadTimeout(30000);
                active.setRequestProperty("User-Agent", USER_AGENT);
                active.setRequestProperty("Referer", referer);
                active.setRequestProperty("Accept-Encoding", "identity");
                int status = active.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = active.getHeaderField("Location");
                    if (location == null) {
                        throw new Exception("视频线路返回了无效跳转");
                    }
                    next = new URL(next, location);
                    active.disconnect();
                    redirects += 1;
                    continue;
                }
                if (status != 200) {
                    throw new Exception("视频线路返回 HTTP " + status + "，请重新解析后重试");
                }
                String type = active.getContentType();
                if (type != null && (type.contains("text/") || type.contains("json"))) {
                    throw new Exception("平台返回了错误页面，请重新解析");
                }
                break;
            }
            if (redirects > 8 || active == null) {
                throw new Exception("视频地址跳转次数过多");
            }
            long expected = active.getContentLengthLong();
            File storage = Environment.getExternalStorageDirectory();
            if (expected > 0 && storage.getUsableSpace() < expected * 2 + 20 * 1024 * 1024) {
                throw new Exception("手机存储空间不足，请清理后重试");
            }
            try (InputStream input = active.getInputStream(); OutputStream output = temporary.output()) {
                byte[] buffer = new byte[65536];
                long received = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (cancelled) {
                        throw new InterruptedException();
                    }
                    output.write(buffer, 0, count);
                    received += count;
                    if (storage.getUsableSpace() < 10 * 1024 * 1024) {
                        throw new Exception("手机存储空间不足");
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 450) {
                        int progress = -1;
                        String message = String.format(Locale.CHINA, "已下载 %.1f MB", received / 1048576.0);
                        if (expected > 0) {
                            progress = (int) Math.min(99, received * 100 / expected);
                            message = String.format(Locale.CHINA, "%.1f / %.1f MB", received / 1048576.0, expected / 1048576.0);
                        }
                        message = "正在下载" + kind + " · " + message;
                        publish("running", message, progress, "");
                        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(message, progress, true, null));
                        lastUpdate = now;
                    }
                }
                if (received == 0 || expected > 0 && received != expected) {
                    throw new Exception("视频下载不完整，请检查网络后重试");
                }
            }
        } finally {
            if (active != null) {
                active.disconnect();
            }
        }
    }

    private void saveImages(JSONArray groups, String referer, String baseName) throws Exception {
        int completed = 0;
        try {
            for (int index = 0; index < groups.length(); index++) {
                if (cancelled) {
                    throw new InterruptedException();
                }
                JSONObject entry = groups.optJSONObject(index);
                boolean motion = false;
                int position = index + 1;
                JSONArray group;
                if (entry == null) {
                    group = groups.getJSONArray(index);
                } else {
                    group = entry.getJSONArray("urls");
                    motion = entry.optBoolean("motion");
                    position = entry.optInt("position", position);
                }
                String[] urls = new String[group.length()];
                for (int urlIndex = 0; urlIndex < group.length(); urlIndex++) {
                    urls[urlIndex] = group.getString(urlIndex);
                }
                String name = baseName + "_" + position;
                String mime = "image/jpeg";
                String kind = "图片";
                String extension = ".jpg";
                if (motion) {
                    mime = "video/mp4";
                    extension = ".mp4";
                    kind = "实况";
                }
                GalleryItem item = new GalleryItem(this, name + extension, mime);
                try {
                    publish("running", "正在保存" + kind + " " + (index + 1) + " / " + groups.length(), -1, "");
                    receiveCandidates(urls, item, referer, kind);
                    android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    try (android.os.ParcelFileDescriptor file = item.descriptor(false)) {
                        if (motion) {
                            MediaMetadataRetriever metadata = new MediaMetadataRetriever();
                            try {
                                metadata.setDataSource(file.getFileDescriptor());
                                options.outWidth = Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                                options.outHeight = Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                            } finally {
                                metadata.release();
                            }
                        } else {
                            android.graphics.BitmapFactory.decodeFileDescriptor(file.getFileDescriptor(), null, options);
                            mime = options.outMimeType;
                            item.imageType(mime);
                        }
                    }
                    if (cancelled) {
                        throw new InterruptedException();
                    }
                    item.commit();
                    completed += 1;
                    remember(item.uri, currentTitle + " · " + position, options.outWidth, options.outHeight, mime);
                } finally {
                    item.discard();
                }
            }
        } catch (Exception error) {
            if (cancelled) {
                throw new Exception("已取消，已保存 " + completed + " 项");
            }
            throw new Exception("已保存 " + completed + " 项，其余未完成：" + error.getMessage());
        }
        String message = "已保存 " + completed + " 项图集内容到相册";
        publish("complete", message, 100, "");
        stopForeground(STOP_FOREGROUND_REMOVE);
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(message, 100, false, null));
    }

    private void remember(Uri uri, String title, int width, int height, String mime) throws Exception {
        SharedPreferences preferences = getSharedPreferences("downloads", MODE_PRIVATE);
        JSONArray previous = new JSONArray(preferences.getString("history", "[]"));
        JSONArray next = new JSONArray();
        JSONObject entry = new JSONObject();
        entry.put("uri", uri.toString());
        entry.put("title", title);
        entry.put("width", width);
        entry.put("height", height);
        entry.put("mime", mime);
        entry.put("time", System.currentTimeMillis());
        next.put(entry);
        for (int index = 0; index < previous.length() && index < 29; index++) {
            next.put(previous.get(index));
        }
        preferences.edit().putString("history", next.toString()).apply();
    }

    private Notification notification(String message, int progress, boolean ongoing, Uri video) {
        Intent open = new Intent(this, MainActivity.class);
        if (video != null) {
            open.putExtra("openVideo", video.toString());
        }
        PendingIntent content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(currentTitle)
            .setContentText(message)
            .setContentIntent(content)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing);
        if (ongoing) {
            boolean indeterminate = progress < 0;
            builder.setProgress(100, Math.max(0, progress), indeterminate);
            Intent cancel = new Intent(this, DownloadService.class).setAction("cancel");
            PendingIntent pending = PendingIntent.getService(this, 1, cancel, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(null, "取消", pending).build());
        } else {
            builder.setAutoCancel(true);
        }
        return builder.build();
    }

    private void publish(String state, String message, int progress, String uri) {
        getSharedPreferences("downloads", MODE_PRIVATE).edit()
            .putString("state", state).putString("message", message)
            .putInt("progress", progress).putString("uri", uri).apply();
        Intent event = new Intent(EVENT).setPackage(getPackageName());
        event.putExtra("state", state).putExtra("message", message)
            .putExtra("progress", progress).putExtra("uri", uri);
        sendBroadcast(event);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        cancelled = true;
        HttpURLConnection active = connection;
        if (active != null) {
            active.disconnect();
        }
        worker.shutdown();
        super.onDestroy();
    }

    @Override
    public void onTimeout(int startId, int type) {
        cancelled = true;
        HttpURLConnection active = connection;
        if (active != null) {
            active.disconnect();
        }
        publish("error", "下载超过系统允许的后台运行时间，请重试", 0, "");
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
}
