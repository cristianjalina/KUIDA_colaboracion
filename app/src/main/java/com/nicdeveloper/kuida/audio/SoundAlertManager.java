package com.nicdeveloper.kuida.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Escucha del telefono: clasifica sonidos con red neuronal 100% en el dispositivo,
 * sin internet. Detecta timbre, bebe llorando, alarmas, sirenas y peligros.
 */
public class SoundAlertManager {

    public interface Listener {
        void onReady();
        void onError(String message);
        void onAlert(String spanishLabel, float score);
    }

    private static final String TAG = "KuidaSound";
    private static final int SAMPLE_RATE = 16000;
    private static final int WINDOW_SAMPLES = 15600; // 0.975 segundos que pide la red
    private static final float NORMAL_THRESHOLD = 0.35f;
    private static final float DANGER_THRESHOLD = 0.25f;
    private static final long COOLDOWN_MS = 10000;

    private final Context appContext;
    private final Listener listener;
    private volatile boolean running;
    private Thread worker;
    private Interpreter interpreter;
    private String[] classNames = new String[0];

    /** Palabra inglesa -> aviso en espanol (orden = prioridad). */
    private static final String[][] SPANISH = {
            {"smoke detector", "Detector de humo"},
            {"fire alarm", "Alarma de incendio"},
            {"siren", "Sirena de emergencia"},
            {"baby cry", "Bebé llorando"},
            {"infant cry", "Bebé llorando"},
            {"scream", "Grito de auxilio"},
            {"shout", "Gritos"},
            {"crying", "Llanto"},
            {"sobbing", "Llanto"},
            {"glass", "Vidrios rotos"},
            {"gunshot", "Detonación"},
            {"explosion", "Explosión"},
            {"fire", "Fuego"},
            {"doorbell", "Timbre de la puerta"},
            {"knock", "Tocan la puerta"},
            {"telephone", "Teléfono sonando"},
            {"alarm", "Alarma sonando"},
            {"car alarm", "Alarma de auto"},
            {"bark", "Perro ladrando"},
            {"dog", "Perro cerca"},
            {"car horn", "Bocina de auto"},
            {"vehicle horn", "Bocina de auto"},
            {"thunder", "Trueno"},
    };

    private static final String[] DANGER_WORDS = {
            "smoke detector", "fire", "siren", "scream", "glass", "gunshot", "explosion",
    };

    public SoundAlertManager(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        worker = new Thread(this::loop, "kuida-sound-alerts");
        worker.start();
    }

    public void stop() {
        running = false;
        if (worker != null) {
            try { worker.join(1500); } catch (InterruptedException ignored) {}
            worker = null;
        }
        if (interpreter != null) {
            try { interpreter.close(); } catch (Exception ignored) {}
            interpreter = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void loop() {
        try {
            loadModel();
        } catch (Exception e) {
            Log.e(TAG, "No se pudo cargar la escucha: " + e.getMessage());
            running = false;
            listener.onError("No se pudo activar la escucha en este teléfono.");
            return;
        }

        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) {
            running = false;
            listener.onError("Micrófono no disponible.");
            return;
        }

        AudioRecord recorder = null;
        try {
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuffer * 2, WINDOW_SAMPLES * 2));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                listener.onError("Micrófono ocupado. Cierre otras apps que lo usen.");
                running = false;
                return;
            }
            recorder.startRecording();
            listener.onReady();

            short[] chunk = new short[3200];
            float[] window = new float[WINDOW_SAMPLES];
            int filled = 0;
            String lastLabel = "";
            long lastTime = 0;

