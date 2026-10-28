package com.dsh.pianji;

import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** 一条作品记录。字段尽量保持扁平，方便备份与未来升级。 */
public class Item {
    public String id = UUID.randomUUID().toString();
    public String title = "";
    public String altTitle = "";
    public String category = Cat.MOVIE.code;
    public String status = Status.DONE.code;
    public double rating = 0d;
    public int year = 0;
    public String creator = "";
    public String genre = "";
    public String platform = "";
    public String progress = "";
    public String review = "";
    public String description = "";
    public String link = "";
    public String source = "手动记录";
    public boolean favorite = false;
    public long createdAt = System.currentTimeMillis();
    public long updatedAt = System.currentTimeMillis();

    public Cat cat() {
        return Cat.fromCode(category);
    }

    public Status st() {
        return Status.fromCode(status);
    }

    public String displayRating() {
        return rating <= 0 ? "未评分" : formatRating(rating);
    }

    public static String formatRating(double value) {
        if (value <= 0) return "—";
        if (Math.abs(value - Math.rint(value)) < 0.001) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(java.util.Locale.CHINA, "%.1f", value);
    }

    public String metaLine() {
        StringBuilder sb = new StringBuilder();
        if (year > 0) sb.append(year);
        if (!TextUtils.isEmpty(genre)) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(genre);
        }
        if (!TextUtils.isEmpty(progress)) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(progress);
        }
        if (sb.length() == 0 && !TextUtils.isEmpty(creator)) sb.append(creator);
        return sb.toString();
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("title", title);
            o.put("altTitle", altTitle);
            o.put("category", category);
            o.put("status", status);
            o.put("rating", rating);
            o.put("year", year);
            o.put("creator", creator);
            o.put("genre", genre);
            o.put("platform", platform);
            o.put("progress", progress);
            o.put("review", review);
            o.put("description", description);
            o.put("link", link);
            o.put("source", source);
            o.put("favorite", favorite);
            o.put("createdAt", createdAt);
            o.put("updatedAt", updatedAt);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public static Item fromJson(JSONObject o) {
        Item it = new Item();
        String id = o.optString("id", "").trim();
        if (!TextUtils.isEmpty(id)) it.id = id;
        it.title = o.optString("title", "").trim();
        it.altTitle = o.optString("altTitle", o.optString("originalTitle", "")).trim();
        it.category = Cat.fromCode(o.optString("category", o.optString("type", ""))).code;
        it.status = Status.fromCode(o.optString("status", "")).code;
        it.rating = clampRating(o.optDouble("rating", o.optDouble("score", 0d)));
        it.year = o.optInt("year", 0);
        if (it.year < 1800 || it.year > 2200) it.year = 0;
        it.creator = o.optString("creator", o.optString("director", "")).trim();
        it.genre = o.optString("genre", "").trim();
        it.platform = o.optString("platform", "").trim();
        it.progress = o.optString("progress", "").trim();
        it.review = o.optString("review", o.optString("comment", "")).trim();
        it.description = o.optString("description", o.optString("summary", "")).trim();
        it.link = o.optString("link", o.optString("url", "")).trim();
        it.source = o.optString("source", "导入").trim();
        if (TextUtils.isEmpty(it.source)) it.source = "导入";
        it.favorite = o.optBoolean("favorite", false);
        it.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        it.updatedAt = o.optLong("updatedAt", it.createdAt);
        if (it.createdAt <= 0) it.createdAt = System.currentTimeMillis();
        if (it.updatedAt <= 0) it.updatedAt = it.createdAt;
        return it;
    }

    public static double clampRating(double r) {
        if (Double.isNaN(r) || Double.isInfinite(r)) return 0d;
        return Math.max(0d, Math.min(10d, Math.round(r * 10d) / 10d));
    }

    public Item copy() {
        try {
            return fromJson(new JSONObject(toJson().toString()));
        } catch (JSONException e) {
            return this;
        }
    }
}
