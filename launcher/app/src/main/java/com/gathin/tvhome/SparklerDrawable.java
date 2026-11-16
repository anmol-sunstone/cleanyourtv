package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * A sparkler for the network pill in the Christmas skin's New Year days, in place of the Advent candle. It follows
 * the connection the way the candle's flame does: it fizzes while the TV is online, with more and longer sparks
 * for a stronger signal (1 to 4), holds a steady spray on Ethernet, and when the connection drops it is a spent
 * stick with a wisp of smoke. Drawn with lines, no pictures; frames run at about 15 fps only while it fizzes.
 */
public final class SparklerDrawable extends Drawable implements FestiveLamp {

    private static final float BOX_DP = 24f;
    private static final long FRAME_MS = 66;
    private static final int STICK = 0xFFB9BEC8, SPENT = 0xFF4A4650, SMOKE = 0x8CC9CED6;
    private static final int SPARK = 0xFFFFD75E, SPARK_HOT = 0xFFFFF7E0, HALO = 0xFFFFC46B;
    private static final int RAYS = 14;

    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] noise = new float[64];
    private final long epoch = SystemClock.uptimeMillis();

    private int level = 4;
    private boolean running, steady, reduced;
    private int alpha = 255;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidateSelf();
            if (wantsFrames()) scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_MS);
        }
    };

    public SparklerDrawable(float density) {
        this.density = density;
        paint.setStrokeCap(Paint.Cap.ROUND);
        Random rnd = new Random(0x5BA4C1EL);
        for (int i = 0; i < noise.length; i++) noise[i] = rnd.nextFloat();
    }

    @Override
    public void setFlame(int newLevel) {
        int lvl = Math.max(0, Math.min(4, newLevel));
        if (lvl == level) return;
        level = lvl;
        kick();
    }

    @Override
    public void setSteady(boolean steady) {
        if (this.steady == steady) return;
        this.steady = steady;
        kick();
    }

    @Override
    public void setReducedMotion(boolean reducedMotion) {
        if (reduced == reducedMotion) return;
        reduced = reducedMotion;
        kick();
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        kick();
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        kick();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (changed) kick();
        return changed;
    }

    private boolean wantsFrames() {
        return running && isVisible() && !reduced && !steady && level > 0;
    }

    private void kick() {
        unscheduleSelf(tick);
        if (wantsFrames()) scheduleSelf(tick, SystemClock.uptimeMillis() + FRAME_MS);
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        float u = b.width() / BOX_DP;                 // one dp of the 24 dp box
        float footX = b.left + 6.5f * u, footY = b.top + 21.5f * u, tipX = b.left + 14.5f * u, tipY = b.top + 9.5f * u;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.3f * u);
        paint.setColor(withAlpha(STICK));
        canvas.drawLine(footX, footY, tipX, tipY, paint);
        // the coated half of the stick, which is what burns
        paint.setStrokeWidth(2.1f * u);
        paint.setColor(withAlpha(SPENT));
        canvas.drawLine((footX + tipX) / 2f, (footY + tipY) / 2f, tipX, tipY, paint);

        if (level == 0) {
            paint.setStrokeWidth(1.1f * u);
            paint.setColor(withAlpha(SMOKE));
            canvas.drawLine(tipX, tipY - 1.5f * u, tipX + 1.4f * u, tipY - 4.5f * u, paint);
            canvas.drawLine(tipX + 1.4f * u, tipY - 4.5f * u, tipX + 0.2f * u, tipY - 7.5f * u, paint);
            return;
        }

        // which sparks are out changes about 15 times a second; a steady or still sparkler keeps one spray
        int beat = steady || reduced || !running ? 0 : (int) ((SystemClock.uptimeMillis() - epoch) / FRAME_MS);
        float reach = (3.2f + 1.3f * level) * u;
        int rays = 6 + 2 * level;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha((HALO & 0x00FFFFFF) | 0x40000000));
        canvas.drawCircle(tipX, tipY, reach * 0.75f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.9f * u);
        for (int i = 0; i < rays && i < RAYS; i++) {
            float n = noise[(beat * 7 + i * 5) & 63];
            double a = 2.0 * Math.PI * (i + noise[(beat * 3 + i) & 63] * 0.7) / rays;
            float len = reach * (0.45f + 0.55f * n);
            float ex = tipX + len * (float) Math.cos(a), ey = tipY + len * (float) Math.sin(a);
            paint.setColor(withAlpha(SPARK));
            canvas.drawLine(tipX, tipY, ex, ey, paint);
            if (n > 0.55f) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(withAlpha(SPARK_HOT));
                canvas.drawCircle(ex, ey, 0.75f * u, paint);
                paint.setStyle(Paint.Style.STROKE);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(SPARK_HOT));
        canvas.drawCircle(tipX, tipY, 1.5f * u, paint);
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
