package com.dsh.pianji;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** 圆角横向进度条，用于统计页。 */
public class HBarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float fraction;
    private int color = Ui.ACCENT;
    private int trackColor = Ui.LINE;

    public HBarView(Context c) {
        super(c);
        setWillNotDraw(false);
    }

    public void setFraction(float fraction) {
        this.fraction = Math.max(0f, Math.min(1f, fraction));
        invalidate();
    }

    public void setColors(int color, int trackColor) {
        this.color = color;
        this.trackColor = trackColor;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight();
        float r = h / 2f;
        rect.set(0, 0, getWidth(), h);
        paint.setColor(trackColor);
        canvas.drawRoundRect(rect, r, r, paint);
        if (fraction > 0) {
            paint.setColor(color);
            rect.set(0, 0, Math.max(h, getWidth() * fraction), h);
            canvas.drawRoundRect(rect, r, r, paint);
        }
    }
}
