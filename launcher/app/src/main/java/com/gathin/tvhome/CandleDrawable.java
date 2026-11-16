package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * A Christmas candle for the network pill while the Christmas skin is on. Its flame follows the connection the
 * way the diya's and the jack-o'-lantern's do: it burns while the TV is online, taller with a stronger signal
 * (1 to 4), flickers a little while started, burns steady on Ethernet and goes out, leaving a wisp of smoke, when
 * the connection drops. It is an Advent candle too: full height until 1 December, a little shorter each day until
 * the 25th. Drawn with paths, no pictures. Frames run at about 15 fps only while the flame burns or fades; nothing
 * is allocated per frame.
 */
public final class CandleDrawable extends Drawable implements FestiveLamp {

    private static final float BOX_DP = 24f;
    private static final long FRAME_MS = 66, FADE_MS = 350;
    /** The flame's size at each level; index 0 is out. */
    private static final float[] LEVEL_FLAME = {0f, 0.62f, 0.76f, 0.9f, 1f};

    private static final int WAX = 0xFFC62F36, WAX_SHADE = 0xFF8E1B24, WAX_TOP = 0xFFF4E4D0;
    private static final int HOLDER = 0xFFE2B04A, HOLDER_SHADE = 0xFFA97A22;
    private static final int WICK = 0xFF2A2222, SMOKE = 0x8CC9CED6;
    private static final int FLAME_OUTER = 0xFFFF9A2E, FLAME_INNER = 0xFFFFE9A6, HALO = 0xFFFFB648;

    private final float density;
    /** How much of the candle is left, 1 (new) down to 0 (Christmas Day). */
    private final float left;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[] noise = new float[64];
    private final long epoch = SystemClock.uptimeMillis();

