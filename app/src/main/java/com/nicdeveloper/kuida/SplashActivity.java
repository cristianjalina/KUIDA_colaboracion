package com.nicdeveloper.kuida;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION = 2600;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Precalentar el motor de voz: la puerta de acceso hablará casi al instante
        try {
            com.nicdeveloper.kuida.audio.KuidaTts.init(this);
        } catch (Exception ignored) {}
        setContentView(R.layout.activity_splash);

        FrameLayout logoBox = findViewById(R.id.splash_logo_box);
        ImageView bar = findViewById(R.id.splash_piece_bar);
        ImageView chevron = findViewById(R.id.splash_piece_chevron);
        ImageView smile = findViewById(R.id.splash_piece_smile);

        float density = getResources().getDisplayMetrics().density;

        // Estado inicial: piezas fuera de pantalla e invisibles
        bar.setTranslationX(-280 * density);
        bar.setAlpha(0f);
        chevron.setTranslationX(280 * density);
        chevron.setAlpha(0f);
        smile.setTranslationY(220 * density);
        smile.setAlpha(0f);

        DecelerateInterpolator easeOut = new DecelerateInterpolator(1.6f);

        // 1) Barra izquierda entra desde la izquierda
        bar.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(650)
                .setInterpolator(easeOut)
                .start();

        // 2) Chevron entra desde la derecha (retraso 150ms)
        chevron.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(650)
                .setStartDelay(150)
                .setInterpolator(easeOut)
                .start();

        // 3) Sonrisa sube desde abajo y completa el K (retraso 300ms)
        smile.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(650)
                .setStartDelay(300)
                .setInterpolator(easeOut)
                .start();

        // 4) Logo ensamblado: pulso "respirando" del conjunto
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            logoBox.animate()
                    .scaleX(1.07f)
                    .scaleY(1.07f)
                    .setDuration(450)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> logoBox.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(450)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .start())
                    .start();
        }, 1100);

        // 5) Entrar a la app
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        }, SPLASH_DURATION);
    }
}