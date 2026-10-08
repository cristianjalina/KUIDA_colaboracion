package com.nicdeveloper.kuida.audio;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Motor de voz único y precalentado: se inicializa en el splash para que
 * la primera locución (puerta de acceso) suene casi al instante.
 * Vive todo el proceso: crearlo una vez evita los 3-5s de arranque en frío.
 */
public final class KuidaTts {

    private static final String TAG = "KuidaTts";
    private static TextToSpeech tts;
    private static boolean ready;
    private static boolean initializing;
    private static final List<Runnable> pendingReady = new ArrayList<>();
    private static final Map<String, List<Runnable>> startCallbacks = new HashMap<>();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private KuidaTts() {}

    /** Idempotente: llamar cuanto antes (Splash) y en cada pantalla que hable. */
    public static synchronized void init(Context context) {
        init(context, null);
    }

    public static synchronized void init(Context context, Runnable onReady) {
        if (onReady != null) {
            if (ready) {
                main.post(onReady);
            } else {
                pendingReady.add(onReady);
            }
        }
        if (tts != null || initializing || context == null) return;
        initializing = true;
        final Context app = context.getApplicationContext();
        try {
            tts = new TextToSpeech(app, status -> {
                synchronized (KuidaTts.class) {
                    initializing = false;
                    if (status != TextToSpeech.SUCCESS || tts == null) {
                        tts = null;
                        return;
                    }
                    Locale[] candidates = {new Locale("es", "ES"), new Locale("es", "US"),
                            new Locale("es", "MX"), Locale.getDefault()};
                    for (Locale loc : candidates) {
                        try {
                            int r = tts.isLanguageAvailable(loc);
                            if (r == TextToSpeech.LANG_AVAILABLE
                                    || r == TextToSpeech.LANG_COUNTRY_AVAILABLE
                                    || r == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE) {
                                tts.setLanguage(loc);
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String utteranceId) {
                            dispatchStart(utteranceId);
                        }
                        @Override public void onDone(String utteranceId) {}
                        @Override public void onError(String utteranceId) {}
                    });
                    ready = true;
                    for (Runnable r : pendingReady) main.post(r);
                    pendingReady.clear();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "No se pudo crear TTS: " + e.getMessage());
            initializing = false;
            tts = null;
        }
    }

    private static void dispatchStart(String utteranceId) {
        List<Runnable> list;
        synchronized (KuidaTts.class) {
            list = startCallbacks.remove(utteranceId);
        }
        if (list != null) {
            for (Runnable r : list) {
                try {
                    r.run();
                } catch (Exception ignored) {}
            }
        }
    }

    public static synchronized boolean isReady() {
        return ready && tts != null;
    }

    /**
     * Habla de inmediato si el motor está listo. onStart se ejecuta al
     * comenzar el audio (hilo del motor). Devuelve false si aún no está listo.
     */
    public static boolean speak(String text, String utteranceId, Runnable onStart) {
        TextToSpeech engine;
        synchronized (KuidaTts.class) {
            if (!ready || tts == null || text == null || text.trim().isEmpty()) return false;
            engine = tts;
            if (onStart != null) {
                List<Runnable> list = startCallbacks.get(utteranceId);
                if (list == null) {
                    list = new ArrayList<>();
                    startCallbacks.put(utteranceId, list);
                }
                list.add(onStart);
            }
        }
        try {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "speak fallo: " + e.getMessage());
            return false;
        }
    }

    public static void stop() {
        try {
            if (tts != null) tts.stop();
        } catch (Exception ignored) {}
    }
}
