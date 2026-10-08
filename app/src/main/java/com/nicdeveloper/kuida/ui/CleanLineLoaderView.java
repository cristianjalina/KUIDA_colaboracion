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

public class CleanLineLoaderView extends View {
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float rotationAngle = 0f;
    private ValueAnimator animator;

    public CleanLineLoaderView(Context context) {
        super(context);
        init();
    }

    public CleanLineLoaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setColor(Color.parseColor("#008080")); // Kuida Teal
        arcPaint.setStrokeWidth(3.5f); // Delgada y elegante, exactamente como 02 ui.jpeg
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(2000);
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
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        // Mantener proporcion circular centrada con margen suficiente
        float diameter = Math.min(width, height) * 0.75f;
        float left = (width - diameter) / 2f;
        float top = (height - diameter) / 2f;
        oval.set(left, top, left + diameter, top + diameter);

        // Dos arcos enfrentados con espacio simetrico (02 ui.jpeg)
        canvas.save();
        canvas.rotate(rotationAngle, width / 2f, height / 2f);
        canvas.drawArc(oval, 205, 130, false, arcPaint);
        canvas.drawArc(oval, 25, 130, false, arcPaint);
        canvas.restore();
    }
}
