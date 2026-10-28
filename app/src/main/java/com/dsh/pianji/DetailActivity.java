package com.dsh.pianji;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 记录详情：内容可滚动，操作栏固定。 */
public class DetailActivity extends Activity {
    public static final String EXTRA_ID = "detail_item_id";

    private Store store;
    private String itemId;
    private Item item;
    private LinearLayout root;
    private boolean firstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.setupWindow(this);
        store = Store.get(this);
        itemId = getIntent().getStringExtra(EXTRA_ID);
        item = store.get(itemId);
        if (item == null) {
            Toast.makeText(this, "记录不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        Item fresh = store.get(itemId);
        if (fresh == null) {
            finish();
            return;
        }
        item = fresh;
        buildUi();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private void buildUi() {
        root.removeAllViews();
        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.divider(this));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout page = Ui.column(this);
        page.setPadding(dp(18), dp(14), dp(18), dp(28));
        scroll.addView(page, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        page.addView(buildItemCard());
        page.addView(Ui.space(this, 12));
        page.addView(buildRatingCard());
        page.addView(Ui.space(this, 12));
        page.addView(buildReviewCard());
        if (!TextUtils.isEmpty(item.description)) {
            page.addView(Ui.space(this, 12));
            page.addView(buildDescriptionCard());
        }
        page.addView(Ui.space(this, 12));
        page.addView(buildInfoCard());
        if (!TextUtils.isEmpty(item.link)) {
            page.addView(Ui.space(this, 12));
            page.addView(buildLinkCard());
        }
        page.addView(Ui.space(this, 16));
        TextView delete = Ui.text(this, "删除记录", 12.5f, Ui.RED, true);
        delete.setGravity(Gravity.CENTER);
        delete.setPadding(dp(12), dp(14), dp(12), dp(14));
        delete.setOnClickListener(v -> confirmDelete());
        page.addView(delete);
    }

    private View buildTopBar() {
        LinearLayout bar = Ui.row(this);
        bar.setPadding(dp(18), dp(9), dp(18), dp(9));
        TextView back = Ui.iconButton(this, "←", this::finish);
        back.setContentDescription("返回");
        bar.addView(back);
        bar.addView(new View(this), new LinearLayout.LayoutParams(
                0, 1, 1f));

        TextView favorite = Ui.iconButton(this, item.favorite ? "★" : "☆",
                this::toggleFavorite);
        favorite.setTextColor(item.favorite ? Ui.ACCENT : Ui.INK_SOFT);
        favorite.setContentDescription(item.favorite ? "取消收藏" : "收藏");
        bar.addView(favorite);

        TextView share = compactAction("分享", false, this::shareItem);
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shareLp.leftMargin = dp(7);
        bar.addView(share, shareLp);

        TextView edit = compactAction("编辑", true, this::openEditor);
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        editLp.leftMargin = dp(7);
        bar.addView(edit, editLp);
        return bar;
    }

    private TextView compactAction(String label, boolean primary, Runnable action) {
        TextView button = Ui.text(this, label, 12, primary ? Ui.WHITE : Ui.INK, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), dp(9), dp(14), dp(9));
        button.setBackground(primary
                ? Ui.tappable(this, Ui.ACCENT, 12, Color.TRANSPARENT, 0)
                : Ui.tappable(this, Ui.SURFACE, 12, Ui.LINE, 1));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private View buildItemCard() {
        LinearLayout card = baseCard();
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        PosterView poster = new PosterView(this);
        poster.setData(item.cat(), item.title);
        row.addView(poster, new LinearLayout.LayoutParams(dp(76), dp(108)));

        LinearLayout info = Ui.column(this);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoLp.leftMargin = dp(14);
        row.addView(info, infoLp);

        LinearLayout metaTop = Ui.row(this);
        metaTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView category = Ui.text(this, item.cat().label, 10.5f, item.cat().color, true);
        metaTop.addView(category);
        TextView status = Ui.text(this, " · " + item.st().label, 10.5f,
                item.st().color, false);
        metaTop.addView(status);
        info.addView(metaTop);

        TextView title = Ui.text(this, item.title, 22, Ui.INK, true);
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(7);
        info.addView(title, titleLp);

        if (!TextUtils.isEmpty(item.altTitle)) {
            TextView alt = Ui.text(this, item.altTitle, 11, Ui.MUTED, false);
            alt.setMaxLines(2);
            alt.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams altLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            altLp.topMargin = dp(5);
            info.addView(alt, altLp);
        }

        String meta = metaLine();
        if (!TextUtils.isEmpty(meta)) {
            TextView metaView = Ui.text(this, meta, 11, Ui.MUTED, false);
            metaView.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            metaLp.topMargin = dp(8);
            info.addView(metaView, metaLp);
        }
        card.addView(row);
        return card;
    }

    private String metaLine() {
        StringBuilder sb = new StringBuilder();
        if (item.year > 0) sb.append(item.year);
        if (!TextUtils.isEmpty(item.genre)) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(item.genre);
        }
        return sb.toString();
    }

    private View buildRatingCard() {
        LinearLayout card = baseCard();
        LinearLayout labelRow = Ui.row(this);
        labelRow.addView(Ui.text(this, "评分", 12, Ui.MUTED, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(labelRow);

        LinearLayout scoreRow = Ui.row(this);
        scoreRow.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scoreLp.topMargin = dp(5);
        if (item.rating > 0) {
            TextView score = Ui.text(this, Item.formatRating(item.rating), 32, Ui.ACCENT_DEEP, true);
            scoreRow.addView(score);
            TextView outOf = Ui.text(this, " / 10", 11, Ui.MUTED, false);
            LinearLayout.LayoutParams outOfLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            outOfLp.bottomMargin = dp(6);
            scoreRow.addView(outOf, outOfLp);
        } else {
            scoreRow.addView(Ui.text(this, "未评分", 16, Ui.MUTED, true));
        }
        card.addView(scoreRow, scoreLp);

        StarRowView stars = new StarRowView(this);
        stars.setRating((float) item.rating);
        LinearLayout.LayoutParams starLp = new LinearLayout.LayoutParams(dp(112), dp(17));
        starLp.topMargin = dp(6);
        card.addView(stars, starLp);
        return card;
    }

    private View buildReviewCard() {
        LinearLayout card = baseCard();
        LinearLayout labelRow = Ui.row(this);
        labelRow.addView(Ui.text(this, "评价", 12, Ui.MUTED, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView edit = Ui.text(this, "编辑", 11, Ui.ACCENT_DEEP, true);
        edit.setPadding(dp(6), dp(4), 0, dp(4));
        edit.setOnClickListener(v -> openEditor());
        labelRow.addView(edit);
        card.addView(labelRow);
        TextView review = Ui.text(this, TextUtils.isEmpty(item.review) ? "暂无" : item.review,
                TextUtils.isEmpty(item.review) ? 13 : 14,
                TextUtils.isEmpty(item.review) ? Ui.MUTED : Ui.INK_SOFT, false);
        review.setLineSpacing(dp(5), 1f);
        LinearLayout.LayoutParams reviewLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        reviewLp.topMargin = dp(8);
        card.addView(review, reviewLp);
        return card;
    }

    private View buildDescriptionCard() {
        LinearLayout card = baseCard();
        card.addView(Ui.text(this, "简介", 12, Ui.MUTED, true));
        TextView description = Ui.text(this, item.description, 13, Ui.INK_SOFT, false);
        description.setLineSpacing(dp(5), 1f);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = dp(8);
        card.addView(description, descLp);
        return card;
    }

    private View buildInfoCard() {
        LinearLayout card = baseCard();
        card.addView(Ui.text(this, "信息", 12, Ui.MUTED, true));
        LinearLayout info = Ui.column(this);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.topMargin = dp(4);
        if (!TextUtils.isEmpty(item.creator)) addInfoRow(info, "主创", item.creator);
        if (!TextUtils.isEmpty(item.platform)) addInfoRow(info, "平台", item.platform);
        if (!TextUtils.isEmpty(item.progress)) addInfoRow(info, "进度", item.progress);
        if (!TextUtils.isEmpty(item.source) && !"手动记录".equals(item.source)) {
            addInfoRow(info, "来源", item.source);
        }
        addInfoRow(info, "更新", dateLabel(item.updatedAt));
        card.addView(info, infoLp);
        return card;
    }

    private void addInfoRow(LinearLayout parent, String label, String value) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, dp(8), 0, 0);
        row.addView(Ui.text(this, label, 11.5f, Ui.MUTED, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView text = Ui.text(this, value, 12, Ui.INK_SOFT, true);
        text.setGravity(Gravity.END);
        text.setMaxLines(3);
        text.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.7f));
        parent.addView(row);
    }

    private View buildLinkCard() {
        LinearLayout card = baseCard();
        LinearLayout row = Ui.row(this);
        row.addView(Ui.text(this, "链接", 12, Ui.MUTED, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView url = Ui.text(this, item.link, 11.5f, Ui.ACCENT_DEEP, false);
        url.setSingleLine(true);
        url.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        row.addView(url, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 2.2f));
        card.addView(row);
        card.setOnClickListener(v -> openLink());
        return card;
    }

    private LinearLayout baseCard() {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.rounded(this, Ui.SURFACE, 16));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        return card;
    }

    private String dateLabel(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(millis));
    }

    private void toggleFavorite() {
        item.favorite = !item.favorite;
        store.save(item);
        buildUi();
    }

    private void openEditor() {
        Intent intent = new Intent(this, EditActivity.class);
        intent.putExtra(EditActivity.EXTRA_ID, item.id);
        startActivity(intent);
    }

    private void openLink() {
        if (!item.link.startsWith("http://") && !item.link.startsWith("https://")) {
            Toast.makeText(this, "链接无效", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.link)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "没有可用浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareItem() {
        StringBuilder text = new StringBuilder();
        text.append("《").append(item.title).append("》");
        text.append(" · ").append(item.cat().label);
        if (item.rating > 0) text.append(" · ").append(Item.formatRating(item.rating)).append("/10");
        if (!TextUtils.isEmpty(item.review)) text.append("\n\n").append(item.review);
        if (!TextUtils.isEmpty(item.link)) text.append("\n").append(item.link);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text.toString());
        startActivity(Intent.createChooser(send, "分享"));
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("删除记录？")
                .setPositiveButton("删除", (d, w) -> {
                    store.delete(item.id);
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
