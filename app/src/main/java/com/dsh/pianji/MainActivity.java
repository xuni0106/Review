package com.dsh.pianji;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 记录、统计与本地设置。内容区滚动仅用于作品列表。 */
public class MainActivity extends Activity {
    private static final int REQUEST_EXPORT = 2101;
    private static final int REQUEST_IMPORT = 2102;
    private static final int TAB_RECORDS = 0;
    private static final int TAB_STATS = 1;
    private static final int TAB_SETTINGS = 2;

    private Store store;
    private FrameLayout contentHost;
    private final TextView[] navLabels = new TextView[3];
    private int tab = TAB_RECORDS;

    private String categoryFilter = "all";
    private String query = "";
    private int sortMode;
    private EditText searchField;
    private TextView searchClear;
    private LinearLayout listHost;
    private ScrollView listScroll;
    private final Map<String, TextView> categoryChips = new LinkedHashMap<>();
    private boolean firstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.setupWindow(this);
        store = Store.get(this);
        sortMode = Prefs.sortMode(this);
        if (savedInstanceState != null) {
            tab = savedInstanceState.getInt("tab", TAB_RECORDS);
            categoryFilter = savedInstanceState.getString("category", "all");
            query = savedInstanceState.getString("query", "");
        }
        buildScaffold();
        renderTab();
        if (!TextUtils.isEmpty(store.lastLoadError())) {
            Toast.makeText(this, store.lastLoadError(), Toast.LENGTH_LONG).show();
        }
        if (savedInstanceState == null) handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
        } else {
            renderTab();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", tab);
        outState.putString("category", categoryFilter);
        outState.putString("query", query);
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }

    private void buildScaffold() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        contentHost = new FrameLayout(this);
        root.addView(contentHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(buildBottomNav(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(Ui.NAV_HEIGHT)));
        setContentView(root);
    }

    private View buildBottomNav() {
        LinearLayout wrap = Ui.column(this);
        wrap.setBackgroundColor(Ui.SURFACE);
        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        wrap.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        LinearLayout row = Ui.row(this);
        row.setPadding(dp(12), dp(6), dp(12), dp(6));
        wrap.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        String[] names = {"记录", "统计", "设置"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            TextView item = Ui.text(this, names[i], 12.5f,
                    i == tab ? Ui.ACCENT_DEEP : Ui.MUTED, i == tab);
            item.setGravity(Gravity.CENTER);
            item.setBackground(Ui.tappable(this,
                    i == tab ? Ui.ACCENT_SOFT : Color.TRANSPARENT, 11,
                    Color.TRANSPARENT, 0));
            item.setOnClickListener(v -> switchTab(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            row.addView(item, lp);
            navLabels[i] = item;
        }
        return wrap;
    }

    private void switchTab(int index) {
        if (index == tab) return;
        tab = index;
        renderTab();
    }

    private void updateNav() {
        for (int i = 0; i < 3; i++) {
            boolean selected = i == tab;
            navLabels[i].setTextColor(selected ? Ui.ACCENT_DEEP : Ui.MUTED);
            navLabels[i].setTypeface(selected ? Ui.sansMedium()
                    : Typeface.create("sans-serif", Typeface.NORMAL));
            navLabels[i].setBackground(Ui.tappable(this,
                    selected ? Ui.ACCENT_SOFT : Color.TRANSPARENT, 11,
                    Color.TRANSPARENT, 0));
        }
    }

    private void renderTab() {
        if (contentHost == null) return;
        contentHost.removeAllViews();
        updateNav();
        View page;
        if (tab == TAB_RECORDS) page = buildRecordsScreen();
        else if (tab == TAB_STATS) page = buildStatsScreen();
        else page = buildSettingsScreen();
        contentHost.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ------------------------------------------------------------------
    // 记录
    // ------------------------------------------------------------------

    private View buildRecordsScreen() {
        List<Item> all = store.all();
        LinearLayout screen = Ui.column(this);
        screen.setBackgroundColor(Ui.BG);

        LinearLayout header = Ui.column(this);
        header.setPadding(dp(18), dp(14), dp(18), dp(12));

        LinearLayout titleRow = Ui.row(this);
        TextView title = Ui.text(this, "记录", 24, Ui.INK, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView sort = Ui.text(this, "排序", 12, Ui.INK_SOFT, true);
        sort.setGravity(Gravity.CENTER);
        sort.setPadding(dp(11), dp(8), dp(11), dp(8));
        sort.setBackground(Ui.tappable(this, Ui.SURFACE, 11, Ui.LINE, 1));
        sort.setOnClickListener(this::showSortMenu);
        LinearLayout.LayoutParams sortLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sortLp.rightMargin = dp(7);
        titleRow.addView(sort, sortLp);

        TextView add = Ui.text(this, "+ 新增", 12, Ui.WHITE, true);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(13), dp(8), dp(13), dp(8));
        add.setBackground(Ui.tappable(this, Ui.ACCENT, 11, Color.TRANSPARENT, 0));
        add.setOnClickListener(v -> openEditor(null));
        titleRow.addView(add);
        header.addView(titleRow);

        TextView summary = Ui.text(this, summaryText(all), 11.5f, Ui.MUTED, false);
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryLp.topMargin = dp(6);
        header.addView(summary, summaryLp);

        if (!all.isEmpty()) {
            header.addView(buildSearchBox(), topParams(12));
            header.addView(buildCategoryChips(all), topParams(10));
        }
        screen.addView(header);
        screen.addView(Ui.divider(this));

        listScroll = new ScrollView(this);
        listScroll.setFillViewport(true);
        listScroll.setClipToPadding(false);
        listScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        listHost = Ui.column(this);
        listHost.setPadding(dp(18), dp(12), dp(18), dp(24));
        listScroll.addView(listHost, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        screen.addView(listScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        refreshList();
        return screen;
    }

    private LinearLayout.LayoutParams topParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private String summaryText(List<Item> all) {
        if (all.isEmpty()) return "0 条记录";
        int rated = 0;
        int favorites = 0;
        double sum = 0;
        for (Item item : all) {
            if (item.rating > 0) {
                rated++;
                sum += item.rating;
            }
            if (item.favorite) favorites++;
        }
        String avg = rated == 0 ? "—" : String.format(Locale.CHINA, "%.1f", sum / rated);
        return all.size() + " 条记录 · 均分 " + avg + " · 收藏 " + favorites;
    }

    private View buildSearchBox() {
        LinearLayout box = Ui.row(this);
        box.setBackground(Ui.rounded(this, Ui.SURFACE, 12));
        box.setPadding(dp(12), dp(2), dp(8), dp(2));
        android.graphics.drawable.GradientDrawable background =
                Ui.rounded(this, Ui.SURFACE, 12);
        background.setStroke(dp(1), Ui.LINE);
        box.setBackground(background);

        searchField = new EditText(this);
        searchField.setHint("搜索记录");
        searchField.setTextSize(14);
        searchField.setTextColor(Ui.INK);
        searchField.setHintTextColor(Ui.FAINT);
        searchField.setSingleLine(true);
        searchField.setBackground(null);
        searchField.setPadding(0, 0, dp(8), 0);
        searchField.setMinHeight(dp(42));
        searchField.setInputType(InputType.TYPE_CLASS_TEXT);
        searchField.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchField.setText(query);
        box.addView(searchField, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        searchClear = Ui.text(this, "清除", 11, Ui.MUTED, false);
        searchClear.setGravity(Gravity.CENTER);
        searchClear.setPadding(dp(8), dp(8), dp(8), dp(8));
        searchClear.setVisibility(TextUtils.isEmpty(query) ? View.GONE : View.VISIBLE);
        searchClear.setOnClickListener(v -> {
            if (searchField != null) searchField.setText("");
        });
        box.addView(searchClear);

        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s == null ? "" : s.toString().trim();
                if (searchClear != null) {
                    searchClear.setVisibility(TextUtils.isEmpty(query) ? View.GONE : View.VISIBLE);
                }
                refreshList();
            }
        });
        return box;
    }

    private View buildCategoryChips(List<Item> all) {
        categoryChips.clear();
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout row = Ui.row(this);

        TextView allChip = Ui.chip(this, "全部 " + all.size(), "all".equals(categoryFilter));
        allChip.setOnClickListener(v -> {
            categoryFilter = "all";
            updateCategoryChips();
            refreshList();
        });
        row.addView(allChip);
        categoryChips.put("all", allChip);

        for (Cat cat : Cat.values()) {
            int count = 0;
            for (Item item : all) if (cat.code.equals(item.category)) count++;
            TextView chip = Ui.chip(this, cat.label + (count > 0 ? " " + count : ""),
                    cat.code.equals(categoryFilter));
            chip.setOnClickListener(v -> {
                categoryFilter = cat.code;
                updateCategoryChips();
                refreshList();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(7);
            row.addView(chip, lp);
            categoryChips.put(cat.code, chip);
        }
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void updateCategoryChips() {
        for (Map.Entry<String, TextView> entry : categoryChips.entrySet()) {
            Ui.updateChip(entry.getValue(), entry.getKey().equals(categoryFilter));
        }
    }

    private void refreshList() {
        if (listHost == null) return;
        listHost.removeAllViews();
        String q = query == null ? "" : query.toLowerCase(Locale.CHINA);
        List<Item> visible = new ArrayList<>();
        for (Item item : store.sorted(sortMode)) {
            if (!"all".equals(categoryFilter) && !categoryFilter.equals(item.category)) continue;
            if (!q.isEmpty()) {
                String haystack = (item.title + " " + item.altTitle + " " + item.creator + " "
                        + item.genre + " " + item.platform + " " + item.review + " "
                        + item.progress + " " + (item.year > 0 ? item.year : ""))
                        .toLowerCase(Locale.CHINA);
                if (!haystack.contains(q)) continue;
            }
            visible.add(item);
        }
        if (visible.isEmpty()) {
            listHost.addView(buildEmptyState(store.count() == 0,
                    !"all".equals(categoryFilter) || !q.isEmpty()));
            return;
        }
        for (Item item : visible) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(9);
            listHost.addView(buildRecordCard(item), lp);
        }
    }

    private View buildEmptyState(boolean noRecords, boolean filtered) {
        LinearLayout box = Ui.column(this);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(16), dp(56), dp(16), dp(30));
        TextView title = Ui.text(this, filtered ? "无匹配记录" : "暂无记录",
                16, Ui.INK, true);
        title.setGravity(Gravity.CENTER);
        box.addView(title);
        TextView action = Ui.primaryButton(this,
                filtered ? "清除筛选" : "新增记录", () -> {
                    if (filtered) {
                        query = "";
                        categoryFilter = "all";
                        if (searchField != null) searchField.setText("");
                        renderTab();
                    } else {
                        openEditor(null);
                    }
                });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(160),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        box.addView(action, lp);
        return box;
    }

    private View buildRecordCard(Item item) {
        Cat cat = item.cat();
        LinearLayout card = Ui.row(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(11), dp(11), dp(12), dp(11));
        card.setBackground(Ui.tappable(this, Ui.SURFACE, 16, Ui.LINE, 1));
        card.setOnClickListener(v -> openDetail(item.id));

        PosterView poster = new PosterView(this);
        poster.setData(cat, item.title);
        card.addView(poster, new LinearLayout.LayoutParams(dp(58), dp(82)));

        LinearLayout info = Ui.column(this);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoLp.leftMargin = dp(12);
        card.addView(info, infoLp);

        LinearLayout top = Ui.row(this);
        top.setGravity(Gravity.TOP);
        TextView title = Ui.text(this, item.title, 15.5f, Ui.INK, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout rating = Ui.column(this);
        rating.setGravity(Gravity.END);
        TextView score = Ui.text(this,
                item.rating > 0 ? Item.formatRating(item.rating) : "未评",
                item.rating > 0 ? 18 : 10.5f,
                item.rating > 0 ? Ui.ACCENT_DEEP : Ui.FAINT,
                item.rating > 0);
        score.setGravity(Gravity.END);
        rating.addView(score);
        if (item.rating > 0) {
            TextView unit = Ui.text(this, "分", 9, Ui.FAINT, false);
            unit.setGravity(Gravity.END);
            rating.addView(unit);
        }
        LinearLayout.LayoutParams ratingLp = new LinearLayout.LayoutParams(dp(42),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ratingLp.leftMargin = dp(8);
        top.addView(rating, ratingLp);
        info.addView(top);

        LinearLayout metaRow = Ui.row(this);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(Ui.rounded(this, cat.color, 3.5f));
        metaRow.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
        TextView meta = Ui.text(this, cat.label + " · " + item.st().label
                + (item.year > 0 ? " · " + item.year : ""), 11, Ui.MUTED, false);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        metaLp.leftMargin = dp(6);
        metaRow.addView(meta, metaLp);
        if (item.favorite) {
            metaRow.addView(Ui.text(this, "★", 10, Ui.ACCENT, false));
        }
        LinearLayout.LayoutParams metaRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaRowLp.topMargin = dp(5);
        info.addView(metaRow, metaRowLp);

        if (!TextUtils.isEmpty(item.review)) {
            TextView review = Ui.text(this, item.review, 11.5f, Ui.INK_SOFT, false);
            review.setSingleLine(true);
            review.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams reviewLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            reviewLp.topMargin = dp(5);
            info.addView(review, reviewLp);
        }
        return card;
    }

    private void showSortMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().setGroupCheckable(0, true, true);
        menu.getMenu().add(0, 0, 0, "最近更新").setChecked(sortMode == 0);
        menu.getMenu().add(0, 1, 1, "评分").setChecked(sortMode == 1);
        menu.getMenu().add(0, 2, 2, "标题").setChecked(sortMode == 2);
        menu.setOnMenuItemClickListener(item -> {
            sortMode = item.getItemId();
            Prefs.setSortMode(this, sortMode);
            refreshList();
            return true;
        });
        menu.show();
    }

    // ------------------------------------------------------------------
    // 统计
    // ------------------------------------------------------------------

    private View buildStatsScreen() {
        List<Item> all = store.all();
        int rated = 0;
        int favorites = 0;
        double sum = 0;
        for (Item item : all) {
            if (item.rating > 0) {
                rated++;
                sum += item.rating;
            }
            if (item.favorite) favorites++;
        }
        String avg = rated == 0 ? "—" : String.format(Locale.CHINA, "%.1f", sum / rated);

        LinearLayout page = Ui.column(this);
        page.setBackgroundColor(Ui.BG);
        page.setPadding(dp(18), dp(14), dp(18), dp(18));

        LinearLayout header = Ui.row(this);
        header.addView(Ui.text(this, "统计", 24, Ui.INK, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        page.addView(header);
        page.addView(Ui.space(this, 14));

        LinearLayout summary = card();
        LinearLayout metrics = Ui.row(this);
        metrics.setGravity(Gravity.CENTER_VERTICAL);
        metrics.addView(metric(String.valueOf(all.size()), "记录"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        metrics.addView(metric(String.valueOf(rated), "已评分"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        metrics.addView(metric(avg, "均分"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        metrics.addView(metric(String.valueOf(favorites), "收藏"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        summary.addView(metrics);
        page.addView(summary);
        page.addView(Ui.space(this, 12));

        LinearLayout categoryCard = card();
        categoryCard.addView(sectionTitle("分类"));
        LinearLayout grid = Ui.column(this);
        Cat[] cats = Cat.values();
        for (int rowIndex = 0; rowIndex < 4; rowIndex++) {
            LinearLayout row = Ui.row(this);
            for (int col = 0; col < 2; col++) {
                int index = rowIndex * 2 + col;
                if (index >= cats.length) break;
                Cat cat = cats[index];
                int count = 0;
                for (Item item : all) if (cat.code.equals(item.category)) count++;
                LinearLayout cell = Ui.row(this);
                cell.setGravity(Gravity.CENTER_VERTICAL);
                cell.setPadding(0, dp(5), 0, dp(5));
                View dot = new View(this);
                dot.setBackground(Ui.rounded(this, cat.color, 3.5f));
                cell.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
                TextView name = Ui.text(this, cat.label, 12, Ui.INK_SOFT, false);
                LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                nameLp.leftMargin = dp(7);
                cell.addView(name, nameLp);
                cell.addView(Ui.text(this, String.valueOf(count), 11, Ui.MUTED, false));
                LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (col == 0) cellLp.rightMargin = dp(12);
                row.addView(cell, cellLp);
            }
            grid.addView(row);
        }
        categoryCard.addView(grid);
        page.addView(categoryCard);
        page.addView(Ui.space(this, 12));

        LinearLayout scoreCard = card();
        scoreCard.addView(sectionTitle("评分"));
        String[] labels = {"9–10", "8–8.9", "7–7.9", "6–6.9", "≤5.9"};
        int[] counts = new int[5];
        for (Item item : all) {
            if (item.rating <= 0) continue;
            int index = ratingBucket(item.rating);
            counts[index]++;
        }
        int max = 1;
        for (int c : counts) if (c > max) max = c;
        for (int i = 0; i < labels.length; i++) {
            LinearLayout row = Ui.row(this);
            row.setPadding(0, dp(4), 0, dp(4));
            TextView label = Ui.text(this, labels[i], 11, Ui.INK_SOFT, false);
            row.addView(label, new LinearLayout.LayoutParams(dp(50),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            HBarView bar = new HBarView(this);
            bar.setColors(Ui.ACCENT, Ui.SURFACE_ALT);
            bar.setFraction(counts[i] * 1f / max);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0, dp(6), 1f);
            row.addView(bar, barLp);
            TextView count = Ui.text(this, String.valueOf(counts[i]), 11, Ui.MUTED, false);
            count.setGravity(Gravity.END);
            LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(dp(26),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            countLp.leftMargin = dp(8);
            row.addView(count, countLp);
            scoreCard.addView(row);
        }
        page.addView(scoreCard);
        page.addView(Ui.space(this, 12));

        LinearLayout statusCard = card();
        statusCard.addView(sectionTitle("状态"));
        LinearLayout statusRow = Ui.row(this);
        for (Status st : Status.values()) {
            int count = 0;
            for (Item item : all) if (st.code.equals(item.status)) count++;
            LinearLayout cell = Ui.column(this);
            cell.setGravity(Gravity.CENTER);
            cell.addView(Ui.text(this, String.valueOf(count), 17, st.color, true));
            TextView label = Ui.text(this, st.label, 10, Ui.MUTED, false);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dp(3);
            cell.addView(label, labelLp);
            statusRow.addView(cell, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        statusCard.addView(statusRow);
        page.addView(statusCard);
        page.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private int ratingBucket(double rating) {
        if (rating >= 9) return 0;
        if (rating >= 8) return 1;
        if (rating >= 7) return 2;
        if (rating >= 6) return 3;
        return 4;
    }

    private LinearLayout metric(String value, String label) {
        LinearLayout box = Ui.column(this);
        box.setGravity(Gravity.CENTER);
        box.addView(Ui.text(this, value, 20, Ui.INK, true));
        TextView caption = Ui.text(this, label, 10, Ui.MUTED, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        box.addView(caption, lp);
        return box;
    }

    private TextView sectionTitle(String text) {
        TextView title = Ui.text(this, text, 12, Ui.INK_SOFT, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        title.setLayoutParams(lp);
        return title;
    }

    private LinearLayout card() {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.rounded(this, Ui.SURFACE, 16));
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        return card;
    }

    // ------------------------------------------------------------------
    // 设置
    // ------------------------------------------------------------------

    private View buildSettingsScreen() {
        LinearLayout page = Ui.column(this);
        page.setBackgroundColor(Ui.BG);
        page.setPadding(dp(18), dp(14), dp(18), dp(18));

        LinearLayout header = Ui.row(this);
        header.addView(Ui.text(this, "设置", 24, Ui.INK, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        page.addView(header);

        TextView summary = Ui.text(this,
                store.count() + " 条记录 · " + store.sizeLabel() + " · 仅本机",
                11.5f, Ui.MUTED, false);
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryLp.topMargin = dp(6);
        page.addView(summary, summaryLp);
        page.addView(Ui.space(this, 18));

        LinearLayout dataCard = card();
        dataCard.addView(sectionTitle("备份"));
        LinearLayout dataRow = Ui.row(this);
        TextView export = Ui.secondaryButton(this, "导出 JSON", this::exportBackup);
        dataRow.addView(export, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView importData = Ui.primaryButton(this, "导入 JSON", this::importBackup);
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        importLp.leftMargin = dp(8);
        dataRow.addView(importData, importLp);
        dataCard.addView(dataRow);
        page.addView(dataCard);
        page.addView(Ui.space(this, 12));

        LinearLayout importCard = card();
        importCard.addView(sectionTitle("资料导入"));
        LinearLayout importRow = Ui.row(this);
        LinearLayout importTexts = Ui.column(this);
        importTexts.addView(Ui.text(this, "豆瓣 / 小黑盒", 13, Ui.INK, true));
        importRow.addView(importTexts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView importAction = Ui.secondaryButton(this, "导入", this::openImportEditor);
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(dp(90),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        importRow.addView(importAction, actionLp);
        importCard.addView(importRow);
        page.addView(importCard);
        page.addView(Ui.space(this, 12));

        LinearLayout prefCard = card();
        LinearLayout prefRow = Ui.row(this);
        prefRow.addView(Ui.text(this, "联网获取资料", 13, Ui.INK, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch networkSwitch = new Switch(this);
        networkSwitch.setChecked(Prefs.onlineImport(this));
        networkSwitch.setShowText(false);
        networkSwitch.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
        networkSwitch.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Ui.alpha(Ui.ACCENT, 0.45f), Ui.alpha(Ui.FAINT, 0.5f)}));
        networkSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                Prefs.setOnlineImport(this, isChecked));
        prefRow.addView(networkSwitch);
        prefCard.addView(prefRow);
        page.addView(prefCard);
        page.addView(Ui.space(this, 12));

        LinearLayout aboutCard = card();
        LinearLayout versionRow = Ui.row(this);
        versionRow.addView(Ui.text(this, "版本", 12.5f, Ui.INK_SOFT, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        versionRow.addView(Ui.text(this, "1.1.0", 12.5f, Ui.MUTED, false));
        aboutCard.addView(versionRow);
        aboutCard.addView(Ui.divider(this));
        TextView clear = Ui.text(this, "清空全部记录", 12.5f, Ui.RED, true);
        clear.setPadding(0, dp(12), 0, dp(2));
        clear.setOnClickListener(v -> confirmClear());
        aboutCard.addView(clear);
        page.addView(aboutCard);
        page.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private void openEditor(String id) {
        Intent intent = new Intent(this, EditActivity.class);
        if (!TextUtils.isEmpty(id)) intent.putExtra(EditActivity.EXTRA_ID, id);
        startActivity(intent);
    }

    private void openImportEditor() {
        Intent intent = new Intent(this, EditActivity.class);
        intent.putExtra(EditActivity.EXTRA_OPEN_IMPORT, true);
        startActivity(intent);
    }

    private void openDetail(String id) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra(DetailActivity.EXTRA_ID, id);
        startActivity(intent);
    }

    private void exportBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, Ui.backupFileName());
        try {
            startActivityForResult(intent, REQUEST_EXPORT);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "没有可用的文件管理器", Toast.LENGTH_LONG).show();
        }
    }

    private void importBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/json", "text/plain", "application/octet-stream"});
        try {
            startActivityForResult(intent, REQUEST_IMPORT);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "没有可用的文件管理器", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) {
            try {
                BackupManager.writeUri(this, uri, store.exportJson());
                Toast.makeText(this, "已导出", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_IMPORT) {
            importFromUri(uri);
        }
    }

    private void importFromUri(Uri uri) {
        final BackupManager.ParseResult result;
        try {
            result = BackupManager.parse(BackupManager.readUri(this, uri));
        } catch (Exception e) {
            Toast.makeText(this, "读取失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if (result.items == null) {
            showErrorDialog("无法导入", result.error);
            return;
        }
        if (result.items.isEmpty()) {
            showErrorDialog("没有记录", "备份文件中没有可导入的记录。");
            return;
        }
        String message = "发现 " + result.items.size() + " 条记录。"
                + "\n\n合并会更新相同记录；覆盖会清空当前记录。";
        if (!TextUtils.isEmpty(result.error)) message += "\n\n" + result.error;
        new AlertDialog.Builder(this)
                .setTitle("导入备份")
                .setMessage(message)
                .setPositiveButton("合并", (d, which) -> doMerge(result.items, false))
                .setNeutralButton("覆盖", (d, which) -> confirmReplace(result.items))
                .setNegativeButton("取消", null)
                .show();
    }

    private void doMerge(List<Item> items, boolean replace) {
        Store.MergeStats stats = store.merge(items, replace);
        Toast.makeText(this, "已导入：" + stats.summary(), Toast.LENGTH_SHORT).show();
        renderTab();
    }

    private void confirmReplace(List<Item> items) {
        new AlertDialog.Builder(this)
                .setTitle("覆盖全部记录？")
                .setMessage("将删除当前 " + store.count() + " 条记录，并恢复备份中的 "
                        + items.size() + " 条记录。")
                .setPositiveButton("覆盖", (d, w) -> doMerge(items, true))
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmClear() {
        if (store.count() == 0) {
            Toast.makeText(this, "暂无记录", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("清空全部记录？")
                .setMessage("此操作不可恢复。")
                .setPositiveButton("清空", (d, w) -> {
                    store.clear();
                    renderTab();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(TextUtils.isEmpty(message) ? "未知错误" : message)
                .setPositiveButton("确定", null)
                .show();
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (TextUtils.isEmpty(text)) text = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            intent.setAction(null);
            setIntent(intent);
            if (!TextUtils.isEmpty(text)) startImportEditor(text);
        } else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            String url = intent.getData().toString();
            intent.setAction(null);
            setIntent(intent);
            startImportEditor(url);
        }
    }

    private void startImportEditor(String text) {
        Intent edit = new Intent(this, EditActivity.class);
        edit.putExtra(EditActivity.EXTRA_IMPORT_TEXT, text);
        edit.putExtra(EditActivity.EXTRA_AUTO_IMPORT, true);
        startActivity(edit);
    }
}
