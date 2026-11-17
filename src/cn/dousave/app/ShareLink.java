package cn.dousave.app;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShareLink {
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"\\u3000]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIDEO = Pattern.compile("/(?:share/video|share/note|share/article|video|note|article)/(\\d{10,25})(?:[/?.#]|$)");
    private static final Pattern MODAL = Pattern.compile("(?:[?&])modal_id=(\\d{10,25})(?:[&#]|$)");
    private static final Pattern COMMAND = Pattern.compile("[︽︾ŠπωΔ▽ΨΦÑÊÄΘη￠%#$^]{1,2}([A-Za-z0-9]{11,16})[︽︾ŠπωΔ▽ΨΦÑÊÄΘη￠%#$^]{1,2}");

    private ShareLink() {}

    public static String parseInput(String text) {
        try {
            return extract(text);
        } catch (IllegalArgumentException error) {
            if (!commandCode(text).isEmpty()) {
                return text.trim();
            }
            throw new IllegalArgumentException("请粘贴完整分享链接或抖音文章口令（保留两侧符号）");
        }
    }

    public static String commandCode(String text) {
        if (text == null || !text.contains("抖音")) {
            return "";
        }
        if (!text.contains("复制") && !text.contains("阅读文章")) {
            return "";
        }
        Matcher matcher = COMMAND.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    public static String extract(String text) {
        if (text == null) {
            throw new IllegalArgumentException("请先粘贴分享链接");
        }
        String normalized = text.replace("\\:", ":").replace("\\/", "/");
        Matcher matcher = URL.matcher(normalized);
        while (matcher.find()) {
            String candidate = matcher.group();
            candidate = candidate.replaceAll("[，。；！、）】》,;!)}]+$", "");
            try {
                URI uri = URI.create(candidate);
                if (isSupportedHost(uri.getHost()) && uri.getUserInfo() == null && uri.getPort() == -1) {
                    if (candidate.startsWith("http://")) {
                        candidate = "https://" + candidate.substring(7);
                    }
                    return candidate;
                }
            } catch (IllegalArgumentException ignored) {
                // Continue searching when a share message contains an unrelated malformed link.
            }
        }
        throw new IllegalArgumentException("请粘贴抖音、小红书或B站的完整分享链接");
    }

    public static boolean isDouyin(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("douyin.com") || normalized.endsWith(".douyin.com")
            || normalized.equals("iesdouyin.com") || normalized.endsWith(".iesdouyin.com");
    }

    public static String videoId(String url) {
        Matcher path = VIDEO.matcher(url);
        if (path.find()) {
            return path.group(1);
        }
        Matcher query = MODAL.matcher(url);
        if (query.find()) {
            return query.group(1);
        }
        return "";
    }

    public static boolean isSupportedHost(String host) {
        return isDouyin(host) || belongs(host, "xiaohongshu.com") || belongs(host, "xhslink.com") || belongs(host, "xhslink.cn")
            || belongs(host, "bilibili.com") || belongs(host, "b23.tv");
    }

    public static boolean belongs(String host, String domain) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(java.util.Locale.ROOT);
        return normalized.equals(domain) || normalized.endsWith("." + domain);
    }

    public static String platform(String url) {
        if (!commandCode(url).isEmpty()) {
            return "抖音";
        }
        String host = URI.create(url).getHost();
        if (isDouyin(host)) {
            return "抖音";
        }
        if (belongs(host, "xiaohongshu.com") || belongs(host, "xhslink.com") || belongs(host, "xhslink.cn")) {
            return "小红书";
        }
        return "B站";
    }
}
