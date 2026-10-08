package com.nicdeveloper.kuida.audio;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import com.nicdeveloper.kuida.BuildConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Voz de Kuida: voz humana (Fish Audio) con internet, voz del sistema sin internet.
 * Nunca se queda muda: si algo falla, usa el telefono.
 */
public final class KuidaVoice {

    private static final String TAG = "KuidaVoice";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static TextToSpeech systemTts;
    private static MediaPlayer player;

    private KuidaVoice() {}

    public static void speak(Context context, String text) {
        if (context == null || text == null || text.trim().isEmpty()) return;
        final Context app = context.getApplicationContext();
        final String clean = AiText(text);
        if (!BuildConfig.FISH_API_KEY.isEmpty() && online(app)) {
            executor.execute(() -> speakFish(app, clean));
        } else {
            systemSpeak(app, clean);
        }
    }

    public static void stop() {
        try {
            if (player != null) {
                player.stop();
                player.release();
                player = null;
            }
        } catch (Exception ignored) {}
        try {
            if (systemTts != null) systemTts.stop();
        } catch (Exception ignored) {}
    }

    private static String AiText(String text) {
        String s = com.nicdeveloper.kuida.data.AiClient.cleanMarkdown(text);
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private static boolean online(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network net = cm.getActiveNetwork();
                if (net == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            }
            return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void speakFish(Context app, String text) {
        try {
            File dir = new File(app.getCacheDir(), "fishvoice");
            if (!dir.exists()) dir.mkdirs();
            File cached = new File(dir, md5(text + "|" + BuildConfig.FISH_VOICE_ID) + ".mp3");
            if (!cached.exists()) {
                byte[] audio = requestFish(text);
                if (audio == null || audio.length < 1000) {
                    systemSpeak(app, text);
                    return;
                }
                try (FileOutputStream out = new FileOutputStream(cached)) {
                    out.write(audio);
                }
            }
            playFile(app, cached, text);
        } catch (Exception e) {
            Log.e(TAG, "Fish fallo, voz del sistema: " + e.getMessage());
            systemSpeak(app, text);
        }
    }

    private static byte[] requestFish(String text) throws Exception {
        URL url = new URL("https://api.fish.audio/v1/tts");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.FISH_API_KEY);
        conn.setRequestProperty("Content-Type", "application/json");
        String body = "{\"text\":" + jsonEscape(text)
                + ",\"format\":\"mp3\",\"mp3_bitrate\":128"
                + ",\"reference_id\":" + jsonEscape(BuildConfig.FISH_VOICE_ID)
                + ",\"normalize\":true,\"chunk_length\":200,\"latency\":\"normal\"}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        if (conn.getResponseCode() != 200) return null;
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static void playFile(Context app, File file, String fallbackText) {
        try {
            stopPlayer();
            if (systemTts != null) systemTts.stop();
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            player.setOnCompletionListener(mp -> stopPlayer());
            player.setOnErrorListener((mp, what, extra) -> {
                stopPlayer();
                systemSpeak(app, fallbackText);
                return true;
            });
            player.start();
        } catch (Exception e) {
            Log.e(TAG, "No se pudo reproducir voz: " + e.getMessage());
            systemSpeak(app, fallbackText);
        }
    }

    private static void stopPlayer() {
        try {
            if (player != null) {
                player.stop();
                player.release();
            }
        } catch (Exception ignored) {}
        player = null;
    }

    private static synchronized void systemSpeak(Context app, String text) {
        try {
            stopPlayer();
            if (systemTts == null) {
                systemTts = new TextToSpeech(app, status -> {
                    if (status == TextToSpeech.SUCCESS) {
                        try { systemTts.setLanguage(new Locale("es", "ES")); } catch (Exception ignored) {}
                    }
                });
                // Esperar medio segundo a que inicie el motor y reintentar el habla
                try { Thread.sleep(600); } catch (InterruptedException ignored) {}
            }
            systemTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kuida-voice");
        } catch (Exception e) {
            Log.e(TAG, "Voz del sistema fallo: " + e.getMessage());
        }
    }

    private static String md5(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }

    private static String jsonEscape(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ") + "\"";
    }
}
