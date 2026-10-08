package com.nicdeveloper.kuida.ui;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.TranslateAnimation;

/** Movimiento placentero global: rebotes al tocar, transiciones y entradas escalonadas. */
public final class UiMotion {

    private UiMotion() {}

    /** Rebote suave al presionar (no interfiere con OnClickListener ni TalkBack). */
    public static void pressBounce(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().cancel();
                    v.animate().scaleX(0.96f).scaleY(0.96f)
                            .setDuration(120)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                    v.animate().scaleX(1f).scaleY(1f)
                            .setDuration(320)
                            .setInterpolator(new OvershootInterpolator(2.2f))
                            .start();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f)
                            .setDuration(200)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    break;
            }
            return false; // Deja pasar el click
        });
    }

    /** Entrada escalonada de los hijos: fundido + subida suave uno tras otro. */
    public static void entranceStagger(ViewGroup parent) {
        if (parent == null) return;
        int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = parent.getChildAt(i);
            if (child == null) continue;
            child.setAlpha(0f);
            child.setTranslationY(36f);
            child.animate().cancel();
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(340)
                    .setStartDelay(Math.min(i * 55, 550))
                    .setInterpolator(new DecelerateInterpolator(1.4f))
                    .start();
        }
    }

    /** Transición entre pantallas: deslizar + fundir (ida y vuelta). */
    public static Animation screenAnim(boolean enter) {
        AnimationSet set = new AnimationSet(true);
        AlphaAnimation fade = enter
                ? new AlphaAnimation(0f, 1f)
                : new AlphaAnimation(1f, 0f);
        fade.setDuration(280);
        TranslateAnimation slide = enter
                ? new TranslateAnimation(
                        Animation.RELATIVE_TO_SELF, 0.12f,
                        Animation.RELATIVE_TO_SELF, 0f,
                        Animation.RELATIVE_TO_SELF, 0f,
                        Animation.RELATIVE_TO_SELF, 0f)
                : new TranslateAnimation(
                        Animation.RELATIVE_TO_SELF, 0f,
                        Animation.RELATIVE_TO_SELF, -0.08f,
                        Animation.RELATIVE_TO_SELF, 0f,
                        Animation.RELATIVE_TO_SELF, 0f);
        slide.setDuration(300);
        slide.setInterpolator(new DecelerateInterpolator(1.3f));
        set.addAnimation(fade);
        set.addAnimation(slide);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        return set;
    }
}
