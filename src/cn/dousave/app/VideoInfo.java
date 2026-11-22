package cn.dousave.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public final class VideoInfo {
    public String id;
    public String title;
    public String author;
    public String cover;
    public String description = "";
    public String sourceLink = "";
    public String qualityMessage = "";
    public boolean article;
    public final ArrayList<GalleryEntry> images = new ArrayList<>();
    public int unavailableMotionCount;
    public long duration;
    public String platform = "抖音";
    public String referer = "https://www.douyin.com/";
    public final List<Variant> variants = new ArrayList<>();

    public static final class GalleryEntry {
        public final ArrayList<String> urls;
        public final boolean motion;
        public final int position;

        public GalleryEntry(ArrayList<String> urls, boolean motion, int position) {
            this.urls = urls;
            this.motion = motion;
            this.position = position;
        }
    }

    public void addStillImage(ArrayList<String> urls) {
        images.add(new GalleryEntry(urls, false, images.size() + 1));
    }

    public boolean hasMotion() {
        for (GalleryEntry entry : images) {
            if (entry.motion) {
                return true;
            }
        }
        return false;
    }

    public String galleryNotice() {
        if (unavailableMotionCount == 0) {
            return "";
        }
        return "平台未提供 " + unavailableMotionCount + " 张实况图的可用动态源";
    }

    public static final class Variant {
        public int width;
        public int height;
        public long bitrate;
        public long bytes;
        public boolean hevc;
        public String name = "";
        public final ArrayList<String> audioUrls = new ArrayList<>();
        public final ArrayList<String> urls = new ArrayList<>();

        public String label() {
            int resolution = Math.min(width, height);
            String name = "平台可用画质";
            if (!this.name.isEmpty()) {
                name = this.name;
            }
            if (resolution > 0) {
                name = resolution + "P · " + width + " × " + height;
            }
            if (resolution > 0) {
                String codec = "H.264";
                if (hevc) {
                    codec = "H.265";
                }
                name += " · " + codec;
            }
            if (bytes > 0) {
                name += String.format(java.util.Locale.CHINA, " · %.1f MB", bytes / 1048576.0);
            }
            return name;
        }
    }

    public static VideoInfo parse(String json, String expectedId) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject detail = root.optJSONObject("aweme_detail");
        if (detail == null || !expectedId.equals(detail.optString("aweme_id"))) {
            throw new IllegalArgumentException("返回的作品与分享链接不一致");
        }
        JSONObject video = detail.optJSONObject("video");
        VideoInfo info = new VideoInfo();
        info.id = expectedId;
        info.title = detail.optString("desc", "抖音视频");
        info.description = info.title;
        info.author = "";
        JSONObject author = detail.optJSONObject("author");
        if (author != null) {
            info.author = author.optString("nickname", "");
        }
        JSONObject article = detail.optJSONObject("article_info");
        if (article != null) {
            parseArticle(info, article);
            return info;
        }
        JSONArray images = detail.optJSONArray("images");
        if (images != null && images.length() > 0) {
            info.cover = "";
            for (int index = 0; index < images.length(); index++) {
                JSONObject image = images.optJSONObject(index);
                if (image == null) {
                    continue;
                }
                ArrayList<String> candidates = new ArrayList<>();
                JSONArray urls = image.optJSONArray("url_list");
                if (urls != null) {
                    for (int urlIndex = 0; urlIndex < urls.length(); urlIndex++) {
                        addUnmarkedUrl(candidates, urls.optString(urlIndex));
                    }
                }
                if (info.cover.isEmpty() && !candidates.isEmpty()) {
                    info.cover = candidates.get(0);
                }
                JSONObject motion = image.optJSONObject("video");
                if (motion != null || image.optBoolean("live_photo") || image.optInt("live_photo") == 1) {
                    ArrayList<String> motionUrls = new ArrayList<>();
                    if (motion != null) {
                        for (Variant variant : videoVariants(motion)) {
                            for (String url : variant.urls) {
                                addUnmarkedUrl(motionUrls, url);
                            }
                        }
                    }
                    if (!motionUrls.isEmpty()) {
                        info.images.add(new GalleryEntry(motionUrls, true, index + 1));
                    } else {
                        info.unavailableMotionCount += 1;
                    }
                } else if (!candidates.isEmpty()) {
                    info.images.add(new GalleryEntry(candidates, false, index + 1));
                }
            }
            return info;
        }
        if (video == null) {
            info.cover = "";
            return info;
        }
        info.duration = video.optLong("duration");
        info.cover = firstUrl(video.optJSONObject("origin_cover"));
        if (info.cover.isEmpty()) {
            info.cover = firstUrl(video.optJSONObject("cover"));
        }
        info.variants.addAll(videoVariants(video));
        if (info.variants.isEmpty()) {
            throw new IllegalArgumentException("官方页面没有提供可用的无水印视频源");
        }
        return info;
    }

    private static List<Variant> videoVariants(JSONObject video) {
        List<Variant> variants = new ArrayList<>();
        if (video.optBoolean("is_drm") || video.optInt("is_drm") != 0) {
            return variants;
        }
        JSONArray rates = video.optJSONArray("bit_rate");
        HashSet<String> known = new HashSet<>();
        if (rates != null) {
            for (int index = 0; index < rates.length(); index++) {
                JSONObject rate = rates.optJSONObject(index);
                if (rate != null && !rate.optBoolean("is_drm") && rate.optInt("is_drm") == 0) {
                    add(variants, rate.optJSONObject("play_addr"), rate, video, known);
                }
            }
        }
        add(variants, video.optJSONObject("play_addr"), new JSONObject(), video, known);
        add(variants, video.optJSONObject("play_addr_h264"), new JSONObject(), video, known);
        Collections.sort(variants, new Comparator<Variant>() {
            @Override
            public int compare(Variant left, Variant right) {
                long leftPixels = (long) left.width * left.height;
                long rightPixels = (long) right.width * right.height;
                int resolution = Long.compare(rightPixels, leftPixels);
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
        return variants;
    }

    private static void parseArticle(VideoInfo info, JSONObject article) throws Exception {
        String articleId = article.optString("article_id");
        if (!articleId.isEmpty() && !articleId.equals(info.id)) {
            throw new IllegalArgumentException("文章编号与分享作品不一致");
        }
        info.article = true;
        info.cover = "";
        String title = article.optString("article_title").trim();
        if (!title.isEmpty()) {
            info.title = title;
        }
        JSONObject content = article.optJSONObject("article_content");
        if (content == null) {
            String encoded = article.optString("article_content");
            if (!encoded.trim().isEmpty()) {
                content = new JSONObject(encoded);
            }
        }
        if (content == null || content.optString("markdown").trim().isEmpty()) {
            throw new IllegalArgumentException("平台未提供完整文章正文，请稍后重试；不会将摘要当作全文保存");
        }
        // Keep the complete Markdown source, including paragraphs and links, for lossless TXT export.
        info.description = content.getString("markdown");
        JSONObject frontend = article.optJSONObject("fe_data");
        if (frontend == null) {
            String encoded = article.optString("fe_data");
            if (!encoded.trim().isEmpty()) {
                try {
                    frontend = new JSONObject(encoded);
                } catch (Exception ignored) {
                    // Embedded Markdown image links remain available if optional frontend data changes.
                }
            }
        }
        JSONArray imageList = null;
        if (frontend != null) {
            imageList = frontend.optJSONArray("image_list");
        }
        if (imageList != null) {
            for (int index = 0; index < imageList.length(); index++) {
                JSONObject image = imageList.optJSONObject(index);
                if (image == null) {
                    continue;
                }
                ArrayList<String> sources = new ArrayList<>();
                addImageUrl(sources, image.optString("origin_image_url"));
                addImageUrl(sources, image.optString("high_image_url"));
                addImageUrl(sources, image.optString("markdown_url"));
                if (!sources.isEmpty()) {
                    info.addStillImage(sources);
                }
            }
        }
        if (info.images.isEmpty()) {
            // Douyin embeds dimensions after the URL: ![](https://... width=... height=...).
            java.util.regex.Matcher images = java.util.regex.Pattern.compile("!\\[[^\\]]*\\]\\((https?://[^\\s)]+)").matcher(info.description);
            HashSet<String> known = new HashSet<>();
            while (images.find()) {
                ArrayList<String> sources = new ArrayList<>();
                addImageUrl(sources, images.group(1));
                if (!sources.isEmpty() && known.add(sources.get(0))) {
                    info.addStillImage(sources);
                }
            }
        }
        if (!info.images.isEmpty()) {
            info.cover = info.images.get(0).urls.get(0);
        }
    }

    public static void addImageUrl(ArrayList<String> urls, String url) {
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

    public static boolean isUnmarkedUrl(String url) {
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        return !lower.contains("/playwm/") && !lower.contains("/playwm?")
            && !lower.contains("watermark=1") && !lower.contains("watermark=true")
            && !lower.contains("watermark=webp") && !lower.contains("watermark=gif")
            && !lower.contains("!h5_1080jpg");
    }

    public static void addUnmarkedUrl(ArrayList<String> urls, String url) {
        if (isUnmarkedUrl(url)) {
            addImageUrl(urls, url);
        }
    }

    private static void add(List<Variant> variants, JSONObject address, JSONObject rate, JSONObject video, HashSet<String> known) {
        if (address == null) {
            return;
        }
        JSONArray urls = address.optJSONArray("url_list");
        if (urls == null) {
            return;
        }
        Variant variant = new Variant();
        variant.width = address.optInt("width", video.optInt("width"));
        variant.height = address.optInt("height", video.optInt("height"));
        variant.bytes = address.optLong("data_size");
        variant.bitrate = rate.optLong("bit_rate");
        variant.hevc = rate.optInt("is_h265") == 1 || rate.optInt("is_bytevc1") == 1;
        for (int index = 0; index < urls.length(); index++) {
            addUnmarkedUrl(variant.urls, urls.optString(index));
        }
        if (!variant.urls.isEmpty() && known.add(variant.urls.get(0))) {
            variants.add(variant);
        }
    }

    private static String firstUrl(JSONObject address) {
        if (address == null) {
            return "";
        }
        JSONArray urls = address.optJSONArray("url_list");
        if (urls == null) {
            return "";
        }
        return urls.optString(0);
    }
}
