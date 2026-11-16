package com.gathin.tvhome;

/**
 * The snow lying on the dock's top edge in the Christmas skin, or along the bottom of the screen on the clock
 * screensaver ({@link #walled}), and the plough that clears it. The ledge is cut into
 * columns {@link #BIN} dp wide; a flake that lands adds its own area to the columns under it, snow steeper than
 * {@link #REPOSE} slumps to the next column (and off the two ends), and once the mean depth reaches
 * {@link #PLOUGH_DEPTH} the plough is {@link #due}. The plough's blade then travels the ledge: it scrapes each
 * column down to {@link #RESIDUE}, carries what it scraped as a heap in front of it and throws the heap off a
 * little at a time, which is what {@link #advance} returns for the view to draw as flying snow.
 *
 * <p>All lengths are dp and all amounts of snow are dp² of cross-section, so the model knows nothing about the
 * screen except where the ledge is ({@link #place}), which the dock's decoration writes and the snowfall reads;
 * on the screensaver the snowfall writes it itself, with a larger dp.
 */
final class SnowDrift {

    /** Width of one column of snow. */
    static final float BIN = 4f;
    /** The steepest slope lying snow keeps, as rise over run: wind-smoothed, well under a fresh pile's. */
    static final float REPOSE = 0.3f;
    /**
     * Lying snow packs down: a flake adds this share of its own area to the drift. Every flake over the dock
     * lands on it, the big near ones included, and with this packing the drift fills as fast as it did when only
     * the small ones landed and counted for more.
     */
    static final float PACKING = 0.78f;
    /**
     * The mean depth that calls the plough: a little under a fifth of a dock tile's height. With the skin's 46
     * flakes the ledge gains about 0.03 dp a second (measured on a 1080p set while only the small flakes landed;
     * {@link #PACKING} keeps that rate), so a cleared ledge takes a little under five minutes to fill.
     */
    static final float PLOUGH_DEPTH = 10f;
    /** The skim of snow a blade leaves behind. */
    static final float RESIDUE = 0.9f;
    /** The plough's speed along the ledge, dp a second. */
    static final float SPEED = 105f;
    /** How long snow rides in front of the blade before it is thrown off, seconds. */
    static final float HEAP_TAU = 0.4f;
    /** The blade's height: the heap in front of it cannot stand taller. */
    static final float BLADE_HEIGHT = 15f;
    /** Pushed snow stands steeper than fallen snow. */
    private static final float HEAP_SLOPE = 0.8f;
    /** The Christmas tree's footprint and height; it stands this far in from the ledge's right end. */
    static final float TREE_W = 64f, TREE_H = 96f, TREE_INSET = 46f;
    /** How much snow the tree's branches hold before it slides off, dp². */
    static final float TREE_HOLDS = 420f;
    /** What stays on the branches when the rest slides off, and what the plough's spray adds, as shares of that. */
    static final float TREE_KEEPS = 0.2f, TREE_DUSTING = 0.25f;

    private float[] h = new float[0];
    private int n;
    private float width;

    /** The plough's speed on this ledge, dp a second. */
    private float speed = SPEED;
    /** Walls at both ends: nothing slumps off them and the snow lies right up to them. */
    private boolean walled;

    private boolean ploughing;
    private int dir = 1;
    private float blade, end, heap;

    /** Where the tree stands, dp along the ledge; negative on a ledge too short for one. */
    private float treeX = -1f;
    /** How full of snow its branches are, 0 to 1. */
    private float treeLoad;
    private boolean shed;

    /** Where the ledge is in the window, px: written by the dock's decoration on every draw. */
    float ledgeLeft, ledgeRight, ledgeTop;
    /** How far the dock's rounded corners reach beyond the ledge at each end, px: snow lands there too. */
    float ledgeCorner;
    /** False while the dock is off screen or has no band. */
    boolean ledgeShown;

    /** The decoration's report of the ledge; a ledge of a new width keeps the snow it had, stretched to fit. */
    void place(float leftPx, float rightPx, float topPx, float cornerPx, float density) {
        ledgeCorner = cornerPx;
        ledgeLeft = leftPx;
        ledgeRight = rightPx;
        ledgeTop = topPx;
        ledgeShown = true;
        resize((rightPx - leftPx) / density);
    }

    /**
     * A ledge between two walls, as the bottom of a screen is: its snow lies level up to both ends and none falls
     * off them. The plough crosses it at {@code dpPerSecond}.
     */
    void walled(float dpPerSecond) {
        walled = true;
        speed = dpPerSecond;
    }

