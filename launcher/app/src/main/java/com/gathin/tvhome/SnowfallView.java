package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * The Christmas skin's snow: a transparent full-screen overlay where snowflakes drift down for as long as Home is
 * on screen, from {@link #fall} until {@link #stop}. Each flake keeps its own speed, size and sway and starts
 * again at the top when it reaches the bottom. Like {@link PetalShowerView} and {@link BatFlightView} it takes no
 * focus, clicks or accessibility and allocates nothing per frame; the flakes are soft white dots, so there is no
 * picture to decode. It draws about 30 times a second, not on every display frame, and not at all while stopped.
 */
public final class SnowfallView extends View {

    private static final double TAU = 2 * Math.PI;
    private static final long FRAME_MS = 33;

    private final float density;
    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int count;
    private float[] x0 = new float[0], period = new float[0], offset = new float[0], size = new float[0],
            sway = new float[0], swayHz = new float[0], phase = new float[0];

    private boolean falling;
    private long start;
    private final Runnable frame = this::invalidate;

    public SnowfallView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        paint.setColor(0xFFFFFFFF);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
    }

    /** Starts {@code flakes} snowflakes falling, already spread down the screen; does nothing if they are falling. */
    public void fall(int flakes) {
        if (falling) return;
        int n = Math.max(0, flakes);
        if (n != count) {
            ensureCapacity(n);
            count = n;
            for (int i = 0; i < n; i++) {
                x0[i] = rnd.nextFloat();                          // fraction of the width
                size[i] = 1.3f + rnd.nextFloat() * rnd.nextFloat() * 3.6f;   // dp radius; mostly small, a few big
                period[i] = 20f - size[i] * 2.2f + rnd.nextFloat() * 6f;     // seconds top to bottom; big ones are nearer and faster
                offset[i] = rnd.nextFloat() * period[i];          // so the screen starts full, not empty
                sway[i] = 6f + rnd.nextFloat() * 20f;             // dp of side-to-side drift
                swayHz[i] = 0.12f + rnd.nextFloat() * 0.25f;
                phase[i] = rnd.nextFloat() * (float) TAU;
            }
            start = SystemClock.uptimeMillis();
        }
        falling = true;
        invalidate();
    }

    /** Stops the snow and clears it from the screen; {@link #fall} carries on from where the flakes were. */
    public void stop() {
        if (!falling) return;
        falling = false;
        removeCallbacks(frame);
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(frame);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!falling) return;
        float secs = (SystemClock.uptimeMillis() - start) / 1000f;
        float w = getWidth(), h = getHeight();
        for (int i = 0; i < count; i++) {
            float p = ((secs + offset[i]) / period[i]) % 1f;
            float r = size[i] * density;
            float y = -r + (h + 2f * r) * p;
            float x = x0[i] * w + sway[i] * density * (float) Math.sin(TAU * swayHz[i] * secs + phase[i]);
            // the big flakes are nearer and softer, the small ones crisp
            paint.setAlpha(size[i] > 3.2f ? 130 : 200);
            c.drawCircle(x, y, r, paint);
        }
        removeCallbacks(frame);
        postDelayed(frame, FRAME_MS);
    }

    private void ensureCapacity(int n) {
        if (x0.length >= n) return;
        x0 = new float[n];
        period = new float[n];
        offset = new float[n];
        size = new float[n];
        sway = new float[n];
        swayHz = new float[n];
        phase = new float[n];
    }
}
