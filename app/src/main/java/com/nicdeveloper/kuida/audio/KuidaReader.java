package com.nicdeveloper.kuida.audio;

import android.app.Activity;
import android.content.Context;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.nicdeveloper.kuida.data.ProfileManager;

import java.lang.ref.WeakReference;

/**
 * Lector Kuida: lector de pantalla propio para personas ciegas que NO tienen
 * TalkBack activado. Toca para escuchar qué hay, toca dos veces para usarlo.
 * Igual que TalkBack, pero dentro de Kuida. No interfiere si TalkBack está activo.
 */
public final class KuidaReader {

    private static WeakReference<View> overlayRef;
    private static long lastDownTime;
    private static float lastDownX;
    private static float lastDownY;
    private static View lastTarget;
    private static long lastSpeakTime;

    private KuidaReader() {}

    /** ¿Debe estar activo? Solo ciego + sin TalkBack del sistema + interruptor ON. */
    public static boolean shouldBeOn(Context context) {
        if (context == null) return false;
        if (!ProfileManager.PROFILE_BLIND.equals(ProfileManager.selectedProfile(context))) return false;
        if (ProfileManager.isTalkBackActive(context)) return false;
        return ProfileManager.isReaderEnabled(context);
    }

    /** Llamar en onCreate/onResume de MainActivity y al cambiar de perfil. */
    public static void refresh(Activity activity) {
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            detach();
            if (!shouldBeOn(activity)) return;
            try {
                ViewGroup content = activity.findViewById(android.R.id.content);
                if (content == null) return;
                View overlay = new View(activity);
                overlay.setFocusable(false);
                overlay.setClickable(false);
                overlay.setContentDescription(null);
                overlay.setOnTouchListener(KuidaReader::handleTouch);
                content.addView(overlay, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                overlayRef = new WeakReference<>(overlay);
                KuidaVoice.speak(activity,
                        "Lector Kuida activado. Toca la pantalla para escuchar qué hay. Toca dos veces para usarlo.");
                vibrate(activity, 120);
            } catch (Exception ignored) {}
        });
    }

    public static void detach() {
        try {
            View overlay = overlayRef != null ? overlayRef.get() : null;
            overlayRef = null;
            if (overlay != null && overlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) overlay.getParent()).removeView(overlay);
            }
        } catch (Exception ignored) {}
    }

    private static boolean handleTouch(View overlay, MotionEvent event) {
        try {
            Activity activity = (Activity) overlay.getContext();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    float x = event.getX();
                    float y = event.getY();
                    long now = System.currentTimeMillis();
                    View target = findTarget(activity, overlay, x, y);
                    // Doble toque: activar el elemento
                    if (target != null && now - lastDownTime < 350
                            && distance(x, y, lastDownX, lastDownY) < 90) {
                        lastDownTime = 0;
                        vibrate(activity, 60);
                        target.performClick();
                        return true; // Consumido: no disparar el toque simple
                    }
                    lastDownTime = now;
                    lastDownX = x;
                    lastDownY = y;
                    lastTarget = target;
                    if (target != null && now - lastSpeakTime > 450) {
                        lastSpeakTime = now;
                        KuidaVoice.speak(activity, describe(target));
                    }
                    return false; // El toque simple sigue su curso normal
                }
                case MotionEvent.ACTION_MOVE: {
                    if (event.getPointerCount() != 1) return false;
                    float x = event.getX();
                    float y = event.getY();
                    long now = System.currentTimeMillis();
                    View target = findTarget(activity, overlay, x, y);
                    if (target != null && target != lastTarget && now - lastSpeakTime > 450) {
                        lastTarget = target;
                        lastSpeakTime = now;
                        KuidaVoice.speak(activity, describe(target));
                    }
                    return false;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Busca la vista más profunda con algo que decir bajo el dedo. */
    private static View findTarget(Activity activity, View overlay, float x, float y) {
        try {
            ViewGroup content = activity.findViewById(android.R.id.content);
            if (content == null) return null;
            int[] rootLoc = new int[2];
            content.getLocationOnScreen(rootLoc);
            return hitTest(content, overlay, rootLoc[0] + x, rootLoc[1] + y);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static View hitTest(View view, View overlay, float sx, float sy) {
        if (view == null || view == overlay || view.getVisibility() != View.VISIBLE || !view.isShown()) {
            return null;
        }
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        float w = view.getWidth();
        float h = view.getHeight();
        if (sx < loc[0] || sy < loc[1] || sx > loc[0] + w || sy > loc[1] + h) return null;
        if (view instanceof ViewGroup && !(view instanceof android.widget.AdapterView)) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View hit = hitTest(group.getChildAt(i), overlay, sx, sy);
                if (hit != null) return hit;
            }
        }
        return speaks(view) ? view : null;
    }

    private static boolean speaks(View view) {
        CharSequence cd = view.getContentDescription();
        if (cd != null && cd.length() > 0) return true;
        if (view.isClickable()) return true;
        if (view instanceof TextView) {
            CharSequence t = ((TextView) view).getText();
            return t != null && t.length() > 0 && t.length() < 220;
        }
        return view instanceof ImageView;
    }

    private static String describe(View view) {
        CharSequence cd = view.getContentDescription();
        if (cd != null && !cd.toString().trim().isEmpty()) {
            return cd.toString().trim() + (view.isClickable() ? ". Toca dos veces para usarlo." : ".");
        }
        if (view instanceof TextView) {
            CharSequence t = ((TextView) view).getText();
            if (t != null && !t.toString().trim().isEmpty()) return t.toString().trim() + ".";
        }
        if (view instanceof ImageView) return "Imagen.";
        if (view.isClickable()) return "Botón. Toca dos veces para usarlo.";
        return "Elemento.";
    }

    private static void vibrate(Context context, long ms) {
        try {
            Vibrator vib = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null && vib.hasVibrator()) vib.vibrate(ms);
        } catch (Exception ignored) {}
    }
}
