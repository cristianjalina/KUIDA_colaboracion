package com.nicdeveloper.kuida.audio;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;

import com.nicdeveloper.kuida.BuildConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Voz premium de Jarvis con Fish Audio (nube). Si no hay key, internet
 * o la API falla, el llamador debe usar el TTS local. Nunca revienta.
 */
public final class FishVoice {

    public interface Callback {
        void onDone();
        void onError();
    }

    private static final String TAG = "KuidaFish";
    private static final String ENDPOINT = "https://api.fish.audio/v1/tts";

    private static MediaPlayer player;

    private FishVoice() {}

    public static boolean hasKey() {
        try {
            return !BuildConfig.FISH_API_KEY.isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void speak(Context context, String text, Callback callback) {
        stop();
        if (!hasKey() || text == null || text.trim().isEmpty() || text.length() > 600) {
            callback.onError();
            return;
        }
        final String clean = text.trim();
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(20000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.FISH_API_KEY);
                String voice = "";
                try {
                    if (!BuildConfig.FISH_VOICE_ID.isEmpty()) {
                        voice = ",\"reference_id\":\"" + BuildConfig.FISH_VOICE_ID + "\"";
                    }
                } catch (Exception ignored) {}
                String body = "{\"text\":\"" + escape(clean) + "\",\"format\":\"mp3\"" + voice + "}";
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
                if (conn.getResponseCode() >= 400) {
                    Log.w(TAG, "Fish HTTP " + conn.getResponseCode());
                    callback.onError();
                    return;
                }
                File tmp = new File(app.getCacheDir(), "jarvis_voice.mp3");
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                if (tmp.length() < 1024) {
                    callback.onError();
                    return;
                }
                player = new MediaPlayer();
                player.setDataSource(tmp.getAbsolutePath());
                player.prepare();
                player.setOnCompletionListener(mp -> {
                    stop();
                    callback.onDone();
                });
                player.setOnErrorListener((mp, what, extra) -> {
                    stop();
                    callback.onError();
                    return true;
                });
                player.start();
            } catch (Exception e) {
                Log.w(TAG, "Fish fallo, usando voz local: " + e.getMessage());
                stop();
                callback.onError();
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "kuida-fish").start();
    }

    public static void stop() {
        try {
            if (player != null) {
                try { player.stop(); } catch (Exception ignored) {}
                try { player.release(); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        player = null;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
