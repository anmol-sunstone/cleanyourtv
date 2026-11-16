package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import java.time.LocalDate;

/**
 * The Christmas skin's tree: a small fir standing in the snow at the back of the dock's top edge, near its right
 * end, where the plough drives past in front of it; on the clock screensaver it stands the same way on the snow
 * along the bottom of the screen, drawn larger. It is dressed a little more as Christmas comes nearer
 * ({@link #decorations}): fairy lights that twinkle and baubles that do not. Its star is lit from Christmas Day
 * ({@link #starLit}), and presents lie underneath from then until New Year's Eve ({@link NewYear#presents}).
 * Snow that lands on it lies on its branches, drawn thicker the more there is. Like the plough it is drawn from shapes, so there is no picture to decode.
 */
final class ChristmasTree {

    /** The tree's footprint and height, dp; {@link SnowDrift} keeps the same outline for the flakes. */
    static final float WIDTH = SnowDrift.TREE_W, HEIGHT = SnowDrift.TREE_H;
    /** How many lights and baubles a fully dressed tree carries. */
    static final int DECORATIONS = 30;
    /** A tree is never quite bare while the skin is on. */
    static final int FEWEST = 6;

    /** Each tier of branches: where its tip and its skirt are above the ground, and half its width, dp. */
    private static final float[] TIP = {52f, 68f, 82f, 93f}, SKIRT = {10f, 32f, 52f, 68f}, HALF = {32f, 25f, 18f, 12f};
    private static final int[] GREEN = {0xFF14502B, 0xFF1C6A3A, 0xFF24854A, 0xFF2E9E59};
    private static final int[] LIGHT = {0xFFFFE9A8, 0xFFFF6B5E, 0xFF7FD0FF, 0xFFFFC46B};

    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path[] tier = new Path[TIP.length];
    private final Path star = new Path(), cap = new Path();
    private final float[] dx = new float[DECORATIONS], dy = new float[DECORATIONS];

