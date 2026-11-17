package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * The Christmas skin's snow: a transparent full-screen overlay where snowflakes drift down for as long as Home, or
 * the clock screensaver, is on screen, from {@link #fall} until {@link #stop}. Each flake keeps its own speed, size and sway and starts
 * again at the top when it reaches the bottom. Like {@link PetalShowerView} and {@link BatFlightView} it takes no
 * focus, clicks or accessibility and allocates nothing per frame; the flakes are soft white dots, so there is no
 * picture to decode. It draws about 30 times a second, not on every display frame, and not at all while stopped.
 *
 * <p>With a {@link SnowDrift} ({@link #collectOn}) every flake that comes down over the dock comes to rest on its
 * top edge, where the dock's decoration draws them as a growing drift; only the flakes beside the dock fall on
 * past it. A flake over the Christmas tree rests on its branches. When the
 * drift is deep enough a little red plough drives along the dock, alternately from the left and from the right.
 * Its blade scrapes the drift, the snow it gathers rides in front of it and is thrown off in arcs: some lands
 * further along and is ploughed again, some falls past the dock, and a few clumps fly at the viewer, hit the
 * inside of the screen and slide down it while they melt. With {@link #santa} Santa drives it. A drift set to {@link SnowDrift#flyby} is cleared by a
 * wizard on a broomstick instead: he skims over it in six seconds, his wake lifts most of the snow as powder
 * that thins away, and at the New Year he trails sparks and crosses the finale's fireworks.
 *
 * <p>On the clock screensaver ({@link #ground}) there is no dock: the view keeps a drift of its own along the
 * bottom of the screen, from edge to edge, and draws it and the tree itself, with the whole scene larger than on
 * Home. At the turn of the year it also draws the fireworks, the countdown and the finale ({@link NewYear}).
 */
public final class SnowfallView extends View {

    private static final double TAU = 2 * Math.PI;
    private static final long FRAME_MS = 33;
    /** Flakes up to this radius are the far ones, drawn crisp; bigger ones are nearer and softer. */
    private static final float FAR = 3.2f;

    /** Gravity for thrown snow, dp/s². Scaled for the eye, as the flakes' own fall is, not for a real truck. */
    private static final float GRAVITY = 520f;
    /** How much thrown snow makes one flying clump, dp². */
    private static final float CLUMP = 12f;
    /** How much larger than its drawing the plough is shown: big enough to read from the sofa. */
    private static final float SCALE = 1.4f;
    /** The plough from the back of its hopper to the front of its blade, dp. */
    private static final float TRUCK = 66f * SCALE;

    private static final int SPRAY = 200;
    private static final byte FREE = 0, FALLS = 1, MAY_LAND = 2, AT_GLASS = 3, FADING = 4, POWDER = 5;
    /** How long a puff of the powder a flyby raises hangs in the air, seconds. */
    private static final float POWDER_LIFE = 2.6f;
    /** How much larger than his drawing the flyer is shown: less on the screensaver, whose whole scene is larger. */
    private float flyer = 2f;
    private static final int SPLATS = 8;
    private static final float SPLAT_LIFE = 5.2f;

    private final float density;
    /**
     * One dp of the snow scene, px: the drift, the tree, the plough and the snow it throws. The display's own
     * density on Home; larger on the screensaver ({@link #ground}), where the scene has the whole screen.
     */
    private float unit;
    /** The screensaver's scene: the drift lies along the bottom of this view, which draws it and the tree itself. */
    private boolean ground;
    private ChristmasTree tree;
    private final Path bank = new Path();
    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int count;
    private float[] x0 = new float[0], period = new float[0], offset = new float[0], size = new float[0],
            sway = new float[0], swayHz = new float[0], phase = new float[0], prevY = new float[0];
    /** The fall (counted from 1) in which flake i landed on the dock: it is not drawn again until the next one. */
    private int[] landed = new int[0];

    private boolean falling;
    /** The first frame after {@link #fall}: the flakes may have jumped, so nothing lands and no time passes. */
    private boolean fresh;
    private long start, last;
    private final Runnable frame = this::invalidate;

    private SnowDrift drift;
    private Runnable driftChanged;
    private int passes, frames;
    private float carry;
    private final int[] origin = new int[2];

    private final byte[] sKind = new byte[SPRAY];
    private final float[] sx = new float[SPRAY], sy = new float[SPRAY], svx = new float[SPRAY],
            svy = new float[SPRAY], sr = new float[SPRAY], sArea = new float[SPRAY], sAge = new float[SPRAY],
            sHit = new float[SPRAY];

    private final float[] bx = new float[SPLATS], by = new float[SPLATS], by0 = new float[SPLATS],
            br = new float[SPLATS], bAge = new float[SPLATS];
    private final int[] bSeed = new int[SPLATS];

    private final Path blade = new Path(), beam = new Path(), heap = new Path();
    private final Path cape = new Path(), hair = new Path(), bristles = new Path(), torso = new Path();
    private final Path hat = new Path();
    /** Santa drives the plough. */
    private boolean santa;
    /** The flyer's extra pass through the finale's fireworks: 0 to 1 across the screen, negative when there is none. */
    private float skyPass = -1f;
    private int skyDir = 1, skyPasses;

    /** The turn of the year ({@link NewYear}): fireworks, the last ten seconds counted down, and the finale. */
    private static final String[] COUNT = {"", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"};
    private Fireworks fireworks;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long skinSecond = -1L, countAt, finaleAt;
    private float launchGap, launchIn = -1f;
    private int volley = 1, countdown;
    private boolean finale;
    private String newYearText = "";
    private Runnable finaleOver;

    public SnowfallView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        unit = density;
        paint.setColor(0xFFFFFFFF);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
        for (int i = 0; i < SPLATS; i++) bAge[i] = -1f;
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setShadowLayer(10f * density, 0f, 2f * density, 0xA0000000);
        try {
            text.setTypeface(context.getResources().getFont(R.font.outfit_light));
        } catch (RuntimeException e) {
            // the system's own typeface will do
        }
        // The plough is drawn in dp with its blade's foot at the origin, facing +x, the ground at y = 0.
        blade.moveTo(2f, 0f);
        blade.quadTo(-5.5f, -7f, 3.5f, -15f);
        blade.lineTo(1.2f, -15.6f);
        blade.quadTo(-9f, -7f, -1.2f, 0f);
        blade.close();
        beam.moveTo(-11f, -13.5f);
        beam.lineTo(48f, -25f);
        beam.lineTo(48f, 0f);
        beam.close();
        // Santa's hat, seen through the cab's window: it tips back, as the wind would have it.
        hat.moveTo(-36.9f, -21.9f);
        hat.quadTo(-36.2f, -24.6f, -33.4f, -24.3f);
        hat.lineTo(-32.3f, -22.1f);
        hat.close();
        hat.moveTo(-36.9f, -21.9f);
        hat.quadTo(-38.6f, -24.4f, -39.2f, -21.6f);
        hat.lineTo(-37.6f, -21.2f);
        hat.close();
        // The flyer is drawn in dp too, facing +x, with the middle of his broomstick at the origin.
        hair.moveTo(4.5f, -29f);
        hair.lineTo(2.6f, -34f);
        hair.lineTo(1.2f, -38f);
        hair.lineTo(4.6f, -37f);
        hair.lineTo(5.2f, -41.2f);
        hair.lineTo(8.4f, -38.6f);
        hair.lineTo(10.4f, -42f);
        hair.lineTo(12.6f, -38.8f);
        hair.lineTo(15.6f, -41f);
        hair.lineTo(16.2f, -37.6f);
        hair.lineTo(19f, -37.2f);
        hair.lineTo(18.2f, -34.2f);
        hair.lineTo(15.4f, -36f);
        hair.lineTo(12.2f, -34.4f);
        hair.lineTo(9.6f, -36f);
        hair.lineTo(7f, -32.6f);
        hair.close();
        // a racing broom's tail: twigs bound tight and swept back to a point
        bristles.moveTo(-33f, 0.6f);
        bristles.quadTo(-44f, -6.5f, -54f, -3.5f);
        bristles.lineTo(-69f, 2.2f);
        bristles.lineTo(-54f, 7.6f);
        bristles.quadTo(-44f, 9.5f, -33f, 4.4f);
        bristles.close();
        torso.moveTo(-9f, 1f);
        torso.lineTo(1f, -23.5f);
        torso.lineTo(10.5f, -21f);
        torso.lineTo(3.5f, 2f);
        torso.close();
    }

    /**
     * The flakes land on this drift from now on, and {@code changed} runs after every frame in which the drift
     * gained or lost snow, so that whoever draws it can draw it again.
     */
    void collectOn(SnowDrift d, Runnable changed) {
        drift = d;
        driftChanged = changed;
    }

    /**
     * The screensaver's scene. The snow collects along the bottom of this view, from edge to edge, with the tree
     * near its right end, and the view draws both itself. With {@code broom} a flyer clears it, not the plough. Everything in the scene is {@code scale} times its size
     * on Home; the plough takes about ten seconds to cross. Call before {@link #fall}.
     */
    /** Puts Santa at the plough's wheel, his sack in the back and antlers on the bonnet. */
    void santa(boolean on) {
        santa = on;
    }

    void ground(float scale, boolean broom) {
        unit = density * scale;
        ground = true;
        drift = new SnowDrift();
        drift.walled(60f);
        drift.flyby(broom);
        flyer = 1.5f;
        tree = new ChristmasTree();
    }

    /** Runs {@code then} once the New Year finale is over, for Home to change into its New Year look. */
    void whenFinaleEnds(Runnable then) {
        finaleOver = then;
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
                // dp radius; mostly small, a few big. The screensaver's snowfall is heavier: more than twice the
                // flakes and a few bigger ones, so its bank fills in about two and a half minutes.
                size[i] = ground ? 1.3f + (float) Math.pow(rnd.nextFloat() * rnd.nextFloat(), 1.5) * 4.8f
                        : 1.3f + rnd.nextFloat() * rnd.nextFloat() * 3.6f;
                period[i] = 20f - size[i] * 2.2f + rnd.nextFloat() * 6f;     // seconds top to bottom; big ones are nearer and faster
                offset[i] = rnd.nextFloat() * period[i];          // so the screen starts full, not empty
                sway[i] = 6f + rnd.nextFloat() * 20f;             // dp of side-to-side drift
                swayHz[i] = 0.12f + rnd.nextFloat() * 0.25f;
                phase[i] = rnd.nextFloat() * (float) TAU;
                landed[i] = 0;
            }
            start = SystemClock.uptimeMillis();
        }
        falling = true;
        fresh = true;
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
        long now = SystemClock.uptimeMillis();
        float dt = fresh ? 0f : Math.min(0.1f, (now - last) / 1000f);
        last = now;
        float secs = (now - start) / 1000f;
        float w = getWidth(), h = getHeight();

        if (ground && w > 0f) {
            getLocationInWindow(origin);
            float floor = h - 5f * density;
            drift.place(origin[0], origin[0] + w, origin[1] + floor, 0f, unit);
            drawGround(c, w, h, floor, secs);
        }
        boolean ledge = drift != null && drift.ledgeShown;
        float l = 0f, r = 0f, top = 0f, corner = 0f;
        if (ledge) {
            corner = drift.ledgeCorner;
            getLocationInWindow(origin);
            l = drift.ledgeLeft - origin[0];
            r = drift.ledgeRight - origin[0];
            top = drift.ledgeTop - origin[1];
        }
        boolean changed = false;

        for (int i = 0; i < count; i++) {
            float span = (secs + offset[i]) / period[i];
            int cycle = (int) span;
            float rad = size[i] * density;
            float y = -rad + (h + 2f * rad) * (span - cycle);
            float before = prevY[i];
            prevY[i] = y;
            if (landed[i] == cycle + 1) continue;
            // a flake comes down somewhere else each time, or the big ones, which come round every few seconds,
            // would each build a heap of their own
            float across = (x0[i] + cycle * 0.6180339887f) % 1f;
            float x = across * w + sway[i] * density * (float) Math.sin(TAU * swayHz[i] * secs + phase[i]);
            if (ledge && !fresh && x > l - corner && x < r + corner) {
                // over a rounded corner the flake still stops on the dock: it joins the snow at that end
                float along = Math.max(0.5f, Math.min(drift.width() - 0.5f, (x - l) / unit));
                float fir = drift.treeTop(along);
                if (fir > 0f) {
                    // over the Christmas tree: the flake comes to rest on its branches
                    float branches = top - fir * unit;
                    if (before < branches && y >= branches) {
                        drift.landOnTree(size[i] * density / unit);
                        landed[i] = cycle + 1;
                        changed = true;
                        continue;
                    }
                }
                float surface = top - drift.depth(along) * unit;
                if (before < surface && y >= surface) {
                    drift.land(along, size[i] * density / unit);
                    landed[i] = cycle + 1;
                    changed = true;
                    continue;
                }
            }
            // the big flakes are nearer and softer, the small ones crisp
            paint.setAlpha(size[i] > FAR ? 130 : 200);
            c.drawCircle(x, y, rad, paint);
        }

        if (drift != null) {
            if (ledge && drift.due()) {
                boolean right = passes++ % 2 == 0;
                // the flyer comes in from well off the screen and leaves it the same way
                float lead = drift.flyby() ? 70f * flyer * unit : 12f * unit;
                float truck = drift.flyby() ? 70f * flyer * unit : TRUCK * unit;
                drift.startPlough(((right ? -lead : w + lead) - l) / unit,
                        ((right ? w + truck : -truck) - l) / unit);
                carry = 0f;
            }
            if (drift.ploughing()) {
                float thrown = drift.advance(dt);
                changed = true;
                if (ledge) {
                    float road = top - SnowDrift.RESIDUE * unit;
                    float bladeX = l + drift.blade() * unit;
                    if (drift.flyby()) {
                        raisePowder(thrown, bladeX, road, drift.direction());
                        float skim = road - (SnowDrift.FLYBY_HEIGHT + 2.5f * (float) Math.sin(secs * 5.2f)) * unit;
                        drawFlyer(c, bladeX, skim, drift.direction(), secs, unit,
                                NewYear.phase(NewYear.today()) != NewYear.CHRISTMAS);
                    } else {
                        throwSnow(thrown, bladeX, road, drift.direction());
                        drawPlough(c, bladeX, road, drift.direction(), secs);
                    }
                }
            }
            if (drift.takeShed() && ledge) slideOffTree(l + drift.treeX() * unit, top);
            // the tree's lights twinkle in the dock's own drawing, so that is redrawn a few times a second
            if (ledge && !ground && drift.hasTree() && ++frames % 10 == 0) changed = true;
            if (stepSpray(c, dt, ledge, l, r, top)) changed = true;
            stepSplats(c, dt);
            if (changed && driftChanged != null) driftChanged.run();
        }

        newYear(c, dt, w, h, ledge, l, r, top);

        fresh = false;
        removeCallbacks(frame);
        postDelayed(frame, FRAME_MS);
    }

    /**
     * The turn of the year. The date is read once a second: it says how often a firework goes up, whether the
     * last ten seconds of the year are being counted down, and whether the finale is on. Rockets start from the
     * dock's top edge and burst over the upper part of the picture.
     */
    private void newYear(Canvas c, float dt, float w, float h, boolean ledge, float l, float r, float top) {
        long second = System.currentTimeMillis() / 1000L;
        if (second != skinSecond) {
            skinSecond = second;
            java.time.LocalDateTime t = NewYear.now();
            launchGap = NewYear.gap(t);
            volley = NewYear.volley(t);
            int count = NewYear.countdown(t);
            if (count != countdown) {
                countdown = count;
                countAt = SystemClock.uptimeMillis();
            }
            float into = NewYear.finale(t);
            if (into >= 0f && !finale) {
                finaleAt = SystemClock.uptimeMillis() - (long) (into * 1000f);
                newYearText = getResources().getString(R.string.festival_new_year_year, NewYear.year(t.toLocalDate()));
            }
            if (into < 0f && finale && finaleOver != null) post(finaleOver);
            finale = into >= 0f;
            if (!finale) skyPasses = 0;
            // twice in the finale the flyer crosses high up, through the fireworks
            if (finale && drift != null && drift.flyby() && skyPass < 0f
                    && (skyPasses == 0 && into >= 6f || skyPasses == 1 && into >= 32f)) {
                skyPass = 0f;
                skyDir = skyPasses++ % 2 == 0 ? 1 : -1;
            }
        }
        if (launchGap > 0f) {
            if (launchIn < 0f || launchIn > launchGap * 1.4f) launchIn = launchGap * rnd.nextFloat();
            launchIn -= dt;
            if (launchIn <= 0f) {
                launchIn = launchGap * (0.6f + 0.8f * rnd.nextFloat());
                if (fireworks == null) fireworks = new Fireworks(density);
                int rockets = volley > 1 ? 2 + rnd.nextInt(volley - 1) : 1;
                for (int i = 0; i < rockets; i++) {
                    fireworks.launch(w * (0.12f + 0.76f * rnd.nextFloat()), ledge ? top : h * 0.72f,
                            h * (0.14f + 0.26f * rnd.nextFloat()));
                }
            }
        }
        if (fireworks != null) fireworks.step(c, dt, ledge ? l : 0f, ledge ? r : 0f, top);
        if (skyPass >= 0f) {
            skyPass += dt / SnowDrift.FLYBY_SECONDS;
            float span = w + 140f * flyer * unit;
            float x = (skyDir > 0 ? skyPass : 1f - skyPass) * span - 70f * flyer * unit;
            float y = h * (0.34f - 0.1f * (float) Math.sin(Math.PI * skyPass));
            drawFlyer(c, x, y, skyDir, skinSecond % 3600L + skyPass * 3f, unit, true);
            if (skyPass >= 1f) skyPass = -1f;
        }
        if (countdown > 0) {
            // each number lands a little too big and settles, then dims as the next one is due
            float f = Math.min(1f, (SystemClock.uptimeMillis() - countAt) / 1000f);
            text.setTextSize(190f * density * (1.18f - 0.18f * Math.min(1f, f * 4f)));
            text.setAlpha(Math.round(255 * (1f - 0.6f * Math.max(0f, (f - 0.8f) / 0.2f))));
            // on the screensaver both stand lower, under the clock, which keeps to the upper part of the screen
            c.drawText(COUNT[countdown], w / 2f, h * (ground ? 0.84f : 0.52f), text);
        } else if (finale) {
            float e = (SystemClock.uptimeMillis() - finaleAt) / 1000f;
            float show = Math.max(0f, Math.min(1f, e / 0.6f) * Math.min(1f, (NewYear.FINALE_SECONDS - e) / 6f));
            text.setTextSize(64f * density);
            text.setAlpha(Math.round(255 * show));
            c.drawText(newYearText, w / 2f, h * (ground ? 0.74f : 0.44f), text);
        }
    }

    /**
     * The screensaver's ground: the tree, then in front of its trunk the snow lying along the bottom of the view,
     * column by column, and white from there down to the edge.
     */
    private void drawGround(Canvas c, float w, float h, float floor, float secs) {
        if (drift.hasTree()) {
            java.time.LocalDate today = NewYear.today();
            tree.draw(c, drift.treeX() * unit, floor, unit, drift.treeLoad(), ChristmasTree.decorations(today),
                    ChristmasTree.starLit(today), NewYear.presents(today), secs);
        }
        int n = drift.bins();
        float bin = w / n;
        bank.rewind();
        bank.moveTo(0f, h);
        float px = 0f, py = floor - drift.column(0) * unit;
        bank.lineTo(px, py);
        for (int i = 0; i < n; i++) {
            float x = (i + 0.5f) * bin, top = floor - drift.column(i) * unit;
            bank.quadTo(px, py, (px + x) / 2f, (py + top) / 2f);
            px = x;
            py = top;
        }
        bank.quadTo(px, py, w, py);
        bank.lineTo(w, h);
        bank.close();
        ink.setColor(0xF4FFFFFF);
        c.drawPath(bank, ink);
    }

    /** Turns the snow the blade shed this frame into clumps flying up and ahead of it; a few of them at the glass. */
    private void throwSnow(float thrown, float bladeX, float ground, int dir) {
        carry += thrown;
        int made = 0;
        for (int i = 0; i < SPRAY && carry >= CLUMP && made < 6; i++) {
            if (sKind[i] != FREE) continue;
            float area = CLUMP * (0.6f + rnd.nextFloat());
            carry -= area;
            made++;
            boolean glass = rnd.nextFloat() < 0.02f;
            sKind[i] = glass ? AT_GLASS : rnd.nextBoolean() ? MAY_LAND : FALLS;
            sArea[i] = area;
            sr[i] = 1.2f * (float) Math.sqrt(area / Math.PI) * unit;
            sx[i] = bladeX + dir * rnd.nextFloat() * 8f * SCALE * unit;
            sy[i] = ground - (6f + rnd.nextFloat() * 10f) * SCALE * unit;
            svx[i] = dir * (glass ? 40f + rnd.nextFloat() * 160f : drift.speed() * 0.7f + rnd.nextFloat() * 150f);
            svy[i] = -(glass ? 230f + rnd.nextFloat() * 170f : 70f + rnd.nextFloat() * 230f);
            sAge[i] = 0f;
            sHit[i] = 0.35f + rnd.nextFloat() * 0.4f;
        }
        // more than the clumps could carry this frame simply was not seen flying
        if (carry > CLUMP * 12f) carry = CLUMP * 12f;
    }

    /**
     * A flyby's wake: the snow it lifted this frame rises behind the flyer as puffs of powder that are dragged
     * along after it, slow, hang and thin to nothing. A few clumps are flung at the glass.
     */
    private void raisePowder(float thrown, float flyerX, float ground, int dir) {
        carry += thrown;
        float puff = CLUMP * 1.6f;
        int made = 0;
        for (int i = 0; i < SPRAY && carry >= puff && made < 9; i++) {
            if (sKind[i] != FREE) continue;
            float area = puff * (0.6f + rnd.nextFloat());
            carry -= area;
            made++;
            boolean glass = rnd.nextFloat() < 0.035f;
            sKind[i] = glass ? AT_GLASS : POWDER;
            sArea[i] = area;
            sr[i] = (glass ? 1.2f : 0.9f) * (float) Math.sqrt(area / Math.PI) * unit;
            // it leaves the ground a little behind him, where the air closes in again
            sx[i] = flyerX - dir * (4f + rnd.nextFloat() * 50f) * unit;
            sy[i] = ground - rnd.nextFloat() * 5f * unit;
            svx[i] = dir * drift.speed() * (0.1f + 0.5f * rnd.nextFloat()) + (rnd.nextFloat() - 0.5f) * 70f;
            svy[i] = -(glass ? 230f + rnd.nextFloat() * 170f : 50f + rnd.nextFloat() * rnd.nextFloat() * 260f);
            sAge[i] = 0f;
            sHit[i] = 0.35f + rnd.nextFloat() * 0.4f;
        }
        if (carry > puff * 14f) carry = puff * 14f;
    }

    /**
     * The flyer: a young wizard leaning low over a racing broom, black robes and a red and gold scarf streaming
     * behind him, round glasses, untidy black hair and a scar on his forehead, chasing a small winged golden
     * ball. Drawn facing the way he flies with the middle of the broomstick at ({@code x}, {@code y}), px;
     * {@code sparks} gives the broom a trail of gold sparks, for the New Year.
     */
    private void drawFlyer(Canvas c, float x, float y, int dir, float secs, float unit, boolean sparks) {
        c.save();
        c.translate(x, y);
        c.scale(dir * unit * flyer, unit * flyer);
        c.rotate(-4f + 2f * (float) Math.sin(secs * 2.6f));
        float wave = (float) Math.sin(secs * 9f), wave2 = (float) Math.sin(secs * 9f + 1.4f);

        if (sparks) {
            for (int k = 0; k < 14; k++) {
                float back = 62f + k * 6.5f + 5f * unit(k, (int) (secs * 15f));
                float off = (unit(k + 31, (int) (secs * 15f)) - 0.5f) * (5f + k * 1.3f);
                ink.setColor(k % 3 == 0 ? 0xFFFFF4D6 : 0xFFFFD75E);
                ink.setAlpha(Math.round(235 * (1f - k / 14f)));
                c.drawCircle(-back, 1.5f + off, 1.5f - k * 0.06f, ink);
            }
        }

        // the cloak, blown back
        cape.rewind();
        cape.moveTo(3f, -23f);
        cape.quadTo(-14f, -27f + 3f * wave, -34f, -20f + 4f * wave2);
        cape.lineTo(-30f, -12f + 3f * wave);
        cape.lineTo(-37f, -5f + 4f * wave2);
        cape.quadTo(-18f, -2f + 2f * wave, -7f, 0f);
        cape.close();
        ink.setColor(0xFF15151B);
        c.drawPath(cape, ink);

        // the broom
        ink.setColor(0xFFB98A3A);
        c.drawPath(bristles, ink);
        line.setColor(0xFF7E5A1F);
        line.setStrokeWidth(0.8f);
        c.drawLine(-36f, 1.4f, -62f, 0.6f, line);
        c.drawLine(-36f, 3.4f, -62f, 3.6f, line);
        // a dark polished handle, gold bands on the tail and a footrest under it
        line.setColor(0xFF3B2414);
        line.setStrokeWidth(2.5f);
        c.drawLine(-36f, 2.4f, 32f, -3.2f, line);
        ink.setColor(0xFFEEBA30);
        c.drawRect(-37.5f, -0.4f, -34f, 5.4f, ink);
        c.drawRect(-50.5f, -3.6f, -48.6f, 8.2f, ink);
        line.setColor(0xFFEEBA30);
        line.setStrokeWidth(1.1f);
        c.drawLine(-14f, 1f, -9f, 12.6f, line);
        c.drawLine(-12f, 12.8f, -3.5f, 13f, line);

        // leg and shoe, behind the stick's near side
        line.setColor(0xFF2B2B33);
        line.setStrokeWidth(4.2f);
        c.drawLine(-5f, 0f, 3f, 8.5f, line);
        c.drawLine(3f, 8.5f, -6f, 11.5f, line);
        ink.setColor(0xFF0E0E12);
        c.drawCircle(-7.5f, 11.8f, 2.4f, ink);

        ink.setColor(0xFF1E1E26);
        c.drawPath(torso, ink);
        // the house colours at the collar
        ink.setColor(0xFFAE0001);
        c.drawCircle(6.2f, -22.6f, 3.4f, ink);

        // the scarf, in six stripes, streaming
        for (int k = 0; k < 6; k++) {
            float x0 = 4f - k * 5.6f, x1 = x0 - 5.8f;
            float y0 = -24f + (k == 0 ? 0f : 3f * (float) Math.sin(secs * 9f + k * 0.9f)) - k * 0.5f;
            float y1 = -24f + 3f * (float) Math.sin(secs * 9f + (k + 1) * 0.9f) - (k + 1) * 0.5f;
            line.setColor(k % 2 == 0 ? 0xFFAE0001 : 0xFFEEBA30);
            line.setStrokeWidth(3.6f);
            c.drawLine(x0, y0, x1, y1, line);
        }

        // arm to the stick, and the hand
        line.setColor(0xFF15151B);
        line.setStrokeWidth(4f);
        c.drawLine(6f, -19.5f, 13f, -9.5f, line);
        c.drawLine(13f, -9.5f, 20.5f, -4f, line);
        ink.setColor(0xFFF2C9A0);
        c.drawCircle(21.5f, -3.6f, 2.2f, ink);

        // head, hair, glasses, scar
        ink.setColor(0xFFF2C9A0);
        c.drawCircle(11f, -31f, 7.6f, ink);
        ink.setColor(0xFF0E0E12);
        c.drawPath(hair, ink);
        line.setColor(0xFFB3261E);
        line.setStrokeWidth(0.75f);
        c.drawLine(13.2f, -36.6f, 12.2f, -35.2f, line);
        c.drawLine(12.2f, -35.2f, 13.6f, -35f, line);
        c.drawLine(13.6f, -35f, 12.8f, -33.6f, line);
        ink.setColor(0x38FFFFFF);
        c.drawCircle(10.6f, -30.4f, 2.7f, ink);
        c.drawCircle(16.2f, -30.4f, 2.5f, ink);
        line.setColor(0xFF15151B);
        line.setStrokeWidth(0.85f);
        line.setStyle(Paint.Style.STROKE);
        c.drawCircle(10.6f, -30.4f, 2.7f, line);
        c.drawCircle(16.2f, -30.4f, 2.5f, line);
        c.drawLine(13.3f, -30.6f, 13.7f, -30.6f, line);
        c.drawLine(7.9f, -30.8f, 4.4f, -31.6f, line);
        ink.setColor(0xFF2E6B3A);
        c.drawCircle(11f, -30.3f, 0.8f, ink);
        c.drawCircle(16.4f, -30.3f, 0.8f, ink);
        line.setColor(0xFF8A4B3A);
        line.setStrokeWidth(0.7f);
        c.drawLine(12.4f, -26.4f, 15.4f, -26.8f, line);

        // the golden ball he is after, wings beating
        float bx = 46f + 3f * (float) Math.sin(secs * 6.3f), by = -32f + 3.5f * (float) Math.cos(secs * 7.7f);
        float beat = 3.2f * (float) Math.sin(secs * 46f);
        line.setColor(0xD9FFFFFF);
        line.setStrokeWidth(0.9f);
        c.drawLine(bx, by, bx - 5.5f, by - 2f + beat, line);
        c.drawLine(bx, by, bx + 5.5f, by - 2f + beat, line);
        ink.setColor(0xFFEEBA30);
        c.drawCircle(bx, by, 1.9f, ink);
        c.restore();
    }

    /**
     * Flies the thrown clumps one step and draws them. A clump coming down on the ledge ahead of the blade may
     * settle there, to be ploughed again; the others drop past the dock and fade, and one aimed at the viewer
     * grows as it nears and becomes a splat.
     *
     * @return whether any snow settled back on the drift
     */
    private boolean stepSpray(Canvas c, float dt, boolean ledge, float l, float r, float top) {
        boolean settled = false;
        boolean ploughOut = ledge && drift.ploughing();
        float bladeX = ploughOut ? l + drift.blade() * unit : 0f;
        int dir = drift.direction();
        float drag = 1f - 0.6f * dt;
        for (int i = 0; i < SPRAY; i++) {
            byte kind = sKind[i];
            if (kind == FREE) continue;
            sAge[i] += dt;
            if (kind == POWDER) {
                // powder is light: it falls slowly and the air stops it quickly
                svy[i] = svy[i] * (1f - 1.6f * dt) + GRAVITY * 0.07f * dt;
                svx[i] *= 1f - 1.5f * dt;
            } else {
                svy[i] += GRAVITY * dt;
                svx[i] *= drag;
            }
            sx[i] += svx[i] * dt * unit;
            sy[i] += svy[i] * dt * unit;
            float rad = sr[i];
            int alpha = 235;
            if (kind == AT_GLASS) {
                float near = sAge[i] / sHit[i];
                if (near >= 1f) {
                    splat(sx[i], sy[i], rad * 3f);
                    sKind[i] = FREE;
                    continue;
                }
                rad *= 1f + 2f * near;
            } else if (kind == POWDER) {
                float t = sAge[i] / POWDER_LIFE;
                if (t >= 1f) {
                    sKind[i] = FREE;
                    continue;
                }
                rad *= 1f + 1.1f * t;
                alpha = Math.round(210 * (1f - t) * (1f - t));
            } else if (kind == FADING) {
                alpha = Math.round(235 * (1f - sAge[i] / 0.45f));
                if (alpha <= 0) {
                    sKind[i] = FREE;
                    continue;
                }
            } else if (svy[i] > 0f) {
                boolean over = ledge && sx[i] > l && sx[i] < r;
                float surface = over ? top - drift.depth((sx[i] - l) / unit) * unit : top;
                if (sy[i] >= surface) {
                    if (kind == MAY_LAND && over && ploughOut && dir * (sx[i] - bladeX) > 0f) {
                        drift.deposit((sx[i] - l) / unit, sArea[i]);
                        sKind[i] = FREE;
                        settled = true;
                        continue;
                    }
                    sKind[i] = FADING;
                    sAge[i] = 0f;
                }
            }
            paint.setAlpha(alpha);
            c.drawCircle(sx[i], sy[i], rad, paint);
        }
        return settled;
    }

    /** Snow slides off the Christmas tree: a few clumps tumble down its sides to the drift at its foot. */
    private void slideOffTree(float treeX, float top) {
        int made = 0;
        for (int i = 0; i < SPRAY && made < 12; i++) {
            if (sKind[i] != FREE) continue;
            made++;
            float side = rnd.nextBoolean() ? 1f : -1f, up = 0.15f + 0.6f * rnd.nextFloat();
            sKind[i] = FALLS;
            sArea[i] = CLUMP;
            sr[i] = (1.6f + rnd.nextFloat() * 1.6f) * unit;
            sx[i] = treeX + side * SnowDrift.TREE_W / 2f * (1f - up) * unit;
            sy[i] = top - SnowDrift.TREE_H * up * unit;
            svx[i] = side * (15f + rnd.nextFloat() * 45f);
            svy[i] = -rnd.nextFloat() * 40f;
            sAge[i] = 0f;
        }
    }

    /** A clump hits the inside of the screen at this point; with every splat in use it is simply not seen. */
    private void splat(float x, float y, float radius) {
        for (int i = 0; i < SPLATS; i++) {
            if (bAge[i] >= 0f) continue;
            bx[i] = x;
            by[i] = y;
            by0[i] = y;
            br[i] = radius;
            bAge[i] = 0f;
            bSeed[i] = rnd.nextInt();
            return;
        }
    }

    /**
     * The splats on the glass: each flattens out as it hits, then creeps down under its own weight, faster as it
     * wets, leaving a faint trail, and melts away.
     */
    private void stepSplats(Canvas c, float dt) {
        for (int i = 0; i < SPLATS; i++) {
            if (bAge[i] < 0f) continue;
            bAge[i] += dt;
            float t = bAge[i] / SPLAT_LIFE;
            if (t >= 1f) {
                bAge[i] = -1f;
                continue;
            }
            if (bAge[i] > 0.25f) by[i] += (3f + 11f * t) * unit * dt;
            float hit = Math.min(1f, bAge[i] / 0.12f);
            float rad = br[i] * (0.55f + 0.45f * hit) * (1f - 0.4f * t);
            int alpha = Math.round(215 * (1f - t * t));
            int seed = bSeed[i];
            line.setColor(0xFFFFFFFF);
            line.setAlpha(alpha / 3);
            line.setStrokeWidth(rad * 0.7f);
            c.drawLine(bx[i], by0[i], bx[i], by[i], line);
            paint.setAlpha(alpha);
            c.drawCircle(bx[i], by[i], rad, paint);
            for (int k = 0; k < 5; k++) {
                double a = TAU * (k + unit(seed, k)) / 5.0;
                float far = rad * (0.7f + 0.4f * unit(seed, k + 5)) * hit;
                c.drawCircle(bx[i] + far * (float) Math.cos(a), by[i] + far * (float) Math.sin(a) * 0.85f,
                        rad * (0.35f + 0.22f * unit(seed, k + 10)), paint);
            }
            for (int k = 0; k < 3; k++) {
                double a = TAU * unit(seed, k + 20);
                float far = rad * (1.5f + 0.8f * unit(seed, k + 23)) * hit;
                c.drawCircle(bx[i] + far * (float) Math.cos(a), by[i] + far * (float) Math.sin(a),
                        rad * 0.14f, paint);
            }
        }
        paint.setAlpha(255);
    }

    /**
     * The plough, facing the way it drives: headlight beam, a red truck with a lit cab, snow on its roof, a
     * blinking amber beacon and turning wheels, the yellow blade, and in front of the blade the snow it is pushing.
     */
    private void drawPlough(Canvas c, float bladeX, float ground, int dir, float secs) {
        c.save();
        c.translate(bladeX, ground);
        c.scale(dir * unit * SCALE, unit * SCALE);

        ink.setColor(0x24FFE9A8);
        c.drawPath(beam, ink);

        float turn = drift.blade() / (5f * SCALE);
        wheel(c, -51f, turn);
        wheel(c, -19f, turn);

        c.translate(0f, 0.4f * (float) Math.sin(secs * 24f));   // the engine's shake
        ink.setColor(0xFF2B2B33);
        c.drawRect(-60f, -10f, -10f, -6f, ink);
        line.setColor(0xFF2B2B33);
        line.setStrokeWidth(2f);
        c.drawLine(-2f, -6f, -12f, -7.5f, line);
        ink.setColor(0xFF8E1F1C);
        c.drawRoundRect(-60f, -22f, -44f, -9f, 2f, 2f, ink);
        ink.setColor(0xFFC8322B);
        c.drawRoundRect(-43f, -28f, -26f, -9f, 3f, 3f, ink);
        c.drawRoundRect(-27f, -18f, -11f, -9f, 2.5f, 2.5f, ink);
        ink.setColor(0xFFFFD98A);
        c.drawRoundRect(-40f, -25f, -29f, -17f, 1.5f, 1.5f, ink);
        if (santa) {
            drawSanta(c);
        } else {
            ink.setColor(0xFFFFF1C4);
            c.drawCircle(-11.5f, -13.5f, 1.6f, ink);
        }
        ink.setColor(0xF4FFFFFF);
        c.drawRoundRect(-60.5f, -24f, -43.5f, -21.5f, 1.2f, 1.2f, ink);
        c.drawRoundRect(-43.5f, -29.6f, -25.5f, -27.4f, 1.1f, 1.1f, ink);
        if ((secs * 2.2f) % 1f < 0.5f) {
            ink.setColor(0x46FFA726);
            c.drawCircle(-34.5f, -31.2f, 5f, ink);
            ink.setColor(0xFFFFA726);
        } else {
            ink.setColor(0xFF9A5A12);
        }
        c.drawCircle(-34.5f, -31.2f, 1.7f, ink);

        ink.setColor(0xFFF2B33D);
        c.drawPath(blade, ink);

        float tall = drift.heapHeight();
        if (tall > 0.3f) {
            float reach = drift.heapReach();
            heap.rewind();
            // the heap is the drift's own snow, so it keeps the drift's scale while the plough is drawn larger
            tall /= SCALE;
            reach /= SCALE;
            heap.moveTo(1f, 0.6f);
            heap.lineTo(1f, -tall);
            heap.quadTo(1f + reach * 0.55f, -tall * 0.95f, 1f + reach, 0.6f);
            heap.close();
            ink.setColor(0xF4FFFFFF);
            c.drawPath(heap, ink);
        }
        c.restore();
    }

    /**
     * Santa's plough: Santa in profile at the wheel, his sack in the back (full of presents until Christmas
     * Eve, slumped and empty once they lie under the tree), a pair of antlers on the bonnet and a red nose for
     * a headlight. Drawn in the plough's own dp, after its body and before the snow on its roofs.
     */
    private void drawSanta(Canvas c) {
        java.time.LocalDate today = NewYear.today();
        boolean full = NewYear.phase(today) == NewYear.CHRISTMAS && !NewYear.presents(today);
        // the sack, and what sticks out of it
        if (full) {
            ink.setColor(0xFF2E8B57);
            c.drawRect(-57.5f, -34.5f, -53f, -30f, ink);
            ink.setColor(0xFF3F7FD0);
            c.drawRect(-50f, -36f, -46.2f, -31f, ink);
            line.setColor(0xFFFFD75E);
            line.setStrokeWidth(0.9f);
            c.drawLine(-55.2f, -34.5f, -55.2f, -30f, line);
            c.drawLine(-50f, -33.6f, -46.2f, -33.6f, line);
            ink.setColor(0xFF8A5A2B);
            c.drawRoundRect(-59f, -32f, -45f, -21f, 5f, 5f, ink);
            ink.setColor(0xFF6E4520);
            c.drawRoundRect(-54.5f, -33.4f, -49.5f, -31.4f, 1f, 1f, ink);
        } else {
            ink.setColor(0xFF8A5A2B);
            c.drawRoundRect(-58.5f, -26.5f, -47f, -21f, 2.4f, 2.4f, ink);
        }
        // Santa, behind the glass
        c.save();
        c.clipRect(-40f, -25f, -29f, -17f);
        ink.setColor(0xFFB3211D);
        c.drawCircle(-35.6f, -15.2f, 3.9f, ink);
        ink.setColor(0xFFFFFFFF);
        c.drawCircle(-33.5f, -18.5f, 2.3f, ink);
        ink.setColor(0xFFF2C6A0);
        c.drawCircle(-34.5f, -20.6f, 1.9f, ink);
        ink.setColor(0xFFE58A7A);
        c.drawCircle(-32.7f, -20.3f, 0.55f, ink);
        ink.setColor(0xFF2B2B33);
        c.drawCircle(-33.6f, -21f, 0.32f, ink);
        ink.setColor(0xFFC8322B);
        c.drawPath(hat, ink);
        ink.setColor(0xFFFFFFFF);
        c.drawRoundRect(-37.2f, -22.5f, -32f, -21.3f, 0.6f, 0.6f, ink);
        c.drawCircle(-38.9f, -21.3f, 0.9f, ink);
        c.restore();
        // antlers on the bonnet, and the nose
        line.setColor(0xFF8A5A2B);
        line.setStrokeWidth(1.1f);
        c.drawLine(-16f, -18f, -14.5f, -24f, line);
        c.drawLine(-15.3f, -20.6f, -12.6f, -22.2f, line);
        c.drawLine(-15.6f, -19.6f, -18.4f, -22f, line);
        c.drawLine(-14.8f, -22.6f, -16.8f, -24.6f, line);
        ink.setColor(0x50FF3B30);
        c.drawCircle(-11.5f, -13.5f, 3.4f, ink);
        ink.setColor(0xFFFF3B30);
        c.drawCircle(-11.5f, -13.5f, 1.8f, ink);
    }

    private void wheel(Canvas c, float x, float turn) {
        ink.setColor(0xFF15151A);
        c.drawCircle(x, -5f, 5f, ink);
        ink.setColor(0xFFB9BEC8);
        c.drawCircle(x, -5f, 2f, ink);
        line.setColor(0xFFB9BEC8);
        line.setStrokeWidth(0.9f);
        float dx = 3.6f * (float) Math.cos(turn), dy = 3.6f * (float) Math.sin(turn);
        c.drawLine(x - dx, -5f - dy, x + dx, -5f + dy, line);
        c.drawLine(x + dy, -5f - dx, x - dy, -5f + dx, line);
    }

    /** A fixed pseudo-random value in 0..1 for the k-th feature of a splat. */
    private static float unit(int seed, int k) {
        int v = seed + k * 0x9E3779B1;
        v ^= v >>> 15;
        v *= 0x2C1B3C6D;
        v ^= v >>> 12;
        return (v & 0xFFFF) / 65535f;
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
        prevY = new float[n];
        landed = new int[n];
    }
}
