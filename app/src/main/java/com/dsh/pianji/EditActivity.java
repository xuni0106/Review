package com.dsh.pianji;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 新增/编辑记录。表单按“资料、评分、评价”分区，避免单页堆叠。 */
public class EditActivity extends Activity {
    public static final String EXTRA_ID = "edit_item_id";
    public static final String EXTRA_OPEN_IMPORT = "open_import_dialog";
    public static final String EXTRA_IMPORT_TEXT = "import_text";
    public static final String EXTRA_AUTO_IMPORT = "auto_import";

    private Store store;
    private Item editing;
    private boolean isNew;
    private boolean suppressDirty = false;
    private boolean dirty = false;
    private boolean favorite = false;
    private Cat selectedCat = Cat.MOVIE;
    private Status selectedStatus = Status.DONE;
    private int ratingProgress = 0;
    private String importSource = "手动记录";
    private boolean autoImportStarted = false;

    private EditText titleInput;
    private EditText altTitleInput;
    private EditText yearInput;
    private EditText creatorInput;
    private EditText genreInput;
    private EditText platformInput;
    private EditText progressInput;
    private EditText linkInput;
    private EditText reviewInput;
    private EditText descriptionInput;
    private SeekBar ratingSeek;
    private TextView ratingValue;
    private TextView sourceHint;
    private TextView importStatus;
    private StarRowView ratingStars;
    private Switch favoriteSwitch;
    private HorizontalScrollView categoryScroll;
    private final Map<String, TextView> catChips = new LinkedHashMap<>();
    private final Map<String, TextView> statusChips = new LinkedHashMap<>();
    private final View[] tabPages = new View[3];
    private final TextView[] tabLabels = new TextView[3];
    private final View[] tabIndicators = new View[3];
    private int activeTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.setupWindow(this);
        store = Store.get(this);
        String id = getIntent().getStringExtra(EXTRA_ID);
        isNew = TextUtils.isEmpty(id);
        if (!isNew) {
            Item found = store.get(id);
            if (found == null) {
                Toast.makeText(this, "记录不存在", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            editing = found.copy();
        } else {
            editing = new Item();
            editing.source = "手动记录";
        }
        selectedCat = Cat.fromCode(editing.category);
        selectedStatus = Status.fromCode(editing.status);
        ratingProgress = (int) Math.round(Item.clampRating(editing.rating) * 10);
        favorite = editing.favorite;
        importSource = TextUtils.isEmpty(editing.source) ? "手动记录" : editing.source;
        buildUi();
        populateFields();
        attachDirtyWatchers();
        dirty = false;
        maybeStartRequestedImport();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private void buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.divider(this));
        root.addView(buildTabBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.divider(this));

        FrameLayout host = new FrameLayout(this);
        tabPages[0] = buildDataTab();
        tabPages[1] = buildRatingTab();
        tabPages[2] = buildReviewTab();
        for (View page : tabPages) {
            host.addView(page, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        root.addView(host, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        switchTab(0);
        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout header = Ui.row(this);
        header.setPadding(dp(18), dp(9), dp(18), dp(9));
        TextView back = Ui.iconButton(this, "←", this::attemptClose);
        back.setContentDescription("返回");
        header.addView(back);

        TextView title = Ui.text(this, isNew ? "新增" : "编辑", 18, Ui.INK, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(11);
        header.addView(title, titleLp);

        TextView importButton = Ui.text(this, "导入", 12, Ui.ACCENT_DEEP, true);
        importButton.setGravity(Gravity.CENTER);
        importButton.setPadding(dp(11), dp(8), dp(11), dp(8));
        importButton.setBackground(Ui.tappable(this, Ui.ACCENT_SOFT, 11,
                Color.TRANSPARENT, 0));
        importButton.setOnClickListener(v -> showImportDialog());
        header.addView(importButton);

        TextView save = Ui.text(this, "保存", 12, Ui.WHITE, true);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(16), dp(9), dp(16), dp(9));
        save.setBackground(Ui.tappable(this, Ui.ACCENT, 12, Color.TRANSPARENT, 0));
        save.setOnClickListener(v -> saveAndFinish());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveLp.leftMargin = dp(7);
        header.addView(save, saveLp);
        return header;
    }

    private View buildTabBar() {
        LinearLayout row = Ui.row(this);
        row.setBackgroundColor(Ui.SURFACE);
        String[] names = {"资料", "评分", "评价"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            LinearLayout tab = Ui.column(this);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, dp(11), 0, 0);
            tab.setOnClickListener(v -> switchTab(index));
            TextView label = Ui.text(this, names[i], 12.5f,
                    i == activeTab ? Ui.INK : Ui.MUTED, i == activeTab);
            label.setGravity(Gravity.CENTER);
            tab.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View indicator = new View(this);
            indicator.setBackgroundColor(i == activeTab ? Ui.ACCENT : Color.TRANSPARENT);
            LinearLayout.LayoutParams indicatorLp = new LinearLayout.LayoutParams(dp(22), dp(2));
            indicatorLp.topMargin = dp(9);
            tab.addView(indicator, indicatorLp);
            row.addView(tab, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            tabLabels[i] = label;
            tabIndicators[i] = indicator;
        }
        return row;
    }

    private void switchTab(int index) {
        if (index < 0 || index > 2) return;
        activeTab = index;
        for (int i = 0; i < tabPages.length; i++) {
            if (tabPages[i] != null) {
                tabPages[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
            }
            tabLabels[i].setTextColor(i == index ? Ui.INK : Ui.MUTED);
            tabLabels[i].setTypeface(i == index ? Ui.sansMedium()
                    : android.graphics.Typeface.create("sans-serif",
                    android.graphics.Typeface.NORMAL));
            tabIndicators[i].setBackgroundColor(i == index ? Ui.ACCENT : Color.TRANSPARENT);
        }
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    private ScrollView scrollPage(LinearLayout page) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.addView(page, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private View buildDataTab() {
        LinearLayout page = Ui.column(this);
        page.setPadding(dp(18), dp(14), dp(18), dp(24));

        importStatus = Ui.text(this, "", 11, Ui.ACCENT_DEEP, true);
        importStatus.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.bottomMargin = dp(8);
        page.addView(importStatus, statusLp);

        LinearLayout card = formCard();
        addLabel(card, "标题 *");
        titleInput = compactField("");
        titleInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        card.addView(titleInput);

        addLabel(card, "原名");
        altTitleInput = compactField("可留空");
        card.addView(altTitleInput);

        addLabel(card, "分类");
        card.addView(buildCategoryChips());

        LinearLayout meta = Ui.row(this);
        meta.setGravity(Gravity.TOP);
        LinearLayout yearCol = Ui.column(this);
        addLabel(yearCol, "年份");
        yearInput = compactField("2024");
        yearInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        yearInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        yearCol.addView(yearInput);
        meta.addView(yearCol, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.75f));

        LinearLayout progressCol = Ui.column(this);
        addLabel(progressCol, "进度");
        progressInput = compactField("第 8 集 / 32 集");
        progressCol.addView(progressInput);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f);
        progressLp.leftMargin = dp(10);
        meta.addView(progressCol, progressLp);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = dp(12);
        card.addView(meta, metaLp);

        addLabel(card, "主创");
        creatorInput = compactField("导演、主演或开发商");
        card.addView(creatorInput);

        LinearLayout pair = Ui.row(this);
        pair.setGravity(Gravity.TOP);
        LinearLayout genreCol = Ui.column(this);
        addLabel(genreCol, "类型");
        genreInput = compactField("剧情 / 动画 / RPG");
        genreCol.addView(genreInput);
        pair.addView(genreCol, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout platformCol = Ui.column(this);
        addLabel(platformCol, "平台");
        platformInput = compactField("影院 / Netflix / Steam");
        platformCol.addView(platformInput);
        LinearLayout.LayoutParams platformLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        platformLp.leftMargin = dp(10);
        pair.addView(platformCol, platformLp);
        LinearLayout.LayoutParams pairLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pairLp.topMargin = dp(12);
        card.addView(pair, pairLp);

        addLabel(card, "链接");
        linkInput = compactField("豆瓣 / 小黑盒链接");
        linkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        card.addView(linkInput);

        page.addView(card);
        sourceHint = Ui.text(this, "", 10.5f, Ui.FAINT, false);
        LinearLayout.LayoutParams sourceLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sourceLp.topMargin = dp(10);
        page.addView(sourceHint, sourceLp);
        return scrollPage(page);
    }

    private View buildRatingTab() {
        LinearLayout page = Ui.column(this);
        page.setPadding(dp(18), dp(14), dp(18), dp(24));
        LinearLayout card = formCard();

        LinearLayout heading = Ui.row(this);
        heading.addView(Ui.text(this, "评分", 14, Ui.INK, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(heading);

        LinearLayout scoreRow = Ui.row(this);
        scoreRow.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scoreLp.topMargin = dp(8);
        ratingValue = Ui.text(this, "0", 38, Ui.ACCENT_DEEP, true);
        scoreRow.addView(ratingValue);
        TextView outOf = Ui.text(this, " / 10", 11, Ui.MUTED, false);
        LinearLayout.LayoutParams outOfLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        outOfLp.bottomMargin = dp(8);
        scoreRow.addView(outOf, outOfLp);
        scoreRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        ratingStars = new StarRowView(this);
        scoreRow.addView(ratingStars, new LinearLayout.LayoutParams(dp(120), dp(18)));
        card.addView(scoreRow, scoreLp);

        ratingSeek = new SeekBar(this);
        ratingSeek.setMax(100);
        ratingSeek.setProgress(ratingProgress);
        ratingSeek.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        ratingSeek.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.LINE));
        ratingSeek.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT_DEEP));
        ratingSeek.setPadding(dp(4), 0, dp(4), 0);
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        seekLp.topMargin = dp(6);
        card.addView(ratingSeek, seekLp);
        LinearLayout range = Ui.row(this);
        range.addView(Ui.text(this, "0", 9.5f, Ui.FAINT, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ten = Ui.text(this, "10", 9.5f, Ui.FAINT, false);
        ten.setGravity(Gravity.END);
        range.addView(ten);
        card.addView(range);

        LinearLayout quick = Ui.row(this);
        LinearLayout.LayoutParams quickLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        quickLp.topMargin = dp(8);
        String[] scores = {"6", "7", "8", "9", "10"};
        for (int i = 0; i < scores.length; i++) {
            final int value = Integer.parseInt(scores[i]) * 10;
            TextView chip = Ui.chip(this, scores[i], false);
            chip.setOnClickListener(v -> {
                ratingSeek.setProgress(value);
                markDirty();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(5);
            quick.addView(chip, lp);
        }
        card.addView(quick, quickLp);

        ratingSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ratingProgress = progress;
                updateRatingUi();
                if (fromUser) markDirty();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        card.addView(Ui.space(this, 14));
        card.addView(Ui.divider(this));
        addLabel(card, "状态");
        card.addView(buildStatusChips());
        card.addView(Ui.space(this, 14));
        card.addView(Ui.divider(this));

        LinearLayout favoriteRow = Ui.row(this);
        favoriteRow.setPadding(0, dp(14), 0, 0);
        favoriteRow.addView(Ui.text(this, "收藏", 13, Ui.INK, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        favoriteSwitch = new Switch(this);
        favoriteSwitch.setChecked(favorite);
        favoriteSwitch.setShowText(false);
        favoriteSwitch.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
        favoriteSwitch.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Ui.alpha(Ui.ACCENT, 0.45f), Ui.alpha(Ui.FAINT, 0.5f)}));
        favoriteSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            favorite = isChecked;
            markDirty();
        });
        favoriteRow.addView(favoriteSwitch);
        card.addView(favoriteRow);

        page.addView(card);
        return scrollPage(page);
    }

    private View buildReviewTab() {
        LinearLayout page = Ui.column(this);
        page.setPadding(dp(18), dp(14), dp(18), dp(24));
        LinearLayout card = formCard();

        addLabel(card, "评价");
        reviewInput = Ui.multiline(this, "", 130);
        reviewInput.setMinHeight(dp(150));
        card.addView(reviewInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(160)));

        addLabel(card, "简介");
        descriptionInput = Ui.multiline(this, "", 110);
        card.addView(descriptionInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(130)));
        page.addView(card);
        return scrollPage(page);
    }

    private LinearLayout formCard() {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.rounded(this, Ui.SURFACE, 16));
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        return card;
    }

    private EditText compactField(String hint) {
        EditText input = Ui.singleLine(this, hint);
        input.setTextSize(14);
        return input;
    }

    private void addLabel(LinearLayout parent, String text) {
        TextView label = Ui.text(this, text, 10.5f, Ui.MUTED, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = parent.getChildCount() == 0 ? 0 : dp(12);
        lp.bottomMargin = dp(5);
        parent.addView(label, lp);
    }

    private View buildCategoryChips() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        categoryScroll = scroll;
        LinearLayout row = Ui.row(this);
        catChips.clear();
        boolean first = true;
        for (Cat cat : Cat.values()) {
            TextView chip = Ui.chip(this, cat.label, cat == selectedCat);
            chip.setOnClickListener(v -> {
                selectedCat = cat;
                updateCategoryChips();
                markDirty();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (!first) lp.leftMargin = dp(7);
            first = false;
            row.addView(chip, lp);
            catChips.put(cat.code, chip);
        }
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void updateCategoryChips() {
        for (Map.Entry<String, TextView> entry : catChips.entrySet()) {
            Ui.updateChip(entry.getValue(), entry.getKey().equals(selectedCat.code));
        }
        final TextView selected = catChips.get(selectedCat.code);
        if (selected != null && categoryScroll != null) {
            categoryScroll.post(() -> {
                int target = selected.getLeft() + selected.getWidth() / 2
                        - categoryScroll.getWidth() / 2;
                categoryScroll.smoothScrollTo(Math.max(0, target), 0);
            });
        }
    }

    private View buildStatusChips() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(this);
        statusChips.clear();
        boolean first = true;
        for (Status st : Status.values()) {
            TextView chip = Ui.chip(this, st.label, st == selectedStatus);
            chip.setOnClickListener(v -> {
                selectedStatus = st;
                updateStatusChips();
                markDirty();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (!first) lp.leftMargin = dp(7);
            first = false;
            row.addView(chip, lp);
            statusChips.put(st.code, chip);
        }
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void updateStatusChips() {
        for (Map.Entry<String, TextView> entry : statusChips.entrySet()) {
            Ui.updateChip(entry.getValue(), entry.getKey().equals(selectedStatus.code));
        }
    }

    private void populateFields() {
        suppressDirty = true;
        titleInput.setText(editing.title);
        altTitleInput.setText(editing.altTitle);
        yearInput.setText(editing.year > 0 ? String.valueOf(editing.year) : "");
        creatorInput.setText(editing.creator);
        genreInput.setText(editing.genre);
        platformInput.setText(editing.platform);
        progressInput.setText(editing.progress);
        linkInput.setText(editing.link);
        reviewInput.setText(editing.review);
        descriptionInput.setText(editing.description);
        ratingSeek.setProgress(ratingProgress);
        updateRatingUi();
        updateCategoryChips();
        updateStatusChips();
        favoriteSwitch.setChecked(favorite);
        updateSourceHint();
        suppressDirty = false;
    }

    private void updateSourceHint() {
        if (sourceHint == null) return;
        boolean manual = TextUtils.isEmpty(importSource) || "手动记录".equals(importSource);
        sourceHint.setText(manual ? "" : "来源：" + importSource);
        sourceHint.setVisibility(manual ? View.GONE : View.VISIBLE);
    }

    private void attachDirtyWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                markDirty();
            }
        };
        titleInput.addTextChangedListener(watcher);
        altTitleInput.addTextChangedListener(watcher);
        yearInput.addTextChangedListener(watcher);
        creatorInput.addTextChangedListener(watcher);
        genreInput.addTextChangedListener(watcher);
        platformInput.addTextChangedListener(watcher);
        progressInput.addTextChangedListener(watcher);
        linkInput.addTextChangedListener(watcher);
        reviewInput.addTextChangedListener(watcher);
        descriptionInput.addTextChangedListener(watcher);
    }

    private void markDirty() {
        if (!suppressDirty) dirty = true;
    }

    private void updateRatingUi() {
        if (ratingValue == null) return;
        double rating = ratingProgress / 10d;
        ratingValue.setText(rating <= 0 ? "0" : Item.formatRating(rating));
        ratingStars.setRating((float) rating);
    }

    private void maybeStartRequestedImport() {
        if (autoImportStarted) return;
        Intent intent = getIntent();
        if (intent.getBooleanExtra(EXTRA_OPEN_IMPORT, false)) {
            autoImportStarted = true;
            titleInput.post(this::showImportDialog);
            return;
        }
        String shared = intent.getStringExtra(EXTRA_IMPORT_TEXT);
        if (intent.getBooleanExtra(EXTRA_AUTO_IMPORT, false) && !TextUtils.isEmpty(shared)) {
            autoImportStarted = true;
            titleInput.post(() -> autoImport(shared));
        }
    }

    private void autoImport(String raw) {
        switchTab(0);
        importStatus.setVisibility(View.VISIBLE);
        importStatus.setText("正在获取资料…");
        PlatformImporter.Draft offline = PlatformImporter.parse(raw);
        String url = PlatformImporter.firstUrl(raw);
        if (!TextUtils.isEmpty(url) && Prefs.onlineImport(this) && isNetworkAvailable()) {
            final ProgressDialog pd = new ProgressDialog(this);
            pd.setMessage("正在获取资料…");
            pd.setCancelable(false);
            pd.show();
            new Thread(() -> {
                PlatformImporter.Draft fetched = null;
                String error = null;
                try {
                    fetched = PlatformImporter.fetch(url);
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final PlatformImporter.Draft finalFetched = fetched;
                final String finalError = error;
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    pd.dismiss();
                    PlatformImporter.Draft combined = mergeDrafts(finalFetched, offline);
                    applyDraft(combined);
                    if (finalFetched == null) {
                        importStatus.setText(combined.hasAnything()
                                ? "已填入" : "未识别到资料");
                        if (finalError != null && !TextUtils.isEmpty(combined.title)) {
                            Toast.makeText(this, "未获取到网页资料", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        importStatus.setText("已填入");
                    }
                });
            }, "pianji-import").start();
        } else {
            applyDraft(offline);
            importStatus.setText(offline.hasAnything() ? "已填入" : "未识别到资料");
        }
    }

    private PlatformImporter.Draft mergeDrafts(PlatformImporter.Draft primary,
                                               PlatformImporter.Draft fallback) {
        PlatformImporter.Draft d = new PlatformImporter.Draft();
        if (primary != null) {
            d.title = primary.title;
            d.altTitle = primary.altTitle;
            d.category = primary.category;
            d.creator = primary.creator;
            d.genre = primary.genre;
            d.platform = primary.platform;
            d.progress = primary.progress;
            d.description = primary.description;
            d.link = primary.link;
            d.source = primary.source;
            d.year = primary.year;
            d.rating = primary.rating;
        }
        if (fallback != null) {
            if (TextUtils.isEmpty(d.title)) d.title = fallback.title;
            if (TextUtils.isEmpty(d.altTitle)) d.altTitle = fallback.altTitle;
            if (TextUtils.isEmpty(d.category)) d.category = fallback.category;
            if (TextUtils.isEmpty(d.creator)) d.creator = fallback.creator;
            if (TextUtils.isEmpty(d.genre)) d.genre = fallback.genre;
            if (TextUtils.isEmpty(d.platform)) d.platform = fallback.platform;
            if (TextUtils.isEmpty(d.progress)) d.progress = fallback.progress;
            if (TextUtils.isEmpty(d.description)) d.description = fallback.description;
            if (TextUtils.isEmpty(d.link)) d.link = fallback.link;
            if (TextUtils.isEmpty(d.source)) d.source = fallback.source;
            if (d.year <= 0) d.year = fallback.year;
            if (d.rating <= 0) d.rating = fallback.rating;
        }
        if (fallback != null && isSpecificCategory(fallback.category)) {
            d.category = fallback.category;
        }
        return d;
    }

    private boolean isSpecificCategory(String code) {
        return Cat.KDRAMA.code.equals(code) || Cat.USDRAMA.code.equals(code)
                || Cat.ANIME.code.equals(code) || Cat.SHORT.code.equals(code)
                || Cat.AISHORT.code.equals(code) || Cat.GAME.code.equals(code);
    }

    private void applyDraft(PlatformImporter.Draft d) {
        if (d == null) return;
        suppressDirty = true;
        switchTab(0);
        if (!TextUtils.isEmpty(d.title)) {
            titleInput.setText(d.title);
            titleInput.setSelection(titleInput.getText().length());
        }
        if (!TextUtils.isEmpty(d.altTitle)) altTitleInput.setText(d.altTitle);
        if (d.year > 0) yearInput.setText(String.valueOf(d.year));
        if (!TextUtils.isEmpty(d.creator)) creatorInput.setText(d.creator);
        if (!TextUtils.isEmpty(d.genre)) genreInput.setText(d.genre);
        if (!TextUtils.isEmpty(d.platform)) platformInput.setText(d.platform);
        if (!TextUtils.isEmpty(d.progress)) progressInput.setText(d.progress);
        if (!TextUtils.isEmpty(d.link)) linkInput.setText(d.link);
        if (!TextUtils.isEmpty(d.description)) descriptionInput.setText(d.description);
        if (d.rating > 0) {
            ratingProgress = (int) Math.round(Item.clampRating(d.rating) * 10);
            ratingSeek.setProgress(ratingProgress);
            updateRatingUi();
        }
        Cat imported = categoryFromDraft(d.category);
        if (imported != null) {
            selectedCat = imported;
            updateCategoryChips();
        }
        if (!TextUtils.isEmpty(d.source)) {
            importSource = d.source;
            updateSourceHint();
        }
        suppressDirty = false;
        dirty = true;
    }

    private Cat categoryFromDraft(String value) {
        if (TextUtils.isEmpty(value)) return null;
        Cat byLabel = Cat.fromLabel(value);
        if (byLabel != null) return byLabel;
        for (Cat cat : Cat.values()) {
            if (cat.code.equalsIgnoreCase(value.trim())) return cat;
        }
        return null;
    }

    private void showImportDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = Ui.column(this);
        sheet.setBackground(Ui.rounded(this, Ui.SURFACE, 20));
        sheet.setPadding(dp(20), dp(12), dp(20), dp(20));

        View handle = new View(this);
        handle.setBackground(Ui.rounded(this, Ui.LINE, 2));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(36), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(handle, handleLp);

        TextView title = Ui.text(this, "导入资料", 19, Ui.INK, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(14);
        sheet.addView(title, titleLp);

        EditText input = Ui.multiline(this,
                "粘贴豆瓣 / 小黑盒链接或分享文案", 100);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(130));
        inputLp.topMargin = dp(10);
        sheet.addView(input, inputLp);

        LinearLayout helper = Ui.row(this);
        LinearLayout.LayoutParams helperLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        helperLp.topMargin = dp(4);
        TextView paste = Ui.text(this, "剪贴板", 11, Ui.ACCENT_DEEP, true);
        paste.setPadding(dp(2), dp(7), dp(10), dp(7));
        paste.setOnClickListener(v -> {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip()) {
                    ClipData clip = cm.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        CharSequence cs = clip.getItemAt(0).coerceToText(this);
                        if (!TextUtils.isEmpty(cs)) {
                            input.setText(cs);
                            input.setSelection(input.getText().length());
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        });
        helper.addView(paste, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        helper.addView(Ui.text(this, Prefs.onlineImport(this) ? "联网" : "离线",
                10, Ui.FAINT, false));
        sheet.addView(helper, helperLp);

        LinearLayout progressRow = Ui.row(this);
        progressRow.setVisibility(View.GONE);
        ProgressBar spinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        progressRow.addView(spinner, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView dialogStatus = Ui.text(this, "正在获取…", 11, Ui.MUTED, false);
        LinearLayout.LayoutParams dialogStatusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialogStatusLp.leftMargin = dp(8);
        progressRow.addView(dialogStatus, dialogStatusLp);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressLp.topMargin = dp(8);
        sheet.addView(progressRow, progressLp);

        LinearLayout buttons = Ui.row(this);
        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsLp.topMargin = dp(12);
        TextView cancel = Ui.secondaryButton(this, "取消", dialog::dismiss);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cancelLp.rightMargin = dp(5);
        buttons.addView(cancel, cancelLp);
        TextView parse = Ui.primaryButton(this, "获取", null);
        LinearLayout.LayoutParams parseLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f);
        parseLp.leftMargin = dp(5);
        buttons.addView(parse, parseLp);
        sheet.addView(buttons, buttonsLp);

        parse.setOnClickListener(v -> {
            String raw = input.getText().toString().trim();
            if (TextUtils.isEmpty(raw)) {
                input.setError("请输入链接或分享文案");
                return;
            }
            PlatformImporter.Draft offline = PlatformImporter.parse(raw);
            String url = PlatformImporter.firstUrl(raw);
            if (!TextUtils.isEmpty(url) && Prefs.onlineImport(this) && isNetworkAvailable()) {
                parse.setEnabled(false);
                parse.setAlpha(0.6f);
                cancel.setEnabled(false);
                progressRow.setVisibility(View.VISIBLE);
                new Thread(() -> {
                    PlatformImporter.Draft fetched = null;
                    try {
                        fetched = PlatformImporter.fetch(url);
                    } catch (Exception ignored) {
                    }
                    final PlatformImporter.Draft finalFetched = fetched;
                    runOnUiThread(() -> {
                        if (isFinishing()) return;
                        PlatformImporter.Draft combined = mergeDrafts(finalFetched, offline);
                        applyDraft(combined);
                        importStatus.setVisibility(View.VISIBLE);
                        importStatus.setText(combined.hasAnything() ? "已填入" : "未识别到资料");
                        dialog.dismiss();
                    });
                }, "pianji-import-dialog").start();
            } else {
                applyDraft(offline);
                importStatus.setVisibility(View.VISIBLE);
                importStatus.setText(offline.hasAnything() ? "已填入" : "未识别到资料");
                dialog.dismiss();
            }
        });

        dialog.setContentView(sheet);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                lp.copyFrom(window.getAttributes());
                lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
                lp.gravity = Gravity.BOTTOM;
                window.setAttributes(lp);
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private void saveAndFinish() {
        String title = titleInput.getText().toString().trim();
        if (TextUtils.isEmpty(title)) {
            switchTab(0);
            titleInput.setError("请填写标题");
            titleInput.requestFocus();
            return;
        }
        editing.title = title;
        editing.altTitle = altTitleInput.getText().toString().trim();
        editing.category = selectedCat.code;
        editing.status = selectedStatus.code;
        editing.rating = Item.clampRating(ratingProgress / 10d);
        editing.year = parseYear(yearInput.getText().toString());
        editing.creator = creatorInput.getText().toString().trim();
        editing.genre = genreInput.getText().toString().trim();
        editing.platform = platformInput.getText().toString().trim();
        editing.progress = progressInput.getText().toString().trim();
        editing.link = linkInput.getText().toString().trim();
        editing.review = reviewInput.getText().toString().trim();
        editing.description = descriptionInput.getText().toString().trim();
        editing.favorite = favorite;
        editing.source = TextUtils.isEmpty(importSource) ? "手动记录" : importSource;
        if (editing.createdAt <= 0) editing.createdAt = System.currentTimeMillis();
        store.save(editing);
        dirty = false;
        setResult(RESULT_OK);
        finish();
    }

    private int parseYear(String s) {
        if (TextUtils.isEmpty(s)) return 0;
        String digits = s.replaceAll("[^0-9]", "");
        if (digits.length() > 4) digits = digits.substring(0, 4);
        if (digits.length() != 4) return 0;
        try {
            int y = Integer.parseInt(digits);
            return (y >= 1800 && y <= 2200) ? y : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void attemptClose() {
        if (!dirty) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("放弃修改？")
                .setPositiveButton("放弃", (d, w) -> finish())
                .setNegativeButton("继续", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        attemptClose();
    }
}
