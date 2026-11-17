package cn.dousave.app;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class HttpClient {
    public static final String AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private final CookieManager cookies = new CookieManager(null, new CookiePolicy() {
        @Override
        public boolean shouldAccept(URI uri, HttpCookie cookie) {
            if (CookiePolicy.ACCEPT_ORIGINAL_SERVER.shouldAccept(uri, cookie)) {
                return true;
            }
            // Guest registration issues a platform cookie from the separate registration host.
            if ("ttwid.bytedance.com".equals(uri.getHost()) && "ttwid".equals(cookie.getName())) {
                return true;
            }
            return false;
        }
    });

    public static final class Response {
        public String url;
        public String body;
        public int code;
    }

    public Response request(String url, String method, String body, String referer, Map<String, String> headers) throws Exception {
        URL next = new URL(url);
        for (int redirect = 0; redirect < 8; redirect++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            NetworkPolicy.check(next, false);
            HttpURLConnection connection = (HttpURLConnection) next.openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(18000);
                connection.setRequestProperty("User-Agent", AGENT);
                connection.setRequestProperty("Accept-Encoding", "identity");
                if (!referer.isEmpty()) {
                    connection.setRequestProperty("Referer", referer);
                }
                Map<String, List<String>> stored = cookies.get(next.toURI(), java.util.Collections.emptyMap());
                for (Map.Entry<String, List<String>> header : stored.entrySet()) {
                    connection.setRequestProperty(header.getKey(), String.join("; ", header.getValue()));
                }
                if (headers != null) {
                    for (Map.Entry<String, String> header : headers.entrySet()) {
                        connection.setRequestProperty(header.getKey(), header.getValue());
                    }
                }
                connection.setRequestMethod(method);
                if (method.equals("POST")) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json");
                    try (java.io.OutputStream output = connection.getOutputStream()) {
                        output.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                }
                int code = connection.getResponseCode();
                cookies.put(next.toURI(), connection.getHeaderFields());
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) {
                        throw new Exception("分享链接跳转无效");
                    }
                    next = new URL(next, location);
                    if ("http".equalsIgnoreCase(next.getProtocol()) && ShareLink.isSupportedHost(next.getHost())) {
                        next = new URL("https" + next.toString().substring(4));
                    }
                    if (!ShareLink.isSupportedHost(next.getHost())) {
                        throw new Exception("分享链接跳转到了不支持的平台");
                    }
                    method = "GET";
                    continue;
                }
                Response response = new Response();
                response.code = code;
                response.url = next.toString();
                InputStream stream = connection.getErrorStream();
                if (code < 400) {
                    stream = connection.getInputStream();
                }
                response.body = "";
                if (stream != null) {
                    try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[16384];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (Thread.currentThread().isInterrupted()) {
                                throw new InterruptedException();
                            }
                            if (output.size() + count > 10 * 1024 * 1024) {
                                throw new Exception("服务器响应过大");
                            }
                            output.write(buffer, 0, count);
                        }
                        response.body = new String(output.toByteArray(), StandardCharsets.UTF_8);
                    }
                }
                return response;
            } finally {
                connection.disconnect();
            }
        }
        throw new Exception("分享链接跳转次数过多");
    }

    public Response get(String url, String referer) throws Exception {
        return request(url, "GET", "", referer, null);
    }

    public String cookie(String domain) {
        StringBuilder result = new StringBuilder();
        for (HttpCookie cookie : cookies.getCookieStore().get(URI.create(domain))) {
            if (result.length() > 0) {
                result.append("; ");
            }
            result.append(cookie.getName()).append('=').append(cookie.getValue());
        }
        return result.toString();
    }
}
