package com.ahmadhaaroon.i221295.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** The exported rectangle uses the exact transform shown inside the preview mask. */
public class PhotoCropView extends View {

    private Bitmap bitmap;
    private final boolean circle;
    private final RectF crop = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector pinch;
    private float scale, x, y, lastX, lastY;

    public PhotoCropView(Context context, boolean circle) {
        super(context);
        this.circle = circle;
        setContentDescription(circle ? "Circular profile photo crop. Drag to move; pinch to zoom." : "Cover photo crop. Drag to move; pinch to zoom.");
        pinch = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (bitmap == null) return false;
                float next = Math.max(minScale(), Math.min(minScale() * 8, scale * detector.getScaleFactor()));
                float ratio = next / scale;
                x = detector.getFocusX() - (detector.getFocusX() - x) * ratio;
                y = detector.getFocusY() - (detector.getFocusY() - y) * ratio;
                scale = next;
                constrain();
                invalidate();
                return true;
            }
        });
    }

    public void setBitmap(Bitmap value) {
        bitmap = value;
        reset();
    }

    public boolean ready() {
        return bitmap != null && crop.width() > 0;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float width = Math.min(w - 32 * getResources().getDisplayMetrics().density, circle ? h * 0.85f : h * 1.7f);
        float height = circle ? width : width / 2;
        crop.set((w - width) / 2, (h - height) / 2, (w + width) / 2, (h + height) / 2);
        reset();
    }

    private float minScale() {
        return Math.max(crop.width() / bitmap.getWidth(), crop.height() / bitmap.getHeight());
    }

    public void reset() {
        if (!ready()) return;
        scale = minScale();
        x = crop.centerX() - bitmap.getWidth() * scale / 2;
        y = crop.centerY() - bitmap.getHeight() * scale / 2;
        invalidate();
    }

    public void rotate() {
        if (!ready()) return;
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        reset();
    }

    public void flip() {
        if (!ready()) return;
        Matrix matrix = new Matrix();
        matrix.setScale(-1, 1);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        reset();
    }

    private void constrain() {
        x = Math.min(crop.left, Math.max(crop.right - bitmap.getWidth() * scale, x));
        y = Math.min(crop.top, Math.max(crop.bottom - bitmap.getHeight() * scale, y));
    }

    private void drawPhoto(Canvas canvas) {
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate(x, y);
        canvas.drawBitmap(bitmap, matrix, paint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF173537);
        if (!ready()) return;
        drawPhoto(canvas);
        Path mask = new Path();
        mask.setFillType(Path.FillType.EVEN_ODD);
        mask.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        if (circle) mask.addOval(crop, Path.Direction.CW);
        else mask.addRect(crop, Path.Direction.CW);
        paint.setColor(0xB0000000);
        if (circle) canvas.drawPath(mask, paint);
        else {
            canvas.drawRect(0, 0, getWidth(), crop.top, paint);
            canvas.drawRect(0, crop.bottom, getWidth(), getHeight(), paint);
            canvas.drawRect(0, crop.top, crop.left, crop.bottom, paint);
            canvas.drawRect(crop.right, crop.top, getWidth(), crop.bottom, paint);
        }
        paint.setColor(0xFFFFFFFF);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
        if (circle) canvas.drawOval(crop, paint);
        else canvas.drawRect(crop, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
    }

    public Bitmap export() {
        if (!ready()) throw new IllegalStateException("Please wait for the photo to load.");
        int width = circle ? 1024 : 1280;
        Bitmap out = Bitmap.createBitmap(width, circle ? width : width / 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.scale(width / crop.width(), width / crop.width());
        canvas.translate(-crop.left, -crop.top);
        drawPhoto(canvas);
        return out;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!ready()) return true;
        pinch.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            lastX = event.getX(); lastY = event.getY();
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (!pinch.isInProgress() && event.getPointerCount() == 1) {
                x += event.getX() - lastX; y += event.getY() - lastY;
                constrain(); invalidate();
            }
            lastX = event.getX(); lastY = event.getY();
        } else if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
            int remaining = event.getActionIndex() == 0 ? 1 : 0;
            lastX = event.getX(remaining); lastY = event.getY(remaining);
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
