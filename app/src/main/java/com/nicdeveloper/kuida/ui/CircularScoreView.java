package com.nicdeveloper.kuida.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;

public class CircularScoreView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private int score = 80;
    private String label = "Indice";

    public CircularScoreView(Context context) {
        super(context);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(Color.rgb(226, 236, 240));

        progress.setStyle(Paint.Style.STROKE);
        progress.setStrokeCap(Paint.Cap.ROUND);
        progress.setColor(Color.rgb(0, 188, 196));

        valuePaint.setColor(Color.rgb(20, 147, 221));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

        labelPaint.setColor(Color.rgb(76, 114, 128));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
    }

    public void setScore(int score, String label) {
        this.score = Math.max(0, Math.min(100, score));
        this.label = label;
        setContentDescription(String.format(Locale.US, "%s %d por ciento", label, this.score));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight()) * 0.78f;
        float stroke = Math.max(12f, size * 0.06f);
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        arc.set(left, top, left + size, top + size);
        track.setStrokeWidth(stroke);
        progress.setStrokeWidth(stroke);
        canvas.drawArc(arc, 138, 264, false, track);
        canvas.drawArc(arc, 138, 264 * (score / 100f), false, progress);

        valuePaint.setTextSize(size * 0.25f);
        labelPaint.setTextSize(size * 0.07f);
        canvas.drawText(String.valueOf(score), getWidth() / 2f, getHeight() / 2f + size * 0.04f, valuePaint);
        canvas.drawText(label, getWidth() / 2f, getHeight() / 2f + size * 0.17f, labelPaint);
    }
}
