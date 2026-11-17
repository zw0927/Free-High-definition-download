package cn.dousave.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class BiliLoginActivity extends Activity {
    private WebView browser;
    private TextView status;
    private volatile boolean verifying;
    private volatile boolean closed;
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.WHITE);
        status = new TextView(this);
        status.setText("登录B站后，点击完成登录");
        status.setTextSize(16);
        status.setPadding(24, 32, 24, 16);
        layout.addView(status);
        Button done = new Button(this);
        done.setText("完成登录");
        done.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View view) {
                captureLogin();
            }
        });
        layout.addView(done);
        browser = new WebView(this);
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setAllowFileAccess(false);
        browser.getSettings().setAllowContentAccess(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false);
        browser.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                android.net.Uri uri = request.getUrl();
                String host = uri.getHost();
                return !"https".equals(uri.getScheme()) || host == null || !ShareLink.belongs(host, "bilibili.com");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                String raw = CookieManager.getInstance().getCookie("https://www.bilibili.com/");
                if (!BiliLogin.sessionCookie(raw).isEmpty()) {
                    captureLogin();
                }
            }
        });
        layout.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        browser.loadUrl("https://passport.bilibili.com/login");
    }

    private void captureLogin() {
        if (verifying || closed) {
            return;
        }
        String cookie = BiliLogin.sessionCookie(CookieManager.getInstance().getCookie("https://www.bilibili.com/"));
        if (cookie.isEmpty()) {
            status.setText("请先在下方B站页面完成登录");
            return;
        }
        verifying = true;
        status.setText("正在确认登录状态…");
        worker.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    BiliLogin.verify(cookie);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (closed) {
                                return;
                            }
                            getSharedPreferences("bili_account", MODE_PRIVATE).edit().putString("cookie", cookie).apply();
                            CookieManager.getInstance().flush();
                            android.widget.Toast.makeText(BiliLoginActivity.this, "B站登录成功，请重新解析视频", android.widget.Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });
                } catch (Exception error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!closed) {
                                verifying = false;
                                status.setText(error.getMessage());
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        closed = true;
        worker.shutdownNow();
        browser.destroy();
        super.onDestroy();
    }
}
