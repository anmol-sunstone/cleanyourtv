package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.Random;

/**
 * Fireworks for the turn of the year, drawn by {@link SnowfallView} over Home and over the clock screensaver. A
 * rocket climbs from behind the dock (on the screensaver, from the snow along the bottom) against gravity, trailing sparks, and bursts just before the top of its climb into a shell of sparks that
 * fly outwards, slow in the air, sink and fade, flickering as they die. A burst lights the snow on the dock
 * below it for a moment. Everything lives in fixed arrays: nothing is allocated while they fly.
 */
final class Fireworks {

    private static final int ROCKETS = 8, SPARKS = 520;
    /** Gravity on a climbing rocket and on a spark, dp/s²; a spark is light and the air slows it quickly. */
    private static final float ROCKET_GRAVITY = 300f, SPARK_GRAVITY = 70f, SPARK_DRAG = 1.9f;
    private static final int[] COLOURS = {0xFFFFD75E, 0xFFFF5A4D, 0xFF7CE38B, 0xFF8FD3FF, 0xFFFF8AD8, 0xFFFFF4D6};

    private final float density;
    private final Random rnd = new Random();
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG), tail = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final boolean[] rOn = new boolean[ROCKETS];
    private final float[] rx = new float[ROCKETS], ry = new float[ROCKETS], rvx = new float[ROCKETS],
            rvy = new float[ROCKETS], rFuse = new float[ROCKETS];

    private final float[] sx = new float[SPARKS], sy = new float[SPARKS], svx = new float[SPARKS],
            svy = new float[SPARKS], sAge = new float[SPARKS], sLife = new float[SPARKS];
    private final int[] sColour = new int[SPARKS];

    /** The last burst's light, 1 fading to 0, where it was and its colour. */
    private float flash, flashX, flashY;
    private int flashColour;
    private int flying;

    Fireworks(float density) {
        this.density = density;
        tail.setStyle(Paint.Style.STROKE);
        tail.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < SPARKS; i++) sLife[i] = -1f;
    }

    /** Whether anything is in the air or still glowing. */
    boolean busy() {
        return flying > 0 || flash > 0.01f;
    }

    /**
     * Sends a rocket up from ({@code x}, {@code from}) to burst near height {@code apex}, px. With every rocket
     * in the air it is not sent.
     */
    void launch(float x, float from, float apex) {
        for (int i = 0; i < ROCKETS; i++) {
            if (rOn[i]) continue;
            float climb = Math.max(40f, (from - apex) / density);
            float up = (float) Math.sqrt(2f * ROCKET_GRAVITY * climb);
            rOn[i] = true;
            rx[i] = x;
            ry[i] = from;
            rvx[i] = (rnd.nextFloat() - 0.5f) * 50f;
            rvy[i] = -up;
            rFuse[i] = up / ROCKET_GRAVITY * 0.9f;
            return;
        }
    }

    private void burst(float x, float y) {
        int colour = COLOURS[rnd.nextInt(COLOURS.length)];
        boolean ring = rnd.nextInt(4) == 0;
        float power = 210f + rnd.nextFloat() * 110f;
        int made = 0;
        for (int i = 0; i < SPARKS && made < 64; i++) {
            if (sLife[i] >= 0f) continue;
            made++;
            double a = rnd.nextFloat() * 2.0 * Math.PI;
            float speed = power * (ring ? 0.92f + 0.08f * rnd.nextFloat() : 0.3f + 0.7f * (float) Math.sqrt(rnd.nextFloat()));
            sx[i] = x;
            sy[i] = y;
            svx[i] = speed * (float) Math.cos(a);
            svy[i] = speed * (float) Math.sin(a);
            sAge[i] = 0f;
            sLife[i] = 1.5f + rnd.nextFloat() * 0.8f;
            sColour[i] = made % 5 == 0 ? 0xFFFFFFFF : colour;
        }
        flash = 1f;
        flashX = x;
        flashY = y;
        flashColour = colour;
    }

    /**
     * Flies everything on by {@code dt} seconds and draws it. {@code left}, {@code right} and {@code top} are the
     * dock's top edge, px, lit by a burst; a {@code right} not beyond {@code left} means there is no dock to light.
     */
    void step(Canvas c, float dt, float left, float right, float top) {
        flying = 0;
        for (int i = 0; i < ROCKETS; i++) {
            if (!rOn[i]) continue;
            rvy[i] += ROCKET_GRAVITY * dt;
            rx[i] += rvx[i] * dt * density;
            ry[i] += rvy[i] * dt * density;
            rFuse[i] -= dt;
            if (rFuse[i] <= 0f) {
                rOn[i] = false;
                burst(rx[i], ry[i]);
                continue;
            }
            flying++;
            tail.setColor(0x96FFE2A0);
            tail.setStrokeWidth(1.4f * density);
            c.drawLine(rx[i], ry[i], rx[i] - rvx[i] * 0.05f * density, ry[i] - rvy[i] * 0.05f * density, tail);
            dot.setColor(0xFFFFF4D6);
            c.drawCircle(rx[i], ry[i], 1.7f * density, dot);
        }

        if (flash > 0.01f) {
            flash = Math.max(0f, flash - 4f * dt);
            dot.setColor(flashColour);
            dot.setAlpha(Math.round(46 * flash));
            c.drawCircle(flashX, flashY, 70f * density, dot);
            if (right > left) {
                dot.setAlpha(Math.round(70 * flash));
                c.drawRect(left, top - 14f * density, right, top, dot);
            }
        }

        float drag = (float) Math.exp(-SPARK_DRAG * dt);
        tail.setStrokeWidth(1.5f * density);
        for (int i = 0; i < SPARKS; i++) {
            if (sLife[i] < 0f) continue;
            sAge[i] += dt;
            float t = sAge[i] / sLife[i];
            if (t >= 1f) {
                sLife[i] = -1f;
                continue;
            }
            flying++;
            svx[i] *= drag;
            svy[i] = svy[i] * drag + SPARK_GRAVITY * dt;
            sx[i] += svx[i] * dt * density;
            sy[i] += svy[i] * dt * density;
            float glow = 1f - t * t;
            if (t > 0.7f) glow *= 0.55f + 0.45f * (float) Math.sin(sAge[i] * 42f + i);
            int alpha = Math.round(255 * Math.max(0f, glow));
            tail.setColor(sColour[i]);
            tail.setAlpha(alpha * 2 / 3);
            c.drawLine(sx[i], sy[i], sx[i] - svx[i] * 0.11f * density, sy[i] - svy[i] * 0.11f * density, tail);
            dot.setColor(sColour[i]);
            dot.setAlpha(alpha);
            c.drawCircle(sx[i], sy[i], 1.9f * density, dot);
        }
    }
}
