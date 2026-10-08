package com.nicdeveloper.kuida.audio;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;

/**
 * Respaldo en línea cuando Vosk no carga (ej. emulador 16KB).
 * Usa el reconocedor del sistema. Solo se activa si lo offline falla.
 */
public class GoogleVoice {

    public interface Listener {
        void onPartial(String text);
        void onFinal(String text);
        void onError(String message);
    }

    private static final String TAG = "KuidaGoogleVoice";
    private static final long MAX_MS = 12000;

    private final Context appContext;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private volatile boolean active;
    private final StringBuilder buffer = new StringBuilder();

    public GoogleVoice(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        stop();
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            listener.onError("Este teléfono no tiene reconocimiento de voz.");
            return;
        }
        buffer.setLength(0);
        active = true;
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {
                    finish();
                }
                @Override public void onError(int error) {
                    if (!active) return;
                    finish();
                    if (buffer.length() == 0) {
                        listener.onError("No te escuché. Habla más fuerte e inténtalo de nuevo.");
                    }
                }
                @Override public void onResults(Bundle results) {
                    if (!active) return;
                    ArrayList<String> m = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (m != null && !m.isEmpty()) {
                        buffer.append(m.get(0)).append(" ");
                        listener.onFinal(buffer.toString().trim());
                    }
                    finish();
                }
                @Override public void onPartialResults(Bundle partialResults) {
                    if (!active) return;
                    ArrayList<String> m = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (m != null && !m.isEmpty() && !m.get(0).trim().isEmpty()) {
                        listener.onPartial(m.get(0).trim());
                    }
                }
                @Override public void onEvent(int eventType, Bundle params) {}
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            recognizer.startListening(intent);
            main.postDelayed(() -> {
                if (active) {
                    finish();
                    if (buffer.length() == 0) {
                        listener.onError("Se acabó el tiempo. Toca de nuevo y habla.");
                    }
                }
            }, MAX_MS);
        } catch (Exception e) {
            Log.e(TAG, "GoogleVoice fallo: " + e.getMessage());
            active = false;
            listener.onError("No se pudo escuchar. Inténtalo de nuevo.");
        }
    }

    private void finish() {
        active = false;
        try {
            if (recognizer != null) {
                recognizer.cancel();
                recognizer.destroy();
            }
        } catch (Exception ignored) {}
        recognizer = null;
    }

    public void stop() {
        active = false;
        main.removeCallbacksAndMessages(null);
        finish();
    }

    public boolean isActive() {
        return active;
    }
}
