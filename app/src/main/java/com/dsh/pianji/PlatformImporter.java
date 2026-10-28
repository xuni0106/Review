package com.dsh.pianji;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * 从豆瓣 / 小黑盒的分享文案或公开网页中提取作品资料。
 * 导入只负责补全资料，最终内容始终由用户确认后保存。
 *
 * 说明：豆瓣的桌面页会拦截无浏览器 Cookie 的请求，因此豆瓣专题统一
 * 走官方移动版公开页面；电影再尝试移动版公开 JSON 接口补全主创信息。
 * 小黑盒不提供公开稳定接口，优先解析分享文案，链接失败时由用户手动补充。
 */
public final class PlatformImporter {
    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[^\\s\\u3000<>\"']+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CN_TITLE_PATTERN =
            Pattern.compile("《([^》\\n]{1,80})》");
    private static final Pattern BRACKET_TITLE_PATTERN =
            Pattern.compile("[【\\[]([^】\\]\\n]{1,80})[】\\]]");
    private static final Pattern CORNER_TITLE_PATTERN =
            Pattern.compile("「([^」\\n]{1,80})」");
    private static final Pattern DOUBAN_ID_PATTERN =
            Pattern.compile("(?:subject|movie|tv)/(\\d{5,10})(?:[/?#&]|$)",
                    Pattern.CASE_INSENSITIVE);
    private static final int MAX_HTML_CHARS = 350_000;

    private PlatformImporter() {
    }

    public static final class Draft {
        public String title = "";
        public String altTitle = "";
        public String category = "";
        public String creator = "";
        public String genre = "";
        public String platform = "";
        public String progress = "";
        public String description = "";
        public String link = "";
        public String source = "导入";
        public int year = 0;
        public double rating = 0d;

        /** 是否拿到了值得填入的资料（仅有链接不算）。 */
        public boolean hasAnything() {
            return !TextUtils.isEmpty(title) || !TextUtils.isEmpty(description)
                    || !TextUtils.isEmpty(creator) || !TextUtils.isEmpty(genre)
                    || year > 0;
        }
    }

    public static boolean looksLikeUrl(String text) {
        return !TextUtils.isEmpty(firstUrl(text));
    }

    public static String firstUrl(String text) {
        if (text == null) return "";
        Matcher m = URL_PATTERN.matcher(text);
        if (!m.find()) return "";
        String url = m.group();
        while (url.length() > 0) {
            char last = url.charAt(url.length() - 1);
            if (last == ')' || last == '）' || last == '，' || last == '。'
                    || last == '、' || last == '；' || last == ';' || last == ','
                    || last == ']' || last == '】' || last == '"' || last == '\'') {
                url = url.substring(0, url.length() - 1);
            } else {
                break;
            }
        }
        return url;
    }

    public static String sourceForUrl(String url) {
        if (url == null) return "导入";
        String u = url.toLowerCase(Locale.ROOT);
        if (u.contains("douban.com")) return "豆瓣";
        if (u.contains("xiaoheihe.cn")) return "小黑盒";
        if (u.contains("bilibili.com")) return "哔哩哔哩";
        if (u.contains("maoyan.com")) return "猫眼";
        if (u.contains("iqiyi.com")) return "爱奇艺";
        if (u.contains("youku.com")) return "优酷";
        if (u.contains("v.qq.com")) return "腾讯视频";
        return "网页";
    }

    /** 不联网，仅解析用户粘贴的分享文案。 */
    public static Draft parse(String raw) {
        Draft d = new Draft();
        if (TextUtils.isEmpty(raw)) return d;
        String text = cleanText(raw);
        String url = firstUrl(text);
        if (!TextUtils.isEmpty(url)) {
            d.link = url;
            d.source = sourceForUrl(url);
        } else if (text.contains("小黑盒")) {
            d.source = "小黑盒";
        } else if (text.contains("豆瓣")) {
            d.source = "豆瓣";
        }
        d.title = extractTitle(text);
        d.category = inferCategory(text, url);
        d.year = extractYear(text);
        d.altTitle = firstCapture(text,
                "(?:又名|原名|英文名|外文名)\\s*[:：]\\s*([^\\n\\r]{1,100})");
        d.creator = firstCapture(text,
                "(?:导演|编剧|主创|开发商|开发团队|制作公司|发行商)\\s*[:：]\\s*([^\\n\\r<|]{1,90})");
        d.genre = firstCapture(text,
                "(?:类型|题材|标签)\\s*[:：]\\s*([^\\n\\r<|]{1,70})");
        d.platform = firstCapture(text,
                "(?:播出平台|上线平台|发行平台|游戏平台|平台)\\s*[:：]\\s*([^\\n\\r<|]{1,70})");
        d.progress = firstCapture(text,
                "(?:集数|共\\s*\\d+\\s*集|单集片长|游戏时长|游玩时长)\\s*[:：]?\\s*([^\\n\\r<|]{1,40})");
        d.rating = extractMyRating(text);
        String desc = firstCapture(text,
                "(?:剧情简介|内容简介|简介|故事简介)\\s*[:：]\\s*([\\s\\S]{0,700})");
        if (!TextUtils.isEmpty(desc)) {
            d.description = truncate(trimBlock(desc), 700);
        }
        cleanDraft(d);
        return d;
    }

    /**
     * 联网读取公开资料。必须在后台线程调用。
     * 豆瓣链接会自动改走手机版公开页面；小黑盒等链接按普通 Open Graph 解析。
     */
    public static Draft fetch(String url) throws IOException {
        if (TextUtils.isEmpty(url)) throw new IOException("链接为空");
        url = normalizeUrl(url.trim());
        String doubanId = doubanSubjectId(url);
        if (!TextUtils.isEmpty(doubanId)) {
            return fetchDouban(url, doubanId);
        }
        return fetchGeneric(url);
    }

    /** 豆瓣/小黑盒均已支持 HTTPS；把旧分享中的 http 链接自动升级，避免明文网络被系统拦截。 */
    private static String normalizeUrl(String url) {
        if (!url.startsWith("http://")) return url;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("douban.com") || lower.contains("xiaoheihe.cn")
                || lower.contains("bilibili.com") || lower.contains("maoyan.com")) {
            return "https://" + url.substring("http://".length());
        }
        return url;
    }

