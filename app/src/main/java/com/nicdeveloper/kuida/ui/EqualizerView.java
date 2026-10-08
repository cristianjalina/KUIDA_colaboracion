package com.nicdeveloper.kuida.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.nicdeveloper.kuida.R;

public class EqualizerView extends View {
    private final Paint active = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inactive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private double level;

    public EqualizerView(Context context) {
        super(context);
        active.setColor(safeColor(context, R.color.kuida_teal, Color.rgb(0, 201, 190)));
        inactive.setColor(safeColor(context, R.color.kuida_metric, Color.rgb(226, 236, 240)));
    }

    private static int safeColor(Context context, int resId, int fallback) {
        try {
            return ContextCompat.getColor(context, resId);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public void setLevel(double db) {
        level = Math.max(0, Math.min(1, db / 100.0));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int bars = 28;
        float gap = Math.max(3f, getWidth() * 0.008f);
        float barWidth = (getWidth() - gap * (bars - 1)) / bars;
        float center = getHeight() / 2f;
        for (int i = 0; i < bars; i++) {
            float wave = (float) (0.25 + 0.75 * Math.abs(Math.sin(i * 0.42)));
            float height = getHeight() * (0.18f + wave * 0.72f);
            float left = i * (barWidth + gap);
            Paint paint = i / (float) bars <= level ? active : inactive;
            canvas.drawRoundRect(left, center - height / 2f, left + barWidth, center + height / 2f, barWidth, barWidth, paint);
        }
    }
}
