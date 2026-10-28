package com.dsh.pianji;

/** 作品分类。颜色同时用于海报占位、标签和统计图。 */
public enum Cat {
    MOVIE("movie", "电影", "影", 0xFFE2703A),
    TV("tv", "电视剧", "剧", 0xFF4C8DFF),
    SHORT("short", "真人短剧", "短", 0xFFD9457A),
    AISHORT("aishort", "AI短剧", "AI", 0xFF8B5CF6),
    KDRAMA("kdrama", "韩剧", "韩", 0xFF12A594),
    USDRAMA("usdrama", "美剧", "美", 0xFF5B6FD6),
    ANIME("anime", "动漫", "漫", 0xFFE59A12),
    GAME("game", "游戏", "游", 0xFF1FA971);

    public final String code;
    public final String label;
    public final String badge;
    public final int color;

    Cat(String code, String label, String badge, int color) {
        this.code = code;
        this.label = label;
        this.badge = badge;
        this.color = color;
    }

    public static Cat fromCode(String code) {
        if (code != null) {
            for (Cat c : values()) {
                if (c.code.equals(code)) return c;
            }
        }
        return MOVIE;
    }

    public static Cat fromLabel(String label) {
        if (label != null) {
            String l = label.trim();
            for (Cat c : values()) {
                if (c.label.equals(l)) return c;
            }
            if (l.contains("短剧")) {
                return l.toUpperCase(java.util.Locale.ROOT).contains("AI") ? AISHORT : SHORT;
            }
            if (l.contains("韩")) return KDRAMA;
            if (l.contains("美剧")) return USDRAMA;
            if (l.contains("动漫") || l.contains("动画") || l.contains("番剧")) return ANIME;
            if (l.contains("游戏")) return GAME;
            if (l.contains("剧")) return TV;
            if (l.contains("电影") || l.contains("影片")) return MOVIE;
        }
        return null;
    }
}