    // ------------------------------------------------------------------
    // 豆瓣
    // ------------------------------------------------------------------

    private static Draft fetchDouban(String inputUrl, String id) throws IOException {
        String canonical = "https://movie.douban.com/subject/" + id + "/";
        Draft page = null;
        IOException pageError = null;
        String pageHtml;
        try {
            pageHtml = download("https://m.douban.com/movie/subject/" + id + "/",
                    "https://m.douban.com/");
            page = parseDoubanPage(pageHtml, canonical);
        } catch (IOException e) {
            pageError = e;
            pageHtml = null;
        }

        boolean isTv = page != null && Cat.TV.code.equals(page.category);
        Draft api = null;
        IOException apiError = null;
        if (!isTv) {
            try {
                String json = download(
                        "https://m.douban.com/rexxar/api/v2/movie/" + id + "?for_mobile=1",
                        "https://m.douban.com/movie/subject/" + id + "/");
                if (json != null && json.trim().startsWith("{")) {
                    api = parseDoubanApi(json, canonical);
                }
            } catch (IOException e) {
                apiError = e;
            }
        }

        Draft result = null;
        if (api != null && hasUsefulMetadata(api)) {
            result = api;
            fillBlanks(result, page);
        } else if (page != null && hasUsefulMetadata(page)) {
            result = page;
        }
        if (result == null) {
            if (pageError != null) throw pageError;
            if (apiError != null) throw apiError;
            throw new IOException("豆瓣页面中没有读取到作品资料");
        }
        result.link = canonical;
        result.source = "豆瓣";
        if (TextUtils.isEmpty(result.category)) {
            result.category = isTv || containsAny(pageHtml, "电视剧", "剧集")
                    ? Cat.TV.code : Cat.MOVIE.code;
        }
        cleanDraft(result);
        return result;
    }

