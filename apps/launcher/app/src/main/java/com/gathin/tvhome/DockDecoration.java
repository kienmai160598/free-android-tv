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
 */
public class DockDecoration extends RecyclerView.ItemDecoration {

    /** Space between the tiles and the container's edge, on every side. */
    static final float PADDING_DP = 12f;

    private int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    DockDecoration(int dockSize, float density) {
        this.dockSize = dockSize;
        this.density = density;
        band.setColor(Palette.currentOrBaseline().surfaceContainer);   // until Home applies its scheme
    }

    void setDockSize(int size) {
        dockSize = size;
    }

    /** The scheme's surfaceContainer. */
    void setColor(int argb) {
        band.setColor(argb);
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
        }
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
    }
}