    float speed() {
        return speed;
    }

    void resize(float widthDp) {
        int bins = Math.max(8, Math.round(widthDp / BIN));
        if (bins == n) return;
        float[] next = new float[bins];
        for (int i = 0; i < bins; i++) {
            next[i] = n == 0 ? firstSnow(i, bins, walled) : h[Math.min(n - 1, i * n / bins)];
        }
        h = next;
        n = bins;
        width = bins * BIN;
        treeX = width >= 240f ? width - TREE_INSET : -1f;
        settle();
    }

    boolean hasTree() {
        return treeX >= 0f;
    }

    /** Where the tree's trunk stands, dp along the ledge. */
    float treeX() {
        return treeX;
    }

    /** How full of snow the tree's branches are, 0 to 1. */
    float treeLoad() {
        return treeLoad;
    }

    /** How high the tree's outline stands above the ledge at {@code x}: a cone over its footprint, 0 beside it. */
    float treeTop(float x) {
        if (treeX < 0f) return 0f;
        float off = Math.abs(x - treeX);
        return off >= TREE_W / 2f ? 0f : TREE_H * (1f - off / (TREE_W / 2f));
    }

    /** A flake of this radius comes to rest on the tree's branches. */
    void landOnTree(float radius) {
        loadTree(PACKING * (float) Math.PI * radius * radius);
    }

    /**
     * {@code area} dp² more snow on the branches. Once they hold all they can, most of it slides off at once and
     * lands in the drift at the tree's foot, half on each side.
     */
    private void loadTree(float area) {
        if (treeX < 0f) return;
        treeLoad += area / TREE_HOLDS;
        if (treeLoad < 1f) return;
        float fell = (treeLoad - TREE_KEEPS) * TREE_HOLDS;
        treeLoad = TREE_KEEPS;
        shed = true;
        deposit(treeX - 12f, fell / 2f);
        deposit(treeX + 12f, fell / 2f);
    }

    /** Whether snow has slid off the tree since this was last asked. */
    boolean takeShed() {
        boolean was = shed;
        shed = false;
        return was;
    }

    /** The light, uneven cover the dock starts with, thinning to nothing at both ends. */
    private static float firstSnow(int i, int bins, boolean walled) {
        float taper = walled ? 1f : Math.min(1f, Math.min(i + 0.5f, bins - i - 0.5f) * BIN / 60f);
        double a = i * BIN;
        float wave = (float) (Math.sin(a / 31.0) + 0.6 * Math.sin(a / 13.0 + 1.7) + 0.4 * Math.sin(a / 71.0 + 0.4));
        return taper * (2.4f + 0.8f * wave);
    }

    float width() {
        return width;
    }

    int bins() {
        return n;
    }

    /** Depth of column {@code i}. */
    float column(int i) {
        return h[i];
    }

    /** Depth at {@code x} dp along the ledge, between the two nearest columns; 0 off the ledge. */
    float depth(float x) {
        if (n == 0 || x < 0 || x > width) return 0f;
        float f = x / BIN - 0.5f;
        int i = (int) Math.floor(f);
        float t = f - i;
        float a = i < 0 ? h[0] : h[Math.min(i, n - 1)];
        float b = i + 1 >= n ? h[n - 1] : h[Math.max(0, i + 1)];
        return a + (b - a) * t;
    }

    float mean() {
        if (n == 0) return 0f;
        float sum = 0f;
        for (int i = 0; i < n; i++) sum += h[i];
        return sum / n;
    }

    /** All the snow on the ledge, dp². */
    float lying() {
        return mean() * width;
    }

    /** A flake of this radius comes to rest at {@code x}. */
    void land(float x, float radius) {
        deposit(x, PACKING * (float) Math.PI * radius * radius);
    }

    /** {@code area} dp² of snow comes to rest at {@code x}: half under it, a quarter to each side. */
    void deposit(float x, float area) {
        if (n == 0 || x < 0 || x >= width) return;
        int i = Math.min(n - 1, (int) (x / BIN));
        float lift = area / BIN;
        h[i] += lift * 0.5f;
        if (i > 0) h[i - 1] += lift * 0.25f;
        if (i < n - 1) h[i + 1] += lift * 0.25f;
        settle();
    }

