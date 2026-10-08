package com.nicdeveloper.kuida.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

public class SoundWaveView extends View {
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint softPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float phase = 0f;
    private ValueAnimator animator;

    // Pattern of relative heights for wave bars
    private final float[] baseHeights = {0.25f, 0.45f, 0.7f, 0.95f, 0.8f, 0.6f, 0.4f, 0.75f, 0.9f, 0.65f, 0.35f, 0.2f};

    public SoundWaveView(Context context) {
        super(context);
        init(context);
    }

    public SoundWaveView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        activePaint.setColor(safeColor(context, com.nicdeveloper.kuida.R.color.kuida_teal, Color.parseColor("#00C9BE")));
        activePaint.setStyle(Paint.Style.FILL);

        softPaint.setColor(safeColor(context, com.nicdeveloper.kuida.R.color.kuida_wave_soft, Color.parseColor("#E0F4F4")));
        softPaint.setStyle(Paint.Style.FILL);

        animator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        animator.setDuration(1200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
    }

    private static int safeColor(Context context, int resId, int fallback) {
        try {
            return androidx.core.content.ContextCompat.getColor(context, resId);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator != null && !animator.isRunning()) {
            animator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        int totalBars = 22;
        float barWidth = w / (float) (totalBars * 2);
        float spacing = barWidth;
        float startX = spacing / 2f;
        float centerY = h / 2f;

        for (int i = 0; i < totalBars; i++) {
            float x = startX + i * (barWidth + spacing);
            float base = baseHeights[i % baseHeights.length];
            float modulation = (float) Math.sin(phase + i * 0.35f) * 0.25f;
            float factor = Math.max(0.15f, Math.min(1.0f, base + modulation));
            float barHeight = (h * 0.85f) * factor;

            float left = x;
            float top = centerY - barHeight / 2f;
            float right = x + barWidth;
            float bottom = centerY + barHeight / 2f;

            // Half active teal/mint, half subtle soft
            Paint paintToUse = (i < totalBars / 2 + 2) ? activePaint : softPaint;
            canvas.drawRect(left, top, right, bottom, paintToUse);
        }
    }
}
