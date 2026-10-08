package com.nicdeveloper.kuida.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

public class TealCircleLoaderView extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float rotationAngle = 0f;
    private ValueAnimator animator;

    public TealCircleLoaderView(Context context) {
        super(context);
        init();
    }

    public TealCircleLoaderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setColor(Color.parseColor("#387E82")); // Darker teal ring
        trackPaint.setStrokeWidth(14f);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setColor(Color.parseColor("#FFB59E")); // Melocoton estilo referencia
        arcPaint.setStrokeWidth(14f);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(1600);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            rotationAngle = (float) animation.getAnimatedValue();
            invalidate();
        });
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
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float stroke = 14f;
        float diameter = Math.min(w, h) - stroke * 2;
        float left = (w - diameter) / 2f;
        float top = (h - diameter) / 2f;
        oval.set(left, top, left + diameter, top + diameter);

        // Background track circle
        canvas.drawCircle(w / 2f, h / 2f, diameter / 2f, trackPaint);

        // Rotating highlight arc
        canvas.save();
        canvas.rotate(rotationAngle, w / 2f, h / 2f);
        canvas.drawArc(oval, 0, 110, false, arcPaint);
        canvas.restore();
    }
}
