package cn.dousave.app;

import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public final class VideoResolver {
    private final HttpClient http = new HttpClient();
    private final String biliCookie;

    public VideoResolver() {
        this("");
    }

    public VideoResolver(String biliCookie) {
        this.biliCookie = BiliLogin.sessionCookie(biliCookie);
    }

    public VideoInfo resolve(String text) throws Exception {
        String link = ShareLink.parseInput(text);
        String command = ShareLink.commandCode(link);
        if (!command.isEmpty()) {
            link = resolveDouyinCommand(command);
        }
        String platform = ShareLink.platform(link);
        VideoInfo result;
        if (platform.equals("抖音")) {
            result = douyin(link);
        } else if (platform.equals("小红书")) {
            result = xiaohongshu(link);
        } else {
            result = bilibili(link);
        }
        result.sourceLink = link;
        if (result.images.isEmpty() && result.unavailableMotionCount == 0 && result.cover != null && !result.cover.isEmpty()) {
            ArrayList<String> coverSources = new ArrayList<>();
            VideoInfo.addImageUrl(coverSources, result.cover);
            if (!coverSources.isEmpty()) {
                result.addStillImage(coverSources);
            }
        }
        return result;
    }

    private String resolveDouyinCommand(String command) throws Exception {
        HttpClient.Response response;
        try {
            // Send only the public token, without the share text or platform cookies.
            response = new HttpClient().get("https://parse-api.sokoko.org/api/douyin/resolve?code=" + encode(command), "");
        } catch (Exception error) {
            throw new Exception("抖音口令转换服务暂不可用，请稍后重试，或粘贴作品的网页链接");
        }
        String id = parseCommandResponse(response.code, response.body);
        return "https://www.douyin.com/article/" + id;
    }

    public static String parseCommandResponse(int status, String body) throws Exception {
        if (status != 200 || body.trim().isEmpty()) {
            throw new Exception("抖音口令转换服务暂不可用，请稍后重试或使用网页链接");
        }
        JSONObject response;
        try {
            response = new JSONObject(body);
        } catch (Exception error) {
            throw new Exception("抖音口令转换服务返回了无效数据");
        }
        JSONObject data = response.optJSONObject("data");
        if (response.optInt("code") != 200 || data == null) {
            throw new Exception("抖音口令未能转换，请从抖音重新复制完整口令，或使用网页链接");
        }
        String id = data.optString("item_id");
        if (!id.matches("[0-9]{10,25}")) {
            throw new Exception("口令没有对应的抖音作品编号，请改用作品网页链接");
        }
        return id;
    }

    private VideoInfo douyin(String link) throws Exception {
        String id = ShareLink.videoId(link);
        if (id.isEmpty()) {
            id = ShareLink.videoId(http.get(link, "https://www.douyin.com/").url);
        }
        if (id.isEmpty()) {
            throw new Exception("未识别到抖音作品编号，请使用完整分享链接或文章口令");
        }
        String registration = "{\"region\":\"cn\",\"aid\":1768,\"needFid\":false,\"service\":\"www.iesdouyin.com\",\"migrate_info\":{\"ticket\":\"\",\"source\":\"node\"},\"cbUrlProtocol\":\"https\",\"union\":true}";
        try {
            http.request("https://ttwid.bytedance.com/ttwid/union/register/", "POST", registration, "https://www.douyin.com/", null);
        } catch (Exception ignored) {
            // Public share-page fallback remains available if guest registration fails.
        }
        String query = "device_platform=webapp&aid=6383&channel=channel_pc_web&aweme_id=" + id
            + "&pc_client_type=1&version_code=170400&version_name=17.4.0&cookie_enabled=true"
            + "&screen_width=1920&screen_height=1080&browser_language=zh-CN&browser_platform=Win32&browser_name=Chrome"
            + "&browser_version=131.0.0.0&browser_online=true&engine_name=Blink&engine_version=131.0.0.0"
            + "&os_name=Windows&os_version=10&platform=PC&msToken=";
        String signature = DouyinSigner.sign(query, HttpClient.AGENT);
        HashMap<String, String> headers = new HashMap<>();
        headers.put("x-tt-argus", "0");
        String guest = http.cookie("https://ttwid.bytedance.com/");
        if (!guest.isEmpty()) {
            headers.put("Cookie", guest);
        }
        String error = "";
        try {
            HttpClient.Response response = http.request("https://www.douyin.com/aweme/v1/web/aweme/detail/?" + query
                + "&a_bogus=" + encode(signature), "GET", "", "https://www.douyin.com/video/" + id, headers);
            if (!response.body.isEmpty()) {
                return VideoInfo.parse(response.body, id);
            }
            error = "HTTP " + response.code + " 空响应";
        } catch (Exception failure) {
            error = failure.getMessage();
        }
        HttpClient.Response share = http.get("https://www.iesdouyin.com/share/video/" + id + "/", "https://www.douyin.com/");
        try {
            JSONObject state = objectAfter(share.body, "window._ROUTER_DATA");
            JSONObject detail = findDouyin(state, id, 0);
            if (detail != null) {
                return VideoInfo.parse(new JSONObject().put("aweme_detail", detail).toString(), id);
            }
        } catch (Exception ignored) {
            // Report a bounded native error instead of opening a CAPTCHA page.
        }
        throw new Exception("抖音暂未返回作品内容（" + error + "）。请稍后重试或切换网络");
    }

    private static JSONObject findDouyin(Object value, String id, int depth) {
        if (depth > 12) {
            return null;
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (id.equals(object.optString("aweme_id")) && (object.optJSONObject("video") != null
                || object.optJSONObject("article_info") != null || object.optJSONArray("images") != null)) {
                return object;
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                JSONObject found = findDouyin(object.opt(keys.next()), id, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                JSONObject found = findDouyin(array.opt(index), id, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private VideoInfo xiaohongshu(String link) throws Exception {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1");
        HttpClient.Response response = http.request(link, "GET", "", "https://www.xiaohongshu.com/", headers);
        URI finalUri = URI.create(response.url);
        if (finalUri.getPath().equals("/login") && finalUri.getRawQuery() != null) {
            for (String parameter : finalUri.getRawQuery().split("&")) {
                if (parameter.startsWith("redirectPath=")) {
                    String target = java.net.URLDecoder.decode(parameter.substring(13), "UTF-8");
                    target = ShareLink.extract(target);
                    if (!ShareLink.belongs(URI.create(target).getHost(), "xiaohongshu.com")) {
                        throw new Exception("小红书分享链接跳转无效");
                    }
                    response = http.request(target, "GET", "", "https://www.xiaohongshu.com/", headers);
                    break;
                }
            }
        }
        if (response.code != 200) {
            throw new Exception("小红书拒绝访问（HTTP " + response.code + "），请使用 App 复制的完整分享链接");
        }
        String id = match(response.url, "/(?:explore|discovery/item|user/profile/[^/]+)/([a-fA-F0-9]{24})");
        if (id.isEmpty()) {
            id = match(response.body, "\"noteId\"\\s*:\\s*\"([a-fA-F0-9]{24})\"");
        }
        JSONObject state;
        try {
            state = objectAfter(response.body, "window.__INITIAL_STATE__");
        } catch (Exception failure) {
            throw new Exception("小红书未返回作品数据，请从 App 重新复制完整链接（需保留 xsec_token）");
        }
        return parseXhs(state, id);
    }

    public static VideoInfo parseXhs(JSONObject state, String id) throws Exception {
        JSONObject noteState = state.optJSONObject("note");
        JSONObject map = null;
        if (noteState != null) {
            map = noteState.optJSONObject("noteDetailMap");
        }
        JSONObject note = null;
        if (map != null) {
            JSONObject entry = map.optJSONObject(id);
            if (entry != null) {
                note = entry.optJSONObject("note");
            }
        }
        if (note == null) {
            JSONObject container = state.optJSONObject("noteData");
            if (container != null && container.optJSONObject("data") != null) {
                note = container.getJSONObject("data").optJSONObject("noteData");
            }
        }
        if (note == null || !id.equals(note.optString("noteId", note.optString("note_id")))) {
            throw new Exception("小红书链接已失效或缺少访问参数，请重新复制完整分享链接");
        }
        JSONObject video = note.optJSONObject("video");
        VideoInfo info = new VideoInfo();
        info.platform = "小红书";
        info.referer = "https://www.xiaohongshu.com/";
        info.id = id;
        info.title = note.optString("title");
        info.description = note.optString("desc");
        if (info.title.isEmpty()) {
            info.title = note.optString("desc", "小红书视频");
        }
        JSONObject user = note.optJSONObject("user");
        info.author = "";
        if (user != null) {
            info.author = user.optString("nickname", user.optString("nickName"));
        }
        info.cover = "";
        JSONArray images = note.optJSONArray("imageList");
        if (images == null) {
            images = note.optJSONArray("images_list");
        }
        if (video == null || !note.optString("type", "video").equals("video")) {
            if (images != null) {
                for (int index = 0; index < images.length(); index++) {
                    JSONObject image = images.optJSONObject(index);
                    if (image == null) {
                        continue;
                    }
                    ArrayList<String> motionUrls = xhsMotionSources(image);
                    boolean livePhoto = image.optBoolean("livePhoto") || image.optJSONObject("livePhoto") != null
                        || image.optBoolean("live_photo") || image.optJSONObject("live_photo") != null || !motionUrls.isEmpty();
                    try {
                        ArrayList<String> candidates = xhsImageSources(image);
                        if (info.cover.isEmpty()) {
                            info.cover = candidates.get(0);
                        }
                        info.addStillImage(candidates, index + 1);
                    } catch (Exception error) {
                        if (!livePhoto) {
                            throw error;
                        }
                    }
                    if (livePhoto) {
                        if (motionUrls.isEmpty()) {
                            info.unavailableMotionCount += 1;
                        } else {
                            info.addMotion(motionUrls, index + 1);
                        }
                    }
                }
            }
            return info;
        }
        if (images != null && images.optJSONObject(0) != null) {
            info.cover = xhsImageSources(images.getJSONObject(0)).get(0);
        }
        JSONObject media = video.optJSONObject("media");
        if (media != null) {
            JSONObject streams = media.optJSONObject("stream");
            if (streams != null) {
                Iterator<String> codecs = streams.keys();
                while (codecs.hasNext()) {
                    String codec = codecs.next();
                    JSONArray tracks = streams.optJSONArray(codec);
                    if (tracks == null) {
                        continue;
                    }
                    for (int index = 0; index < tracks.length(); index++) {
                        JSONObject track = tracks.optJSONObject(index);
                        if (track == null) {
                            continue;
                        }
                        info.duration = Math.max(info.duration, track.optLong("duration"));
                    }
                }
            }
        }
        JSONObject consumer = video.optJSONObject("consumer");
        if (consumer != null) {
            String key = consumer.optString("originVideoKey");
            if (!key.isEmpty() && !key.contains(":") && !key.contains("..")) {
                VideoInfo.Variant original = new VideoInfo.Variant();
                original.name = "原始视频源";
                addUrl(original.urls, "https://sns-video-bd.xhscdn.com/" + key);
                addUrl(original.urls, "https://sns-video-qc.xhscdn.com/" + key);
                info.variants.add(original);
            } else {
                throw new Exception("小红书未提供原片地址，暂不使用可能带角标的播放流");
            }
        } else {
            throw new Exception("小红书未提供原片地址，暂不使用可能带角标的播放流");
        }
        if (info.variants.isEmpty()) {
            throw new Exception("小红书没有返回可下载的视频源");
        }
        return info;
    }

    public static ArrayList<String> xhsImageSources(JSONObject image) throws Exception {
        ArrayList<String> sources = new ArrayList<>();
        String key = image.optString("fileId", image.optString("file_id"));
        if (key.isEmpty()) {
            String address = image.optString("urlDefault", image.optString("url_default",
                image.optString("urlPre", image.optString("url_pre", image.optString("url")))));
            JSONArray alternatives = image.optJSONArray("infoList");
            if (alternatives == null) {
                alternatives = image.optJSONArray("info_list");
            }
            if (address.isEmpty() && alternatives != null) {
                for (int index = 0; index < alternatives.length(); index++) {
                    JSONObject alternative = alternatives.optJSONObject(index);
                    if (alternative == null) {
                        continue;
                    }
                    address = alternative.optString("url");
                    if (!address.isEmpty()) {
                        break;
                    }
                }
            }
            if (!address.isEmpty()) {
                URI uri = URI.create(address);
                String host = uri.getHost();
                if (host != null && ShareLink.belongs(host, "xhscdn.com")) {
                    String path = uri.getPath();
                    int transform = path.indexOf('!');
                    if (transform >= 0) {
                        path = path.substring(0, transform);
                    }
                    // Webpic paths prepend an expiring date and signature before the image key.
                    path = path.replaceFirst("^/[0-9]{12}/[a-fA-F0-9]{32}/", "/");
                    if (path.startsWith("/")) {
                        key = path.substring(1);
                    }
                }
            }
        }
        if (!key.matches("[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*")) {
            throw new Exception("小红书未提供可识别的原图地址，请重新复制完整分享链接");
        }
        VideoInfo.addImageUrl(sources, "https://sns-img-qc.xhscdn.com/" + key);
        VideoInfo.addImageUrl(sources, "https://sns-img-bd.xhscdn.com/" + key);
        VideoInfo.addImageUrl(sources, "https://ci.xiaohongshu.com/" + key);
        return sources;
    }

    public static ArrayList<String> xhsMotionSources(JSONObject image) {
        return collectXhsMotionSources(image);
    }

    private static ArrayList<String> collectXhsMotionSources(JSONObject image) {
        ArrayList<String> urls = new ArrayList<>();
        JSONObject streams = image.optJSONObject("stream");
        JSONObject livePhoto = image.optJSONObject("livePhoto");
        if (livePhoto == null) {
            livePhoto = image.optJSONObject("live_photo");
        }
        if (livePhoto != null) {
            JSONObject media = livePhoto.optJSONObject("media");
            if (media != null && media.optJSONObject("stream") != null) {
                streams = media.optJSONObject("stream");
            } else if (livePhoto.optJSONObject("stream") != null) {
                streams = livePhoto.optJSONObject("stream");
            }
        }
        if (streams == null) {
            return urls;
        }
        for (String codec : new String[] {"h264", "h265", "av1"}) {
            JSONArray tracks = streams.optJSONArray(codec);
            if (tracks == null) {
                continue;
            }
            for (int index = 0; index < tracks.length(); index++) {
                JSONObject track = tracks.optJSONObject(index);
                if (track == null || track.optBoolean("isDrm") || track.optInt("isDrm") != 0) {
                    continue;
                }
                JSONArray backups = track.optJSONArray("backupUrls");
                if (backups == null) {
                    backups = track.optJSONArray("backup_urls");
                }
                VideoInfo.addUnmarkedUrl(urls, track.optString("masterUrl", track.optString("master_url")));
                if (backups != null) {
                    for (int position = 0; position < backups.length(); position++) {
                        VideoInfo.addUnmarkedUrl(urls, backups.optString(position));
                    }
                }
            }
        }
        return urls;
    }

    private VideoInfo bilibili(String link) throws Exception {
        String resolved = link;
        if (ShareLink.belongs(URI.create(link).getHost(), "b23.tv")) {
            resolved = http.get(link, "https://www.bilibili.com/").url;
        }
        String bvid = match(resolved, "(?i)(BV[0-9a-z]{10})");
        String aid = match(resolved, "(?i)/video/av([0-9]+)");
        String query;
        if (!bvid.isEmpty()) {
            query = "bvid=" + bvid;
        } else if (!aid.isEmpty()) {
            query = "aid=" + aid;
        } else {
            throw new Exception("请使用B站普通视频的 BV、av 或 b23.tv 链接");
        }
        HashMap<String, String> headers = new HashMap<>();
        if (!biliCookie.isEmpty()) {
            headers.put("Cookie", biliCookie);
            JSONObject account = json(http.request("https://api.bilibili.com/x/web-interface/nav", "GET", "",
                "https://www.bilibili.com/", headers));
            if (account.optJSONObject("data") == null || !account.getJSONObject("data").optBoolean("isLogin")) {
                throw new Exception("B站登录已失效，请点击B站登录后重新解析");
            }
        }
        JSONObject metadata = json(http.request("https://api.bilibili.com/x/web-interface/view?" + query,
            "GET", "", "https://www.bilibili.com/", headers));
        if (metadata.optInt("code", -1) != 0) {
            throw new Exception("B站作品不可用：" + metadata.optString("message"));
        }
        JSONObject data = metadata.getJSONObject("data");
        JSONArray pages = data.getJSONArray("pages");
        int part = 1;
        String pageNumber = match(resolved, "[?&]p=(\\d+)");
        if (!pageNumber.isEmpty()) {
            part = Integer.parseInt(pageNumber);
        }
        if (part < 1 || part > pages.length()) {
            throw new Exception("B站分P编号超出范围");
        }
        JSONObject page = pages.getJSONObject(part - 1);
        String playQuery = "bvid=" + data.getString("bvid") + "&cid=" + page.getLong("cid")
            + "&qn=127&fnval=4048&fnver=0&fourk=1";
        JSONObject playback = json(http.request("https://api.bilibili.com/x/player/playurl?" + playQuery,
            "GET", "", "https://www.bilibili.com/", headers));
        if (playback.optInt("code", -1) != 0) {
            throw new Exception("B站播放接口返回：" + playback.optString("message"));
        }
        VideoInfo info = parseBili(data, page, playback.getJSONObject("data"));
        if (biliCookie.isEmpty()) {
            info.qualityMessage = "当前为B站访客画质；登录后重新解析可获取账号可用的高清画质";
        } else {
            info.qualityMessage = "已获取B站登录账号可用画质";
        }
        return info;
    }

    public static VideoInfo parseBili(JSONObject metadata, JSONObject page, JSONObject play) throws Exception {
        VideoInfo info = new VideoInfo();
        info.platform = "B站";
        info.referer = "https://www.bilibili.com/";
        info.id = metadata.getString("bvid") + "_" + page.optInt("page", 1);
        info.title = metadata.optString("title", "B站视频");
        info.description = metadata.optString("desc");
        if (metadata.optInt("videos") > 1) {
            info.title += " · " + page.optString("part");
        }
        info.cover = metadata.optString("pic");
        info.author = "";
        if (metadata.optJSONObject("owner") != null) {
            info.author = metadata.optJSONObject("owner").optString("name");
        }
        info.duration = play.optLong("timelength", page.optLong("duration") * 1000);
        JSONObject dash = play.optJSONObject("dash");
        if (dash != null) {
            JSONArray videos = dash.optJSONArray("video");
            JSONArray audio = dash.optJSONArray("audio");
            JSONObject bestAudio = null;
            if (audio != null) {
                for (int index = 0; index < audio.length(); index++) {
                    JSONObject track = audio.getJSONObject(index);
                    if (track.optString("mimeType", track.optString("mime_type")).contains("mp4")) {
                        if (bestAudio == null || track.optLong("bandwidth") > bestAudio.optLong("bandwidth")) {
                            bestAudio = track;
                        }
                    }
                }
            }
            if (videos != null) {
                for (int index = 0; index < videos.length(); index++) {
                    JSONObject track = videos.getJSONObject(index);
                    String codecs = track.optString("codecs");
                    boolean hevc = codecs.startsWith("hev1") || codecs.startsWith("hvc1");
                    if (!codecs.startsWith("avc") && !hevc) {
                        continue;
                    }
                    VideoInfo.Variant variant = new VideoInfo.Variant();
                    variant.hevc = hevc;
                    variant.width = track.optInt("width");
                    variant.height = track.optInt("height");
                    if (variant.width <= 0 || variant.height <= 0) {
                        continue;
                    }
                    variant.bitrate = track.optLong("bandwidth");
                    addTrackUrls(variant.urls, track);
                    if (bestAudio != null) {
                        addTrackUrls(variant.audioUrls, bestAudio);
                    }
                    if (!variant.urls.isEmpty() && !variant.audioUrls.isEmpty()) {
                        info.variants.add(variant);
                    }
                }
            }
        }
        JSONArray durl = play.optJSONArray("durl");
        if (info.variants.isEmpty() && durl != null && durl.length() == 1) {
            JSONObject track = durl.getJSONObject(0);
            VideoInfo.Variant variant = new VideoInfo.Variant();
            variant.name = play.optInt("quality") + " 档 · 完整视频";
            variant.bytes = track.optLong("size");
            addUrl(variant.urls, track.optString("url"));
            addUrls(variant.urls, track.optJSONArray("backup_url"));
            if (!variant.urls.isEmpty()) {
                info.variants.add(variant);
            }
        }
        if (info.variants.isEmpty()) {
            throw new Exception("B站没有返回可保存的普通视频流，付费或受限作品暂不支持");
        }
        Collections.sort(info.variants, new Comparator<VideoInfo.Variant>() {
            @Override
            public int compare(VideoInfo.Variant left, VideoInfo.Variant right) {
                int resolution = Long.compare((long) right.width * right.height, (long) left.width * left.height);
                if (resolution != 0) {
                    return resolution;
                }
                if (left.hevc != right.hevc) {
                    if (left.hevc) {
                        return 1;
                    }
                    return -1;
                }
                return Long.compare(right.bitrate, left.bitrate);
            }
        });
        return info;
    }

    private static void addTrackUrls(ArrayList<String> target, JSONObject track) {
        addUrl(target, track.optString("baseUrl", track.optString("base_url")));
        addUrls(target, track.optJSONArray("backupUrl"));
        addUrls(target, track.optJSONArray("backup_url"));
    }

    private static void addUrls(ArrayList<String> urls, JSONArray source) {
        if (source != null) {
            for (int index = 0; index < source.length(); index++) {
                addUrl(urls, source.optString(index));
            }
        }
    }

    private static void addUrl(ArrayList<String> urls, String url) {
        if (url.startsWith("http://")) {
            url = "https://" + url.substring(7);
        }
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        if (url.startsWith("https://") && !urls.contains(url)) {
            urls.add(url);
        }
    }

    private static JSONObject json(HttpClient.Response response) throws Exception {
        if (response.code != 200 || response.body.isEmpty()) {
            throw new Exception("平台返回 HTTP " + response.code + "，请稍后重试");
        }
        return new JSONObject(response.body);
    }

    public static JSONObject objectAfter(String text, String marker) throws Exception {
        int markerPosition = text.indexOf(marker);
        if (markerPosition < 0) {
            throw new Exception("页面未提供作品数据");
        }
        int start = text.indexOf('{', markerPosition + marker.length());
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        StringBuilder json = new StringBuilder();
        for (int index = start; index >= 0 && index < text.length(); index++) {
            char character = text.charAt(index);
            if (!quoted && text.startsWith("undefined", index)) {
                json.append("null");
                index += 8;
                continue;
            }
            json.append(character);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    quoted = false;
                }
            } else if (character == '"') {
                quoted = true;
            } else if (character == '{') {
                depth += 1;
            } else if (character == '}') {
                depth -= 1;
                if (depth == 0) {
                    return new JSONObject(json.toString());
                }
            }
        }
        throw new Exception("作品数据不完整");
    }

    private static String match(String value, String expression) {
        Matcher matcher = Pattern.compile(expression).matcher(value);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }
}
