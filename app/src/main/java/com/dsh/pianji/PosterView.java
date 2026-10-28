package com.dsh.pianji;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/** 分类色块占位图，仅承载分类识别，不叠加装饰文案。 */
public class PosterView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private Cat cat = Cat.MOVIE;

    public PosterView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    public void setData(Cat cat, String ignoredTitle) {
        this.cat = cat == null ? Cat.MOVIE : cat;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float radius = Math.min(w, h) * 0.15f;
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);

        paint.setShader(new LinearGradient(0, 0, w, h,
                Ui.blend(cat.color, Color.WHITE, 0.10f),
                Ui.blend(cat.color, Color.BLACK, 0.26f), Shader.TileMode.CLAMP));
        canvas.drawRect(rect, paint);
        paint.setShader(null);

        paint.setColor(0x18FFFFFF);
        canvas.drawCircle(w * 0.92f, h * 0.12f, w * 0.56f, paint);
        paint.setColor(0x10FFFFFF);
        canvas.drawCircle(w * 0.10f, h * 0.90f, w * 0.42f, paint);

        paint.setColor(0x26FFFFFF);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, w * 0.012f));
        canvas.drawCircle(w * 0.50f, h * 0.47f, w * 0.31f, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(0xF5FFFFFF);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(Math.min(w, h) * 0.27f);
        float badgeY = h * 0.52f - (paint.ascent() + paint.descent()) / 2f;
        canvas.drawText(cat.badge, w * 0.5f, badgeY, paint);
        canvas.restore();
    }
}
