package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /** Extra band below the tiles for their names (always_show_names), in px. */
    private final float namesRoom;
    /** Light glass like the top bar's pills (config.xml dock_glass): a white tint that fades downwards. */
    private final boolean lightGlass;
    /** Padding all round the tiles equal to the gap between them (config.xml dock_even_padding). */
    private final boolean evenPadding;
    /** The band's top and bottom the glass gradient was made for; it is remade when they move. */
    private float shaderTop = Float.NaN, shaderBottom = Float.NaN;
    /** Draw the band and its edge (config.xml dock_band); false leaves the tiles floating on the wallpaper. */
    private final boolean bandVisible;
    /** single_row: the tiles after the dock share its row, so they take the same offsets to stay level with it. */
    private final boolean singleRow;
    /** dock_band_all: in a single row, the band runs behind every app instead of the dock apps only. */
    private final boolean bandAll;
    /** Draw a line of settled snow on the band's top edge (the Christmas skin). */
    private boolean snow;
    private final Paint snowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path snowPath = new Path();
    /** The band the snow was shaped for; it is reshaped when the band moves. */
    private final RectF snowFor = new RectF();
    /** The snow that really collects on the band while the snowfall runs; null draws the fixed drifts instead. */
    private SnowDrift drift;
    private final int[] inWindow = new int[2];
    /** The Christmas tree standing in that drift, and how it is dressed today (looked up once an hour). */
    private ChristmasTree tree;
    private int treeDecorations;
    private boolean treeLit, treeGifts;
    private long treeHour = -1;

    /**
     * @param translucent the cinematic shelf's band: the artwork and the glow show through it.
     * @param names       the dock tiles show their names, so the band reaches down over them.
     * @param opacity     the plain band's opacity in percent (dock_opacity), so a wallpaper can show through it.
     * @param lightGlass  a light frosted band like the pills, for a sky or wallpaper behind it.
     * @param evenPadding the band's padding is the gap between the tiles on all four sides (dock_even_padding).
     * @param bandVisible false skips the band and its edge, so the tiles float on the wallpaper (dock_band).
     * @param singleRow   every app sits in the dock's row (single_row); the band covers the dock apps only, unless bandAll.
     * @param bandAll     with singleRow, the band runs behind every app in the row (dock_band_all).
     */
    DockDecoration(int dockSize, float density, boolean translucent, boolean names, int opacity, boolean lightGlass,
                   boolean evenPadding, boolean bandVisible, boolean singleRow, boolean bandAll) {
        this.dockSize = dockSize;
        this.singleRow = singleRow;
        this.bandAll = singleRow && bandAll;
        this.density = density;
        this.namesRoom = names ? 22 * density : 0;
        this.lightGlass = lightGlass;
        this.evenPadding = evenPadding;
        this.bandVisible = bandVisible;
        band.setColor(translucent ? 0x8C121014 : (Math.round(opacity * 2.55f) << 24) | 0x141416);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(lightGlass ? 0x38FFFFFF : translucent ? 0x26FFFFFF : 0x1FFFFFFF);
    }

    /** Settled snow along the band's top edge, for the Christmas skin. */
    DockDecoration snow(boolean on) {
        snow = on;
        return this;
    }

    /** The living drift the snowfall lands on and the plough clears; the fixed drifts are drawn without one. */
    DockDecoration drift(SnowDrift d) {
        drift = d;
        return this;
    }

    /** Concentric corners: the band's radius is the tiles' radius plus the band's padding around them. */
    static float radiusPx(float tileRadiusPx, float paddingPx) {
        return tileRadiusPx + paddingPx;
    }

    /**
     * The band's padding on every side for dock_even_padding: the gap between two dock tiles (a cell minus its tile, as
     * AppAdapter.tileWidthPx lays them out), capped by the room the grid leaves at its edges.
     */
    static float evenPaddingPx(int innerWidthPx, int columns, int tileWidthPx, float roomPx) {
        float gap = columns > 0 ? innerWidthPx / (float) columns - tileWidthPx : 0f;
        return Math.max(0f, Math.min(gap, roomPx));
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && (singleRow || pos < dockSize)) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (drift != null) drift.ledgeShown = false;
        if (!bandVisible) return;
        boolean any = false;
        int cardWidth = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || (!bandAll && pos >= dockSize)) continue;
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            cardWidth = card.getWidth();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float pad = 14 * density;
        // The band can only draw inside the grid, and the first and last dock tiles sit 12dp in from its
        // edges: a full 14dp at the sides sliced the rounded ends flat (#454). Keep the sides, with the
        // 1dp edge line, inside the grid; the top and bottom have room.
        // 22dp at the sides (the grid's 10dp padding makes room): a focused first or last tile grows about 7dp
        // outwards and still keeps about the band's top margin from its rounded end.
        float sidePad = 22 * density;
        float side = Math.max(0f, Math.min(sidePad, Math.min(rect.left, parent.getWidth() - rect.right) - density));
        float radius = AppAdapter.cornerRadius(cardWidth, density);
        if (evenPadding) {
            // The same padding on all four sides, so the band's corners are concentric with the tiles' everywhere.
            int inner = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
            // A single row's tiles can run off the screen, so only its top limits the padding; its ends are clamped below.
            float room = (singleRow ? rect.top : Math.min(rect.top, Math.min(rect.left, parent.getWidth() - rect.right))) - density;
            float even = evenPaddingPx(inner, dockSize, cardWidth, room);
            rect.inset(-even, -even);
            radius = radiusPx(radius, even);
        } else {
            // dock_even_padding off: the band as before, with its fixed 26 dp corners.
            rect.inset(singleRow ? -sidePad : -side, -pad);
            radius = 26 * density;
        }
        if (singleRow) {
            // Tiles past the screen's edge: the band stops at the grid's edge with its rounded end instead of being
            // sliced square there.
            rect.left = Math.max(rect.left, density);
            rect.right = Math.min(rect.right, parent.getWidth() - density);
        }
        rect.bottom += namesRoom;
        if (lightGlass && (rect.top != shaderTop || rect.bottom != shaderBottom)) {
            // The pills' glass (pill_bg: white, 18% fading to 8%), a little fainter over this much larger area.
            band.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x24FFFFFF, 0x0DFFFFFF,
                    Shader.TileMode.CLAMP));
            shaderTop = rect.top;
            shaderBottom = rect.bottom;
        }
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
        if (snow && drift != null) drawDrift(c, parent, radius);
        else if (snow) drawSnow(c, radius);
    }

    /**
     * The collected snow, column by column, on the band's top edge between its rounded corners. The ledge's place
     * in the window goes to the drift on every draw, so the snowfall above knows where its flakes come to rest.
     */
    private void drawDrift(Canvas c, RecyclerView parent, float radius) {
        float l = rect.left + radius * 0.7f, r = rect.right - radius * 0.7f;
        float y = rect.top + 0.8f * density;
        if (r - l < 32f * density) return;
        parent.getLocationInWindow(inWindow);
        drift.place(inWindow[0] + l, inWindow[0] + r, inWindow[1] + y, radius * 0.7f, density);
        if (drift.hasTree()) {
            long hour = System.currentTimeMillis() / 3_600_000L;
            if (tree == null) tree = new ChristmasTree();
            if (hour != treeHour) {
                treeHour = hour;
                java.time.LocalDate today = NewYear.today();
                treeDecorations = ChristmasTree.decorations(today);
                treeLit = ChristmasTree.starLit(today);
                treeGifts = NewYear.presents(today);
            }
            // behind the drift, which is drawn next and so lies in front of its trunk
            tree.draw(c, l + drift.treeX() * density, y, density, drift.treeLoad(), treeDecorations, treeLit, treeGifts,
                    (android.os.SystemClock.uptimeMillis() % 3_600_000L) / 1000f);
        }
        int n = drift.bins();
        float bin = (r - l) / n;
        snowPath.rewind();
        snowPath.moveTo(l, y);
        float px = l, py = y;
        for (int i = 0; i < n; i++) {
            float x = l + (i + 0.5f) * bin, top = y - drift.column(i) * density;
            // a curve through the midpoints, so the columns read as one soft surface
            snowPath.quadTo(px, py, (px + x) / 2f, (py + top) / 2f);
            px = x;
            py = top;
        }
        snowPath.quadTo(px, py, r, y);
        snowPath.close();
        snowFor.setEmpty();
        snowPaint.setColor(0xF4FFFFFF);
        c.drawPath(snowPath, snowPaint);
    }

    /**
     * Snow lying on the band's top edge between its rounded corners: flat along the edge underneath and piled in
     * low, uneven drifts on top, thinning to nothing at both ends. The drifts come from the position along the
     * band, so they stay put from one draw to the next.
     */
    private void drawSnow(Canvas c, float radius) {
        if (!snowFor.equals(rect)) {
            snowFor.set(rect);
            float l = rect.left + radius * 0.7f, r = rect.right - radius * 0.7f;
            float y = rect.top + 0.8f * density;      // just over the band's edge line
            snowPath.rewind();
            snowPath.moveTo(l, y);
            float x = l;
            int i = 0;
            while (x < r - 1f) {
                float span = (34f + 46f * unit(i * 2)) * density;
                float next = Math.min(r, x + span);
                if (r - next < 22f * density) next = r;
                // lower drifts near the two ends, so the snow tapers into the corners
                float edge = Math.min(1f, Math.min((x + next) / 2f - l, r - (x + next) / 2f) / (90f * density));
                float height = (2.6f + 4.6f * unit(i * 2 + 1)) * density * (0.35f + 0.65f * edge);
                float dip = next == r ? 0f : (0.9f + 1.3f * unit(i * 2 + 7)) * density;
                snowPath.quadTo((x + next) / 2f, y - height * 2f + dip, next, y - dip);
                x = next;
                i++;
            }
            snowPath.close();
        }
        snowPaint.setColor(0xF4FFFFFF);
        c.drawPath(snowPath, snowPaint);
    }

    /** A fixed pseudo-random value in 0..1 for the i-th mound. */
    private static float unit(int i) {
        int h = i * 0x9E3779B1 + 0x7F4A7C15;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0xFFFF) / 65535f;
    }
}
