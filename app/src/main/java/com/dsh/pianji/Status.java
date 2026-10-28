package com.dsh.pianji;

/** 观看 / 游玩状态（对影视与游戏都通用） */
public enum Status {
    PLANNED("planned", "待开始", 0xFF8B93A3),
    WATCHING("watching", "进行中", 0xFF3B82F6),
    DONE("done", "已完成", 0xFF1FA971),
    DROPPED("dropped", "弃了", 0xFFD64545);

    public final String code;
    public final String label;
    public final int color;

    Status(String code, String label, int color) {
        this.code = code;
        this.label = label;
        this.color = color;
    }

    public static Status fromCode(String code) {
        if (code != null) {
            for (Status s : values()) {
                if (s.code.equals(code)) return s;
            }
        }
        return DONE;
    }
}
