package com.dsh.pianji;

import android.content.Context;
import android.text.TextUtils;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 本地数据仓库。唯一真实数据源是 files/records.json。
 * 使用 AtomicFile 写入，避免保存过程中进程被杀导致半截文件。
 */
public final class Store {
    public static final int SCHEMA = 1;
    private static Store instance;

    private final Context appContext;
    private final File dataFile;
    private final AtomicFile atomicFile;
    private final List<Item> items = new ArrayList<>();
    private String lastLoadError = "";

    private Store(Context context) {
        appContext = context.getApplicationContext();
        dataFile = new File(appContext.getFilesDir(), "records.json");
        atomicFile = new AtomicFile(dataFile);
        load();
    }

    public static synchronized Store get(Context context) {
        if (instance == null) instance = new Store(context);
        return instance;
    }

    public String lastLoadError() {
        return lastLoadError;
    }

    private synchronized void load() {
        items.clear();
        lastLoadError = "";
        if (!dataFile.exists()) return;
        String raw;
        try {
            raw = BackupManager.readFile(dataFile);
        } catch (IOException e) {
            lastLoadError = "读取本地数据失败：" + e.getMessage();
            return;
        }
        BackupManager.ParseResult result = BackupManager.parse(raw);
        if (result.items == null) {
            lastLoadError = TextUtils.isEmpty(result.error) ? "本地数据格式无法识别" : result.error;
            // 保留损坏文件，避免下一次保存静默覆盖用户仅有的数据。
            File broken = new File(appContext.getFilesDir(),
                    "records.broken-" + System.currentTimeMillis() + ".json");
            try {
                BackupManager.copyFile(dataFile, broken);
                dataFile.delete();
            } catch (IOException ignored) {
            }
            return;
        }
        items.addAll(result.items);
        if (!TextUtils.isEmpty(result.error)) lastLoadError = result.error;
    }

    public synchronized List<Item> all() {
        return new ArrayList<>(items);
    }

    public synchronized List<Item> sorted(int sortMode) {
        List<Item> out = all();
        if (sortMode == 1) {
            Collections.sort(out, new Comparator<Item>() {
                @Override
                public int compare(Item a, Item b) {
                    int r = Double.compare(b.rating, a.rating);
                    return r != 0 ? r : Long.compare(b.updatedAt, a.updatedAt);
                }
            });
        } else if (sortMode == 2) {
            Collections.sort(out, new Comparator<Item>() {
                @Override
                public int compare(Item a, Item b) {
                    return a.title.compareToIgnoreCase(b.title);
                }
            });
        } else {
            Collections.sort(out, new Comparator<Item>() {
                @Override
                public int compare(Item a, Item b) {
                    return Long.compare(b.updatedAt, a.updatedAt);
                }
            });
        }
        return out;
    }

    public synchronized Item get(String id) {
        if (id == null) return null;
        for (Item it : items) {
            if (id.equals(it.id)) return it;
        }
        return null;
    }

    public synchronized void save(Item item) {
        if (item == null || TextUtils.isEmpty(item.title)) return;
        item.updatedAt = System.currentTimeMillis();
        item.rating = Item.clampRating(item.rating);
        boolean replaced = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(item.id)) {
                items.set(i, item);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            if (item.createdAt <= 0) item.createdAt = System.currentTimeMillis();
            items.add(item);
        }
        persistLocked();
    }

    public synchronized boolean delete(String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) {
                items.remove(i);
                persistLocked();
                return true;
            }
        }
        return false;
    }

    public synchronized void clear() {
        items.clear();
        persistLocked();
    }

    public synchronized MergeStats merge(List<Item> incoming, boolean replace) {
        MergeStats stats = new MergeStats();
        if (incoming == null) return stats;
        if (replace) {
            stats.removed = items.size();
            items.clear();
        }
        for (Item src : incoming) {
            if (src == null || TextUtils.isEmpty(src.title)) continue;
            Item incomingItem = src.copy();
            int found = -1;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).id.equals(incomingItem.id)) {
                    found = i;
                    break;
                }
            }
            if (found >= 0) {
                items.set(found, incomingItem);
                stats.updated++;
            } else {
                items.add(incomingItem);
                stats.added++;
            }
        }
        persistLocked();
        return stats;
    }

    public synchronized int count() {
        return items.size();
    }

    public synchronized String exportJson() {
        JSONObject root = new JSONObject();
        JSONArray array = new JSONArray();
        for (Item it : items) array.put(it.toJson());
        try {
            root.put("app", "pianji");
            root.put("schema", SCHEMA);
            root.put("exportedAt", System.currentTimeMillis());
            root.put("count", array.length());
            root.put("items", array);
            return root.toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException("无法生成备份", e);
        }
    }

    private void persistLocked() {
        FileOutputStream out = null;
        try {
            out = atomicFile.startWrite();
            out.write(exportJson().getBytes("UTF-8"));
            out.flush();
            out.getFD().sync();
            atomicFile.finishWrite(out);
        } catch (Exception e) {
            if (out != null) atomicFile.failWrite(out);
        }
    }

    public String storagePath() {
        return dataFile.getAbsolutePath();
    }

    public String sizeLabel() {
        long bytes = dataFile.exists() ? dataFile.length() : 0;
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.CHINA, "%.1f KB", bytes / 1024d);
        return String.format(Locale.CHINA, "%.1f MB", bytes / 1024d / 1024d);
    }

    public static final class MergeStats {
        public int added;
        public int updated;
        public int removed;

        public String summary() {
            return "新增 " + added + " 条，更新 " + updated + " 条";
        }
    }
}
