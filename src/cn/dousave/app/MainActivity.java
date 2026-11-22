package cn.dousave.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(27, 37, 43);
    private static final int MUTED = Color.rgb(103, 115, 124);
    private static final int GREEN = Color.rgb(8, 127, 114);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newFixedThreadPool(2);
    private LinearLayout root;
    private LinearLayout result;
    private LinearLayout history;
    private EditText input;
    private TextView status;
    private TextView title;
    private TextView author;
    private TextView transfer;
    private TextView description;
    private TextView qualityLabel;
    private Button analyze;
    private Button download;
    private Button imagesDownload;
    private Button cancel;
    private ProgressBar progress;
    private Spinner quality;
    private ImageView cover;
    private java.util.concurrent.Future<?> pendingRequest;
    private Runnable pendingInput;
    private String lastLink = "";
    private volatile int generation;
    private VideoInfo video;
    private volatile boolean resolving;
    private boolean downloading;
    private boolean receiverRegistered;
    private boolean destroyed;
    private String pendingText = "";
    private Button biliAccount;
    private String activeBiliCookie = "";

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshDownload();
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(246, 248, 250));
        getWindow().setNavigationBarColor(Color.rgb(246, 248, 250));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        buildUi();
        applyIntent(getIntent());
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246, 248, 250));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(12), dp(22), dp(24));
        scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public android.view.WindowInsets onApplyWindowInsets(View view, android.view.WindowInsets insets) {
                root.setPadding(dp(22), dp(12) + insets.getSystemWindowInsetTop(), dp(22), dp(24) + insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        setContentView(scroll);

        LinearLayout header = row();
        ImageView logo = new ImageView(this);
        logo.setImageResource(cn.dousave.app.R.drawable.ic_app);
        header.addView(logo, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView name = text("无水印下载", 22, INK, true);
        LinearLayout.LayoutParams nameLayout = new LinearLayout.LayoutParams(0, -2, 1);
        nameLayout.leftMargin = dp(12);
        header.addView(name, nameLayout);
        root.addView(header);
        addSpace(root, 28);
        root.addView(text("新建下载", 22, INK, true));
        addSpace(root, 14);

        input = new EditText(this);
        input.setTextSize(15);
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setHint("粘贴抖音、小红书、B站链接或分享文案");
        input.setGravity(Gravity.TOP);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setMinLines(3);
        input.setMaxLines(5);
        input.setPadding(dp(14), dp(14), dp(14), dp(14));
        input.setBackground(background(Color.WHITE, Color.rgb(217, 224, 228)));
        root.addView(input, new LinearLayout.LayoutParams(-1, dp(126)));
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                scheduleResolve();
            }

            @Override
            public void afterTextChanged(Editable value) {}
        });
        addSpace(root, 8);
        LinearLayout tools = row();
        Button paste = button("粘贴", false);
        paste.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_edit, 0, 0, 0);
        paste.setCompoundDrawablePadding(dp(8));
        paste.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ClipboardManager clipboard = getSystemService(ClipboardManager.class);
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    input.setText(clip.getItemAt(0).coerceToText(MainActivity.this));
                } else {
                    toast("剪贴板里没有内容");
                }
            }
        });
        LinearLayout.LayoutParams pasteLayout = new LinearLayout.LayoutParams(0, dp(46), 1);
        pasteLayout.rightMargin = dp(5);
        tools.addView(paste, pasteLayout);
        Button clear = button("清空", false);
        clear.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_close_clear_cancel, 0, 0, 0);
        clear.setCompoundDrawablePadding(dp(8));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                input.setText("");
                clearFinishedDownload();
            }
        });
        LinearLayout.LayoutParams clearLayout = new LinearLayout.LayoutParams(0, dp(46), 1);
        clearLayout.leftMargin = dp(5);
        tools.addView(clear, clearLayout);
        root.addView(tools);
        addSpace(root, 10);
        analyze = button("解析链接", true);
        analyze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resolve();
            }
        });
        root.addView(analyze, new LinearLayout.LayoutParams(-1, dp(50)));
        addSpace(root, 12);
        status = text("等待链接", 14, MUTED, false);
        root.addView(status);
        biliAccount = button("B站登录 · 获取高清画质", false);
        biliAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (downloading) {
                    toast("请等待当前下载完成");
                    return;
                }
                String cookie = getSharedPreferences("bili_account", MODE_PRIVATE).getString("cookie", "");
                if (!cookie.isEmpty()) {
                    new AlertDialog.Builder(MainActivity.this).setTitle("B站账号")
                        .setItems(new String[] {"重新登录", "退出登录"}, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int choice) {
                                getSharedPreferences("bili_account", MODE_PRIVATE).edit().clear().apply();
                                android.webkit.CookieManager.getInstance().removeAllCookies(null);
                                updateBiliAccount();
                                scheduleResolve();
                                if (choice == 0) {
                                    startActivity(new Intent(MainActivity.this, BiliLoginActivity.class));
                                }
                            }
                        }).show();
                } else {
                    startActivity(new Intent(MainActivity.this, BiliLoginActivity.class));
                }
            }
        });
        root.addView(biliAccount, new LinearLayout.LayoutParams(-1, dp(46)));
        updateBiliAccount();

        result = column();
        result.setVisibility(View.GONE);
        addSpace(result, 22);
        LinearLayout summary = row();
        summary.setGravity(Gravity.TOP);
        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(background(Color.rgb(225, 233, 235), Color.TRANSPARENT));
        cover.setClipToOutline(true);
        summary.addView(cover, new LinearLayout.LayoutParams(dp(84), dp(112)));
        LinearLayout metadata = column();
        LinearLayout.LayoutParams metaLayout = new LinearLayout.LayoutParams(0, -2, 1);
        metaLayout.leftMargin = dp(14);
        summary.addView(metadata, metaLayout);
        author = text("", 12, GREEN, true);
        metadata.addView(author);
        addSpace(metadata, 7);
        title = text("", 16, INK, true);
        title.setMaxLines(4);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        metadata.addView(title);
        result.addView(summary);
        description = text("", 14, INK, false);
        description.setTextIsSelectable(true);
        result.addView(description);
        addSpace(result, 16);
        qualityLabel = text("画质", 13, MUTED, false);
        result.addView(qualityLabel);
        quality = new Spinner(this);
        result.addView(quality, new LinearLayout.LayoutParams(-1, dp(52)));
        download = button("保存视频到相册", true);
        download.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.stat_sys_download, 0, 0, 0);
        download.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        download.setCompoundDrawablePadding(dp(10));
        download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestedImages = false;
                requestDownload();
            }
        });
        result.addView(download, new LinearLayout.LayoutParams(-1, dp(50)));
        imagesDownload = button("保存图片到相册", true);
        imagesDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestedImages = true;
                requestDownload();
            }
        });
        LinearLayout.LayoutParams imagesLayout = new LinearLayout.LayoutParams(-1, dp(50));
        imagesLayout.topMargin = dp(8);
        result.addView(imagesDownload, imagesLayout);
        addSpace(result, 10);
        LinearLayout textTools = row();
        Button copyText = button("复制文案", false);
        copyText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String value = exportText();
                if (value.trim().isEmpty()) {
                    toast("暂无文字");
                    return;
                }
                getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("文案", value));
                toast("文案已复制");
            }
        });
        LinearLayout.LayoutParams copyLayout = new LinearLayout.LayoutParams(0, dp(44), 1);
        copyLayout.rightMargin = dp(5);
        textTools.addView(copyText, copyLayout);
        Button saveText = button("保存文字 TXT", false);
        saveText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pendingText = exportText();
                if (pendingText.trim().isEmpty()) {
                    toast("暂无文字");
                    return;
                }
                Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                create.addCategory(Intent.CATEGORY_OPENABLE);
                create.setType("text/plain");
                create.putExtra(Intent.EXTRA_TITLE, "文案_" + System.currentTimeMillis() + ".txt");
                startActivityForResult(create, 31);
            }
        });
        LinearLayout.LayoutParams textLayout = new LinearLayout.LayoutParams(0, dp(44), 1);
        textLayout.leftMargin = dp(5);
        textTools.addView(saveText, textLayout);
        result.addView(textTools);
        root.addView(result);

        addSpace(root, 14);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(6)));
        transfer = text("", 13, MUTED, false);
        root.addView(transfer);
        cancel = button("取消下载", false);
        cancel.setVisibility(View.GONE);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startService(new Intent(MainActivity.this, DownloadService.class).setAction("cancel"));
            }
        });
        root.addView(cancel, new LinearLayout.LayoutParams(-1, dp(42)));

        addSpace(root, 22);
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(220, 226, 230));
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        addSpace(root, 18);
        LinearLayout historyHeader = row();
        historyHeader.addView(text("最近下载", 17, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button clearHistory = button("清空记录", false);
        clearHistory.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_delete, 0, 0, 0);
        clearHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getSharedPreferences("downloads", MODE_PRIVATE).edit().putString("history", "[]").apply();
                clearFinishedDownload();
                renderHistory();
                toast("已清空记录，相册文件保留");
            }
        });
        historyHeader.addView(clearHistory, new LinearLayout.LayoutParams(-2, dp(42)));
        root.addView(historyHeader);
        addSpace(root, 10);
        history = column();
        root.addView(history);
        renderHistory();
    }

    private void scheduleResolve() {
        if (pendingInput != null) {
            handler.removeCallbacks(pendingInput);
        }
        generation += 1;
        if (pendingRequest != null) {
            pendingRequest.cancel(true);
        }
        resolving = false;
        video = null;
        lastLink = "";
        result.setVisibility(View.GONE);
        analyze.setEnabled(!downloading);
        analyze.setText("解析链接");
        status.setText("等待链接");
        pendingInput = new Runnable() {
            @Override
            public void run() {
                if (downloading || destroyed) {
                    return;
                }
                try {
                    String link = ShareLink.parseInput(input.getText().toString());
                    if (!link.equals(lastLink)) {
                        resolve();
                    }
                } catch (IllegalArgumentException ignored) {
                    lastLink = "";
                }
            }
        };
        handler.postDelayed(pendingInput, 900);
    }

    private void resolve() {
        if (downloading) {
            toast("请等待当前下载完成");
            return;
        }
        final String link;
        try {
            link = ShareLink.parseInput(input.getText().toString());
        } catch (IllegalArgumentException error) {
            status.setText(error.getMessage());
            return;
        }
        generation += 1;
        int token = generation;
        if (pendingInput != null) {
            handler.removeCallbacks(pendingInput);
        }
        if (pendingRequest != null) {
            pendingRequest.cancel(true);
        }
        lastLink = link;
        resolving = true;
        video = null;
        result.setVisibility(View.GONE);
        analyze.setEnabled(false);
        analyze.setText("正在解析…");
        status.setText("正在读取" + ShareLink.platform(link) + "作品");
        transfer.setText("");
        android.view.inputmethod.InputMethodManager keyboard = getSystemService(android.view.inputmethod.InputMethodManager.class);
        keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
        pendingRequest = worker.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    String cookie = getSharedPreferences("bili_account", MODE_PRIVATE).getString("cookie", "");
                    VideoInfo parsed = new VideoResolver(cookie).resolve(link);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (token != generation || destroyed) {
                                return;
                            }
                            showVideo(parsed, token);
                        }
                    });
                } catch (Exception error) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (token == generation && !destroyed) {
                                resolving = false;
                                analyze.setEnabled(true);
                                analyze.setText("重试解析");
                                status.setText("解析失败：" + error.getMessage());
                            }
                        }
                    });
                }
            }
        });
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (token == generation && resolving && !destroyed) {
                    pendingRequest.cancel(true);
                    generation += 1;
                    resolving = false;
                    analyze.setEnabled(true);
                    analyze.setText("重试解析");
                    status.setText("解析超时，请检查网络后重试");
                }
            }
        }, 60000);
    }
    private void showVideo(VideoInfo parsed, int token) {
        video = parsed;
        resolving = false;
        analyze.setEnabled(true);
        analyze.setText("解析链接");
        title.setText(parsed.title);
        long seconds = parsed.duration / 1000;
        String details = parsed.platform + " · " + parsed.author;
        if (parsed.article) {
            details += " · 文章";
        }
        if (!parsed.variants.isEmpty()) {
            details += " · " + seconds + " 秒";
        }
        if (!parsed.images.isEmpty()) {
            if (parsed.variants.isEmpty()) {
                details += " · " + parsed.images.size() + " 项图集内容";
            }
        }
        author.setText(details);
        description.setText(parsed.description);
        qualityLabel.setVisibility(View.GONE);
        quality.setVisibility(View.GONE);
        download.setVisibility(View.GONE);
        imagesDownload.setVisibility(View.GONE);
        if (!parsed.variants.isEmpty()) {
            qualityLabel.setVisibility(View.VISIBLE);
            quality.setVisibility(View.VISIBLE);
            download.setVisibility(View.VISIBLE);
        }
        if (!parsed.images.isEmpty()) {
            imagesDownload.setVisibility(View.VISIBLE);
            imagesDownload.setText("保存 " + parsed.images.size() + " 张图片到相册");
            if (parsed.hasMotion()) {
                imagesDownload.setText("保存图片和实况到相册");
            }
            if (!parsed.variants.isEmpty()) {
                imagesDownload.setText("保存封面到相册");
            }
        }
        ArrayList<String> labels = new ArrayList<>();
        for (VideoInfo.Variant variant : parsed.variants) {
            labels.add(variant.label());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        quality.setAdapter(adapter);
        result.setVisibility(View.VISIBLE);
        download.setEnabled(!downloading);
        imagesDownload.setEnabled(!downloading);
        status.setText("已获取作品");
        if (parsed.article) {
            status.setText("已获取文章正文");
        }
        if (!parsed.qualityMessage.isEmpty()) {
            status.setText(parsed.qualityMessage);
        }
        if (!parsed.galleryNotice().isEmpty()) {
            status.setText(parsed.galleryNotice());
        }
        cover.setImageDrawable(null);
        loadCover(parsed.cover, token);
    }

    private void loadCover(String url, int token) {
        if (!url.startsWith("https://")) {
            return;
        }
        worker.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    URL address = new URL(url);
                    NetworkPolicy.check(address, true);
                    connection = (HttpURLConnection) address.openConnection();
                    connection.setInstanceFollowRedirects(false);
                    connection.setRequestProperty("User-Agent", HttpClient.AGENT);
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    byte[] data;
                    try (InputStream stream = connection.getInputStream()) {
                        data = read(stream, 5 * 1024 * 1024);
                    }
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (token == generation && !destroyed) {
                                cover.setImageBitmap(image);
                            }
                        }
                    });
                } catch (Exception ignored) {
                    // A missing cover never blocks the video download.
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        });
    }

    private void requestDownload() {
        if (video == null || downloading) {
            return;
        }
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, 11);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            && !getPreferences(MODE_PRIVATE).getBoolean("notificationAsked", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked", true).apply();
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 12);
            return;
        }
        beginDownload();
    }

    private boolean requestedImages;

    private String exportText() {
        if (video == null) {
            return "";
        }
        String value = video.title;
        if (!video.description.isEmpty() && !video.description.equals(video.title)) {
            value += "\n\n" + video.description;
        }
        return value;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 31 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            String textToSave = pendingText;
            Uri target = data.getData();
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    String message = "文字已保存";
                    try (java.io.OutputStream output = getContentResolver().openOutputStream(target, "wt")) {
                        if (output == null) {
                            throw new Exception("无法写入文件");
                        }
                        output.write(textToSave.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } catch (Exception error) {
                        message = "文字保存失败：" + error.getMessage();
                    }
                    String resultMessage = message;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!destroyed) {
                                toast(resultMessage);
                            }
                        }
                    });
                }
            });
        }
    }

    private void beginDownload() {
        if (video == null || downloading) {
            return;
        }
        Intent intent = new Intent(this, DownloadService.class);
        if (requestedImages) {
            if (video.images.isEmpty()) {
                return;
            }
            JSONArray groups = new JSONArray();
            try {
                for (VideoInfo.GalleryEntry entry : video.images) {
                    JSONObject group = new JSONObject();
                    group.put("urls", new JSONArray(entry.urls));
                    group.put("motion", entry.motion);
                    group.put("position", entry.position);
                    groups.put(group);
                }
            } catch (org.json.JSONException error) {
                toast("无法准备图集下载，请重新解析");
                return;
            }
            intent.putExtra("images", groups.toString());
        } else {
            int index = quality.getSelectedItemPosition();
            if (index < 0 || index >= video.variants.size()) {
                return;
            }
            VideoInfo.Variant selected = video.variants.get(index);
            intent.putExtra("urls", selected.urls.toArray(new String[0]));
            intent.putExtra("audioUrls", selected.audioUrls.toArray(new String[0]));
        }
        intent.putExtra("referer", video.referer);
        intent.putExtra("platform", video.platform);
        intent.putExtra("title", video.title);
        intent.putExtra("videoId", video.id);
        downloading = true;
        input.setEnabled(false);
        analyze.setEnabled(false);
        download.setEnabled(false);
        imagesDownload.setEnabled(false);
        cancel.setVisibility(View.VISIBLE);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        transfer.setText("准备下载");
        startForegroundService(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 11) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                requestDownload();
            } else {
                toast("保存到相册需要存储权限");
            }
        } else if (requestCode == 12) {
            beginDownload();
        }
    }

    private void clearFinishedDownload() {
        SharedPreferences preferences = getSharedPreferences("downloads", MODE_PRIVATE);
        if (downloading || preferences.getString("state", "").equals("running")) {
            return;
        }
        preferences.edit().remove("state").remove("message").remove("progress").remove("uri").apply();
        transfer.setText("");
        progress.setVisibility(View.GONE);
        cancel.setVisibility(View.GONE);
    }

    private void refreshDownload() {
        SharedPreferences preferences = getSharedPreferences("downloads", MODE_PRIVATE);
        String state = preferences.getString("state", "");
        if (state.equals("running")) {
            boolean serviceFound = false;
            android.app.ActivityManager manager = getSystemService(android.app.ActivityManager.class);
            for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(100)) {
                if (DownloadService.class.getName().equals(service.service.getClassName())) {
                    serviceFound = true;
                    break;
                }
            }
            if (!serviceFound) {
                state = "error";
                preferences.edit().putString("state", "error").putString("message", "上次下载已中断，请重新解析后下载").apply();
            }
        }
        downloading = state.equals("running");
        analyze.setEnabled(!downloading && !resolving);
        download.setEnabled(!downloading);
        imagesDownload.setEnabled(!downloading);
        cancel.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        if (downloading) {
            input.setEnabled(false);
            cancel.setVisibility(View.VISIBLE);
            progress.setVisibility(View.VISIBLE);
            int value = preferences.getInt("progress", -1);
            progress.setIndeterminate(value < 0);
            if (value >= 0) {
                progress.setProgress(value);
            }
        }
        input.setEnabled(!downloading);
        transfer.setText(preferences.getString("message", ""));
        if (state.equals("complete")) {
            renderHistory();
        }
    }

    private void renderHistory() {
        history.removeAllViews();
        try {
            JSONArray items = new JSONArray(getSharedPreferences("downloads", MODE_PRIVATE).getString("history", "[]"));
            if (items.length() == 0) {
                history.addView(text("暂无下载记录", 14, MUTED, false));
                return;
            }
            for (int index = 0; index < items.length(); index++) {
                JSONObject item = items.getJSONObject(index);
                LinearLayout entry = column();
                entry.setPadding(0, dp(12), 0, dp(12));
                TextView heading = text(item.optString("title"), 14, INK, true);
                heading.setMaxLines(2);
                heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
                entry.addView(heading);
                String time = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(item.optLong("time"));
                entry.addView(text(item.optInt("width") + " × " + item.optInt("height") + " · " + time, 12, MUTED, false));
                entry.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        openMedia(item.optString("uri"), item.optString("mime", "video/mp4"));
                    }
                });
                entry.setContentDescription("播放 " + item.optString("title"));
                history.addView(entry);
            }
        } catch (Exception error) {
            history.addView(text("暂无下载记录", 14, MUTED, false));
        }
    }

    private void openVideo(String value) {
        openMedia(value, "video/mp4");
    }

    private void openMedia(String value, String mime) {
        try {
            Uri uri = Uri.parse(value);
            if ("file".equals(uri.getScheme())) {
                String path = uri.getPath();
                Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                if (mime.startsWith("image/")) {
                    collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                }
                try (android.database.Cursor cursor = getContentResolver().query(collection,
                    new String[] {MediaStore.Video.Media._ID}, MediaStore.Video.Media.DATA + " = ?", new String[] {path}, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        uri = android.content.ContentUris.withAppendedId(collection, cursor.getLong(0));
                    } else {
                    toast("文件正在加入相册，请稍后再试");
                        return;
                    }
                }
            }
            Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception error) {
            toast("无法打开文件，请在相册的无水印下载文件夹查看");
        }
    }

    private void applyIntent(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null) {
                input.setText(shared);
            }
        }
        String open = intent.getStringExtra("openVideo");
        if (open != null) {
            openVideo(open);
            intent.removeExtra("openVideo");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        String currentCookie = getSharedPreferences("bili_account", MODE_PRIVATE).getString("cookie", "");
        if (!currentCookie.equals(activeBiliCookie)) {
            activeBiliCookie = currentCookie;
            if (!downloading && !input.getText().toString().trim().isEmpty()) {
                scheduleResolve();
            }
        }
        updateBiliAccount();
        IntentFilter filter = new IntentFilter(DownloadService.EVENT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
        receiverRegistered = true;
        refreshDownload();
    }

    private void updateBiliAccount() {
        if (biliAccount == null) {
            return;
        }
        String cookie = getSharedPreferences("bili_account", MODE_PRIVATE).getString("cookie", "");
        biliAccount.setText("B站登录 · 获取高清画质");
        if (!cookie.isEmpty()) {
            biliAccount.setText("B站已登录 · 管理账号");
        }
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(receiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        generation += 1;
        handler.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        super.onDestroy();
    }

    private static byte[] read(InputStream stream, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (output.size() + count > limit) {
                throw new Exception("页面响应过大");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(3), 1);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private Button button(String value, boolean primary) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        if (primary) {
            button.setTextColor(Color.WHITE);
            button.setBackground(background(GREEN, Color.TRANSPARENT));
        } else {
            button.setTextColor(MUTED);
            button.setBackground(background(Color.WHITE, Color.rgb(157, 174, 182)));
        }
        return button;
    }

    private GradientDrawable background(int color, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(8));
        shape.setStroke(dp(1), stroke);
        return shape;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private void addSpace(LinearLayout layout, int height) {
        layout.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