    private static Draft parseDoubanPage(String html, String canonical) {
        Draft d = new Draft();
        String ogTitle = metaContent(html, "og:title");
        String subTitle = blockTextByClass(html, "sub-title");
        String subOriginal = blockTextByClass(html, "sub-original-title");
        String subMeta = blockTextByClass(html, "sub-meta");
        boolean isTv = html.contains("subject-mark mark-tv")
                || html.contains("data-type=\"tv\"")
                || cleanTitle(ogTitle).contains("电视剧")
                || ogTitle.contains("电视剧");
        d.title = !TextUtils.isEmpty(subTitle) ? cleanTitle(subTitle) : cleanTitle(ogTitle);
        d.category = isTv ? Cat.TV.code : Cat.MOVIE.code;
        d.year = extractYear(htmlUnescape(subOriginal));
        if (d.year <= 0) d.year = extractYearFromMetaLine(subMeta);
        d.genre = parseGenreFromMetaLine(subMeta);
        d.altTitle = cleanAltTitle(subOriginal, d.title);
        String desc = metaContent(html, "og:description");
        if (TextUtils.isEmpty(desc)) desc = metaContent(html, "description");
        d.description = cleanPlatformDescription(desc, d.title, 1000);
        d.link = canonical;
        d.source = "豆瓣";
        return d;
    }

    private static Draft parseDoubanApi(String json, String canonical) throws IOException {
        try {
            JSONObject o = new JSONObject(json);
            Draft d = new Draft();
            d.title = o.optString("title", "").trim();
            d.altTitle = joinObjectNames(o.optJSONArray("aka"), " / ", 3);
            d.year = parseYear(o.optString("year", ""));
            boolean tv = "tv".equalsIgnoreCase(o.optString("type"))
                    || "tv".equalsIgnoreCase(o.optString("subtype"))
                    || o.optBoolean("is_tv", false);
            d.category = tv ? Cat.TV.code : Cat.MOVIE.code;
            d.genre = joinStrings(o.optJSONArray("genres"), " / ");
            d.creator = buildDoubanCreator(o);
            d.description = truncate(o.optString("intro", "").trim(), 1200);
            int episodes = o.optInt("episodes_count", 0);
            if (episodes > 0) d.progress = "共 " + episodes + " 集";
            d.link = canonical;
            d.source = "豆瓣";
            return d;
        } catch (Exception e) {
            throw new IOException("豆瓣资料格式错误", e);
        }
    }

