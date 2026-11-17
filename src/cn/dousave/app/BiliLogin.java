package cn.dousave.app;

import java.net.URLEncoder;
import java.util.HashMap;
import org.json.JSONObject;

public final class BiliLogin {
    private final HttpClient http = new HttpClient();

    public static final class Code {
        public String url;
        public String key;
    }

    public static final class Status {
        public int code;
        public String cookie = "";
    }

    public Code generate() throws Exception {
        JSONObject data = response(http.get("https://passport.bilibili.com/x/passport-login/web/qrcode/generate",
            "https://www.bilibili.com/"));
        Code result = new Code();
        result.url = data.getString("url");
        result.key = data.getString("qrcode_key");
        java.net.URI target = java.net.URI.create(result.url);
        if (!"https".equals(target.getScheme()) || target.getHost() == null
            || !ShareLink.belongs(target.getHost(), "bilibili.com") || target.getUserInfo() != null) {
            throw new Exception("B站返回了无效的登录地址");
        }
        return result;
    }

    public Status poll(String key) throws Exception {
        JSONObject data = response(http.get("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key="
            + URLEncoder.encode(key, "UTF-8"), "https://www.bilibili.com/"));
        Status status = new Status();
        status.code = data.getInt("code");
        if (status.code == 0) {
            status.cookie = sessionCookie(http.cookie("https://www.bilibili.com/"));
            if (status.cookie.isEmpty()) {
                throw new Exception("未收到B站登录凭据，请重新扫码");
            }
            verify(status.cookie);
        }
        return status;
    }

    public static void verify(String cookie) throws Exception {
        HashMap<String, String> headers = new HashMap<>();
        String session = sessionCookie(cookie);
        if (session.isEmpty()) {
            throw new Exception("尚未完成B站登录");
        }
        headers.put("Cookie", session);
        HttpClient.Response reply = new HttpClient().request("https://api.bilibili.com/x/web-interface/nav",
            "GET", "", "https://www.bilibili.com/", headers);
        JSONObject data = response(reply);
        if (!data.optBoolean("isLogin")) {
            throw new Exception("B站登录已失效，请重新登录");
        }
    }

    public static String sessionCookie(String raw) {
        if (raw == null || raw.indexOf('\r') >= 0 || raw.indexOf('\n') >= 0) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        boolean hasSession = false;
        for (String part : raw.split(";")) {
            int separator = part.indexOf('=');
            if (separator < 1) {
                continue;
            }
            String name = part.substring(0, separator).trim();
            String value = part.substring(separator + 1).trim();
            if (name.equals("SESSDATA") || name.equals("bili_jct") || name.equals("DedeUserID")) {
                if (value.isEmpty()) {
                    continue;
                }
                if (result.length() > 0) {
                    result.append("; ");
                }
                result.append(name).append('=').append(value);
                if (name.equals("SESSDATA")) {
                    hasSession = true;
                }
            }
        }
        if (!hasSession) {
            return "";
        }
        return result.toString();
    }

    private static JSONObject response(HttpClient.Response reply) throws Exception {
        if (reply.code != 200 || reply.body.isEmpty()) {
            throw new Exception("B站登录接口暂不可用，请稍后重试");
        }
        JSONObject result = new JSONObject(reply.body);
        if (result.optInt("code", -1) != 0 || result.optJSONObject("data") == null) {
            throw new Exception("B站登录状态不可用，请重新登录");
        }
        return result.getJSONObject("data");
    }
}
