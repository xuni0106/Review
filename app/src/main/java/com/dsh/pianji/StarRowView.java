package com.dsh.pianji;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** 十分制评分对应的五颗星，支持半颗星显示。 */
public class StarRowView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path star = new Path();
    private float rating = 0f;
    private int fillColor = Ui.ACCENT;
    private int outlineColor = Ui.LINE;

    public StarRowView(Context c) {
        super(c);
        setWillNotDraw(false);
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, Ui.dp(c, 1)));
    }

    public void setRating(float rating) {
        this.rating = Math.max(0f, Math.min(10f, rating));
        setContentDescription("评分 " + Item.formatRating(this.rating) + " / 10");
        invalidate();
    }

    public void setColors(int fillColor, int outlineColor) {
        this.fillColor = fillColor;
        this.outlineColor = outlineColor;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int h = getHeight();
        float size = Math.min(h, getWidth() / 5.8f);
        if (size <= 0) return;
        float gap = (getWidth() - size * 5) / 5.5f;
        float top = (h - size) / 2f;
        for (int i = 0; i < 5; i++) {
            float left = i * (size + gap);
            star.reset();
            buildStar(star, left, top, size);
            stroke.setColor(outlineColor);
            canvas.drawPath(star, stroke);
            float amount = Math.max(0f, Math.min(1f, (rating - i * 2f) / 2f));
            if (amount > 0f) {
                canvas.save();
                canvas.clipRect(left, 0, left + size * amount, getHeight());
                fill.setColor(fillColor);
                canvas.drawPath(star, fill);
                canvas.restore();
            }
        }
    }

    private void buildStar(Path p, float left, float top, float size) {
        float cx = left + size / 2f;
        float cy = top + size * 0.46f;
        float outer = size * 0.47f;
        float inner = outer * 0.44f;
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(-90 + i * 36);
            float r = (i % 2 == 0) ? outer : inner;
            float x = cx + (float) (Math.cos(angle) * r);
            float y = cy + (float) (Math.sin(angle) * r);
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.close();
    }
}
