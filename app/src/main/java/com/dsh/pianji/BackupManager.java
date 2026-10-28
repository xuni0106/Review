package com.dsh.pianji;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 备份文件读写与宽松解析，支持片刻 JSON 和裸 JSON 数组。 */
public final class BackupManager {
    private static final int MAX_BYTES = 20 * 1024 * 1024;

    private BackupManager() {
    }

    public static String readFile(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        try {
            return readStream(in, file.length());
        } finally {
            closeQuietly(in);
        }
    }

    public static String readUri(Context context, Uri uri) throws IOException {
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("无法打开所选文件");
        try {
            return readStream(in, -1);
        } finally {
            closeQuietly(in);
        }
    }

    private static String readStream(InputStream in, long knownSize) throws IOException {
        if (knownSize > MAX_BYTES) throw new IOException("文件太大（超过 20 MB）");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) throw new IOException("文件太大（超过 20 MB）");
            out.write(buffer, 0, read);
        }
        String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);
        return text;
    }

    public static void writeUri(Context context, Uri uri, String content) throws IOException {
        OutputStream out = context.getContentResolver().openOutputStream(uri, "wt");
        if (out == null) throw new IOException("无法写入所选文件");
        try {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } finally {
            closeQuietly(out);
        }
    }

    public static void copyFile(File from, File to) throws IOException {
        FileInputStream in = new FileInputStream(from);
        FileOutputStream out = new FileOutputStream(to);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.flush();
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    public static ParseResult parse(String raw) {
        ParseResult result = new ParseResult();
        if (TextUtils.isEmpty(raw) || raw.trim().isEmpty()) {
            result.error = "文件是空的";
            return result;
        }
        String text = raw.trim();
        try {
            JSONArray array;
            if (text.startsWith("[")) {
                array = new JSONArray(text);
            } else if (text.startsWith("{")) {
                JSONObject root = new JSONObject(text);
                array = root.optJSONArray("items");
                if (array == null) array = root.optJSONArray("records");
                if (array == null) array = root.optJSONArray("data");
                if (array == null && root.has("title")) {
                    array = new JSONArray();
                    array.put(root);
                }
                if (array == null) {
                    result.error = "不是片刻备份文件，找不到 records / items 数据";
                    return result;
                }
            } else {
                result.error = "不是有效的 JSON 备份文件";
                return result;
            }
            List<Item> items = new ArrayList<>();
            int skipped = 0;
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) {
                    skipped++;
                    continue;
                }
                Item it = Item.fromJson(o);
                if (TextUtils.isEmpty(it.title)) {
                    skipped++;
                    continue;
                }
                items.add(it);
            }
            result.items = items;
            if (skipped > 0) result.error = "已跳过 " + skipped + " 条没有标题的记录";
        } catch (JSONException e) {
            result.error = "JSON 格式错误：" + e.getMessage();
        } catch (RuntimeException e) {
            result.error = "解析失败：" + e.getMessage();
        }
        return result;
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static final class ParseResult {
        public List<Item> items;
        public String error = "";
    }
}
