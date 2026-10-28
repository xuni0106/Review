package com.dsh.pianji;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 轻量界面工具，全部使用原生控件，不依赖 AndroidX。 */
public final class Ui {
    public static final int BG = 0xFFF5F3EF;
    public static final int SURFACE = 0xFFFFFFFF;
    public static final int SURFACE_ALT = 0xFFF8F6F2;
    public static final int INK = 0xFF1B1C1E;
    public static final int INK_SOFT = 0xFF414349;
    public static final int MUTED = 0xFF7C7B77;
    public static final int FAINT = 0xFFA8A59E;
    public static final int LINE = 0xFFE6E2DA;
    public static final int ACCENT = 0xFFC96826;
    public static final int ACCENT_DEEP = 0xFF8E3F11;
    public static final int ACCENT_SOFT = 0xFFF6E8D8;
    public static final int DARK = 0xFF1E2026;
    public static final int DARK_2 = 0xFF2A2D35;
    public static final int GREEN = 0xFF2D8A66;
    public static final int RED = 0xFFC84C4C;
    public static final int WHITE = Color.WHITE;
    public static final int NAV_HEIGHT = 56;
    public static final int PAGE_PAD = 18;

    private static Typeface serifBold;
    private static Typeface serif;
    private static Typeface sansMedium;

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static int screenWidth(Context c) {
        return c.getResources().getDisplayMetrics().widthPixels;
    }

    public static int statusBarHeight(Context c) {
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? c.getResources().getDimensionPixelSize(id) : dp(c, 24);
    }

    public static int alpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static int blend(int c1, int c2, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        int a = Math.round(Color.alpha(c1) + (Color.alpha(c2) - Color.alpha(c1)) * r);
        int red = Math.round(Color.red(c1) + (Color.red(c2) - Color.red(c1)) * r);
        int green = Math.round(Color.green(c1) + (Color.green(c2) - Color.green(c1)) * r);
        int blue = Math.round(Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * r);
        return Color.argb(a, red, green, blue);
    }

    public static Typeface serifBold() {
        if (serifBold == null) serifBold = Typeface.create("serif", Typeface.BOLD);
        return serifBold;
    }

    public static Typeface serif() {
        if (serif == null) serif = Typeface.create("serif", Typeface.NORMAL);
        return serif;
    }

    public static Typeface sansMedium() {
        if (sansMedium == null) sansMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        return sansMedium;
    }

