package com.nicdeveloper.kuida.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.card.MaterialCardView;
import com.nicdeveloper.kuida.R;
import com.nicdeveloper.kuida.data.ProfileManager;

import java.util.Locale;

public class ProfileGateFragment extends Fragment {

    private FrameLayout container;
    private volatile boolean promptSpoken = false;
    private int promptAttempts = 0;
    private Runnable promptRetryRunnable;
    private Runnable promptRepeatRunnable;
    private boolean userInteracted = false;
    private static final long LONG_PRESS_MS = 2500;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;
    private boolean isLongPressedTriggered = false;

    // Palette extracted from the reference images
    private static final int COLOR_TEAL_PANEL = Color.parseColor("#44888C"); // Verde/turquesa del fondo
    private static final int COLOR_PRIMARY_TEAL = Color.parseColor("#008080");
    private static final int COLOR_SLATE = Color.parseColor("#4C7280");
    private static final int COLOR_NIGHT = Color.parseColor("#0B2228");
    private static final int COLOR_BORDER = Color.parseColor("#E0EAEA");

    private int surfaceBg() { return res(R.color.kuida_surface, "#FFFFFF"); }
    private int cardBg() { return res(R.color.kuida_card_bg, "#FFFFFF"); }
    private int border() { return res(R.color.kuida_border_subtle, "#E0EAEA"); }
    private int nightText() { return res(R.color.kuida_on_surface, "#0B2228"); }
    private int slateText() { return res(R.color.kuida_slate, "#4C7280"); }
    private int teal() { return res(R.color.kuida_teal, "#008080"); }

    /** Color por recurso dia/noche (el sistema resuelve; sin ramas manuales). */
    private int res(int resId, String fallbackHex) {
        try {
            Context ctx = getContext();
            if (ctx != null) return androidx.core.content.ContextCompat.getColor(ctx, resId);
        } catch (Exception ignored) {}
        return Color.parseColor(fallbackHex);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // El motor ya viene precalentado del splash: avisar en cuanto esté listo
        try {
            com.nicdeveloper.kuida.audio.KuidaTts.init(requireContext(), () -> {
                if (isAdded()) speakInitialPrompt();
            });
        } catch (Exception ignored) {}
    }

    private void speakInitialPrompt() {
        speakInitialPrompt(false);
    }

