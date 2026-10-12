package org.ngram.core;

import android.view.animation.Interpolator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import org.telegram.messenger.AndroidUtilities;

/**
 * What a button does under the finger in Ngram.
 *
 * Jelly: the button is squashed (wider and flatter) and wobbles back when let go.
 * Glass: the button swells a little, like a drop, and settles back softly.
 *
 * Both are limited in pixels, so a wide card moves by the same few pixels as a small round
 * button and never pushes into its neighbours.
 */
public final class NgramMotion {

    private NgramMotion() {
    }

    /**
     * Installs Ngram's press animation on a view.
     *
     * @return false when the user chose the plain one: the caller then installs Telegram's own
     */
    public static boolean apply(View view) {
        return apply(view, null);
    }

    /**
     * The same, for a button whose glass is drawn by something else (a header, the message
     * field): {@code onPose} runs on every step so that the glass can follow the button.
     */
    public static boolean apply(View view, Runnable onPose) {
        if (view == null) {
            return true;
        }
        final int motion = NgramConfig.pressMotion;
        if (motion == NgramConfig.MOTION_PLAIN) {
            return false;
        }
        final boolean jelly = motion == NgramConfig.MOTION_JELLY;

        // [0] how far the press has gone (1 = fully pressed), [1] where the running animation started
        final float[] state = new float[2];

        final ValueAnimator press = ValueAnimator.ofFloat(0f, 1f);
        press.setDuration(jelly ? 110 : 150);
        press.setInterpolator(new DecelerateInterpolator(1.6f));
        press.addUpdateListener(a -> {
            state[0] = state[1] + (1f - state[1]) * a.getAnimatedFraction();
            pose(view, state[0], jelly);
            if (onPose != null) {
                onPose.run();
            }
        });
        press.addListener(new Steps(state, view, jelly, 1f, onPose));

        final ValueAnimator release = ValueAnimator.ofFloat(0f, 1f);
        release.setDuration(jelly ? 640 : 430);
        release.setInterpolator(new LinearInterpolator());
        release.addUpdateListener(a -> {
            final float t = a.getAnimatedFraction();
            state[0] = state[1] * (jelly ? wobble(t) : settle(t));
            pose(view, state[0], jelly);
            if (onPose != null) {
                onPose.run();
            }
        });
        release.addListener(new Steps(state, view, jelly, 0f, onPose));

        final StateListAnimator animator = new StateListAnimator();
        animator.addState(new int[]{android.R.attr.state_pressed}, press);
        animator.addState(new int[0], release);
        view.setStateListAnimator(animator);
        return true;
    }

    /**
     * A view that has just become the chosen one (a tab, a tile) swells and swings back to its
     * size. Does nothing when the user asked for plain motion.
     */
    public static void pop(View view, float amount) {
        if (view == null || NgramConfig.pressMotion == NgramConfig.MOTION_PLAIN) {
            return;
        }
        final boolean jelly = NgramConfig.pressMotion == NgramConfig.MOTION_JELLY;
        view.animate().cancel();
        view.setScaleX(1f + amount);
        view.setScaleY(1f + amount);
        view.animate().scaleX(1f).scaleY(1f).setDuration(jelly ? 620 : 420)
            .setInterpolator(t -> 1f - (jelly ? wobble(t) : settle(t))).start();
    }

    /**
     * How far a glass shape drawn for {@code view} has to grow on each side to follow the
     * view's press animation: [0] across, [1] down, in pixels.
     */
    public static void spread(View view, float width, float height, float[] out) {
        out[0] = view == null ? 0f : (view.getScaleX() - 1f) * width / 2f;
        out[1] = view == null ? 0f : (view.getScaleY() - 1f) * height / 2f;
    }

    /** For value animators that run from "pressed" to "rest": overshoots rest and swings back. */
    public static final Interpolator WOBBLE = t -> 1f - wobble(t);

    /** A spring that swings past rest several times: 1 at the start, 0 at the end. */
    private static float wobble(float t) {
        return (float) (Math.exp(-5.4f * t) * Math.cos(15.5f * t));
    }

    /** A soft return with one small swing past rest. */
    private static float settle(float t) {
        return (float) (Math.exp(-6.2f * t) * Math.cos(6.5f * t));
    }

    private static void pose(View view, float p, boolean jelly) {
        final int w = view.getWidth(), h = view.getHeight();
        if (jelly) {
            final float ax = w > 0 ? Math.min(0.085f, AndroidUtilities.dp(9) / (float) w) : 0.06f;
            final float ay = h > 0 ? Math.min(0.12f, AndroidUtilities.dp(6) / (float) h) : 0.08f;
            view.setScaleX(1f + ax * p);
            view.setScaleY(1f - ay * p);
        } else {
            final int side = Math.max(w, h);
            final float a = side > 0 ? Math.min(0.075f, AndroidUtilities.dp(5) / (float) side) : 0.05f;
            view.setScaleX(1f + a * p);
            view.setScaleY(1f + a * p);
        }
    }

    private static final class Steps extends AnimatorListenerAdapter {
        private final float[] state;
        private final View view;
        private final boolean jelly;
        private final float end;
        private final Runnable onPose;
        private boolean cancelled;

        Steps(float[] state, View view, boolean jelly, float end, Runnable onPose) {
            this.state = state;
            this.view = view;
            this.jelly = jelly;
            this.end = end;
            this.onPose = onPose;
        }

        @Override
        public void onAnimationStart(Animator animation) {
            cancelled = false;
            state[1] = state[0];
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            cancelled = true;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (!cancelled) {
                state[0] = end;
                pose(view, end, jelly);
                if (onPose != null) {
                    onPose.run();
                }
            }
        }
    }
}
