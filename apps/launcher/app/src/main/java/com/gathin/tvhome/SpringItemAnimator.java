package com.gathin.tvhome;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

/**
 * The grid's item animator in Edit Home: a tile that changes place slides there on the same spatial
 * spring the focus indicator and the widgets move on, so carrying an app reads like carrying a widget.
 * Only moves animate; a rebind (a tile crossing the dock edge) changes in place. Outside Edit Home the
 * grid has no item animator at all.
 */
final class SpringItemAnimator extends DefaultItemAnimator {

    private final Motion.Spec spring;

    SpringItemAnimator(boolean reducedMotion) {
        spring = Motion.DEFAULT_SPATIAL.reduced(reducedMotion);
        setSupportsChangeAnimations(false);
        setMoveDuration(spring.durationMs());
        setAddDuration(0);
        setRemoveDuration(0);
    }

    @Override
    public boolean animateMove(@NonNull RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
        boolean pending = super.animateMove(holder, fromX, fromY, toX, toY);
        // super reset the view's animator to its own ease; the move it starts later reuses that animator.
        holder.itemView.animate().setInterpolator(spring.interpolator());
        return pending;
    }
}
