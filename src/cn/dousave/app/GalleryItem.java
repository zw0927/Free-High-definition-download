package cn.dousave.app;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import org.json.JSONArray;

/** A pending public media file, never an application cache copy. */
public final class GalleryItem {
    private final Context context;
    public Uri uri;
    private File file;
    private String name;
    private String mime;
    private boolean committed;

    public GalleryItem(Context context, String name, String mime) throws Exception {
        this.context = context;
        this.name = name;
        this.mime = mime;
        String directory = "Movies";
        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        if (mime.startsWith("image/")) {
            directory = "Pictures";
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        } else if (mime.startsWith("audio/")) {
            directory = "Music";
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, directory + "/无水印下载");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            uri = context.getContentResolver().insert(collection, values);
            if (uri == null) {
                throw new Exception("无法创建相册文件");
            }
        } else {
            File folder = new File(Environment.getExternalStoragePublicDirectory(directory), "无水印下载");
            if (!folder.exists() && !folder.mkdirs()) {
                throw new Exception("无法创建相册目录");
            }
            file = new File(folder, "." + name + ".part");
            uri = Uri.fromFile(file);
        }
        try {
            track(uri.toString());
        } catch (Exception error) {
            discard();
            throw error;
        }
    }

    public OutputStream output() throws Exception {
        if (file != null) {
            return new FileOutputStream(file);
        }
        OutputStream output = context.getContentResolver().openOutputStream(uri, "wt");
        if (output == null) {
            throw new Exception("无法写入相册");
        }
        return output;
    }

    public ParcelFileDescriptor descriptor(boolean write) throws Exception {
        String mode = "r";
        if (write) {
            mode = "rwt";
        }
        if (file != null) {
            int flags = ParcelFileDescriptor.MODE_READ_ONLY;
            if (write) {
                flags = ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE;
            }
            return ParcelFileDescriptor.open(file, flags);
        }
        ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, mode);
        if (descriptor == null) {
            throw new Exception("无法读取相册文件");
        }
        return descriptor;
    }

    public void commit() throws Exception {
        if (file != null) {
            File finalFile = new File(file.getParentFile(), name);
            if (!file.renameTo(finalFile)) {
                throw new Exception("无法完成相册保存");
            }
            file = finalFile;
            uri = Uri.fromFile(file);
            MediaScannerConnection.scanFile(context, new String[] {file.getAbsolutePath()}, new String[] {mime}, null);
        } else {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (context.getContentResolver().update(uri, values, null, null) != 1) {
                throw new Exception("无法发布相册文件");
            }
        }
        committed = true;
    }

    public void imageType(String detectedMime) throws Exception {
        String extension = ".jpg";
        if ("image/png".equals(detectedMime)) {
            extension = ".png";
        } else if ("image/webp".equals(detectedMime)) {
            extension = ".webp";
        } else if (!"image/jpeg".equals(detectedMime)) {
            throw new Exception("暂不支持这张图片的格式");
        }
        mime = detectedMime;
        name = name.substring(0, name.lastIndexOf('.')) + extension;
        if (file == null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            context.getContentResolver().update(uri, values, null, null);
        }
    }

    public void discard() {
        if (committed) {
            return;
        }
        try {
            if (file != null) {
                file.delete();
            } else {
                context.getContentResolver().delete(uri, null, null);
            }
        } catch (Exception ignored) {
            // The pending registry retries cleanup before the next download.
        }
    }

    private void track(String value) throws Exception {
        android.content.SharedPreferences settings = context.getSharedPreferences("pending_media", Context.MODE_PRIVATE);
        JSONArray entries = new JSONArray(settings.getString("items", "[]"));
        entries.put(value);
        if (!settings.edit().putString("items", entries.toString()).commit()) {
            throw new Exception("无法记录下载状态，请检查存储空间");
        }
    }

    public static void recover(Context context) {
        android.content.SharedPreferences settings = context.getSharedPreferences("pending_media", Context.MODE_PRIVATE);
        JSONArray remaining = new JSONArray();
        try {
            JSONArray entries = new JSONArray(settings.getString("items", "[]"));
            for (int index = 0; index < entries.length(); index++) {
                String value = entries.getString(index);
                try {
                    Uri uri = Uri.parse(value);
                    if ("file".equals(uri.getScheme())) {
                        File item = new File(uri.getPath());
                        File publicRoot = Environment.getExternalStorageDirectory().getCanonicalFile();
                        if (item.getCanonicalPath().startsWith(publicRoot.getPath() + File.separator)
                            && item.getName().endsWith(".part") && item.getParentFile().getName().equals("无水印下载")) {
                            if (item.exists() && !item.delete()) {
                                remaining.put(value);
                            }
                        }
                    } else if (Build.VERSION.SDK_INT >= 29 && "media".equals(uri.getAuthority())) {
                        try (android.database.Cursor cursor = context.getContentResolver().query(uri,
                            new String[] {MediaStore.MediaColumns.IS_PENDING}, null, null, null)) {
                            if (cursor != null && cursor.moveToFirst() && cursor.getInt(0) == 1) {
                                context.getContentResolver().delete(uri, null, null);
                            }
                        }
                    }
                } catch (Exception error) {
                    remaining.put(value);
                }
            }
        } catch (Exception ignored) {
            // Keep startup usable if old task metadata is damaged.
        }
        settings.edit().putString("items", remaining.toString()).commit();
    }
}