    /**
     * Snow steeper than {@link #REPOSE} slumps downhill until none is; what slumps past an end falls off. A blade
     * on the ledge is a wall: the snow in front of it does not slump back over the ground it has cleared. The
     * number of sweeps is bounded, which is enough for a ledge the plough keeps under {@link #PLOUGH_DEPTH}: a
     * landing only ever disturbs the few columns around it.
     */
    private void settle() {
        float step = REPOSE * BIN;
        // the pair of columns the blade stands between, or none
        int wall = !ploughing ? -2 : dir > 0 ? (int) Math.floor(blade / BIN - 0.5f) : (int) Math.ceil(blade / BIN - 0.5f) - 1;
        for (int pass = 0; pass < 48; pass++) {
            // one sweep each way, so a slump runs to either end in a single pass
            boolean moved = false;
            for (int i = 0; i < n - 1; i++) {
                if (i != wall && level(i, step)) moved = true;
            }
            for (int i = n - 2; i >= 0; i--) {
                if (i != wall && level(i, step)) moved = true;
            }
            // Nothing can stand higher than the slope up from the nearer end allows: whatever does is on its way
            // off that end, and taking it at once spares the many sweeps it would need to slide there.
            for (int i = 0; i < n && !walled; i++) {
                float most = step * (Math.min(i, n - 1 - i) + 1);
                if (h[i] > most + 0.01f) {
                    h[i] = most;
                    moved = true;
                }
            }
            if (!moved) return;
        }
    }

    /** Brings columns {@code i} and {@code i + 1} to the angle of repose if they are steeper; true if snow moved. */
    private boolean level(int i, float step) {
        float diff = h[i] - h[i + 1];
        if (diff > step + 0.01f) {
            float m = (diff - step) / 2f;
            h[i] -= m;
            h[i + 1] += m;
            return true;
        }
        if (diff < -step - 0.01f) {
            float m = (-diff - step) / 2f;
            h[i] += m;
            h[i + 1] -= m;
            return true;
        }
        return false;
    }

    /** Enough snow has collected and no plough is out. */
    boolean due() {
        return !ploughing && n > 0 && mean() >= PLOUGH_DEPTH;
    }

    boolean ploughing() {
        return ploughing;
    }

    /** +1 while the plough drives towards the ledge's right end, -1 towards its left. */
    int direction() {
        return dir;
    }

    /** Where the blade is, dp along the ledge; outside 0..width while the plough drives on or off. */
    float blade() {
        return blade;
    }

    /** The snow riding in front of the blade, dp². */
    float heap() {
        return heap;
    }

    /** How tall that heap stands against the blade. */
    float heapHeight() {
        return Math.min(BLADE_HEIGHT, (float) Math.sqrt(2f * heap * HEAP_SLOPE));
    }

    /** How far the heap reaches in front of the blade. */
    float heapReach() {
        return heapHeight() / HEAP_SLOPE;
    }

    /** Sends the plough from {@code from} to {@code to}, both dp along the ledge and normally off its two ends. */
    void startPlough(float from, float to) {
        if (ploughing || n == 0) return;
        ploughing = true;
        dir = to >= from ? 1 : -1;
        blade = from;
        end = to;
        heap = 0f;
    }

    /**
     * Moves the plough on by {@code dt} seconds. Columns the blade passes are scraped to {@link #RESIDUE} into the
     * heap, and the heap sheds a share of itself, faster once the blade is past the ledge's end and there is
     * nothing left to hold it up.
     *
     * @return the snow thrown off in this step, dp²
     */
    float advance(float dt) {
        if (!ploughing || dt <= 0f) return 0f;
        float from = blade;
        blade += dir * speed * dt;
        float lo = Math.min(from, blade), hi = Math.max(from, blade);
        int first = Math.max(0, (int) Math.ceil(lo / BIN - 0.5f));
        int last = Math.min(n - 1, (int) Math.floor(hi / BIN - 0.5f));
        for (int i = first; i <= last; i++) {
            float take = h[i] - RESIDUE;
            if (take > 0f) {
                h[i] = RESIDUE;
                heap += take * BIN;
            }
        }
        if (treeX >= 0f && (from - treeX) * (blade - treeX) <= 0f && from != blade) {
            // passing the tree: some of the snow the blade is throwing lands on its branches
            float dusting = Math.min(heap, TREE_DUSTING * TREE_HOLDS);
            heap -= dusting;
            loadTree(dusting);
        }
        boolean offLedge = dir > 0 ? blade > width : blade < 0f;
        float thrown = heap * (1f - (float) Math.exp(-dt / (offLedge ? HEAP_TAU / 3f : HEAP_TAU)));
        heap -= thrown;
        if (dir > 0 ? blade >= end : blade <= end) {
            thrown += heap;
            heap = 0f;
            ploughing = false;
        }
        return thrown;
    }
}
