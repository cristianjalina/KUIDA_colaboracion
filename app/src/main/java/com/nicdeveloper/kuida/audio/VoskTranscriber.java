package com.nicdeveloper.kuida.audio;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Transcripcion de voz 100% en el telefono con Vosk: sin app de Google,
 * sin asistente, sin internet. El modelo espanol viaja dentro de la app.
 */
public class VoskTranscriber {

    public interface Listener {
        void onPreparing();
        void onReady();
        void onError(String message);
        void onPartial(String text);
        void onFinal(String text);
        /** El teléfono no puede cargar la voz offline (ej. emulador 16KB). */
        default void onUnsupported() {
            onError("Este teléfono no soporta la voz offline. Prueba en otro dispositivo.");
        }
    }

    private static final String TAG = "KuidaVosk";
    private static final float SAMPLE_RATE = 16000.0f;

    private final Context appContext;
    private final Listener listener;
    private volatile boolean running;
    private Model model;
    private Recognizer recognizer;
    private SpeechService speechService;

    public VoskTranscriber(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        new Thread(this::prepareAndStart, "kuida-vosk").start();
    }

    public void stop() {
        running = false;
        try {
            if (speechService != null) {
                speechService.stop();
                speechService.shutdown();
            }
        } catch (Exception ignored) {}
        speechService = null;
        recognizer = null;
        try {
            if (model != null) model.close();
        } catch (Exception ignored) {}
        model = null;
    }

    public boolean isRunning() {
        return running && speechService != null;
    }

    private void prepareAndStart() {
        try {
            File modelDir = new File(appContext.getFilesDir(), "model-es");
            if (!modelReady(modelDir)) {
                listener.onPreparing();
                copyAssets("model-es", modelDir);
                if (!modelReady(modelDir)) {
                    running = false;
                    listener.onError("No se pudo preparar la voz offline.");
                    return;
                }
                try {
                    new File(modelDir, ".kuida_ready").createNewFile();
                } catch (Exception ignored) {}
            }
            if (!running) return;
            model = new Model(modelDir.getAbsolutePath());
            recognizer = new Recognizer(model, SAMPLE_RATE);
            speechService = new SpeechService(recognizer, SAMPLE_RATE);
            boolean listening = speechService.startListening(new RecognitionListener() {
                @Override public void onPartialResult(String hypothesis) {
                    String text = extract(hypothesis, "partial");
                    if (running && text != null && !text.trim().isEmpty()) {
                        listener.onPartial(text.trim());
                    }
                }
                @Override public void onResult(String hypothesis) {
                    String text = extract(hypothesis, "text");
                    if (running && text != null && !text.trim().isEmpty()) {
                        listener.onFinal(text.trim());
                    }
                }
                @Override public void onFinalResult(String hypothesis) {
                    String text = extract(hypothesis, "text");
                    if (running && text != null && !text.trim().isEmpty()) {
                        listener.onFinal(text.trim());
                    }
                }
                @Override public void onError(Exception e) {
                    if (running) listener.onError("Se cortó la escucha. Vuelve a tocar En vivo.");
                }
                @Override public void onTimeout() {
                    if (running) listener.onError("No se escuchó nada. Vuelve a tocar En vivo.");
                }
            });
            if (!listening) {
                running = false;
                listener.onError("Micrófono ocupado. Cierre otras apps y reintente.");
                return;
            }
            listener.onReady();
        } catch (Throwable t) {
            Log.e(TAG, "Error Vosk: " + t.getMessage());
            running = false;
            if (isNativeMissing(t)) {
                listener.onUnsupported();
            } else {
                listener.onError("No se pudo iniciar la voz offline en este teléfono."
                        + diag(t));
            }
        }
    }

    private boolean isNativeMissing(Throwable t) {
        if (t instanceof UnsatisfiedLinkError) return true;
        Throwable cause = t.getCause();
        while (cause != null) {
            if (cause instanceof UnsatisfiedLinkError) return true;
            String m = cause.getMessage();
            if (m != null && (m.contains("16KB") || m.contains("dlopen failed"))) return true;
            cause = cause.getCause();
        }
        String m = t.getMessage();
        return m != null && (m.contains("16KB") || m.contains("dlopen failed"));
    }

    /** Causa corta para diagnóstico en pantalla (solo debug). */
    private String diag(Throwable t) {
        try {
            String name = t.getClass().getSimpleName();
            String msg = t.getMessage();
            if (msg == null || msg.trim().isEmpty()) return " [" + name + "]";
            msg = msg.trim().replaceAll("\\s+", " ");
            if (msg.length() > 90) msg = msg.substring(0, 90) + "…";
            return " [" + name + ": " + msg + "]";
        } catch (Exception ignored) {
            return "";
        }
    }

    /** El modelo está listo si la copia se completó alguna vez. */
    private boolean modelReady(File dir) {
        if (new File(dir, ".kuida_ready").exists()) return true;
        return new File(dir, "am").exists()
                && new File(dir, "graph").exists()
                && new File(dir, "conf").exists();
    }

    /** Copia recursiva de assets con streams (no depende del uuid de Vosk). */
    private void copyAssets(String assetPath, File target) throws IOException {
        String[] list = appContext.getAssets().list(assetPath);
        if (list == null || list.length == 0) {
            target.getParentFile().mkdirs();
            try (InputStream in = appContext.getAssets().open(assetPath);
                 OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1 && running) {
                    out.write(buffer, 0, read);
                }
            }
            return;
        }
        target.mkdirs();
        for (String child : list) {
            if (!running) return;
            copyAssets(assetPath + "/" + child, new File(target, child));
        }
    }

    private String extract(String json, String key) {
        try {
            return new JSONObject(json).optString(key, "");
        } catch (Exception ignored) {
            return "";
        }
    }
}
