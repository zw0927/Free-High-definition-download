package cn.dousave.app;

import java.net.InetAddress;
import java.net.URL;

public final class NetworkPolicy {
    private NetworkPolicy() {}

    public static void check(URL url, boolean media) throws Exception {
        if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getUserInfo() != null
            || url.getPort() != -1 && url.getPort() != 443) {
            throw new Exception("服务器返回了不安全的地址");
        }
        String host = url.getHost();
        boolean allowed = ShareLink.isSupportedHost(host) || ShareLink.belongs(host, "bytedance.com");
        if (!media && host.equalsIgnoreCase("parse-api.sokoko.org")
            && url.getPath().equals("/api/douyin/resolve")) {
            allowed = true;
        }
        if (media) {
            allowed = allowed || ShareLink.belongs(host, "douyinvod.com") || ShareLink.belongs(host, "douyinstatic.com")
                || ShareLink.belongs(host, "bytecdn.cn") || ShareLink.belongs(host, "ibytedtos.com")
                || ShareLink.belongs(host, "byteimg.com") || ShareLink.belongs(host, "douyinpic.com")
                || ShareLink.belongs(host, "xhscdn.com") || ShareLink.belongs(host, "xiaohongshu.com")
                || ShareLink.belongs(host, "hdslb.com") || ShareLink.belongs(host, "bilivideo.com")
                || ShareLink.belongs(host, "bilivideo.cn") || ShareLink.belongs(host, "akamaized.net")
                || ShareLink.belongs(host, "bytegoofy.com") || ShareLink.belongs(host, "ixigua.com")
                || ShareLink.belongs(host, "snssdk.com") || ShareLink.belongs(host, "pstatp.com");
        }
        if (!allowed) {
            throw new Exception("平台返回了暂不支持的视频线路：" + host);
        }
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()
                || address.isLinkLocalAddress() || address.isMulticastAddress()) {
                throw new Exception("下载地址指向本地网络，已停止访问");
            }
            byte[] bytes = address.getAddress();
            if (bytes.length == 16 && (bytes[0] & 254) == 252) {
                throw new Exception("下载地址指向本地网络，已停止访问");
            }
        }
    }
}
