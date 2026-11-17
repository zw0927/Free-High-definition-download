package cn.dousave.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.json.JSONObject;

public final class CoreTest {
    public static void main(String[] arguments) throws Exception {
        String sample = "3.30 复制打开抖音，看看【数据与科学的作品】 https\\://v.douyin.com/RQtMd_5ce00/ 02/23 nDh:/ l@C.uf :7pm";
        check(ShareLink.extract(sample).equals("https://v.douyin.com/RQtMd_5ce00/"), "Full escaped share text");
        check(ShareLink.extract("链接 https://v.douyin.com/test/。）").equals("https://v.douyin.com/test/"), "Trailing punctuation");
        check(ShareLink.videoId("https://www.iesdouyin.com/share/video/7708260643380177914/?region=CN").equals("7708260643380177914"), "Share page ID");
        check(ShareLink.videoId("https://www.douyin.com/?modal_id=7708260643380177914").equals("7708260643380177914"), "Modal ID");
        check(!ShareLink.isDouyin("douyin.com.attacker.example"), "Reject lookalike host");
        boolean rejected = false;
        try {
            ShareLink.extract("https://douyin.com.attacker.example/video/7708260643380177914");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "Reject unrelated URL");

        String fixture = new String(Files.readAllBytes(Paths.get(arguments[0])), StandardCharsets.UTF_8);
        VideoInfo video = VideoInfo.parse(fixture, "7708260643380177914");
        check(video.author.equals("数据与科学"), "Correct author");
        check(video.title.contains("执行速度"), "Correct title");
        check(video.variants.get(0).width == 1080 && video.variants.get(0).height == 1920, "Highest resolution first");
        check(!video.variants.get(0).hevc, "Prefer compatible H.264 at equal resolution");
        check(video.variants.size() >= 3, "Multiple quality options");
        for (VideoInfo.Variant variant : video.variants) {
            for (String url : variant.urls) {
                check(!url.contains("/playwm/") && url.startsWith("https://"), "No watermarked or insecure source");
            }
        }
        rejected = false;
        try {
            VideoInfo.parse(fixture, "6961737553342991651");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "Reject unrelated recommended video");
        JSONObject modified = new JSONObject(fixture);
        JSONObject source = modified.getJSONObject("aweme_detail").getJSONObject("video");
        source.remove("bit_rate");
        source.remove("play_addr_h264");
        source.getJSONObject("play_addr").put("url_list", new org.json.JSONArray().put("https://www.douyin.com/aweme/v1/playwm/?video_id=watermark"));
        rejected = false;
        try {
            VideoInfo.parse(modified.toString(), "7708260643380177914");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "Never fall back to watermarked source");
        check(ShareLink.platform(ShareLink.extract("笔记 https://xhslink.com/a/abc")).equals("小红书"), "XHS short link");
        check(ShareLink.platform(ShareLink.extract("分享 https://b23.tv/abc")).equals("B站"), "Bili short link");
        String xhsLink = "https://www.xiaohongshu.com/explore/0123456789abcdef01234567?xsec_token=abc%2Fxyz%3D&xsec_source=app_share";
        check(ShareLink.extract(xhsLink).equals(xhsLink), "Preserve XHS access token");
        check(!ShareLink.isSupportedHost("bilibili.com.attacker.example"), "Reject Bili lookalike");
        rejected = false;
        try {
            ShareLink.extract("https://attacker@www.bilibili.com/video/BV1xx411c7mD");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "Reject embedded credentials");
        StringBuilder hash = new StringBuilder();
        for (byte value : DouyinSigner.sm3("abc".getBytes(StandardCharsets.UTF_8))) {
            hash.append(String.format("%02x", value & 255));
        }
        check(hash.toString().equals("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0"), "SM3 standard vector");
        JSONObject state = VideoResolver.objectAfter("<script>window.__INITIAL_STATE__={\"escaped\":\"} undefined\",\"value\":undefined,\"nested\":{\"a\":1}};</script>", "window.__INITIAL_STATE__");
        check(state.isNull("value") && state.getString("escaped").equals("} undefined"), "Balanced page JSON and undefined handling");
        JSONObject metadata = new JSONObject("{\"bvid\":\"BV1xx411c7mD\",\"title\":\"Test\",\"videos\":2,\"owner\":{\"name\":\"Owner\"}}");
        JSONObject page = new JSONObject("{\"page\":2,\"part\":\"Second\",\"duration\":5}");
        JSONObject play = new JSONObject("{\"dash\":{\"video\":[{\"codecs\":\"avc1.640028\",\"width\":1920,\"height\":1080,\"base_url\":\"https://example.invalid/video.m4s\",\"backup_url\":[\"https://example.invalid/backup.m4s\"]},{\"codecs\":\"hev1\",\"baseUrl\":\"https://example.invalid/hevc.m4s\"}],\"audio\":[{\"mime_type\":\"audio/mp4\",\"bandwidth\":128000,\"base_url\":\"https://example.invalid/audio.m4s\"}]}}");
        VideoInfo bili = VideoResolver.parseBili(metadata, page, play);
        check(bili.id.endsWith("_2") && bili.title.contains("Second"), "Correct Bili part");
        check(bili.variants.size() == 1 && bili.variants.get(0).audioUrls.size() == 1, "Bili video paired with audio");
        check(bili.variants.get(0).urls.size() == 2, "Bili backup CDN");
        play.getJSONObject("dash").getJSONArray("video").put(new JSONObject()
            .put("codecs", "hvc1.1.6.L120.90").put("width", 3840).put("height", 2160)
            .put("baseUrl", "https://example.invalid/uhd-hevc.m4s"));
        VideoInfo highQualityBili = VideoResolver.parseBili(metadata, page, play);
        check(highQualityBili.variants.get(0).width == 3840 && highQualityBili.variants.get(0).hevc,
            "Bili keeps highest HEVC quality instead of silently discarding it");
        play.getJSONObject("dash").remove("audio");
        rejected = false;
        try {
            VideoResolver.parseBili(metadata, page, play);
        } catch (Exception expected) {
            rejected = true;
        }
        check(rejected, "Do not save a silent DASH video when audio is missing");
        String noteId = "0123456789abcdef01234567";
        JSONObject note = new JSONObject("{\"noteId\":\"0123456789abcdef01234567\",\"title\":\"XHS test\",\"user\":{\"nickname\":\"Creator\"},\"video\":{\"consumer\":{\"originVideoKey\":\"stream/test.mp4\"},\"media\":{\"stream\":{\"h264\":[{\"width\":1080,\"height\":1920,\"duration\":5000,\"masterUrl\":\"https://example.invalid/marked.mp4\"}]}}}}");
        JSONObject xhsState = new JSONObject().put("note", new JSONObject().put("noteDetailMap", new JSONObject().put(noteId, new JSONObject().put("note", note))));
        VideoInfo xhs = VideoResolver.parseXhs(xhsState, noteId);
        check(xhs.platform.equals("小红书") && xhs.author.equals("Creator"), "XHS metadata");
        check(xhs.variants.size() == 1 && xhs.variants.get(0).urls.get(0).contains("xhscdn.com/stream/test.mp4"), "XHS original source only");
        note.getJSONObject("video").remove("consumer");
        rejected = false;
        try {
            VideoResolver.parseXhs(xhsState, noteId);
        } catch (Exception expected) {
            rejected = true;
        }
        check(rejected, "Reject XHS playback-only source");
        check(ShareLink.platform(ShareLink.extract("新闻联播云电脑关机 https\\://xhslink.cn/o/67YOrxwbrRh")).equals("小红书"), "XHS cn share domain");
        JSONObject mobileState = new JSONObject().put("noteData", new JSONObject().put("data", new JSONObject().put("noteData", note)));
        note.getJSONObject("video").put("consumer", new JSONObject().put("originVideoKey", "pre_post/test"));
        VideoInfo mobile = VideoResolver.parseXhs(mobileState, noteId);
        check(mobile.id.equals(noteId) && mobile.variants.size() == 1, "XHS mobile INITIAL_STATE structure");
        note.put("type", "normal");
        note.put("desc", "Photo post text");
        note.put("imageList", new org.json.JSONArray().put(new JSONObject()
            .put("fileId", "spectrum/1040g34o327lg12566g505pj2pr23cul1hs42fj8")
            .put("url", "http://sns-webpic-qc.xhscdn.com/202611171623/e7c65a4f749135a81c3af77d769051ba/spectrum/1040g34o327lg12566g505pj2pr23cul1hs42fj8!h5_1080jpg")));
        VideoInfo photoNote = VideoResolver.parseXhs(mobileState, noteId);
        check(photoNote.variants.isEmpty() && photoNote.images.size() == 1 && photoNote.images.get(0).size() == 3, "XHS photo post with original CDN backups");
        check(photoNote.images.get(0).get(0).startsWith("https://") && photoNote.description.equals("Photo post text"), "Photo HTTPS and body text");
        check(photoNote.images.get(0).get(0).equals("https://sns-img-qc.xhscdn.com/spectrum/1040g34o327lg12566g505pj2pr23cul1hs42fj8"), "XHS uses full-resolution original, not watermarked H5 image");
        JSONObject imageWithoutId = new JSONObject(note.getJSONArray("imageList").getJSONObject(0).toString());
        imageWithoutId.remove("fileId");
        check(VideoResolver.xhsImageSources(imageWithoutId).equals(photoNote.images.get(0)), "XHS original key derived from signed webpic path when fileId is missing");
        for (String address : photoNote.images.get(0)) {
            check(!address.contains("!") && !address.contains("webpic"), "Never fall back to XHS watermarked transformations");
        }
        rejected = false;
        try {
            VideoResolver.xhsImageSources(new JSONObject().put("fileId", "../private/file"));
        } catch (Exception expected) {
            rejected = true;
        }
        check(rejected, "Reject invalid XHS original key");
        check(BiliLogin.sessionCookie("SESSDATA=test%2Cvalue; bili_jct=csrf; DedeUserID=123; unrelated=secret")
            .equals("SESSDATA=test%2Cvalue; bili_jct=csrf; DedeUserID=123"), "Bili credentials retain only required platform cookies");
        check(BiliLogin.sessionCookie("bili_jct=csrf").isEmpty(), "A CSRF token alone is not a login session");
        check(BiliLogin.sessionCookie("SESSDATA=value\r\nInjected: bad").isEmpty(), "Reject cookie header injection");
        JSONObject photoDetail = new JSONObject(fixture).getJSONObject("aweme_detail");
        photoDetail.remove("video");
        photoDetail.put("images", new org.json.JSONArray().put(new JSONObject().put("url_list",
            new org.json.JSONArray().put("http://example.invalid/douyin.webp"))));
        VideoInfo douyinPhotos = VideoInfo.parse(new JSONObject().put("aweme_detail", photoDetail).toString(), "7708260643380177914");
        check(douyinPhotos.images.size() == 1 && douyinPhotos.variants.isEmpty(), "Douyin photo post without video");
        photoDetail.remove("images");
        VideoInfo textOnly = VideoInfo.parse(new JSONObject().put("aweme_detail", photoDetail).toString(), "7708260643380177914");
        check(textOnly.images.isEmpty() && textOnly.variants.isEmpty() && !textOnly.description.isEmpty(), "Text-only metadata");
        check(ShareLink.videoId("https://www.douyin.com/note/7708260643380177914").equals("7708260643380177914"), "Douyin note URL");
        String command = "8:/-a :9pm 02/22 U@Y.Mj 【“何不食肉糜”：为什么总有人站着说话不腰疼？】长按复制打开抖音，即可阅读文章 ︽︽b20eVzrttZitb9ŠŠ";
        check(ShareLink.commandCode(command).equals("b20eVzrttZitb9"), "User article command with mixed delimiters");
        check(ShareLink.parseInput(command).equals(command) && ShareLink.platform(command).equals("抖音"), "Command accepted by automatic input flow");
        check(ShareLink.commandCode("︽︽b20eVzrttZitb9ŠŠ").isEmpty(), "Require Douyin context for token recognition");
        check(ShareLink.commandCode("复制打开抖音 ︽︽abcŠŠ").isEmpty(), "Reject incomplete token");
        check(ShareLink.parseInput(command + " https://v.douyin.com/test/").equals("https://v.douyin.com/test/"), "Prefer real URL over token");
        check(ShareLink.videoId("https://www.douyin.com/article/7708756233431436544").equals("7708756233431436544"), "Article URL ID");
        check(VideoResolver.parseCommandResponse(200, "{\"code\":200,\"data\":{\"item_id\":\"7708756233431436544\"}}").equals("7708756233431436544"), "Validate command service item ID");
        for (String invalid : new String[] {"{\"code\":400}", "{\"code\":200,\"data\":{\"item_id\":\"https://attacker.example\"}}", "<html>Error</html>"}) {
            rejected = false;
            try {
                VideoResolver.parseCommandResponse(200, invalid);
            } catch (Exception expected) {
                rejected = true;
            }
            check(rejected, "Reject invalid command service response");
        }
        JSONObject articleDetail = new JSONObject(fixture).getJSONObject("aweme_detail");
        String fullBody = "第一段完整正文。\n\n![](https://example.invalid/article.jpg width=1080 height=720)\n\n最后一段正文。";
        JSONObject article = new JSONObject().put("article_id", "7708260643380177914").put("article_title", "长文章标题")
            .put("article_content", new JSONObject().put("long_article_abstract", "截断摘要…").put("markdown", fullBody).toString());
        articleDetail.put("article_info", article);
        String articleJson = new JSONObject().put("aweme_detail", articleDetail).toString();
        VideoInfo parsedArticle = VideoInfo.parse(articleJson, "7708260643380177914");
        check(parsedArticle.article && parsedArticle.variants.isEmpty(), "Article takes precedence over accompanying video");
        check(parsedArticle.title.equals("长文章标题") && parsedArticle.description.equals(fullBody), "Preserve full article rather than abstract or share text");
        check(parsedArticle.images.size() == 1 && parsedArticle.images.get(0).get(0).equals("https://example.invalid/article.jpg"), "Extract article Markdown image without dimensions");
        article.put("fe_data", new JSONObject().put("image_list", new org.json.JSONArray().put(new JSONObject()
            .put("origin_image_url", "https://example.invalid/original.jpg").put("markdown_url", "https://example.invalid/article.jpg"))).toString());
        parsedArticle = VideoInfo.parse(new JSONObject().put("aweme_detail", articleDetail).toString(), "7708260643380177914");
        check(parsedArticle.images.size() == 1 && parsedArticle.images.get(0).size() == 2
            && parsedArticle.cover.equals("https://example.invalid/original.jpg"), "Prefer structured article original image with backup");
        article.put("article_content", new JSONObject().put("long_article_abstract", "摘要").toString());
        rejected = false;
        try {
            VideoInfo.parse(new JSONObject().put("aweme_detail", articleDetail).toString(), "7708260643380177914");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "Reject summary-only article instead of claiming full text");
        System.out.println("All core checks passed. Best source: " + video.variants.get(0).label());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        System.out.println("PASS: " + message);
    }
}
