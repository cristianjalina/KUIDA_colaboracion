package com.nicdeveloper.kuida.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.digitalink.Ink;

import java.util.ArrayList;
import java.util.List;

/** Pizarra blanca para escribir palabras con el dedo (sordos/mudos). */
public class FingerCanvasView extends View {

    private Bitmap bitmap;
    private Canvas drawCanvas;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path currentPath = new Path();
    private final List<List<float[]>> strokes = new ArrayList<>();
    private List<float[]> currentStroke;

    public FingerCanvasView(Context context) {
        super(context);
        init(context);
    }

    public FingerCanvasView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(10f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setBackgroundColor(Color.WHITE);
        setFocusable(true);
        setContentDescription("Pizarra blanca. Dibuja una palabra con el dedo.");
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (w > 0 && h > 0) {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            drawCanvas = new Canvas(bitmap);
            drawCanvas.drawColor(Color.WHITE);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                currentStroke = new ArrayList<>();
                currentStroke.add(new float[]{x, y, SystemClock.uptimeMillis()});
                currentPath.moveTo(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (currentStroke == null) return true;
                for (int i = 0; i < event.getHistorySize(); i++) {
                    float hx = event.getHistoricalX(i);
                    float hy = event.getHistoricalY(i);
                    currentStroke.add(new float[]{hx, hy, SystemClock.uptimeMillis()});
                    currentPath.lineTo(hx, hy);
                }
                currentStroke.add(new float[]{x, y, SystemClock.uptimeMillis()});
                currentPath.lineTo(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (currentStroke != null && !currentStroke.isEmpty()) {
                    strokes.add(currentStroke);
                    currentStroke = null;
                }
                if (drawCanvas != null) {
                    drawCanvas.drawPath(currentPath, paint);
                    currentPath.reset();
                    invalidate();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap != null) canvas.drawBitmap(bitmap, 0, 0, null);
        canvas.drawPath(currentPath, paint);
    }

    public void clear() {
        strokes.clear();
        currentStroke = null;
        currentPath.reset();
        if (drawCanvas != null) drawCanvas.drawColor(Color.WHITE);
        invalidate();
        announceForAccessibility("Pizarra borrada.");
    }

    public boolean hasInk() {
        return !strokes.isEmpty();
    }

    /** Convierte los trazos al formato del reconocedor. */
    @Nullable
    public Ink buildInk() {
        if (strokes.isEmpty()) return null;
        try {
            Ink.Builder ink = Ink.builder();
            for (List<float[]> s : strokes) {
                if (s.size() < 2) continue;
                Ink.Stroke.Builder stroke = Ink.Stroke.builder();
                for (float[] p : s) {
                    stroke.addPoint(Ink.Point.create(p[0], p[1], (long) p[2]));
                }
                ink.addStroke(stroke.build());
            }
            return ink.build();
        } catch (Exception e) {
            return null;
        }
    }
}
