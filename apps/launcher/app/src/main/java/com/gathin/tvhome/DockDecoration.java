package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Draws the M3 container behind the first (dock) row and adds a gap below it. The container is
 * concentric with the tiles it holds: its radius is the tiles' resting radius plus its padding.
 * It is see-through, so the backdrop shows in it, with a hairline edge that keeps its shape on a dark scheme.
 */
public class DockDecoration extends RecyclerView.ItemDecoration {

    /** Space between the tiles and the container's edge, on every side. */
    static final float PADDING_DP = 12f;
    private static final int BAND_ALPHA = 0xB3, EDGE_ALPHA = 0x80;

    private int dockSize;
    private boolean rtl;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    DockDecoration(int dockSize, float density) {
        this.dockSize = dockSize;
        this.density = density;
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, density / 2f));   // half a dp: one pixel on a 1080p TV
        setPalette(Palette.currentOrBaseline());   // until Home applies its scheme
    }

    void setDockSize(int size) {
        dockSize = size;
    }

    void setPalette(Palette p) {
        band.setColor(Palette.alpha(p.surfaceContainerHigh, BAND_ALPHA));
        edge.setColor(Palette.alpha(p.outlineVariant, EDGE_ALPHA));
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            // Nothing above (the item's own 16dp holds the container's 12dp); below, 12dp more so the container
            // ends 32dp above the next row's tiles, the grid's rhythm.
            outRect.top = 0;
            outRect.bottom = (int) (12 * density);
            // A dock with fewer apps than columns sits in the middle, like Apple's; the container hugs it.
            int shift = centringShift(parent, state);
            outRect.left = shift;
            outRect.right = -shift;
        }
    }

    /** How far each dock tile moves so a short dock is centred: half the empty columns' width. */
    private int centringShift(RecyclerView parent, RecyclerView.State state) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (adapter == null) return 0;
        int tiles = 0;
        int count = Math.min(dockSize, state.getItemCount());
        for (int i = 0; i < count; i++) if (adapter.getItemViewType(i) == AppAdapter.TILE_VIEW) tiles++;
        if (tiles == 0 || tiles >= dockSize) return 0;
        float column = (parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight()) / (float) dockSize;
        int shift = Math.round((dockSize - tiles) * column / 2f);
        return rtl ? -shift : shift;
    }

    /** rtl_app_grid: the grid lays out right to left, so the tiles move left. */
    void setRtl(boolean rtl) {
        this.rtl = rtl;
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        int cardWidth = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
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
        float pad = PADDING_DP * density;
        rect.inset(-pad, -pad);
        // Concentric corners: outer radius = inner radius + padding.
        float radius = AppAdapter.cornerRadius(cardWidth, density, Shapes.TILE_REST) + pad;
        c.drawRoundRect(rect, radius, radius, band);
        float half = edge.getStrokeWidth() / 2f;
        rect.inset(half, half);
        c.drawRoundRect(rect, radius - half, radius - half, edge);
    }
}