    ChristmasTree() {
        for (int k = 0; k < tier.length; k++) {
            Path p = new Path();
            p.moveTo(0f, -TIP[k]);
            p.lineTo(HALF[k], -SKIRT[k]);
            p.quadTo(HALF[k] * 0.5f, -SKIRT[k] + 5f, 0f, -SKIRT[k]);
            p.quadTo(-HALF[k] * 0.5f, -SKIRT[k] + 5f, -HALF[k], -SKIRT[k]);
            p.close();
            tier[k] = p;
        }
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * i / 5.0 - Math.PI / 2.0;
            float r = i % 2 == 0 ? 6.2f : 2.6f;
            float x = r * (float) Math.cos(a), y = -HEIGHT + r * (float) Math.sin(a);
            if (i == 0) star.moveTo(x, y);
            else star.lineTo(x, y);
        }
        star.close();
        // The decorations' places, fixed: spread up the tree in an order that fills it evenly as their number grows.
        for (int i = 0; i < DECORATIONS; i++) {
            float up = 15f + (i * 37 % DECORATIONS) * 68f / DECORATIONS;
            float room = 0.78f * 32f * (1f - (up - 10f) / 86f);
            dy[i] = -up;
            // the golden ratio's steps never bunch up, so neighbours in height land on different sides
            dx[i] = (float) ((i * 0.6180339887 + 0.31) % 1.0 * 2.0 - 1.0) * room;
        }
    }

    /**
     * How many lights and baubles the tree carries on this day: {@link #FEWEST} until sixty days before
     * Christmas, then one more every two days, all {@link #DECORATIONS} from Christmas Day until the end of January.
     */
    static int decorations(LocalDate today) {
        if (starLit(today)) return DECORATIONS;
        long left = LocalDate.of(today.getYear(), 12, 25).toEpochDay() - today.toEpochDay();
        if (left > 60) return FEWEST;
        return Math.max(FEWEST, Math.round(DECORATIONS * (60 - left) / 60f));
    }

    /** From Christmas Day until the end of January the star is lit. */
    static boolean starLit(LocalDate today) {
        return today.getMonthValue() == 1 || (today.getMonthValue() == 12 && today.getDayOfMonth() >= 25);
    }

    /**
     * Draws the tree with its trunk's foot at ({@code x}, {@code ground}), px.
     *
     * @param load  how much snow lies on its branches, 0 to 1
     * @param count how many of its decorations are on it
     * @param lit   the star is lit
     * @param gifts the presents are out underneath
     * @param secs  a running clock for the lights' twinkle
     */
    void draw(Canvas c, float x, float ground, float density, float load, int count, boolean lit, boolean gifts,
              float secs) {
        c.save();
        c.translate(x, ground);
        c.scale(density, density);

        if (gifts) presents(c);
        ink.setColor(0xFF5B3A1E);
        c.drawRect(-4f, -12f, 4f, 1f, ink);
        for (int k = 0; k < tier.length; k++) {
            ink.setColor(GREEN[k]);
            c.drawPath(tier[k], ink);
            if (load > 0.04f) snow(c, k, load);
        }

        int n = Math.min(count, DECORATIONS);
        for (int i = 0; i < n; i++) {
            if (i % 2 == 0) {
                float glow = 0.55f + 0.45f * (float) Math.sin(secs * (0.9f + unit(i + 40) * 1.4f) + i * 1.7f);
                int colour = LIGHT[(i / 2) % LIGHT.length];
                ink.setColor(colour);
                ink.setAlpha(Math.round(70 * glow));
                c.drawCircle(dx[i], dy[i], 3.8f, ink);
                ink.setAlpha(Math.round(120 + 135 * glow));
                c.drawCircle(dx[i], dy[i], 1.5f, ink);
            } else {
                ink.setColor(i % 4 == 1 ? 0xFFD9362B : 0xFFF2B33D);
                c.drawCircle(dx[i], dy[i], 2.5f, ink);
                ink.setColor(0xB4FFFFFF);
                c.drawCircle(dx[i] - 0.8f, dy[i] - 0.8f, 0.7f, ink);
            }
        }

        if (lit) {
            ink.setColor(0x50FFD75E);
            c.drawCircle(0f, -HEIGHT, 11f, ink);
        }
        ink.setColor(lit ? 0xFFFFD75E : 0xFF9A7A2A);
        c.drawPath(star, ink);
        c.restore();
    }

    /** Snow on one tier: a white cap from its tip down both slopes, reaching further the more snow there is. */
    private void snow(Canvas c, int k, float load) {
        float reach = 0.2f + 0.5f * Math.min(1f, load);
        float half = HALF[k] * reach, y = -TIP[k] + (TIP[k] - SKIRT[k]) * reach;
        cap.rewind();
        cap.moveTo(0f, -TIP[k] - 0.6f);
        cap.lineTo(half + 0.8f, y);
        cap.quadTo(half * 0.5f, y + 3.4f, 0f, y - 1.2f);
        cap.quadTo(-half * 0.5f, y + 3.4f, -half - 0.8f, y);
        cap.close();
        ink.setColor(0xF4FFFFFF);
        c.drawPath(cap, ink);
    }

    private void presents(Canvas c) {
        ink.setColor(0xFFC8322B);
        c.drawRect(-31f, -9f, -19f, 1f, ink);
        ink.setColor(0xFFF2B33D);
        c.drawRect(-26f, -9f, -24f, 1f, ink);
        ink.setColor(0xFF2E8B4E);
        c.drawRect(-17f, -6.5f, -8f, 1f, ink);
        ink.setColor(0xFFF2B33D);
        c.drawRect(11f, -10f, 23f, 1f, ink);
        ink.setColor(0xFFC8322B);
        c.drawRect(16f, -10f, 18f, 1f, ink);
    }

    /** A fixed pseudo-random value in 0..1 for the i-th decoration. */
    private static float unit(int i) {
        int v = i * 0x9E3779B1 + 0x7F4A7C15;
        v ^= v >>> 15;
        v *= 0x2C1B3C6D;
        v ^= v >>> 12;
        return (v & 0xFFFF) / 65535f;
    }
}