    private int level = 4;
    private boolean primed, running, steady, reduced;
    private float flameFrom = 1f, flameTo = 1f;
    private long fadeStart;
    private int alpha = 255;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidateSelf();
            if (wantsFrames()) scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_MS);
        }
    };

    /** @param left how much of the candle is left, see {@link Festival#candleLeft}. */
    public CandleDrawable(float density, float left) {
        this.density = density;
        this.left = Math.max(0f, Math.min(1f, left));
        Random rnd = new Random(0xCA7D1EL);
        for (int i = 0; i < noise.length; i++) noise[i] = rnd.nextFloat();
    }

    @Override
    public void setFlame(int newLevel) {
        int lvl = Math.max(0, Math.min(4, newLevel));
        long now = SystemClock.uptimeMillis();
        if (!primed || reduced) {
            primed = true;
            level = lvl;
            flameFrom = flameTo = LEVEL_FLAME[lvl];
            kick();
            return;
        }
        if (lvl == level) return;
        flameFrom = flame(now);
        flameTo = LEVEL_FLAME[lvl];
        fadeStart = now;
        level = lvl;
        kick();
    }

    @Override
    public void setSteady(boolean steady) {
        if (this.steady == steady) return;
        this.steady = steady;
        invalidateSelf();
    }

    @Override
    public void setReducedMotion(boolean reducedMotion) {
        if (reduced == reducedMotion) return;
        reduced = reducedMotion;
        if (reduced) flameFrom = flameTo;
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
        flameFrom = flameTo;
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

    private boolean fading(long now) {
        return flameFrom != flameTo && now - fadeStart < FADE_MS;
    }

    private boolean wantsFrames() {
        return running && isVisible() && !reduced && (level > 0 || fading(SystemClock.uptimeMillis()));
    }

    private void kick() {
        unscheduleSelf(tick);
        if (wantsFrames()) scheduleSelf(tick, SystemClock.uptimeMillis() + FRAME_MS);
        invalidateSelf();
    }

    /** The flame's size now, easing from the last level to the new one. */
    private float flame(long now) {
        float p = Math.min(1f, Math.max(0f, (now - fadeStart) / (float) FADE_MS));
        float e = 1f - (1f - p) * (1f - p);
        return flameFrom + (flameTo - flameFrom) * e;
    }

    /** Smooth noise in 0..1 that changes {@code hz} times a second. */
    private float noise(long now, float hz, int offset) {
        float x = (now - epoch) / 1000f * hz;
        int i = (int) x;
        float f = x - i;
        float s = f * f * (3f - 2f * f);
        float a = noise[(i + offset) & 63], b = noise[(i + offset + 1) & 63];
        return a + (b - a) * s;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        Rect b = getBounds();
        float u = b.width() / BOX_DP;                 // one dp of the 24 dp box
        float cx = b.exactCenterX();
        float base = b.top + 22.5f * u;               // bottom of the holder
        float waxBottom = base - 2.2f * u;
        float waxH = (5f + 5.5f * left) * u;          // 10.5 dp new, 5 dp on Christmas Day
        float waxTop = waxBottom - waxH;
        float half = 3.4f * u;

        // holder: a shallow gold dish
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(HOLDER_SHADE));
        rect.set(cx - 7.2f * u, base - 2.6f * u, cx + 7.2f * u, base);
        canvas.drawRoundRect(rect, 1.3f * u, 1.3f * u, paint);
        paint.setColor(withAlpha(HOLDER));
        rect.set(cx - 7.2f * u, base - 2.6f * u, cx + 7.2f * u, base - 1.1f * u);
        canvas.drawRoundRect(rect, 0.75f * u, 0.75f * u, paint);

        // wax: red, a darker right side, a cream melted top with one drip
        paint.setColor(withAlpha(WAX));
        rect.set(cx - half, waxTop, cx + half, waxBottom);
        canvas.drawRoundRect(rect, 1.1f * u, 1.1f * u, paint);
        paint.setColor(withAlpha(WAX_SHADE));
        rect.set(cx + half - 2.1f * u, waxTop + 1.2f * u, cx + half, waxBottom);
        canvas.drawRoundRect(rect, 1.1f * u, 1.1f * u, paint);
        paint.setColor(withAlpha(WAX_TOP));
        rect.set(cx - half, waxTop, cx + half, waxTop + 2f * u);
        canvas.drawRoundRect(rect, 1f * u, 1f * u, paint);
        rect.set(cx - half + 1.1f * u, waxTop + 0.8f * u, cx - half + 2.7f * u, waxTop + 4.4f * u);
        canvas.drawRoundRect(rect, 0.8f * u, 0.8f * u, paint);

        // wick
        paint.setColor(withAlpha(WICK));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(0.9f * u);
        float wickTop = waxTop - 1.7f * u;
        canvas.drawLine(cx, waxTop + 0.3f * u, cx, wickTop, paint);

        float f = flame(now);
        if (f <= 0.02f) {
            // out: a thin wisp of smoke above the wick
            paint.setColor(withAlpha(SMOKE));
            paint.setStrokeWidth(0.8f * u);
            path.rewind();
            path.moveTo(cx, wickTop - 0.6f * u);
            path.cubicTo(cx + 1.6f * u, wickTop - 2.2f * u, cx - 1.6f * u, wickTop - 3.6f * u, cx + 0.5f * u, wickTop - 5.4f * u);
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
            return;
        }
        paint.setStyle(Paint.Style.FILL);

        boolean alive = running && !reduced && level > 0;
        float breath, lean;
        if (!alive) {
            breath = 1f;
            lean = 0f;
        } else if (steady) {
            breath = 0.97f + 0.03f * (float) Math.sin((now - epoch) / 1000f * 1.6f);
            lean = 0f;
        } else {
            breath = 0.86f + 0.14f * noise(now, 7f, 0);
            lean = (noise(now, 3.1f, 17) - 0.5f) * 1.5f * u;
        }
        // The flame may only grow into the room above this candle: a new candle leaves about 7 dp.
        float room = wickTop - b.top - 0.4f * u;
        float h = Math.min(room, 8.2f * u) * f * breath;
        float w = Math.min(2.9f * u, h * 0.46f);
        float fy = wickTop + 0.5f * u;                // the flame's base sits around the wick's tip

        // glow
        paint.setColor(HALO);
        paint.setAlpha(Math.round(alpha * 0.20f * f * breath));
        canvas.drawCircle(cx, fy - h * 0.45f, h * 0.95f, paint);
        paint.setAlpha(Math.round(alpha * 0.22f * f * breath));
        canvas.drawCircle(cx, fy - h * 0.45f, h * 0.62f, paint);

        paint.setColor(withAlpha(FLAME_OUTER));
        teardrop(cx, fy, w, h, lean);
        canvas.drawPath(path, paint);
        paint.setColor(withAlpha(FLAME_INNER));
        teardrop(cx, fy - h * 0.06f, w * 0.52f, h * 0.58f, lean * 0.5f);
        canvas.drawPath(path, paint);
    }

    /** A flame shape: round at the bottom (centre x, bottom y), a point at the top that leans by {@code lean}. */
    private void teardrop(float x, float bottom, float halfWidth, float height, float lean) {
        path.rewind();
        path.moveTo(x + lean, bottom - height);
        path.cubicTo(x + halfWidth * 0.5f + lean * 0.5f, bottom - height * 0.62f,
                x + halfWidth * 1.25f, bottom - height * 0.3f, x + halfWidth * 0.82f, bottom - height * 0.12f);
        path.cubicTo(x + halfWidth * 0.55f, bottom + height * 0.04f, x - halfWidth * 0.55f, bottom + height * 0.04f,
                x - halfWidth * 0.82f, bottom - height * 0.12f);
        path.cubicTo(x - halfWidth * 1.25f, bottom - height * 0.3f,
                x - halfWidth * 0.5f + lean * 0.5f, bottom - height * 0.62f, x + lean, bottom - height);
        path.close();
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