    private static String buildDoubanCreator(JSONObject o) {
        String directors = joinObjectNames(o.optJSONArray("directors"), "、", 2);
        String actors = joinObjectNames(o.optJSONArray("actors"), "、", 3);
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(directors)) sb.append("导演 ").append(directors);
        if (!TextUtils.isEmpty(actors)) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("主演 ").append(actors);
        }
        return sb.toString();
    }

    private static String parseGenreFromMetaLine(String meta) {
        if (TextUtils.isEmpty(meta)) return "";
        String[] pieces = meta.split("\\s*/\\s*");
        List<String> genres = new ArrayList<>();
        for (int i = 1; i < pieces.length; i++) {
            String piece = pieces[i].trim();
            if (piece.isEmpty()) continue;
            if (piece.matches("^\\d{4}-\\d{2}-\\d{2}.*$")) continue;
            if (piece.contains("上映") || piece.contains("首播")) continue;
            if (piece.contains("片长") || piece.matches("^\\d+\\s*分钟$")) continue;
            if (piece.contains("集") && piece.matches("^\\d+.*集$")) continue;
            if (piece.length() > 24) continue;
            genres.add(piece);
        }
        return joinList(genres, " / ");
    }

    private static int extractYearFromMetaLine(String meta) {
        Matcher m = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?=\\s*[-年/])").matcher(meta);
        if (m.find()) return Integer.parseInt(m.group(1));
        return 0;
    }

    private static String cleanAltTitle(String original, String title) {
        if (TextUtils.isEmpty(original)) return "";
        String alt = original;
        alt = alt.replaceFirst("\\s*[\\(（]\\d{4}[\\)）]\\s*$", "").trim();
        if (TextUtils.equals(alt, title)) return "";
        return trimBlock(alt);
    }

    private static String doubanSubjectId(String url) {
        String decoded;
        try {
            decoded = URLDecoder.decode(url, "UTF-8");
        } catch (Exception e) {
            decoded = url;
        }
        String lower = decoded.toLowerCase(Locale.ROOT);
        if (!lower.contains("douban.com") && !lower.contains("douban://")) return "";
        Matcher m = DOUBAN_ID_PATTERN.matcher(decoded);
        if (m.find()) return m.group(1);
        return "";
    }

    // ------------------------------------------------------------------
    // 通用网页（小黑盒等）
    // ------------------------------------------------------------------

    private static Draft fetchGeneric(String url) throws IOException {
        String html = download(url, originOf(url));
        Draft d = parse(htmlToPlain(html));
        d.link = url;
        d.source = sourceForUrl(url);
        String title = metaContent(html, "og:title");
        if (TextUtils.isEmpty(title)) title = metaContent(html, "twitter:title");
        if (TextUtils.isEmpty(title)) title = htmlTitle(html);
        if (!TextUtils.isEmpty(title)) {
            title = cleanTitle(title);
            if (!looksGenericPlatformText(title)) d.title = title;
        }
        String desc = metaContent(html, "og:description");
        if (TextUtils.isEmpty(desc)) desc = metaContent(html, "description");
        if (TextUtils.isEmpty(desc)) desc = metaContent(html, "twitter:description");
        if (!TextUtils.isEmpty(desc)) {
            String cleaned = cleanPlatformDescription(desc, d.title, 900);
            if (!TextUtils.isEmpty(cleaned)) d.description = cleaned;
        }
        if (TextUtils.isEmpty(d.category)) d.category = inferCategory(htmlToPlain(html), url);
        if (TextUtils.isEmpty(d.source)) d.source = sourceForUrl(url);
        cleanDraft(d);
        if (!hasUsefulMetadata(d)) {
            throw new IOException("网页没有公开作品资料；请粘贴分享文案并手动确认片名");
        }
        return d;
    }

    // ------------------------------------------------------------------
    // 文案解析
    // ------------------------------------------------------------------

    public static String inferCategory(String text, String url) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (t.contains("ai短剧") || t.contains("ai 短剧") || t.contains("ai生成短剧")) {
            return Cat.AISHORT.code;
        }
        if (t.contains("真人短剧") || t.contains("微短剧") || t.contains("短剧")) {
            return Cat.SHORT.code;
        }
        if (t.contains("韩剧") || t.contains("韩国电视剧")) return Cat.KDRAMA.code;
        if (t.contains("美剧") || t.contains("欧美剧")) return Cat.USDRAMA.code;
        if (t.contains("动漫") || t.contains("番剧") || t.contains("动画"))
            return Cat.ANIME.code;
        if (t.contains("游戏") || t.contains("手游") || t.contains("端游")
                || t.contains("steam") || t.contains("switch") || t.contains("ps5")) {
            return Cat.GAME.code;
        }
        if (url != null && url.contains("xiaoheihe.cn")) return Cat.GAME.code;
        if (t.contains("电视剧") || t.contains("剧集") || t.contains("集数")
                || t.contains("第一季") || t.contains("第二季")) {
            return Cat.TV.code;
        }
        if (t.contains("电影") || t.contains("影片") || t.contains("上映")) {
            return Cat.MOVIE.code;
        }
        return url != null && url.contains("douban.com") ? Cat.MOVIE.code : "";
    }

    private static int extractYear(String text) {
        String labeled = firstCapture(text,
                "(?:上映日期|首播时间|首播|上映|播出时间|发行日期|发售日期|发行年份|年份)"
                        + "\\s*[:：]?\\s*[^\\n\\r0-9]{0,8}((?:19|20)\\d{2})");
        if (!TextUtils.isEmpty(labeled)) {
            try {
                return Integer.parseInt(labeled);
            } catch (NumberFormatException ignored) {
            }
        }
        Matcher m = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)").matcher(text);
        while (m.find()) {
            int y = Integer.parseInt(m.group(1));
            if (y >= 1900 && y <= 2100) return y;
        }
        return 0;
    }

    private static double extractMyRating(String text) {
        // 只识别“我的评分”或“我给/在…打了 xx 分”，不把豆瓣/小黑盒公开评分当成个人评分。
        String s = firstCapture(text,
                "(?:我的评分|我的打分)\\s*[:：]?\\s*"
                        + "(10(?:\\.0)?|[0-9](?:\\.[0-9])?)\\s*分?");
        if (TextUtils.isEmpty(s)) {
            s = firstCapture(text,
                    "我[^。！\\n0-9]{0,26}?(?:打|评)(?:了)?[^0-9\\n]{0,8}"
                            + "(10(?:\\.0)?|[0-9](?:\\.[0-9])?)\\s*分?");
        }
        if (TextUtils.isEmpty(s)) return 0d;
        try {
            return Item.clampRating(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    private static String extractTitle(String text) {
        if (TextUtils.isEmpty(text)) return "";
        String[] patterns = {"CN", "BRACKET", "CORNER"};
        for (String kind : patterns) {
            Matcher m;
            if ("CN".equals(kind)) m = CN_TITLE_PATTERN.matcher(text);
            else if ("BRACKET".equals(kind)) m = BRACKET_TITLE_PATTERN.matcher(text);
            else m = CORNER_TITLE_PATTERN.matcher(text);
            while (m.find()) {
                String candidate = cleanTitle(m.group(1));
                if (!looksGenericPlatformText(candidate)) return candidate;
            }
        }
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String candidate = cleanTitle(line);
            if (candidate.length() < 2 || candidate.length() > 80) continue;
            if (candidate.startsWith("http") || candidate.contains("://")) continue;
            if (candidate.startsWith("分享") || candidate.startsWith("推荐")
                    || candidate.startsWith("来自")) continue;
            if (candidate.matches("^(豆瓣|小黑盒|电影|电视剧|游戏|动漫).{0,8}$")) continue;
            if (candidate.contains("豆瓣评分") || candidate.contains("小黑盒")
                    || candidate.contains("导演:") || candidate.contains("简介")
                    || candidate.contains("下载")) continue;
            return candidate;
        }
        return "";
    }

    private static String cleanTitle(String raw) {
        if (raw == null) return "";
        String t = htmlUnescape(raw).replace('\u00a0', ' ').trim();
        t = t.replaceAll("\\s+", " ");
        t = t.replaceFirst("^豆瓣电影\\s*[:：-]\\s*", "");
        t = t.replaceFirst("^豆瓣\\s*[:：-]\\s*", "");
        t = t.replaceFirst("^小黑盒\\s*[:：-]\\s*", "");
        t = t.replaceFirst("\\s*[\\(（]豆瓣[\\)）]\\s*$", "");
        t = t.replaceFirst("\\s*[-_|—]\\s*豆瓣(电影|手机版)?\\s*$", "");
        t = t.replaceFirst("\\s*[-_|—]\\s*小黑盒\\s*$", "");
        t = t.replaceFirst("\\s*[_\\-|—]\\s*哔哩哔哩.*$", "");
        t = t.replaceFirst("\\s*[-_|—]\\s*猫眼电影\\s*$", "");
        t = t.replaceFirst("\\s+豆瓣评分\\s*[0-9.]*.*$", "");
        t = t.replaceFirst("\\s*[-_|—]\\s*(电影|电视剧|剧集|综艺|动漫|动画|纪录片)\\s*$", "");
        t = t.replaceFirst("\\s*[\\(（]\\d{4}[\\)）]\\s*$", "");
        return t.trim();
    }

    private static boolean looksGenericPlatformText(String s) {
        if (TextUtils.isEmpty(s)) return true;
        String t = s.trim();
        return t.equals("豆瓣") || t.equals("豆瓣电影") || t.equals("豆瓣手机版")
                || t.equals("小黑盒") || t.equals("登录") || t.equals("页面不存在")
                || t.equals("分享") || t.equals("首页") || t.length() < 2;
    }

    // ------------------------------------------------------------------
    // HTML / 文本工具
    // ------------------------------------------------------------------

    private static String metaContent(String html, String key) {
        if (TextUtils.isEmpty(html)) return "";
        Pattern p = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);
        while (m.find()) {
            String tag = m.group();
            String name = attr(tag, "property");
            if (TextUtils.isEmpty(name)) name = attr(tag, "name");
            if (TextUtils.isEmpty(name)) name = attr(tag, "itemprop");
            if (name != null && name.equalsIgnoreCase(key)) {
                String content = attr(tag, "content");
                if (!TextUtils.isEmpty(content)) return content;
            }
        }
        return "";
    }

    private static String attr(String tag, String attrName) {
        Pattern p = Pattern.compile("\\b" + Pattern.quote(attrName)
                + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(tag);
        if (m.find()) {
            String a = m.group(1);
            return htmlUnescape(a != null ? a : (m.group(2) != null ? m.group(2) : ""));
        }
        return "";
    }

    private static String blockTextByClass(String html, String className) {
        if (TextUtils.isEmpty(html)) return "";
        Pattern p = Pattern.compile(
                "(?is)<([a-zA-Z0-9]+)[^>]*class\\s*=\\s*[\"'][^\"']*\\b"
                        + Pattern.quote(className)
                        + "\\b[^\"']*[\"'][^>]*>(.*?)</\\1\\s*>");
        Matcher m = p.matcher(html);
        if (m.find()) return trimBlock(htmlToPlain(m.group(2)));
        return "";
    }

    private static String htmlTitle(String html) {
        Matcher m = Pattern.compile("<title[^>]*>([\\s\\S]*?)</title>",
                Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) return htmlUnescape(m.group(1)).trim();
        return "";
    }

    private static String cleanPlatformDescription(String raw, String title, int max) {
        if (TextUtils.isEmpty(raw)) return "";
        String t = trimBlock(htmlUnescape(raw));
        if (!TextUtils.isEmpty(title)) {
            if (t.startsWith(title)) t = t.substring(title.length()).trim();
        }
        Matcher intro = Pattern.compile("(?:剧情简介|内容简介|作品简介|简介)\\s*[:：]\\s*")
                .matcher(t);
        if (intro.find()) {
            t = t.substring(intro.end()).trim();
        } else {
            t = t.replaceFirst("^豆瓣评分\\s*[:：]?\\s*[0-9.]+\\s*", "").trim();
        }
        t = t.replaceFirst("^[|·\\-—\\s]+", "");
        t = t.replaceAll("\\s*(?:展开|收起|©豆瓣|© 豆瓣)\\s*$", "");
        return truncate(t, max);
    }

    private static String firstCapture(String text, String regex) {
        if (TextUtils.isEmpty(text)) return "";
        try {
            Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
            if (m.find()) {
                String s = m.group(1);
                return s == null ? "" : trimBlock(htmlUnescape(s));
            }
        } catch (RuntimeException ignored) {
        }
        return "";
    }

    private static String trimBlock(String s) {
        if (s == null) return "";
        String out = s.replace('\u00a0', ' ').replaceAll("[\\t ]+", " ").trim();
        out = out.replaceAll("\\n{3,}", "\n\n");
        return out;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max).trim() + "…";
    }

    private static String cleanText(String raw) {
        if (raw == null) return "";
        String t = raw;
        if (t.indexOf('<') >= 0 && t.indexOf('>') > 0) t = htmlToPlain(t);
        t = htmlUnescape(t);
        t = t.replace('\u00a0', ' ');
        return trimBlock(t);
    }

    /** 去标签并保留段落边界，便于从资料页正文中提取字段。 */
    public static String htmlToPlain(String html) {
        if (html == null) return "";
        String s = html;
        if (s.length() > MAX_HTML_CHARS) s = s.substring(0, MAX_HTML_CHARS);
        s = s.replaceAll("(?is)<script\\b[^>]*>.*?</script>", " ");
        s = s.replaceAll("(?is)<style\\b[^>]*>.*?</style>", " ");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</(p|div|li|h[1-6]|tr)>", "\n");
        s = s.replaceAll("<[^>]+>", " ");
        s = htmlUnescape(s);
        s = s.replace('\u00a0', ' ');
        return trimBlock(s);
    }

    public static String htmlUnescape(String s) {
        if (s == null) return "";
        String out = s
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ");
        Matcher m = Pattern.compile("&#(x?[0-9a-fA-F]{1,6});").matcher(out);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String code = m.group(1);
            try {
                int value = code.startsWith("x") || code.startsWith("X")
                        ? Integer.parseInt(code.substring(1), 16)
                        : Integer.parseInt(code);
                m.appendReplacement(sb, Matcher.quoteReplacement(new String(
                        Character.toChars(value))));
            } catch (RuntimeException e) {
                m.appendReplacement(sb, m.group());
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static void cleanDraft(Draft d) {
        d.title = trimBlock(cleanTitle(d.title));
        d.altTitle = trimBlock(d.altTitle);
        d.creator = trimBlock(d.creator);
        d.genre = trimBlock(d.genre);
        d.platform = trimBlock(d.platform);
        d.progress = trimBlock(d.progress);
        d.description = trimBlock(d.description);
        if (d.title.length() > 100) d.title = d.title.substring(0, 100);
        if (looksGenericPlatformText(d.title)) d.title = "";
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    private static void fillBlanks(Draft target, Draft from) {
        if (target == null || from == null) return;
        if (TextUtils.isEmpty(target.title)) target.title = from.title;
        if (TextUtils.isEmpty(target.altTitle)) target.altTitle = from.altTitle;
        if (TextUtils.isEmpty(target.category)) target.category = from.category;
        if (TextUtils.isEmpty(target.creator)) target.creator = from.creator;
        if (TextUtils.isEmpty(target.genre)) target.genre = from.genre;
        if (TextUtils.isEmpty(target.platform)) target.platform = from.platform;
        if (TextUtils.isEmpty(target.progress)) target.progress = from.progress;
        if (TextUtils.isEmpty(target.description)) target.description = from.description;
        if (target.year <= 0) target.year = from.year;
        if (target.rating <= 0) target.rating = from.rating;
    }

    private static boolean hasUsefulMetadata(Draft d) {
        return d != null && (!TextUtils.isEmpty(d.title) || !TextUtils.isEmpty(d.creator)
                || !TextUtils.isEmpty(d.description) || d.year > 0);
    }

    private static boolean containsAny(String text, String... needles) {
        if (text == null) return false;
        for (String n : needles) if (text.contains(n)) return true;
        return false;
    }

    private static int parseYear(String raw) {
        if (TextUtils.isEmpty(raw)) return 0;
        Matcher m = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)").matcher(raw);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private static String joinStrings(JSONArray array, String separator) {
        if (array == null) return "";
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.length() && values.size() < 8; i++) {
            String s = array.optString(i, "").trim();
            if (!TextUtils.isEmpty(s)) values.add(s);
        }
        return joinList(values, separator);
    }

    private static String joinObjectNames(JSONArray array, String separator, int max) {
        if (array == null) return "";
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.length() && values.size() < max; i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) continue;
            String name = o.optString("name", "").trim();
            if (!TextUtils.isEmpty(name)) values.add(name);
        }
        return joinList(values, separator);
    }

    private static String joinList(List<String> values, String separator) {
        StringBuilder sb = new StringBuilder();
        for (String s : values) {
            if (s == null || s.isEmpty()) continue;
            if (sb.length() > 0) sb.append(separator);
            sb.append(s);
        }
        return sb.toString();
    }

    private static String originOf(String url) {
        try {
            URL u = new URL(url);
            StringBuilder sb = new StringBuilder();
            sb.append(u.getProtocol()).append("://").append(u.getHost());
            if (u.getPort() > 0) sb.append(':').append(u.getPort());
            sb.append('/');
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------

    private static String download(String url, String referer) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            String protocol = u.getProtocol();
            if (!"https".equalsIgnoreCase(protocol) && !"http".equalsIgnoreCase(protocol)) {
                throw new IOException("只支持 http/https 链接");
            }
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/122.0 Mobile Safari/537.36");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6");
            boolean api = url.contains("/rexxar/api/");
            conn.setRequestProperty("Accept", api
                    ? "application/json,text/plain,*/*"
                    : "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            if (!TextUtils.isEmpty(referer)) conn.setRequestProperty("Referer", referer);
            conn.setRequestProperty("Accept-Encoding", "gzip");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) {
                throw new IOException("网站返回 HTTP " + code);
            }
            InputStream in = conn.getInputStream();
            String encoding = conn.getContentEncoding();
            if (encoding != null && encoding.toLowerCase(Locale.ROOT).contains("gzip")) {
                in = new GZIPInputStream(in);
            }
            String html = readHtml(in);
            if (TextUtils.isEmpty(html)) throw new IOException("网页内容为空");
            return html;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readHtml(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            bytes.write(buf, 0, n);
            if (bytes.size() > 1_500_000) break;
        }
        byte[] data = bytes.toByteArray();
        String charset = "UTF-8";
        String head = new String(data, 0, Math.min(data.length, 4096), StandardCharsets.ISO_8859_1);
        Matcher m = Pattern.compile("(?i)charset\\s*=\\s*[\"']?([a-zA-Z0-9_\\-]+)").matcher(head);
        if (m.find()) {
            String cs = m.group(1);
            try {
                if (java.nio.charset.Charset.isSupported(cs)) charset = cs;
            } catch (RuntimeException ignored) {
            }
        }
        try {
            return new String(data, charset);
        } catch (java.io.UnsupportedEncodingException e) {
            return new String(data, StandardCharsets.UTF_8);
        }
    }
}