    public static GradientDrawable rounded(int fill, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusDp * 1f);
        if (strokeColor != Color.TRANSPARENT && strokeDp > 0) d.setStroke(Math.round(strokeDp), strokeColor);
        return d;
    }

    public static GradientDrawable rounded(Context c, int fill, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable gradient(Context c, int from, int to, float radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{from, to});
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static Drawable ripple(Context c, Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x1A000000), content, null);
    }

    public static Drawable tappable(Context c, int fill, float radiusDp, int stroke, float strokeDp) {
        GradientDrawable normal = rounded(c, fill, radiusDp);
        if (stroke != Color.TRANSPARENT && strokeDp > 0) {
            normal.setStroke(dp(c, strokeDp), stroke);
        }
        return new RippleDrawable(ColorStateList.valueOf(0x18000000), normal, null);
    }

    public static StateListDrawable inputBackground(Context c) {
        GradientDrawable normal = rounded(c, SURFACE, 14);
        normal.setStroke(dp(c, 1), LINE);
        GradientDrawable focused = rounded(c, SURFACE, 14);
        focused.setStroke(dp(c, 2), alpha(ACCENT, 0.8f));
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, normal);
        return s;
    }

    public static TextView text(Context c, CharSequence value, float sp, int color) {
        return text(c, value, sp, color, false);
    }

    public static TextView text(Context c, CharSequence value, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(dp(c, 3), 1f);
        if (bold) t.setTypeface(sansMedium());
        return t;
    }

    public static TextView serif(Context c, CharSequence value, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(serifBold());
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    public static TextView label(Context c, CharSequence value) {
        TextView t = text(c, value, 11.5f, MUTED, true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int widthDp, int heightDp) {
        return new LinearLayout.LayoutParams(widthDp, heightDp);
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    public static Space space(Context c, int heightDp) {
        Space s = new Space(c);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, heightDp)));
        return s;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    public static View vDivider(Context c, int heightDp) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, heightDp)));
        return v;
    }

    public static void margin(View v, int l, int t, int r, int b) {
        ViewGroup.LayoutParams raw = v.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
            lp.setMargins(dp(v.getContext(), l), dp(v.getContext(), t),
                    dp(v.getContext(), r), dp(v.getContext(), b));
            v.setLayoutParams(lp);
        }
    }

    public static void marginDp(View v, int l, int t, int r, int b) {
        ViewGroup.LayoutParams raw = v.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
            lp.setMargins(l, t, r, b);
            v.setLayoutParams(lp);
        }
    }

    public static void padding(View v, int l, int t, int r, int b) {
        v.setPadding(dp(v.getContext(), l), dp(v.getContext(), t),
                dp(v.getContext(), r), dp(v.getContext(), b));
    }

    public static TextView iconButton(Context c, String glyph, Runnable action) {
        TextView b = text(c, glyph, 18, INK, false);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        b.setBackground(tappable(c, SURFACE, 22, LINE, 1));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
        if (action != null) b.setOnClickListener(v -> action.run());
        return b;
    }

    public static TextView primaryButton(Context c, String label, Runnable action) {
        TextView b = text(c, label, 15, WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(tappable(c, ACCENT, 16, Color.TRANSPARENT, 0));
        b.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14));
        b.setMinHeight(dp(c, 50));
        if (action != null) b.setOnClickListener(v -> action.run());
        return b;
    }

    public static TextView secondaryButton(Context c, String label, Runnable action) {
        TextView b = text(c, label, 14, INK, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(tappable(c, SURFACE, 15, LINE, 1));
        b.setPadding(dp(c, 16), dp(c, 13), dp(c, 16), dp(c, 13));
        b.setMinHeight(dp(c, 48));
        if (action != null) b.setOnClickListener(v -> action.run());
        return b;
    }

    public static EditText input(Context c, String hint, int inputType, float sp) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(sp);
        e.setTextColor(INK);
        e.setHintTextColor(FAINT);
        e.setBackground(inputBackground(c));
        e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        e.setInputType(inputType);
        e.setIncludeFontPadding(false);
        e.setMinHeight(dp(c, 46));
        e.setSelectAllOnFocus(false);
        return e;
    }

    public static EditText singleLine(Context c, String hint) {
        EditText e = input(c, hint, android.text.InputType.TYPE_CLASS_TEXT, 15f);
        e.setSingleLine(true);
        e.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        return e;
    }

    public static EditText multiline(Context c, String hint, int minHeightDp) {
        EditText e = input(c, hint, android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 15f);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setMinHeight(dp(c, minHeightDp));
        e.setMaxLines(12);
        e.setImeOptions(EditorInfo.IME_ACTION_NONE);
        return e;
    }

    public static TextView chip(Context c, String text, boolean selected) {
        TextView t = text(c, text, 12.5f, selected ? ACCENT_DEEP : INK_SOFT, selected);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        t.setBackground(chipBackground(c, selected));
        return t;
    }

    public static Drawable chipBackground(Context c, boolean selected) {
        GradientDrawable normal = rounded(c, selected ? ACCENT_SOFT : SURFACE, 18);
        if (!selected) normal.setStroke(dp(c, 1), LINE);
        return new RippleDrawable(ColorStateList.valueOf(
                selected ? 0x18C96826 : 0x14000000), normal, null);
    }

    public static void updateChip(TextView chip, boolean selected) {
        chip.setTextColor(selected ? ACCENT_DEEP : INK_SOFT);
        chip.setTypeface(selected ? sansMedium() : Typeface.create("sans-serif", Typeface.NORMAL));
        chip.setBackground(chipBackground(chip.getContext(), selected));
    }

    public static TextView pill(Context c, String text, int fg, int bg) {
        TextView t = text(c, text, 11, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 9), dp(c, 5), dp(c, 9), dp(c, 5));
        t.setBackground(rounded(c, bg, 20));
        return t;
    }

    public static TextView statusPill(Context c, Status st) {
        return pill(c, st.label, st.color, alpha(st.color, 0.12f));
    }

    public static void setupWindow(android.app.Activity activity) {
        Window w = activity.getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(SURFACE);
        View decor = w.getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setNavigationBarDividerColor(LINE);
        }
    }

    public static String todayLabel() {
        return new SimpleDateFormat("M月d日 · EEEE", Locale.CHINA).format(new Date());
    }

    public static String backupFileName() {
        return "pianji-backup-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
                .format(new Date()) + ".json";
    }

    public static void clearText(TextView... views) {
        for (TextView v : views) if (v != null) v.setText("");
    }

    public static void addTextWatcher(EditText e, TextWatcher w) {
        e.addTextChangedListener(w);
    }
}
