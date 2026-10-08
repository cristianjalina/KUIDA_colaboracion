package com.nicdeveloper.kuida.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.nicdeveloper.kuida.MainActivity;
import com.nicdeveloper.kuida.R;
import com.nicdeveloper.kuida.audio.FishVoice;
import com.nicdeveloper.kuida.audio.JarvisEngine;
import com.nicdeveloper.kuida.audio.VoskTranscriber;
import com.nicdeveloper.kuida.data.AiClient;
import com.nicdeveloper.kuida.data.ProfileManager;

import java.util.Locale;

/** Jarvis: habla (ciego), escribe (mudo) o muestra (sordo). Órdenes offline + voz premium. */
public class JarvisFragment extends Fragment {

    private LinearLayout chatBox;
    private ScrollView chatScroll;
    private EditText input;
    private android.widget.ProgressBar voiceBar;
    private VoskTranscriber transcriber;
    private com.nicdeveloper.kuida.audio.GoogleVoice googleCommand;
    private ActivityResultLauncher<String> cameraPermission;
    private Boolean pendingFlash;
    private TextToSpeech systemTts;
    private AiClient aiClient;
    private boolean awaitingCommand;
    private ActivityResultLauncher<String> micPermission;
    private TextView assistantTranscript;
    private ScrollView assistantTranscriptScroll;
    private final StringBuilder assistantTranscriptText = new StringBuilder();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        aiClient = new AiClient();
        micPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) startListening();
            else if (isAdded()) Toast.makeText(requireContext(), "Sin micrófono, escríbeme", Toast.LENGTH_SHORT).show();
        });
        cameraPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (pendingFlash != null) {
                boolean on = pendingFlash;
                pendingFlash = null;
                if (granted) setTorch(on);
                else if (isAdded()) correction("Sin permiso de cámara no puedo usar la linterna.");
            }
        });
        systemTts = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                try { systemTts.setLanguage(new Locale("es", "NI")); } catch (Exception ignored) {}
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        assistantTranscriptText.setLength(0);
        assistantTranscript = null;
        assistantTranscriptScroll = null;
        String profile = ProfileManager.selectedProfile(c);
        boolean isBlind = ProfileManager.PROFILE_BLIND.equals(profile);
        boolean isMute = ProfileManager.needsSpeechTools(c);

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(res(R.color.kuida_surface, "#FFFFFF"));

        root.addView(topBar(c), new LinearLayout.LayoutParams(-1, -2));

        TextView hint = new TextView(c);
        hint.setText(isBlind ? "Toca Hablar y pídeme lo que sea."
                : isMute ? "Escríbeme y yo hablo por ti."
                : "Escríbeme o háblame: te muestro todo en pantalla.");
        hint.setTextSize(13);
        hint.setTextColor(res(R.color.kuida_slate, "#4C7280"));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(20), 0, dp(20), dp(8));
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        if (ProfileManager.needsHearingTools(c)) {
            TextView transcriptTitle = new TextView(c);
            transcriptTitle.setText("TRANSCRIPTOR DE VOZ DEL ASISTENTE");
            transcriptTitle.setTextSize(12);
            transcriptTitle.setTypeface(Typeface.create("sans", Typeface.BOLD));
            transcriptTitle.setTextColor(res(R.color.kuida_teal, "#008080"));
            transcriptTitle.setPadding(dp(16), dp(8), dp(16), dp(2));
            root.addView(transcriptTitle, new LinearLayout.LayoutParams(-1, -2));

            assistantTranscript = new TextView(c);
            assistantTranscript.setText("Aquí aparecerá todo lo que diga el asistente.");
            assistantTranscript.setTextSize(15);
            assistantTranscript.setTextColor(res(R.color.kuida_on_surface, "#0B2228"));
            assistantTranscript.setBackgroundColor(res(R.color.kuida_card_bg, "#EDF3F3"));
            assistantTranscript.setPadding(dp(16), dp(10), dp(16), dp(10));
            assistantTranscript.setContentDescription("Transcripción de voz del asistente");
            assistantTranscriptScroll = new ScrollView(c);
            assistantTranscriptScroll.setFillViewport(false);
            assistantTranscriptScroll.addView(assistantTranscript, new ScrollView.LayoutParams(-1, -2));
            root.addView(assistantTranscriptScroll, new LinearLayout.LayoutParams(-1, dp(104)));
        }

        chatScroll = new ScrollView(c);
        chatScroll.setFillViewport(false);
        chatBox = new LinearLayout(c);
        chatBox.setOrientation(LinearLayout.VERTICAL);
        chatBox.setPadding(dp(16), dp(4), dp(16), dp(8));
        chatScroll.addView(chatBox, new ScrollView.LayoutParams(-1, -2));
        root.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        addSuggestionChips(c, root);

        // Barra de carga: se muestra mientras prepara, escucha o piensa
        voiceBar = new android.widget.ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        voiceBar.setIndeterminate(true);
        try {
            voiceBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(
                    res(R.color.kuida_teal, "#008080")));
        } catch (Exception ignored) {}
        voiceBar.setVisibility(View.GONE);
        voiceBar.setContentDescription("El asistente está trabajando");
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, -2);
        barParams.setMargins(dp(20), 0, dp(20), dp(4));
        root.addView(voiceBar, barParams);

        // Fila inferior: micrófono (no para mudos) + texto (no para ciegos) + enviar
        LinearLayout inputRow = new LinearLayout(c);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(dp(12), dp(8), dp(12), dp(16));

        if (!isMute) {
            ImageView mic = new ImageView(c);
            mic.setImageResource(R.drawable.ic_mic_teal);
            mic.setContentDescription("Hablar con el asistente");
            mic.setClickable(true);
            mic.setFocusable(true);
            GradientDrawable micBg = new GradientDrawable();
            micBg.setShape(GradientDrawable.OVAL);
            micBg.setColor(res(R.color.kuida_button, "#008080"));
            mic.setBackground(micBg);
            mic.setPadding(dp(14), dp(14), dp(14), dp(14));
            mic.setOnClickListener(v -> {
                UiMotion.pressBounce(v);
                toggleListening();
            });
            inputRow.addView(mic, new LinearLayout.LayoutParams(dp(56), dp(56)));
        }

        if (!isBlind) {
            input = new EditText(c);
            input.setHint("Escríbele al asistente...");
            input.setSingleLine(true);
            input.setTextSize(15);
            input.setTextColor(res(R.color.kuida_on_surface, "#0B2228"));
            input.setHintTextColor(res(R.color.kuida_slate, "#4C7280"));
            GradientDrawable inputBg = new GradientDrawable();
            inputBg.setShape(GradientDrawable.RECTANGLE);
            inputBg.setCornerRadius(dp(16));
            inputBg.setColor(res(R.color.kuida_card_bg, "#EDF3F3"));
            input.setBackground(inputBg);
            input.setPadding(dp(16), dp(12), dp(16), dp(12));
            input.setContentDescription("Escribe tu mensaje para el asistente");
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, -2, 1);
            ip.setMargins(isMute ? 0 : dp(8), 0, dp(8), 0);
            inputRow.addView(input, ip);

            MaterialButton send = new MaterialButton(c);
            send.setText("Enviar");
            send.setCornerRadius(dp(16));
            send.setBackgroundColor(res(R.color.kuida_button, "#008080"));
            send.setTextColor(0xFFFFFFFF);
            send.setContentDescription("Enviar mensaje");
            send.setOnClickListener(v -> {
                String text = input.getText().toString().trim();
                if (!text.isEmpty()) {
                    input.setText("");
                    processUserText(text);
                }
            });
            inputRow.addView(send, new LinearLayout.LayoutParams(-2, -2));
        } else {
            TextView voiceHint = new TextView(c);
            voiceHint.setText("Toca el micrófono y habla");
            voiceHint.setTextSize(14);
            voiceHint.setTextColor(res(R.color.kuida_slate, "#4C7280"));
            voiceHint.setPadding(dp(12), 0, 0, 0);
            inputRow.addView(voiceHint, new LinearLayout.LayoutParams(0, -2, 1));
        }
        root.addView(inputRow, new LinearLayout.LayoutParams(-1, -2));

        // Saludo inicial del asistente (texto; voz solo si el perfil oye)
        root.post(() -> {
            if (!isAdded()) return;
            String hello = "Hola, soy el asistente de Kuida. Pídeme la hora, tu ubicación, una seña o ayuda.";
            addJarvisBubble(hello, null, null);
            speakOut(hello);
        });
        return root;
    }

    // ---------- Barra superior ----------

    private LinearLayout topBar(Context c) {
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(10));
        ImageView menu = new ImageView(c);
        menu.setImageResource(R.drawable.ic_menu_hamburger);
        menu.setColorFilter(res(R.color.kuida_topicon, "#008080"));
        menu.setContentDescription("Menú lateral");
        menu.setClickable(true);
        menu.setFocusable(true);
        menu.setPadding(dp(8), dp(8), dp(8), dp(8));
        menu.setOnClickListener(v -> MainActivity.openDrawer(getActivity()));
        bar.addView(menu, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView name = new TextView(c);
        name.setText("Asistente de IA");
        name.setTextSize(22);
        name.setTextColor(res(R.color.kuida_toptitle, "#008080"));
        name.setGravity(Gravity.CENTER);
        name.setTypeface(Typeface.create("sans", Typeface.BOLD));
        bar.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView userIcon = new ImageView(c);
        userIcon.setImageResource(R.drawable.ic_user_profile);
        userIcon.setColorFilter(res(R.color.kuida_topicon, "#008080"));
        userIcon.setContentDescription("Mi perfil");
        userIcon.setClickable(true);
        userIcon.setFocusable(true);
        userIcon.setPadding(dp(8), dp(8), dp(8), dp(8));
        userIcon.setOnClickListener(v -> {
            try { NavHostFragment.findNavController(this).navigate(R.id.profileFragment); }
            catch (Exception ignored) {}
        });
        bar.addView(userIcon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        return bar;
    }

    // ---------- Sugerencias ----------

    private void addSuggestionChips(Context c, LinearLayout root) {
        HorizontalScrollView scroll = new HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), 0, dp(16), dp(8));
        java.util.ArrayList<String> suggestions = new java.util.ArrayList<>();
        suggestions.add("¿Qué hora es?");
        suggestions.add("¿Dónde estoy?");
        if (ProfileManager.canUseSignLanguageCatalog(c)) suggestions.add("Seña de gracias");
        suggestions.add("Pide ayuda");
        String[] ideas = suggestions.toArray(new String[0]);
        for (String idea : ideas) {
            TextView chip = new TextView(c);
            chip.setText(idea);
            chip.setTextSize(13);
            chip.setTypeface(Typeface.create("sans", Typeface.BOLD));
            chip.setTextColor(res(R.color.kuida_on_surface, "#0B2228"));
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(20));
            bg.setColor(res(R.color.kuida_card_bg, "#EDF3F3"));
            chip.setBackground(bg);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setContentDescription("Preguntar: " + idea);
            chip.setOnClickListener(v -> processUserText(idea));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
            p.setMargins(0, 0, dp(8), 0);
            row.addView(chip, p);
        }
        scroll.addView(row);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
    }

    // ---------- Burbujas ----------

    private void addUserBubble(String text) {
        Context c = requireContext();
        TextView bubble = new TextView(c);
        bubble.setText(text);
        bubble.setTextSize(15);
        bubble.setTextColor(0xFFFFFFFF);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setContentDescription("Tú dijiste: " + text);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(res(R.color.kuida_button, "#008080"));
        bubble.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.gravity = Gravity.END;
        p.setMargins(dp(48), dp(4), 0, dp(4));
        chatBox.addView(bubble, p);
        scrollToEnd();
    }

    private void addJarvisBubble(String text, Bitmap image, String imageLabel) {
        Context c = requireContext();
        LinearLayout bubble = new LinearLayout(c);
        bubble.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(res(R.color.kuida_card_bg, "#EDF3F3"));
        bubble.setBackground(bg);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));

        TextView msg = new TextView(c);
        msg.setText(text);
        msg.setTextSize(15);
        msg.setTextColor(res(R.color.kuida_on_surface, "#0B2228"));
        msg.setContentDescription("El asistente dice: " + text);
        bubble.addView(msg);

        if (image != null) {
            ImageView img = new ImageView(c);
            img.setImageBitmap(image);
            img.setAdjustViewBounds(true);
            img.setContentDescription("Seña de " + imageLabel);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
            ip.setMargins(0, dp(8), 0, 0);
            bubble.addView(img, ip);
        }
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.gravity = Gravity.START;
        p.setMargins(0, dp(4), dp(48), dp(4));
        chatBox.addView(bubble, p);
        scrollToEnd();
    }

    private void scrollToEnd() {
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    // ---------- Voz entrante ----------

    private void toggleListening() {
        if (transcriber != null && transcriber.isRunning()) {
            stopListening();
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void showBar() {
        if (voiceBar != null) voiceBar.setVisibility(View.VISIBLE);
    }

    private void hideBar() {
        if (voiceBar != null) voiceBar.setVisibility(View.GONE);
    }

    private void startListening() {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), "Escuchando... habla ahora", Toast.LENGTH_SHORT).show();
        showBar();
        awaitingCommand = true;
        transcriber = new VoskTranscriber(requireContext(), new VoskTranscriber.Listener() {
            @Override public void onPreparing() {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    showBar();
                    addJarvisBubble("Preparando la voz offline por primera vez, espera un momento...", null, null);
                });
            }
            @Override public void onReady() {}
            @Override public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    awaitingCommand = false;
                    hideBar();
                    addJarvisBubble(message, null, null);
                });
            }
            @Override public void onUnsupported() {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Voz en línea en este teléfono", Toast.LENGTH_SHORT).show();
                    beginGoogleCommand();
                });
            }
            @Override public void onPartial(String text) {
                if (!isAdded() || input == null) return;
                requireActivity().runOnUiThread(() -> input.setText(text));
            }
            @Override public void onFinal(String text) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (!awaitingCommand) return;
                    awaitingCommand = false;
                    stopListening();
                    if (input != null) input.setText("");
                    processUserText(text);
                });
            }
        });
        transcriber.start();
    }

    private void beginGoogleCommand() {
        if (!isAdded()) return;
        awaitingCommand = true;
        googleCommand = new com.nicdeveloper.kuida.audio.GoogleVoice(requireContext(),
                new com.nicdeveloper.kuida.audio.GoogleVoice.Listener() {
                    @Override public void onPartial(String text) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            if (input != null) input.setText(text);
                        });
                    }
                    @Override public void onFinal(String text) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            if (!awaitingCommand) return;
                            awaitingCommand = false;
                            stopListening();
                            if (input != null) input.setText("");
                            processUserText(text);
                        });
                    }
                    @Override public void onError(String message) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            awaitingCommand = false;
                            addJarvisBubble(message, null, null);
                        });
                    }
                });
        googleCommand.start();
    }

    private void stopListening() {
        awaitingCommand = false;
        hideBar();
        if (googleCommand != null) {
            googleCommand.stop();
            googleCommand = null;
        }
        if (transcriber != null) {
            transcriber.stop();
            transcriber = null;
        }
    }

    // ---------- Procesar ----------

    private void processUserText(String text) {
        if (!isAdded()) return;
        addUserBubble(text);
        JarvisEngine.Result r = JarvisEngine.resolve(requireContext(), text);
        if (r != null) {
            answer(r.reply, r.imageAsset, r.imageLabel);
            runAction(r);
            return;
        }
        // Pregunta abierta: nube si hay proveedor, si no aviso offline honesto
        if (aiClient != null && aiClient.hasAnyProvider()) {
            addJarvisBubble("Pensando...", null, null);
            aiClient.summarizeAccessible(JarvisEngine.cloudPrompt(text), new AiClient.Callback() {
                @Override public void onResult(String answer) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        removeLastThinking();
                        String clean = AiClient.cleanMarkdown(answer);
                        answer(clean, null, null);
                    });
                }
                @Override public void onError(String message) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        removeLastThinking();
                        answer(JarvisEngine.offlineFallback(), null, null);
                    });
                }
            });
        } else {
            answer(JarvisEngine.offlineFallback(), null, null);
        }
    }

    private void removeLastThinking() {
        if (chatBox == null || chatBox.getChildCount() == 0) return;
        View last = chatBox.getChildAt(chatBox.getChildCount() - 1);
        if (last instanceof LinearLayout) {
            LinearLayout box = (LinearLayout) last;
            if (box.getChildCount() > 0 && box.getChildAt(0) instanceof TextView) {
                String t = ((TextView) box.getChildAt(0)).getText().toString();
                if ("Pensando...".equals(t)) chatBox.removeView(last);
            }
        }
    }

    private void answer(String reply, String imageAsset, String imageLabel) {
        hideBar();
        Bitmap img = null;
        if (imageAsset != null) {
            try {
                com.nicdeveloper.kuida.data.LsnStore.Entry e =
                        com.nicdeveloper.kuida.data.LsnStore.find(requireContext(), imageLabel);
                if (e != null) img = com.nicdeveloper.kuida.data.LsnStore.image(requireContext(), e, 800);
            } catch (Exception ignored) {}
        }
        addJarvisBubble(reply, img, imageLabel);
        // El sordo lee: sin audio para él. Los demás sí escuchan la voz.
        if (!ProfileManager.PROFILE_DEAF.equals(ProfileManager.selectedProfile(requireContext()))) {
            speakOut(reply);
        }
    }

    private void runAction(JarvisEngine.Result r) {
        if (r == null || r.action == null || r.action == JarvisEngine.Action.NONE || !isAdded()) return;
        JarvisEngine.Action action = r.action;
        try {
            if (action == JarvisEngine.Action.OPEN_MAP) {
                NavHostFragment.findNavController(this).navigate(R.id.mapFragment);
            } else if (action == JarvisEngine.Action.OPEN_SUPPORT) {
                NavHostFragment.findNavController(this).navigate(R.id.supportFragment);
            } else if (action == JarvisEngine.Action.DIAL) {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:")));
            } else if (action == JarvisEngine.Action.OPEN_CONTACTS) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        android.provider.ContactsContract.Contacts.CONTENT_URI));
            } else if (action == JarvisEngine.Action.OPEN_CAMERA) {
                startActivity(new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE));
            } else if (action == JarvisEngine.Action.OPEN_GALLERY) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
                } catch (Exception e) {
                    Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                    pick.setType("image/*");
                    startActivity(Intent.createChooser(pick, "Ver fotos"));
                }
            } else if (action == JarvisEngine.Action.OPEN_SMS) {
                startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")));
            } else if (action == JarvisEngine.Action.OPEN_WHATSAPP) {
                try {
                    Intent launch = requireContext().getPackageManager()
                            .getLaunchIntentForPackage("com.whatsapp");
                    if (launch != null) {
                        startActivity(launch);
                    } else {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=com.whatsapp")));
                    }
                } catch (Exception e) {
                    correction("No tienes WhatsApp instalado en este teléfono.");
                }
            } else if (action == JarvisEngine.Action.FLASHLIGHT_ON
                    || action == JarvisEngine.Action.FLASHLIGHT_OFF) {
                applyFlashlight(action == JarvisEngine.Action.FLASHLIGHT_ON);
            } else if (action == JarvisEngine.Action.OPEN_APP) {
                String pkg = JarvisEngine.findAppPackage(requireContext(), r.param);
                if (pkg != null) {
                    try {
                        Intent launch = requireContext().getPackageManager().getLaunchIntentForPackage(pkg);
                        if (launch != null) {
                            startActivity(launch);
                            return;
                        }
                    } catch (Exception ignored) {}
                }
                correction("No encontré la aplicación " + r.param + ". Pregúntame qué aplicaciones tienes.");
            }
        } catch (Exception e) {
            correction("No pude hacer eso en este teléfono.");
        }
    }

    private void correction(String msg) {
        if (!isAdded()) return;
        addJarvisBubble(msg, null, null);
        if (!ProfileManager.PROFILE_DEAF.equals(ProfileManager.selectedProfile(requireContext()))) {
            speakOut(msg);
        }
    }

    private void applyFlashlight(boolean on) {
        if (!isAdded()) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            setTorch(on);
        } else {
            pendingFlash = on;
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void setTorch(boolean on) {
        try {
            android.hardware.camera2.CameraManager cam =
                    (android.hardware.camera2.CameraManager) requireContext()
                            .getSystemService(Context.CAMERA_SERVICE);
            for (String id : cam.getCameraIdList()) {
                Boolean flash = cam.getCameraCharacteristics(id).get(
                        android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flash)) {
                    cam.setTorchMode(id, on);
                    return;
                }
            }
            correction("Tu teléfono no tiene flash.");
        } catch (SecurityException se) {
            pendingFlash = on;
            cameraPermission.launch(Manifest.permission.CAMERA);
        } catch (Exception e) {
            correction("No pude controlar la linterna.");
        }
    }

    // ---------- Voz saliente: Fish premium, local de respaldo ----------

    private void speakOut(String text) {
        if (!isAdded()) return;
        if (ProfileManager.needsHearingTools(requireContext())) {
            if (assistantTranscript != null) {
                if (assistantTranscriptText.length() > 0) assistantTranscriptText.append("\n");
                assistantTranscriptText.append(text);
                assistantTranscript.setText(assistantTranscriptText.toString());
                assistantTranscript.setContentDescription("Transcripción del asistente: " + assistantTranscriptText);
                if (assistantTranscriptScroll != null) {
                    assistantTranscriptScroll.post(() -> assistantTranscriptScroll.fullScroll(View.FOCUS_DOWN));
                }
            }
        }
        String profile = ProfileManager.selectedProfile(requireContext());
        if (ProfileManager.PROFILE_DEAF.equals(profile)) {
            return;
        }
        FishVoice.stop();
        if (FishVoice.hasKey()) {
            FishVoice.speak(requireContext(), text, new FishVoice.Callback() {
                @Override public void onDone() {}
                @Override public void onError() {
                    systemSpeak(text);
                }
            });
        } else {
            systemSpeak(text);
        }
    }

    private void systemSpeak(String text) {
        try {
            if (systemTts != null) systemTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-reply");
        } catch (Exception ignored) {}
    }

    // ---------- Utilidades ----------

    private int res(int resId, String fallbackHex) {
        try {
            Context ctx = getContext();
            if (ctx != null) return androidx.core.content.ContextCompat.getColor(ctx, resId);
        } catch (Exception ignored) {}
        return android.graphics.Color.parseColor(fallbackHex);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Nullable
    @Override
    public android.view.animation.Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        try {
            return UiMotion.screenAnim(enter);
        } catch (Exception ignored) {
            return super.onCreateAnimation(transit, enter, nextAnim);
        }
    }

    @Override
    public void onDestroyView() {
        stopListening();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        stopListening();
        FishVoice.stop();
        try {
            if (systemTts != null) {
                systemTts.stop();
                systemTts.shutdown();
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}
