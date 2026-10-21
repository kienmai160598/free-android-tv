package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.view.animation.Interpolator;

import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * Material 3 Expressive motion: every movement is a spring, with the six tokens from Compose's
 * MotionScheme.expressive() (androidx material3 ExpressiveMotionTokens).
 *
 * Spatial springs move things: position, scale, size, corner radius. They may overshoot.
 * Effects springs change how things look: alpha and colour. They never overshoot.
 * Fast is for small parts (a tile, a pill), default for medium ones (the focus ring, the dock),
 * slow for whole-screen moves (the grid sliding up).
 *
 * Reduced motion keeps each stiffness but damps to 0.95, so nothing bounces.
 */
final class Motion {

    static final Spec FAST_SPATIAL = new Spec(0.6f, 800f);
    static final Spec DEFAULT_SPATIAL = new Spec(0.8f, 380f);
    static final Spec SLOW_SPATIAL = new Spec(0.8f, 200f);
    /** M3's standard (non-expressive) default spatial spring: barely any overshoot. */
    static final Spec STANDARD_DEFAULT_SPATIAL = new Spec(0.9f, 700f);
    static final Spec FAST_EFFECTS = new Spec(1.0f, 3800f);
    static final Spec DEFAULT_EFFECTS = new Spec(1.0f, 1600f);
    static final Spec SLOW_EFFECTS = new Spec(1.0f, 800f);

    private static final float REDUCED_DAMPING = 0.95f;

    /**
     * M3 emphasized easing for transitions between screens (app opening and returning), where a window
     * animation or a one-shot move has no retargeting to keep: decelerate for what arrives (500ms, long2),
     * accelerate for what leaves (200ms, short4).
     */
    static Interpolator emphasizedDecelerate() {
        return new android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    }

    static Interpolator emphasizedAccelerate() {
        return new android.view.animation.PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
    }

    static final long EMPHASIZED_DECELERATE_MS = 500, EMPHASIZED_ACCELERATE_MS = 200;

    private Motion() {
    }

    static final class Spec {
        final float damping;
        final float stiffness;

        Spec(float damping, float stiffness) {
            this.damping = damping;
            this.stiffness = stiffness;
        }

        Spec reduced(boolean reducedMotion) {
            return reducedMotion && damping < REDUCED_DAMPING ? new Spec(REDUCED_DAMPING, stiffness) : this;
        }

        /** Drives a SpringAnimation with this spec, so a new target keeps the current velocity. */
        SpringAnimation apply(SpringAnimation animation) {
            SpringForce force = animation.getSpring();
            if (force == null) {
                force = new SpringForce();
                animation.setSpring(force);
            }
            force.setDampingRatio(damping).setStiffness(stiffness);
            return animation;
        }

        /**
         * The same spring as a TimeInterpolator for one-shot ViewPropertyAnimator moves (fades, the
         * popup unfold), which never need to be retargeted mid-flight. Unit mass, starting at rest.
         */
        Interpolator interpolator() {
            return new SpringInterpolator(this);
        }

        /** A one-shot ViewPropertyAnimator move on this spring: its curve and its settle time. */
        android.view.ViewPropertyAnimator drive(android.view.ViewPropertyAnimator animator) {
            return animator.setInterpolator(interpolator()).setDuration(durationMs());
        }

        /**
         * A view's opacity to {@code alpha} on this spring, after {@code delayMs}, on its own animator: a view
         * has one ViewPropertyAnimator with one curve, so a fade beside a spatial move needs its own.
         */
        ObjectAnimator fade(android.view.View view, float alpha, long delayMs) {
            ObjectAnimator fade = ObjectAnimator.ofFloat(view, android.view.View.ALPHA, alpha);
            fade.setInterpolator(interpolator());
            fade.setDuration(durationMs());
            fade.setStartDelay(delayMs);
            fade.start();
            return fade;
        }

        /** How long the spring takes to settle within 0.1% of its target, for the animator's duration. */
        long durationMs() {
            return SpringInterpolator.settleMs(this);
        }
    }

    /** Closed-form position of a unit-mass spring released from rest at 0 towards 1. */
    static final class SpringInterpolator implements Interpolator {
        private static final float THRESHOLD = 0.001f;
        private final float damping;
        private final float omega;
        private final float settleSeconds;

        SpringInterpolator(Spec spec) {
            damping = spec.damping;
            omega = (float) Math.sqrt(spec.stiffness);
            settleSeconds = settleMs(spec) / 1000f;
        }

        @Override
        public float getInterpolation(float input) {
            if (input >= 1f) return 1f;
            return position(damping, omega, input * settleSeconds);
        }

        static float position(float zeta, float omega, float t) {
            if (zeta >= 1f) {
                return (float) (1 - Math.exp(-omega * t) * (1 + omega * t));
            }
            double wd = omega * Math.sqrt(1 - zeta * zeta);
            return (float) (1 - Math.exp(-zeta * omega * t)
                    * (Math.cos(wd * t) + zeta * omega / wd * Math.sin(wd * t)));
        }

        static long settleMs(Spec spec) {
            float omega = (float) Math.sqrt(spec.stiffness);
            // Walk forward until the spring stays inside the threshold for good (its envelope does).
            for (int ms = 1; ms < 5000; ms++) {
                float t = ms / 1000f;
                float envelope = (float) Math.exp(-Math.min(1f, spec.damping) * omega * t)
                        * (spec.damping >= 1f ? 1 + omega * t : 1f / (float) Math.sqrt(1 - spec.damping * spec.damping));
                if (envelope < THRESHOLD) return ms;
            }
            return 5000;
        }
    }
}
