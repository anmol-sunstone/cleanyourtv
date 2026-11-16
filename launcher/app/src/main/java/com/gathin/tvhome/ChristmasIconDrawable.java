package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A Christmas pill icon, drawn with paths: a gold jingle bell with a holly sprig for Inputs that swings twice
 * when focused, or a snowflake for Settings that turns a sixth of the way round and so lands as it started.
 * The motion plays once per {@link #nudge()}; reduced motion leaves it still.
 */
public final class ChristmasIconDrawable extends Drawable {

    static final int BELL = 0, SNOWFLAKE = 1;

    private static final float BOX_DP = 24f;
    private static final long BELL_MS = 1100, FLAKE_MS = 900;

    private static final int GOLD = 0xFFF2C14E, GOLD_SHADE = 0xFFC08A1E, GOLD_LIGHT = 0xFFFFE7A3;
    private static final int LEAF = 0xFF2E8B4A, LEAF_SHADE = 0xFF1E6B36, BERRY = 0xFFD8323C;
    /** Sky blues rather than white: the focused pill turns white, and a white flake would vanish on it. */
    private static final int ICE = 0xFF8CCBFA, ICE_SHADE = 0xFF4E9FE0;

    private final int kind;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final ValueAnimator animator;
    private float t = -1f;
    private boolean reduced;
    private int alpha = 255;

    public ChristmasIconDrawable(float density, int kind) {
        this.kind = kind;
        this.density = density;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(kind == BELL ? BELL_MS : FLAKE_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidateSelf();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                t = -1f;
                invalidateSelf();
            }
        });
    }

    /** The focus motion: two swings for the bell, a sixth of a turn for the snowflake. */
    public void nudge() {
        if (reduced) return;
        animator.cancel();
        animator.start();
    }

    /** With reduced motion {@link #nudge()} does nothing and a motion in progress stops at rest. */
    public void setReducedMotion(boolean reducedMotion) {
        reduced = reducedMotion;
        if (reduced && animator.isRunning()) animator.cancel();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        float u = b.width() / BOX_DP;
        int save = canvas.save();
        canvas.translate(b.left, b.top);
        if (kind == BELL) drawBell(canvas, u);
        else drawSnowflake(canvas, u);
        canvas.restoreToCount(save);
    }

    private void drawBell(Canvas c, float u) {
        // swings from where it hangs, dying away over two swings
        float swing = t < 0f ? 0f : 16f * (float) Math.sin(t * 2 * Math.PI * 2) * (1f - t);
        float clapper = t < 0f ? 0f : -1.6f * u * (float) Math.sin(t * 2 * Math.PI * 2 + 0.9f) * (1f - t);
        int save = c.save();
        c.rotate(swing, 12f * u, 4.5f * u);

        paint.setStyle(Paint.Style.FILL);
        // clapper, behind the lip
        paint.setColor(withAlpha(GOLD_SHADE));
        c.drawCircle(12f * u + clapper, 20.6f * u, 1.9f * u, paint);
        // body: a dome that flares to the lip
        path.rewind();
        path.moveTo(12f * u, 5.6f * u);
        path.cubicTo(16.6f * u, 5.6f * u, 17.6f * u, 10f * u, 17.9f * u, 14f * u);
        path.cubicTo(18.1f * u, 16.4f * u, 19.4f * u, 17.2f * u, 20.4f * u, 18.6f * u);
        path.lineTo(3.6f * u, 18.6f * u);
        path.cubicTo(4.6f * u, 17.2f * u, 5.9f * u, 16.4f * u, 6.1f * u, 14f * u);
        path.cubicTo(6.4f * u, 10f * u, 7.4f * u, 5.6f * u, 12f * u, 5.6f * u);
        path.close();
        paint.setColor(withAlpha(GOLD));
        c.drawPath(path, paint);
        // lip
        paint.setColor(withAlpha(GOLD_SHADE));
        c.drawRoundRect(3.2f * u, 17.6f * u, 20.8f * u, 19.8f * u, 1.1f * u, 1.1f * u, paint);
        // highlight
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(1.3f * u);
        paint.setColor(withAlpha(GOLD_LIGHT));
        path.rewind();
        path.moveTo(9.6f * u, 8.6f * u);
        path.cubicTo(8.6f * u, 10.2f * u, 8.3f * u, 12.2f * u, 8.2f * u, 14.4f * u);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);

        // holly: two leaves and three berries where the bell hangs
        leaf(c, u, 11.4f, 5.2f, -152f, LEAF);
        leaf(c, u, 12.6f, 5.2f, -28f, LEAF_SHADE);
        paint.setColor(withAlpha(BERRY));
        c.drawCircle(11.1f * u, 5.3f * u, 1.25f * u, paint);
        c.drawCircle(12.9f * u, 5.3f * u, 1.25f * u, paint);
        c.drawCircle(12f * u, 4.1f * u, 1.25f * u, paint);
        c.restoreToCount(save);
    }

    /** A holly leaf 6.5 dp long from (x, y), pointing along {@code degrees}. */
    private void leaf(Canvas c, float u, float x, float y, float degrees, int color) {
        int save = c.save();
        c.translate(x * u, y * u);
        c.rotate(degrees);
        path.rewind();
        path.moveTo(0f, 0f);
        path.quadTo(1.4f * u, -2.4f * u, 2.3f * u, -1.2f * u);
        path.quadTo(3.6f * u, -2.5f * u, 4.5f * u, -1.1f * u);
        path.quadTo(5.6f * u, -1.5f * u, 6.5f * u, 0f);
        path.quadTo(5.6f * u, 1.5f * u, 4.5f * u, 1.1f * u);
        path.quadTo(3.6f * u, 2.5f * u, 2.3f * u, 1.2f * u);
        path.quadTo(1.4f * u, 2.4f * u, 0f, 0f);
        path.close();
        paint.setColor(withAlpha(color));
        c.drawPath(path, paint);
        c.restoreToCount(save);
    }

    private void drawSnowflake(Canvas c, float u) {
        // eases through a sixth of a turn: six-fold, so it comes to rest looking as it did
        float e = t < 0f ? 0f : t * t * (3f - 2f * t);
        int save = c.save();
        c.translate(12f * u, 12f * u);
        c.rotate(60f * e);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 6; i++) {
            paint.setColor(withAlpha(i % 2 == 0 ? ICE : ICE_SHADE));
            paint.setStrokeWidth(1.7f * u);
            c.drawLine(0f, -1.2f * u, 0f, -10f * u, paint);
            paint.setStrokeWidth(1.4f * u);
            c.drawLine(0f, -5.2f * u, 2.6f * u, -7.6f * u, paint);
            c.drawLine(0f, -5.2f * u, -2.6f * u, -7.6f * u, paint);
            c.drawLine(0f, -8f * u, 1.7f * u, -9.6f * u, paint);
            c.drawLine(0f, -8f * u, -1.7f * u, -9.6f * u, paint);
            c.rotate(60f);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(ICE));
        c.drawCircle(0f, 0f, 1.7f * u, paint);
        c.restoreToCount(save);
    }

    private int withAlpha(int argb) {
        int a = ((argb >>> 24) * alpha) / 255;
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    @Override
    public int getIntrinsicWidth() {
        return Math.round(BOX_DP * density);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(BOX_DP * density);
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha == alpha) return;
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter filter) {
        paint.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
