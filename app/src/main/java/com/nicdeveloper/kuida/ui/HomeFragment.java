package com.nicdeveloper.kuida.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.nicdeveloper.kuida.R;
import com.nicdeveloper.kuida.audio.KuidaVoice;
import com.nicdeveloper.kuida.audio.GoogleVoice;
import com.nicdeveloper.kuida.audio.SoundAlertManager;
import com.nicdeveloper.kuida.audio.VoskTranscriber;
import com.nicdeveloper.kuida.data.ProfileManager;

import java.util.ArrayList;
import java.util.Locale;

public class HomeFragment extends BaseKuidaFragment {
    private static final int SAMPLE_RATE = 16000;
    private static final double LOUD_LEVEL = 0.30;

    private ActivityResultLauncher<String> microphonePermission;
    private ActivityResultLauncher<String> alertMicPermission;
    private ActivityResultLauncher<String> cameraPermission;
    private AudioRecord recorder;
    private Thread meterThread;
    private volatile boolean measuring;
    private boolean startTranscriptAfterMicPermission;
    private TextToSpeech speech;
    private TextView dbView;
    private TextView dictatedView;
    private EqualizerView equalizerView;
    private VoskTranscriber transcriber;
    private boolean transcribing;
    private final StringBuilder transcriptBuffer = new StringBuilder();
    private SoundAlertManager alertManager;
    private boolean alertsOn;
    private boolean flashAllowed;
    private TextView alertBanner;
    private TextView alertStatus;
    private TextView alertHistory;
    private final java.util.ArrayList<String> recentAlerts = new java.util.ArrayList<>();

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        microphonePermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                if (startTranscriptAfterMicPermission) {
                    startTranscriptAfterMicPermission = false;
                    beginVosk();
                } else {
                    startAudioMeter();
                }
            } else {
                startTranscriptAfterMicPermission = false;
                Toast.makeText(requireContext(), "Microfono desactivado", Toast.LENGTH_SHORT).show();
            }
        });
        alertMicPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) beginAlerts();
            else if (isAdded()) Toast.makeText(requireContext(), "Sin micrófono no hay alertas", Toast.LENGTH_LONG).show();
        });
        cameraPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            flashAllowed = granted;
        });
        speech = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) speech.setLanguage(new Locale("es", "NI"));
        });
    }

    @Override
    protected void buildContent(Context context) {
        if (ProfileManager.PROFILE_BLIND.equals(ProfileManager.selectedProfile(context))) {
            androidx.navigation.NavOptions navOptions = new androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.homeFragment, true)
                    .build();
            try {
                androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.visionFragment, null, navOptions);
                return;
            } catch (Exception ignored) {}
        }

        // Encabezado superior KUIDA
        screenTopBar(context, "KUIDA");

        // Lógica por perfil: transcripción, medidor y alertas de sonido solo tienen
        // sentido para quien NO oye (personas sordas o hipoacúsicas, asistencia completa).
        // Un mudo oye perfectamente: no necesita que le transcriban ni le avisen sonidos.
        boolean needsHearing = ProfileManager.needsHearingTools(context);
        boolean needsSpeech = ProfileManager.needsSpeechTools(context);
        boolean canUseLsn = ProfileManager.canUseSignLanguageCatalog(context);

        if (needsHearing) {
            // Medidor de Nivel de sonido
            dbView = metric(context, "Intensidad relativa", "Sin medir");
            card(context, "Medidor de intensidad", "Muestra si el sonido ambiente está bajo, moderado o muy alto. Lectura relativa, no decibeles.", v -> requestMicrophone());

            // Mensaje / Transcriptor de texto emitido
            dictatedView = body(context, "Aquí aparecerá lo que se diga en voz alta.");

            // Visualizador de ondas de sonido / ecualizador
            equalizerView = new EqualizerView(context);
            equalizerView.setContentDescription("Visualizador de intensidad del sonido");
            addCustomView(equalizerView, 160);

            // En vivo y Detener: solo quien no oye
            twoColumnTiles(
                    actionTile(context, R.drawable.ic_record_live, "En vivo", "Transcripcion continua", v -> startLiveTranscription()),
                    actionTile(context, R.drawable.ic_stop_privacy, "Detener", "Privacidad", v -> stopLiveTranscription())
            );
        }

        // El perfil que no habla puede escribir una frase y reproducirla para otra persona.
        if (needsSpeech) {
            addSingleActionTile(context, R.drawable.ic_tts_speaker, "Hablar texto", "Escribe una frase para reproducirla en voz alta", v -> showTextToSpeechDialog(context));
        }

        if (needsHearing) {
            // Oidos para personas sordas: el telefono escucha por ti (todo en el telefono)
            alertBanner = new TextView(context);
            alertBanner.setTextSize(20);
            alertBanner.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
            alertBanner.setGravity(android.view.Gravity.CENTER);
            alertBanner.setTextColor(android.graphics.Color.WHITE);
            alertBanner.setBackgroundColor(android.graphics.Color.parseColor("#B3261E"));
            alertBanner.setPadding(dp(16), dp(18), dp(16), dp(18));
            alertBanner.setVisibility(View.GONE);
            LinearLayout.LayoutParams bannerParams = params(-1, -2);
            bannerParams.setMargins(0, dp(4), 0, dp(10));
            content.addView(alertBanner, bannerParams);

            alertStatus = body(context, "Alertas de sonido apagadas.");
            card(context, "Alertas de sonido", "Tu teléfono escucha por ti: timbre, bebé llorando, alarmas y sirenas. Sin internet.", v -> toggleAlerts());
            alertHistory = body(context, "");
            loadAlertHistory(context);
            renderHistory();
            // Si las alertas siguen corriendo al volver a esta pantalla, reflejarlo
            if (alertsOn && alertManager != null && alertManager.isRunning()) {
                alertStatus.setText("Escuchando sonidos a tu alrededor...");
                renderHistory();
            }
        }

        // Asistente de IA por perfil (ciego: voz, persona con dificultad del habla: escribe y el asistente habla; persona sorda: usa texto)
        card(context, "Asistente de IA", "Tu asistente: pregúntale la hora, tu ubicación, una seña o ayuda.", v -> {
            try {
                androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.jarvisFragment);
            } catch (Exception ignored) {}
        });

        if (ProfileManager.showVision(context)
                && !ProfileManager.PROFILE_BLIND.equals(ProfileManager.selectedProfile(context))) {
            card(context, "Herramientas de visión", "Abre OCR, exploración visual, cámara y navegación accesible.", v -> {
                try {
                    androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.visionFragment);
                } catch (Exception ignored) {}
            });
        }

        // Módulos adicionales de apoyo a la comunicación
        if (needsSpeech) {
            card(context, "Tablero de frases rápidas", "Usa frases listas o guarda tus propias frases. Funciona sin internet y las reproduce en voz alta.", v -> showSaacDialog(context));
            card(context, "Chat Bidireccional (Texto y Voz)", "Escribe para comunicarte con una persona oyente.", v -> showDualChat(context));
            card(context, "Escribir a mano", "Dibuja una palabra con el dedo para convertirla a texto y voz.", v -> showHandwritingDialog(context));
            prefetchInkModel();
        }
        if (canUseLsn) {
            card(context, "Catálogo LSN (Señas de Nicaragua)", "244 señas con imágenes: familia, letras, números, comida y más.", v -> {
                try {
                    androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.lsnCatalogFragment);
                } catch (Exception ignored) {}
            });
        }
    }

    private com.google.mlkit.vision.digitalink.DigitalInkRecognizer inkRecognizer;
    private final Handler inkHandler = new Handler(Looper.getMainLooper());
    private Runnable inkWatchdog;

    private void showHandwritingDialog(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(4), dp(4), dp(4), dp(4));

        FingerCanvasView canvas = new FingerCanvasView(context);
        box.addView(canvas, new LinearLayout.LayoutParams(-1, dp(260)));

        final TextView result = new TextView(context);
        result.setText("Dibuja una palabra arriba.");
        result.setTextSize(18);
        result.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        result.setGravity(android.view.Gravity.CENTER);
        result.setPadding(0, dp(12), 0, dp(4));
        try {
            result.setTextColor(night());
        } catch (Exception ignored) {}
        box.addView(result, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER);
        android.widget.Button clearBtn = new android.widget.Button(context);
        clearBtn.setText("Borrar");
        clearBtn.setOnClickListener(v -> {
            canvas.clear();
            result.setText("Dibuja una palabra arriba.");
        });
        android.widget.Button goBtn = new android.widget.Button(context);
        goBtn.setText("Transcribir");
        goBtn.setOnClickListener(v -> transcribeHandwriting(canvas, result));
        row.addView(clearBtn, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(goBtn, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(row, new LinearLayout.LayoutParams(-1, -2));

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context, R.style.Theme_Kuida_Dialog)
                .setTitle("Escribe con tu dedo")
                .setView(box)
                .setNegativeButton("Cerrar", null)
                .show();
    }

    /** Fecha límite global: ninguna etapa puede colgarse en silencio. */
    private void startInkWatchdog(final TextView result) {
        stopInkWatchdog();
        inkWatchdog = () -> {
            if (isAdded() && result != null) {
                result.setText("Tardó demasiado. Revisa tu internet e inténtalo de nuevo.");
            }
        };
        inkHandler.postDelayed(inkWatchdog, 45000);
    }

    private void stopInkWatchdog() {
        if (inkWatchdog != null) inkHandler.removeCallbacks(inkWatchdog);
        inkWatchdog = null;
    }

    /** Descarga el modelo en segundo plano para que esté listo al abrir la pizarra. */
    private void prefetchInkModel() {
        try {
            com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier spec =
                    com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier.fromLanguageTag("es");
            if (spec == null) return;
            com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel model =
                    com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel.builder(spec).build();
            com.google.mlkit.common.model.RemoteModelManager.getInstance()
                    .getDownloadedModels(com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel.class)
                    .addOnSuccessListener(models -> {
                        if (!models.contains(model)) {
                            try {
                                com.google.mlkit.common.model.RemoteModelManager.getInstance()
                                        .download(model, new com.google.mlkit.common.model.DownloadConditions.Builder().build());
                            } catch (Exception ignored) {}
                        }
                    })
                    .addOnFailureListener(e -> {});
        } catch (Exception ignored) {
            // Sin Play Services: se intentará al transcribir, con mensaje claro
        }
    }

    private void transcribeHandwriting(FingerCanvasView canvas, TextView result) {
        if (!isAdded()) return;
        if (!canvas.hasInk()) {
            Toast.makeText(requireContext(), "Dibuja primero una palabra", Toast.LENGTH_SHORT).show();
            return;
        }
        result.setText("Leyendo tu letra...");
        startInkWatchdog(result);
        try {
            com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier spec =
                    com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier.fromLanguageTag("es");
            if (spec == null) {
                stopInkWatchdog();
                result.setText("Modelo de escritura no disponible.");
                return;
            }
            com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel model =
                    com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel.builder(spec).build();
            com.google.mlkit.common.model.RemoteModelManager.getInstance()
                    .getDownloadedModels(com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel.class)
                    .addOnSuccessListener(models -> {
                        if (!isAdded()) return;
                        if (models.contains(model)) {
                            recognizeInk(canvas, result);
                        } else {
                            result.setText("Descargando modelo de escritura una sola vez...");
                            com.google.mlkit.common.model.RemoteModelManager.getInstance().download(model, null)
                                    .addOnSuccessListener(v -> {
                                        if (isAdded()) recognizeInk(canvas, result);
                                    })
                                    .addOnFailureListener(e -> {
                                        if (isAdded()) {
                                            stopInkWatchdog();
                                            result.setText("Sin internet no puedo descargar el modelo. Conéctate una vez.");
                                        }
                                    });
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (isAdded()) {
                            stopInkWatchdog();
                            result.setText("No se pudo revisar el modelo.");
                        }
                    });
        } catch (Exception e) {
            stopInkWatchdog();
            result.setText("No se pudo leer.");
        }
    }

    private void recognizeInk(FingerCanvasView canvas, TextView result) {
        try {
            com.google.mlkit.vision.digitalink.Ink ink = canvas.buildInk();
            if (ink == null) {
                result.setText("No entendí la letra, prueba de nuevo.");
                return;
            }
            if (inkRecognizer == null) {
                com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier spec =
                        com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier.fromLanguageTag("es");
                inkRecognizer = com.google.mlkit.vision.digitalink.DigitalInkRecognition.getClient(
                        com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions.builder(
                                com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel.builder(spec).build())
                                .build());
            }
            inkRecognizer.recognize(ink)
                    .addOnSuccessListener(r -> {
                        if (!isAdded()) return;
                        stopInkWatchdog();
                        String text = (r.getCandidates().isEmpty() ? "" : r.getCandidates().get(0).getText()).trim();
                        if (text.isEmpty()) {
                            result.setText("No entendí la letra, prueba de nuevo.");
                            return;
                        }
                        result.setText(text);
                        result.setContentDescription("Transcripción: " + text);
                        com.nicdeveloper.kuida.audio.KuidaVoice.speak(requireContext(),
                                "La transcripción de escritura dice: " + text);
                        vibratePattern();
                    })
                    .addOnFailureListener(e -> {
                        if (isAdded()) {
                            stopInkWatchdog();
                            result.setText("No se pudo transcribir.");
                        }
                    });
        } catch (Exception e) {
            stopInkWatchdog();
            result.setText("No se pudo transcribir.");
        }
    }

    private void toggleAlerts() {
        if (alertsOn) {
            stopAlerts();
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            beginAlerts();
        } else {
            alertMicPermission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void beginAlerts() {
        if (!isAdded() || alertsOn) return;
        // El microfono es exclusivo: se pausa medidor y transcripcion
        stopAudioMeter();
        stopLiveTranscription();
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            flashAllowed = true;
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
        alertManager = new SoundAlertManager(requireContext(), new SoundAlertManager.Listener() {
            @Override public void onReady() {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    alertsOn = true;
                    if (alertStatus != null) alertStatus.setText("Escuchando sonidos a tu alrededor...");
                    Toast.makeText(requireContext(), "Alertas activadas", Toast.LENGTH_SHORT).show();
                });
            }
            @Override public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    alertsOn = false;
                    if (alertStatus != null) alertStatus.setText("Alertas de sonido apagadas.");
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                });
            }
            @Override public void onAlert(String spanishLabel, float score) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> showAlert(spanishLabel));
            }
        });
        alertManager.start();
        if (alertStatus != null) alertStatus.setText("Activando escucha...");
    }

    private void stopAlerts() {
        alertsOn = false;
        if (alertManager != null) {
            alertManager.stop();
            alertManager = null;
        }
        if (alertStatus != null) alertStatus.setText("Alertas de sonido apagadas.");
        if (alertBanner != null) alertBanner.setVisibility(View.GONE);
    }

    private void showAlert(String label) {
        if (alertBanner != null) {
            alertBanner.setText("¡" + label + "!");
            alertBanner.setContentDescription("Alerta: " + label);
            alertBanner.setVisibility(View.VISIBLE);
            alertBanner.removeCallbacks(hideBannerRunnable);
            alertBanner.postDelayed(hideBannerRunnable, 8000);
        }
        vibratePattern();
        torchBlink();
        String time = new java.text.SimpleDateFormat("HH:mm", Locale.US).format(new java.util.Date());
        recentAlerts.add(0, "• " + time + " — " + label);
        while (recentAlerts.size() > 10) recentAlerts.remove(recentAlerts.size() - 1);
        saveAlertHistory();
        renderHistory();
    }

    private final Runnable hideBannerRunnable = new Runnable() {
        @Override public void run() {
            if (alertBanner != null) alertBanner.setVisibility(View.GONE);
        }
    };

    private void renderHistory() {
        if (alertHistory == null) return;
        if (recentAlerts.isEmpty()) {
            alertHistory.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder("Historial de alertas guardado en este teléfono:\n");
        for (String a : recentAlerts) sb.append(a).append("\n");
        alertHistory.setText(sb.toString().trim());
    }

    private void loadAlertHistory(Context context) {
        recentAlerts.clear();
        String saved = context.getSharedPreferences("kuida_sound_alert_history", Context.MODE_PRIVATE)
                .getString("items", "[]");
        try {
            org.json.JSONArray items = new org.json.JSONArray(saved);
            for (int i = 0; i < items.length() && recentAlerts.size() < 10; i++) {
                recentAlerts.add(items.getString(i));
            }
        } catch (Exception ignored) {}
    }

    private void saveAlertHistory() {
        if (!isAdded()) return;
        org.json.JSONArray items = new org.json.JSONArray();
        for (String alert : recentAlerts) items.put(alert);
        requireContext().getSharedPreferences("kuida_sound_alert_history", Context.MODE_PRIVATE)
                .edit().putString("items", items.toString()).apply();
    }

    private void vibratePattern() {
        try {
            Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            long[] pattern = {0, 300, 150, 300, 150, 500};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception ignored) {}
    }

    private void torchBlink() {
        if (!flashAllowed) return;
        new Thread(() -> {
            try {
                android.hardware.camera2.CameraManager cam =
                        (android.hardware.camera2.CameraManager) requireContext().getSystemService(Context.CAMERA_SERVICE);
                String id = null;
                for (String cid : cam.getCameraIdList()) {
                    try {
                        Boolean hasFlash = cam.getCameraCharacteristics(cid)
                                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                        if (Boolean.TRUE.equals(hasFlash)) { id = cid; break; }
                    } catch (Exception ignored) {}
                }
                if (id == null) return;
                for (int i = 0; i < 3; i++) {
                    try { cam.setTorchMode(id, true); } catch (Exception ignored) {}
                    try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                    try { cam.setTorchMode(id, false); } catch (Exception ignored) {}
                    try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                }
            } catch (Exception ignored) {}
        }, "kuida-torch").start();
    }

    private void addSingleActionTile(Context c, int iconRes, String heading, String description, View.OnClickListener listener) {
        MaterialCardView tile = actionTile(c, iconRes, heading, description, listener);
        LinearLayout.LayoutParams p = params(-1, dp(100));
        p.setMargins(0, 0, 0, dp(14));
        content.addView(tile, p);
    }

    private void startLiveTranscription() {
        // Voz 100% offline con Vosk: sin app de Google, sin asistente, sin internet
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startTranscriptAfterMicPermission = true;
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        beginVosk();
    }

    private void beginVosk() {
        if (!isAdded() || transcribing) return;
        stopAudioMeter();
        transcriptBuffer.setLength(0);
        setTranscript("Activando voz offline...");
        transcriber = new VoskTranscriber(requireContext(), new VoskTranscriber.Listener() {
            @Override public void onPreparing() {
                setTranscript("Preparando voz offline por primera vez, espera...");
            }
            @Override public void onReady() {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    transcribing = true;
                    setTranscript("Escuchando sin internet... habla ahora.");
                });
            }
            @Override public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    transcribing = false;
                    setTranscript(message);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                });
            }
            @Override public void onUnsupported() {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Voz en línea en este teléfono", Toast.LENGTH_SHORT).show();
                    beginGoogleFallback();
                });
            }
            @Override public void onPartial(String text) {
                setTranscript(transcriptBuffer.length() == 0
                        ? "En vivo: " + text
                        : transcriptBuffer.toString().trim() + "\nEn vivo: " + text);
            }
            @Override public void onFinal(String text) {
                transcriptBuffer.append(text).append(" ");
                setTranscript(transcriptBuffer.toString().trim());
            }
        });
        transcriber.start();
    }

    private void setTranscript(String text) {
        if (!isAdded() || dictatedView == null) return;
        requireActivity().runOnUiThread(() -> {
            dictatedView.setText(text);
            dictatedView.setContentDescription("Transcripcion: " + text);
        });
    }

    private GoogleVoice googleFallback;
    private final Runnable restartGoogleFallback = this::beginGoogleFallback;

    private void beginGoogleFallback() {
        if (!isAdded()) return;
        transcribing = true;
        new Handler(Looper.getMainLooper()).removeCallbacks(restartGoogleFallback);
        stopAudioMeter();
        if (googleFallback != null) googleFallback.stop();
        setTranscript("Voz en línea activada en este teléfono, habla ahora...");
        googleFallback = new GoogleVoice(requireContext(), new GoogleVoice.Listener() {
            @Override public void onPartial(String text) {
                setTranscript(transcriptBuffer.length() == 0
                        ? "En vivo: " + text
                        : transcriptBuffer.toString().trim() + "\nEn vivo: " + text);
            }
            @Override public void onFinal(String text) {
                transcriptBuffer.append(text).append(" ");
                setTranscript(transcriptBuffer.toString().trim());
                if (isAdded() && transcribing) {
                    new Handler(Looper.getMainLooper()).postDelayed(restartGoogleFallback, 350);
                }
            }
            @Override public void onError(String message) {
                transcribing = false;
                setTranscript(message);
            }
        });
        googleFallback.start();
    }

    private void stopLiveTranscription() {
        transcribing = false;
        new Handler(Looper.getMainLooper()).removeCallbacks(restartGoogleFallback);
        if (googleFallback != null) {
            googleFallback.stop();
            googleFallback = null;
        }
        if (transcriber != null) {
            transcriber.stop();
            transcriber = null;
        }
        stopAudioMeter();
        if (dictatedView != null) dictatedView.setText("Transcripción detenida.");
    }

    private void requestMicrophone() {
        if (measuring) {
            stopAudioMeter();
            return;
        }
        stopLiveTranscription();
        stopAlerts();
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startAudioMeter();
        } else {
            startTranscriptAfterMicPermission = false;
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startAudioMeter() {
        if (measuring) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) return;
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer);
        recorder.startRecording();
        measuring = true;
        meterThread = new Thread(() -> readAudioLoop(minBuffer), "kuida-home-meter");
        meterThread.start();
    }

    private void readAudioLoop(int bufferSize) {
        short[] buffer = new short[bufferSize / 2];
        while (measuring && recorder != null) {
            int read = recorder.read(buffer, 0, buffer.length);
            if (read > 0) {
                double sum = 0;
                for (int i = 0; i < read; i++) sum += buffer[i] * buffer[i];
                double rms = Math.sqrt(sum / read);
                double relativeLevel = Math.min(1.0, rms / Short.MAX_VALUE);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> updateSoundLevel(relativeLevel));
            }
        }
    }

    private void updateSoundLevel(double level) {
        String state = level >= LOUD_LEVEL ? "Muy alto" : level >= 0.08 ? "Moderado" : "Bajo";
        if (dbView != null) {
            dbView.setText("Intensidad relativa\n" + state);
            dbView.setContentDescription("Intensidad relativa del sonido: " + state);
        }
        if (equalizerView != null) equalizerView.setLevel(level * 100.0);
        if (level >= LOUD_LEVEL && System.currentTimeMillis() - lastNoiseVibrationAt > 1200) {
            lastNoiseVibrationAt = System.currentTimeMillis();
            vibrate();
        }
    }

    private void stopAudioMeter() {
        measuring = false;
        if (recorder != null) {
            try {
                recorder.stop();
                recorder.release();
            } catch (Exception ignored) {}
            recorder = null;
        }
        if (dbView != null) dbView.setText("Intensidad relativa\nMedición detenida");
    }

    private long lastNoiseVibrationAt;

    private void vibrate() {
        Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(90);
        }
    }

    private void speak(String text) {
        if (speech != null) speech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kuida-tts");
    }

    private void showTextToSpeechDialog(Context context) {
        android.widget.EditText input = new android.widget.EditText(context);
        input.setHint("Escribe el mensaje que quieres comunicar...");
        input.setMinLines(2);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context, R.style.Theme_Kuida_Dialog)
                .setTitle("Hablar texto en voz alta")
                .setView(input)
                .setPositiveButton("Reproducir", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) speak(text);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showSaacDialog(Context context) {
        java.util.ArrayList<String> options = new java.util.ArrayList<>(java.util.Arrays.asList(
                "Necesito agua", "Tengo hambre / Comida", "Siento dolor / Auxilio", "Deseo ir al baño", "Necesito mis medicamentos"));
        java.util.Set<String> saved = context.getSharedPreferences("kuida_custom_phrases", Context.MODE_PRIVATE)
                .getStringSet("phrases", java.util.Collections.emptySet());
        options.addAll(saved);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context, R.style.Theme_Kuida_Dialog)
                .setTitle("Frases rápidas · sin internet")
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    String selected = options.get(which);
                    setTranscript(selected);
                    speak(selected);
                })
                .setNeutralButton("Agregar frase", (dialog, which) -> addCustomPhrase(context))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void addCustomPhrase(Context context) {
        android.widget.EditText input = new android.widget.EditText(context);
        input.setHint("Ej.: Necesito que me acompañes");
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context, R.style.Theme_Kuida_Dialog)
                .setTitle("Guardar frase personal")
                .setView(input)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    String phrase = input.getText().toString().trim();
                    if (phrase.isEmpty()) return;
                    android.content.SharedPreferences prefs = context.getSharedPreferences("kuida_custom_phrases", Context.MODE_PRIVATE);
                    java.util.Set<String> phrases = new java.util.HashSet<>(prefs.getStringSet("phrases", java.util.Collections.emptySet()));
                    if (phrases.size() >= 50 && !phrases.contains(phrase)) {
                        Toast.makeText(context, "Puedes guardar hasta 50 frases.", Toast.LENGTH_SHORT).show();
                    } else {
                        phrases.add(phrase);
                        prefs.edit().putStringSet("phrases", phrases).apply();
                    }
                    showSaacDialog(context);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showDualChat(Context context) {
        android.widget.EditText input = new android.widget.EditText(context);
        input.setHint("Escribe lo que deseas decir...");
        input.setTextColor(night());
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context, R.style.Theme_Kuida_Dialog)
                .setTitle("Chat Bidireccional Accesible")
                .setView(input)
                .setPositiveButton("Reproducir Voz (TTS)", (dialog, which) -> {
                    String txt = input.getText().toString();
                    if (!txt.trim().isEmpty()) {
                        setTranscript("Mensaje emitido: " + txt);
                        speak(txt);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public void onDestroy() {
        stopInkWatchdog();
        stopAlerts();
        stopAudioMeter();
        stopLiveTranscription();
        try {
            if (inkRecognizer != null) inkRecognizer.close();
        } catch (Exception ignored) {}
        if (speech != null) {
            speech.stop();
            speech.shutdown();
        }
        super.onDestroy();
    }
}