            while (running) {
                int read = recorder.read(chunk, 0, chunk.length);
                if (read <= 0) continue;
                for (int i = 0; i < read && running; i++) {
                    window[filled++] = chunk[i] / 32768f;
                    if (filled >= WINDOW_SAMPLES) {
                        filled = 0;
                        String found = classify(window);
                        long now = System.currentTimeMillis();
                        if (found != null && (!found.equals(lastLabel) || now - lastTime > COOLDOWN_MS)) {
                            lastLabel = found;
                            lastTime = now;
                            listener.onAlert(found, 1f);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error escuchando: " + e.getMessage());
            if (running) listener.onError("Se detuvo la escucha.");
        } finally {
            running = false;
            if (recorder != null) {
                try { recorder.stop(); } catch (Exception ignored) {}
                try { recorder.release(); } catch (Exception ignored) {}
            }
        }
    }

    /** Devuelve el aviso en espanol o null si no hay nada relevante. */
    private String classify(float[] window) {
        if (interpreter == null) return null;
        try {
            float[][] input = new float[1][WINDOW_SAMPLES];
            System.arraycopy(window, 0, input[0], 0, WINDOW_SAMPLES);

            int[] outShapes = outputShapes();
            Map<Integer, Object> outputs = new HashMap<>();
            for (int i = 0; i < outShapes.length; i++) {
                outputs.put(i, new float[1][outShapes[i]]);
            }
            interpreter.runForMultipleInputsOutputs(new Object[]{input}, outputs);

            // Elegir la salida de puntajes (dimension 521). Soporta 1D/2D/3D.
            float[] scores = null;
            for (Object o : outputs.values()) {
                float[] flat = flattenLastDim(o);
                if (flat == null) continue;
                if (classNames.length > 0 && flat.length == classNames.length) {
                    scores = flat;
                    break;
                }
                if (scores == null || flat.length > scores.length) scores = flat;
            }
            if (scores == null) return null;

            // Top 3
            int[] top = {-1, -1, -1};
            for (int i = 0; i < scores.length; i++) {
                float s = scores[i];
                if (s > scores[top[0] < 0 ? 0 : top[0]] || top[0] < 0) {
                    top[2] = top[1]; top[1] = top[0]; top[0] = i;
                } else if (top[1] < 0 || s > scores[top[1]]) {
                    top[2] = top[1]; top[1] = i;
                } else if (top[2] < 0 || s > scores[top[2]]) {
                    top[2] = i;
                }
            }

            // Peligros en top 3 con umbral bajo
            for (int idx : top) {
                if (idx < 0) continue;
                String english = englishName(idx).toLowerCase(java.util.Locale.US);
                if (scores[idx] >= DANGER_THRESHOLD && isDanger(english)) {
                    String es = toSpanish(english);
                    if (es != null) return es;
                }
            }
            // Top 1 con umbral normal
            if (top[0] >= 0 && scores[top[0]] >= NORMAL_THRESHOLD) {
                return toSpanish(englishName(top[0]).toLowerCase(java.util.Locale.US));
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error clasificando: " + e.getMessage());
            return null;
        }
    }

    private float[] flattenLastDim(Object o) {
        if (o instanceof float[][]) {
            float[][] a = (float[][]) o;
            return a.length > 0 ? a[0] : null;
        }
        if (o instanceof float[][][]) {
            float[][][] a = (float[][][]) o;
            return (a.length > 0 && a[0].length > 0) ? a[0][0] : null;
        }
        if (o instanceof float[]) return (float[]) o;
        return null;
    }

    private int[] outputShapes() {        int n = interpreter.getOutputTensorCount();
        int[] dims = new int[n];
        for (int i = 0; i < n; i++) {
            int[] shape = interpreter.getOutputTensor(i).shape();
            dims[i] = shape[shape.length - 1];
        }
        return dims;
    }

    private String englishName(int index) {
        if (index >= 0 && index < classNames.length) return classNames[index];
        return "";
    }

    private boolean isDanger(String english) {
        for (String w : DANGER_WORDS) if (english.contains(w)) return true;
        return false;
    }

    private String toSpanish(String english) {
        for (String[] pair : SPANISH) {
            if (english.contains(pair[0])) return pair[1];
        }
        return null;
    }

    private void loadModel() throws Exception {
        AssetFileDescriptor fd = appContext.getAssets().openFd("yamnet.tflite");
        FileInputStream in = new FileInputStream(fd.getFileDescriptor());
        MappedByteBuffer model = in.getChannel().map(
                FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
        in.close();
        fd.close();

        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(2);
        interpreter = new Interpreter(model, options);

        List<String> names = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                appContext.getAssets().open("yamnet_classes.csv"), StandardCharsets.UTF_8));
        String line;
        boolean first = true;
        while ((line = reader.readLine()) != null) {
            if (first) { first = false; continue; }
            int cut = line.indexOf(',');
            if (cut < 0) continue;
            String rest = line.substring(cut + 1);
            int cut2 = rest.indexOf(',');
            String name = cut2 >= 0 ? rest.substring(cut2 + 1) : rest;
            name = name.trim();
            if (name.startsWith("\"") && name.endsWith("\"") && name.length() > 1) {
                name = name.substring(1, name.length() - 1);
            }
            names.add(name);
        }
        reader.close();
        classNames = names.toArray(new String[0]);
        Log.i(TAG, "Escucha lista: " + classNames.length + " sonidos conocidos.");
    }
}
