package com.dsh.pianji;

import android.content.Context;
import android.content.SharedPreferences;

/** 少量界面设置，记录数据本身不放在这里。 */
public final class Prefs {
    private static final String FILE = "pianji_prefs";
    private static final String ONLINE_IMPORT = "online_import";
    private static final String SORT_MODE = "sort_mode";

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean onlineImport(Context c) {
        return sp(c).getBoolean(ONLINE_IMPORT, true);
    }

    public static void setOnlineImport(Context c, boolean enabled) {
        sp(c).edit().putBoolean(ONLINE_IMPORT, enabled).apply();
    }

    public static int sortMode(Context c) {
        return sp(c).getInt(SORT_MODE, 0);
    }

    public static void setSortMode(Context c, int mode) {
        sp(c).edit().putInt(SORT_MODE, mode).apply();
    }
}