    private void speakInitialPrompt(boolean repeat) {
        if (!isAdded()) return;
        if (!repeat) {
            // Solo suena UNA vez en la vida de la app (contada al hablar de verdad)
            if (promptSpoken) return;
            if (ProfileManager.isVoicePromptDone(requireContext())) return;
        }
        if (promptAttempts >= 4) return;
        promptAttempts++;
        // Primero al ciego (el que necesita la voz), luego al que sí ve
        String text = "Si usted no puede ver, mantenga un dedo presionado sobre la pantalla hasta sentir la vibración. "
                + "Si usted sí puede ver, deslice el dedo a la derecha o a la izquierda.";
        boolean started = false;
        try {
            started = com.nicdeveloper.kuida.audio.KuidaTts.speak(text, "prompt_init", () -> {
                promptSpoken = true;
                try {
                    ProfileManager.markVoicePromptDone(requireContext());
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {
            started = false;
        }
        if (!started) {
            // Motor aún tibio: el callback de init lo reintentará
            schedulePromptRetry();
        }
    }

    private void schedulePromptRetry() {
        if (!isAdded() || promptSpoken || promptAttempts >= 3) return;
        if (promptRetryRunnable != null) handler.removeCallbacks(promptRetryRunnable);
        promptRetryRunnable = () -> {
            if (!isAdded() || promptSpoken) return;
            if (ProfileManager.isVoicePromptDone(requireContext())) return;
            speakInitialPrompt();
        };
        handler.postDelayed(promptRetryRunnable, 2500);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle savedInstanceState) {
        // Red de seguridad: si el perfil ya existe, salir en silencio al inicio
        // correspondiente sin mostrar ni narrar nada del gate.
        if (ProfileManager.hasCompletedOnboarding(requireContext())) {
            FrameLayout blank = new FrameLayout(requireContext());
            blank.post(() -> {
                if (!isAdded()) return;
                try {
                    androidx.navigation.NavOptions navOptions = new androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.profileGateFragment, true)
                            .build();
                    NavHostFragment.findNavController(this).navigate(
                            com.nicdeveloper.kuida.MainActivity.getHomeDestinationForProfile(requireContext()),
                            null, navOptions);
                } catch (Exception ignored) {}
            });
            container = blank;
            return blank;
        }
        container = new FrameLayout(requireContext());
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        container.setBackgroundColor(surfaceBg());

        // Muestra la Pantalla Inicial por defecto
        showInitialScreen();

        return container;
    }

    // =========================================================================
    // 1. PANTALLA INICIAL:
    // Fondo color verde/turquesa, icono de microfono superior izquierdo,
    // grafica de ondas de sonido y texto centrado 'DESLICE A LA DERECHA...'
    // Detección:
    // - Si desliza a la derecha o izquierda (no tiene una discapacidad visual) -> Pantalla de Carga y Evaluación
    // - Si pulsa por 3 segundos (tiene una discapacidad visual) -> Activa perfil ciego y manda a herramientas
    // =========================================================================
    private void showInitialScreen() {
        container.removeAllViews();
        Context c = requireContext();

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(surfaceBg());

        // --- Header Superior Blanco ---
        LinearLayout header = createHeaderBar(c);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // --- Panel Principal Verde / Turquesa ---
        FrameLayout panel = new FrameLayout(c);
        panel.setBackgroundColor(COLOR_TEAL_PANEL);

        LinearLayout panelContent = new LinearLayout(c);
        panelContent.setOrientation(LinearLayout.VERTICAL);
        panelContent.setGravity(Gravity.CENTER);
        panelContent.setPadding(dp(c, 28), dp(c, 20), dp(c, 28), dp(c, 40));

        // Texto centrado: 'DESLICE A LA DERECHA O A LA IZQUIERDA SI USTED NO ES CIEGO!'
        TextView instruction = new TextView(c);
        instruction.setText("¡DESLICE A LA DERECHA\nO A LA IZQUIERDA SI USTED\nNO ES UNA PERSONA CIEGA\nO NO VIDENTE!");
        instruction.setTextColor(Color.WHITE);
        instruction.setTextSize(17);
        instruction.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        instruction.setGravity(Gravity.CENTER);
        instruction.setLineSpacing(0, 1.3f);
        panelContent.addView(instruction);

        // Icono de gesto / mano con flecha
        ImageView handGesture = new ImageView(c);
        handGesture.setImageResource(R.drawable.ic_hand_swipe);
        handGesture.setColorFilter(Color.WHITE);
        LinearLayout.LayoutParams handParams = new LinearLayout.LayoutParams(dp(c, 90), dp(c, 90));
        handParams.setMargins(0, dp(c, 24), 0, 0);
        panelContent.addView(handGesture, handParams);

        // Aviso visual para baja visión: cómo entrar si no se ve
        TextView pressHint = new TextView(c);
        pressHint.setText("¿NO VES?\nMANTÉN PRESIONADA LA PANTALLA");
        pressHint.setTextColor(Color.WHITE);
        pressHint.setTextSize(14);
        pressHint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        pressHint.setGravity(Gravity.CENTER);
        pressHint.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.setMargins(0, dp(c, 20), 0, 0);
        panelContent.addView(pressHint, hintParams);

        panel.addView(panelContent, new FrameLayout.LayoutParams(-1, -1));

        // Detector de gestos para:
        // 1. Deslizar a la derecha o izquierda -> Si no tiene una discapacidad visual
        // 2. Mantener presionado por 3 segundos -> Si tiene una discapacidad visual
        setupTouchInteraction(panel);

        root.addView(panel, new LinearLayout.LayoutParams(-1, -1));
        container.addView(root);

        // Volver a reproducir instrucción si ya cargó TTS
        speakInitialPrompt();

        // Si en 9 segundos no hubo interacción, repetir la instrucción una vez
        // (el ciego puede necesitar escucharla dos veces)
        if (promptRepeatRunnable != null) handler.removeCallbacks(promptRepeatRunnable);
        promptRepeatRunnable = () -> {
            if (!isAdded() || userInteracted) return;
            speakInitialPrompt(true);
        };
        handler.postDelayed(promptRepeatRunnable, 9000);
    }

    private void setupTouchInteraction(View touchView) {
        GestureDetector gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 80;
            private static final int SWIPE_VELOCITY_THRESHOLD = 80;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 != null && e2 != null) {
                    float diffX = e2.getX() - e1.getX();
                    float diffY = e2.getY() - e1.getY();
                    // Deslizar a la derecha O a la izquierda (ambos lados)
                    if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        cancelLongPress();
                        showLoadingScreen();
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // Si toca simple, también le da paso a la pantalla de carga para accesibilidad visual
                cancelLongPress();
                showLoadingScreen();
                return true;
            }
        });

        touchView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    userInteracted = true;
                    cancelPromptRepeat();
                    isLongPressedTriggered = false;
                    longPressRunnable = () -> {
                        isLongPressedTriggered = true;
                        onBlindUserDetected();
                    };
                    // Mantener presionado 2.5 segundos identifica a la persona ciega o con baja visión
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    cancelLongPress();
                    break;
            }
            return true;
        });
    }

    private void cancelPromptRepeat() {
        if (promptRepeatRunnable != null) handler.removeCallbacks(promptRepeatRunnable);
    }

    private void cancelLongPress() {
        if (longPressRunnable != null) {
            handler.removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    /**
     * Se activa cuando la persona presiona la pantalla durante 3 segundos.
     * Identifica al usuario como ciego/no vidente y lo manda a las herramientas para ciego.
     */
    private void onBlindUserDetected() {
        if (!isAdded()) return;
        cancelPromptRepeat();

        // Anuncio sonoro inmediato para el usuario ciego (motor ya caliente)
        try {
            com.nicdeveloper.kuida.audio.KuidaTts.speak(
                    "Perfil visual activado. Bienvenido a Kuida.", "blind_confirm", null);
        } catch (Exception ignored) {}

        // Guardar perfil de persona no vidente
        ProfileManager.completeOnboarding(requireContext(), ProfileManager.PROFILE_BLIND);

        // Doble vibración de confirmación: corto + largo
        try {
            android.os.Vibrator vib = (android.os.Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null && vib.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vib.vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 150, 120, 400}, -1));
                } else {
                    vib.vibrate(500);
                }
            }
        } catch (Exception ignored) {}

        androidx.navigation.NavOptions navOptions = new androidx.navigation.NavOptions.Builder()
                .setPopUpTo(R.id.profileGateFragment, true)
                .build();
        try {
            NavHostFragment.findNavController(this).navigate(R.id.visionFragment, null, navOptions);
        } catch (Exception e) {
            try {
                NavHostFragment.findNavController(this).navigate(R.id.homeFragment, null, navOptions);
            } catch (Exception ignored) {}
        }
    }

    // =========================================================================
    // 2. PANTALLA DE CARGA:
    // Fondo verde con indicador circular de carga que diga 'Loading...'
    // =========================================================================
    private void showLoadingScreen() {
        userInteracted = true;
        cancelPromptRepeat();
        container.removeAllViews();
        Context c = requireContext();

        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(COLOR_TEAL_PANEL);

        LinearLayout centerBox = new LinearLayout(c);
        centerBox.setOrientation(LinearLayout.VERTICAL);
        centerBox.setGravity(Gravity.CENTER);

        // Indicador circular de carga en un FrameLayout con el texto 'Loading...' en el centro
        FrameLayout loaderContainer = new FrameLayout(c);

        TealCircleLoaderView spinner = new TealCircleLoaderView(c);
        loaderContainer.addView(spinner, new FrameLayout.LayoutParams(dp(c, 210), dp(c, 210), Gravity.CENTER));

        TextView loadingText = new TextView(c);
        loadingText.setText("Loading...");
        loadingText.setTextColor(Color.WHITE);
        loadingText.setTextSize(22);
        loadingText.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        loadingText.setGravity(Gravity.CENTER);
        loaderContainer.addView(loadingText, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        centerBox.addView(loaderContainer, new LinearLayout.LayoutParams(dp(c, 230), dp(c, 230)));
        root.addView(centerBox, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        // Transición visual limpia a la siguiente pantalla (Evaluación) tras 1.5s
        root.postDelayed(() -> {
            if (isAdded()) {
                showEvaluationScreen();
            }
        }, 1500);

        container.addView(root);
    }

    // =========================================================================
    // 3. PANTALLA DE EVALUACIÓN:
    // Fondo blanco, encabezado con micrófono y ondas, texto
    // 'TRANSCRIPTOR EN TIEMPO REAL' y tarjetas de selección inferior.
    // =========================================================================
    private void showEvaluationScreen() {
        container.removeAllViews();
        Context c = requireContext();

        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(surfaceBg());

        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(c, 32));

        // Header Superior
        LinearLayout header = createHeaderBar(c);
        content.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // Separador delgado
        View divider = new View(c);
        divider.setBackgroundColor(border());
        content.addView(divider, new LinearLayout.LayoutParams(-1, dp(c, 1)));

        // Bloque central informativo: TRANSCRIPTOR EN TIEMPO REAL
        LinearLayout transcriptorBlock = new LinearLayout(c);
        transcriptorBlock.setOrientation(LinearLayout.VERTICAL);
        transcriptorBlock.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));

        TextView transcriptorTitle = new TextView(c);
        transcriptorTitle.setText("TRANSCRIPCIÓN DEL ASISTENTE: aquí aparece en texto lo que el asistente dice, para facilitar el acceso a personas sordas o hipoacúsicas");
        transcriptorTitle.setTextColor(teal());
        transcriptorTitle.setTextSize(11);
        transcriptorTitle.setLineSpacing(0, 1.25f);
        transcriptorTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        transcriptorBlock.addView(transcriptorTitle);
        content.addView(transcriptorBlock, new LinearLayout.LayoutParams(-1, -2));

        // Segundo separador
        View divider2 = new View(c);
        divider2.setBackgroundColor(border());
        content.addView(divider2, new LinearLayout.LayoutParams(-1, dp(c, 1)));

        // Tarjetas de Selección Inferior
        LinearLayout cardsLayout = new LinearLayout(c);
        cardsLayout.setOrientation(LinearLayout.VERTICAL);
        cardsLayout.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));

        cardsLayout.addView(createSelectionCard(c,
                "Perfil visual (ceguera o baja visión)",
                "Prioriza lectura por voz y herramientas para consultar el entorno.",
                v -> navigateToHome(ProfileManager.PROFILE_BLIND)));

        cardsLayout.addView(createSelectionCard(c,
                "Perfil auditivo (persona sorda o hipoacúsica)",
                "Prioriza transcripción, texto y avisos visuales o por vibración.",
                v -> navigateToHome(ProfileManager.PROFILE_DEAF)));

        cardsLayout.addView(createSelectionCard(c,
                "Perfil de comunicación del habla",
                "Incluye frases, escritura y reproducción de texto en voz alta.",
                v -> navigateToHome(ProfileManager.PROFILE_MUTE)));

        cardsLayout.addView(createSelectionCard(c,
                "Herramientas Generales / Todos los perfiles",
                "Activar suite completa sin restricciones.",
                v -> navigateToHome(ProfileManager.PROFILE_ALL)));

        content.addView(cardsLayout, new LinearLayout.LayoutParams(-1, -2));

        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        container.addView(scroll);
    }

    // =========================================================================
    // Componentes visuales reutilizables
    // =========================================================================

    private LinearLayout createHeaderBar(Context c) {
        LinearLayout layout = new LinearLayout(c);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(surfaceBg());
        layout.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 14));

        LinearLayout topRow = new LinearLayout(c);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView menu = new ImageView(c);
        menu.setImageResource(R.drawable.ic_menu_hamburger);
        menu.setColorFilter(teal());
        menu.setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
        topRow.addView(menu, new LinearLayout.LayoutParams(dp(c, 32), dp(c, 32)));

        TextView title = new TextView(c);
        title.setText("Evaluacion de\nAccesibilidad");
        title.setTextSize(16);
        title.setTextColor(teal());
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        topRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView profile = new ImageView(c);
        profile.setImageResource(R.drawable.ic_user_profile);
        profile.setColorFilter(teal());
        profile.setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
        topRow.addView(profile, new LinearLayout.LayoutParams(dp(c, 32), dp(c, 32)));

        layout.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout micWaveRow = new LinearLayout(c);
        micWaveRow.setOrientation(LinearLayout.HORIZONTAL);
        micWaveRow.setGravity(Gravity.CENTER_VERTICAL);
        micWaveRow.setPadding(dp(c, 8), dp(c, 12), dp(c, 8), dp(c, 4));

        FrameLayout micCircle = new FrameLayout(c);
        android.graphics.drawable.GradientDrawable circleBg = new android.graphics.drawable.GradientDrawable();
        circleBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circleBg.setColor(teal());
        micCircle.setBackground(circleBg);

        ImageView micIcon = new ImageView(c);
        micIcon.setImageResource(R.drawable.ic_mic_white);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(c, 28), dp(c, 28), Gravity.CENTER);
        micCircle.addView(micIcon, iconParams);

        micWaveRow.addView(micCircle, new LinearLayout.LayoutParams(dp(c, 54), dp(c, 54)));

        SoundWaveView soundWave = new SoundWaveView(c);
        LinearLayout.LayoutParams waveParams = new LinearLayout.LayoutParams(0, dp(c, 44), 1);
        waveParams.setMargins(dp(c, 16), 0, 0, 0);
        micWaveRow.addView(soundWave, waveParams);

        layout.addView(micWaveRow, new LinearLayout.LayoutParams(-1, -2));

        return layout;
    }

    private MaterialCardView createSelectionCard(Context c, String titleText, String descText, View.OnClickListener listener) {
        MaterialCardView card = new MaterialCardView(c);
        card.setRadius(dp(c, 12)); // Plano limpio, sin contornos
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        card.setStrokeColor(border());
        card.setCardBackgroundColor(cardBg());
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));

        TextView title = new TextView(c);
        title.setText(titleText);
        title.setTextSize(15);
        title.setTextColor(nightText());
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

        TextView desc = new TextView(c);
        desc.setText(descText);
        desc.setTextSize(12);
        desc.setTextColor(slateText());
        desc.setPadding(0, dp(c, 4), 0, 0);

        box.addView(title, new LinearLayout.LayoutParams(-1, -2));
        box.addView(desc, new LinearLayout.LayoutParams(-1, -2));
        card.addView(box);

        card.setOnClickListener(listener);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, dp(c, 10));
        card.setLayoutParams(p);
        return card;
    }

    private void navigateToHome(String profile) {
        ProfileManager.completeOnboarding(requireContext(), profile);
        androidx.navigation.NavOptions navOptions = new androidx.navigation.NavOptions.Builder()
                .setPopUpTo(R.id.profileGateFragment, true)
                .build();
        try {
            NavHostFragment.findNavController(this).navigate(R.id.homeFragment, null, navOptions);
        } catch (Exception ignored) {}
    }

    private int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    @Override
    public android.view.animation.Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        try {
            return UiMotion.screenAnim(enter);
        } catch (Exception ignored) {
            return super.onCreateAnimation(transit, enter, nextAnim);
        }
    }

    @Override
    public void onDestroy() {
        cancelLongPress();
        cancelPromptRepeat();
        if (promptRetryRunnable != null) handler.removeCallbacks(promptRetryRunnable);
        // El motor compartido NO se apaga: vive todo el proceso para hablar al instante
        super.onDestroy();
    }
}
