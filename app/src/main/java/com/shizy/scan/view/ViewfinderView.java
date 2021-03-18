package com.shizy.scan.view;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.google.zxing.ResultPoint;
import com.shizy.scan.R;

import java.util.ArrayList;
import java.util.List;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

    private static final long ANIMATION_DELAY = 15L;
    private static final int CURRENT_POINT_OPACITY = 0xA0;
    private static final int MAX_RESULT_POINTS = 20;
    private static final int POINT_SIZE = 6;

    private final Paint paint;
    private Bitmap resultBitmap;
    private final int maskColor;
    private final int resultColor;
    private final int laserColor;
    private final int resultPointColor;
    private final int borderColor;
    private final int borderSize;
    private final int cornerColor;
    private final int cornerSize;
    private final float cornerOffset;
    private final int cornerLength;
    //    private int scannerAlpha;
    private List<ResultPoint> possibleResultPoints;
    private List<ResultPoint> lastPossibleResultPoints;

    private final Rect framingRect = new Rect();
    private final ValueAnimator laserAnimator;
    private boolean scanning = true;

    // This constructor is used when the class is built from an XML resource.
    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize these once for performance rather than calling them every time in onDraw().
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Resources resources = getResources();
        maskColor = resources.getColor(R.color.viewfinder_mask);
        resultColor = resources.getColor(R.color.result_view);
        laserColor = resources.getColor(R.color.viewfinder_laser);
        resultPointColor = resources.getColor(R.color.possible_result_points);
        borderColor = resources.getColor(R.color.viewfinder_border);
        borderSize = resources.getDimensionPixelSize(R.dimen.viewfinder_border);
        cornerColor = resources.getColor(R.color.viewfinder_corner);
        cornerSize = resources.getDimensionPixelSize(R.dimen.viewfinder_corner_size);
        cornerLength = resources.getDimensionPixelSize(R.dimen.viewfinder_corner_length);
        cornerOffset = cornerSize / 2.0f;
        possibleResultPoints = new ArrayList<>(5);
        lastPossibleResultPoints = null;

        laserAnimator = ValueAnimator.ofFloat(0f, 1f);
        laserAnimator.setDuration(2000L);
        laserAnimator.setInterpolator(new LinearInterpolator());
        laserAnimator.setRepeatCount(ValueAnimator.INFINITE);
        laserAnimator.start();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        calculateFramingRect();
    }

    @SuppressLint("DrawAllocation")
    @Override
    public void onDraw(Canvas canvas) {
        Rect frame = framingRect;
//        Rect previewFrame = cameraManager.getFramingRectInPreview();
//        if (frame == null || previewFrame == null) {
//            return;
//        }
        drawMask(canvas);
        drawBorder(canvas);
        drawCorner(canvas);

        if (resultBitmap != null) {
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(CURRENT_POINT_OPACITY);
            canvas.drawBitmap(resultBitmap, null, frame, paint);
        } else {
            if (scanning) {
                drawLaser(canvas);
            }
//            float scaleX = frame.width() / (float) previewFrame.width();
//            float scaleY = frame.height() / (float) previewFrame.height();

            List<ResultPoint> currentPossible = possibleResultPoints;
            List<ResultPoint> currentLast = lastPossibleResultPoints;
            int frameLeft = frame.left;
            int frameTop = frame.top;
            if (currentPossible.isEmpty()) {
                lastPossibleResultPoints = null;
            } else {
                possibleResultPoints = new ArrayList<>(5);
                lastPossibleResultPoints = currentPossible;
                paint.setAlpha(CURRENT_POINT_OPACITY);
                paint.setColor(resultPointColor);
                synchronized (currentPossible) {
//                    for (ResultPoint point : currentPossible) {
//                        canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
//                                frameTop + (int) (point.getY() * scaleY),
//                                POINT_SIZE, paint);
//                    }
                }
            }
            if (currentLast != null) {
                paint.setAlpha(CURRENT_POINT_OPACITY / 2);
                paint.setColor(resultPointColor);
                synchronized (currentLast) {
                    float radius = POINT_SIZE / 2.0f;
//                    for (ResultPoint point : currentLast) {
//                        canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
//                                frameTop + (int) (point.getY() * scaleY),
//                                radius, paint);
//                    }
                }
            }

            // Request another update at the animation interval, but only repaint the laser line,
            // not the entire viewfinder mask.
            postInvalidateDelayed(ANIMATION_DELAY,
                    frame.left,
                    frame.top,
                    frame.right,
                    frame.bottom);
        }
    }

    private void drawMask(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        final Rect frame = framingRect;
        final int width = canvas.getWidth();
        final int height = canvas.getHeight();

        // Draw the exterior (i.e. outside the framing rect) darkened
        paint.setColor(resultBitmap != null ? resultColor : maskColor);
        canvas.drawRect(0, 0, width, frame.top, paint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
        canvas.drawRect(0, frame.bottom + 1, width, height, paint);
    }

    private void drawBorder(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(borderColor);
        paint.setStrokeWidth(borderSize);
        canvas.drawRect(framingRect, paint);
    }

    private void drawCorner(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(cornerColor);
        paint.setStrokeWidth(cornerSize);

        final Rect frame = framingRect;
        canvas.drawLine(frame.left - cornerOffset, frame.top,
                frame.left - cornerOffset + cornerLength, frame.top, paint);
        canvas.drawLine(frame.left, frame.top - cornerOffset,
                frame.left, frame.top - cornerOffset + cornerLength, paint);

        canvas.drawLine(frame.left - cornerOffset, frame.bottom,
                frame.left - cornerOffset + cornerLength, frame.bottom, paint);
        canvas.drawLine(frame.left, frame.bottom + cornerOffset,
                frame.left, frame.bottom + cornerOffset - cornerLength, paint);

        canvas.drawLine(frame.right + cornerOffset, frame.top,
                frame.right + cornerOffset - cornerLength, frame.top, paint);
        canvas.drawLine(frame.right, frame.top - cornerOffset,
                frame.right, frame.top - cornerOffset + cornerLength, paint);

        canvas.drawLine(frame.right + cornerOffset, frame.bottom,
                frame.right + cornerOffset - cornerLength, frame.bottom, paint);
        canvas.drawLine(frame.right, frame.bottom + cornerOffset,
                frame.right, frame.bottom + cornerOffset - cornerLength, paint);
    }

    private void drawLaser(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(laserColor);

        final Rect frame = framingRect;

        final float fraction = laserAnimator.getAnimatedFraction();
        final int laserTop = frame.top + (int) ((frame.bottom - frame.top - 10) * fraction);
        paint.setAlpha((int) (Math.sin(fraction * Math.PI) * 255));
        canvas.drawRect(frame.left + 10, laserTop, frame.right - 10, laserTop + 2, paint);
        paint.setAlpha(255);
    }

    private void calculateFramingRect() {
        final int rectWidth = getWidth() * 3 / 4;
        final int rectHeight = getHeight() * 3 / 4;
        final int size = Math.min(rectWidth, rectHeight);
        final int left = getLeft() + (getWidth() - size) / 2;
        final int top = getTop() + (getHeight() - size) / 2;
        framingRect.left = left;
        framingRect.top = top;
        framingRect.right = left + size;
        framingRect.bottom = top + size;
    }

    public void startScan() {
        scanning = true;
        if (!laserAnimator.isStarted()) {
            laserAnimator.start();
        }
    }

    public void stopScan() {
        scanning = false;
        if (laserAnimator.isStarted()) {
            laserAnimator.cancel();
        }
    }

    public void drawViewfinder() {
        Bitmap resultBitmap = this.resultBitmap;
        this.resultBitmap = null;
        if (resultBitmap != null) {
            resultBitmap.recycle();
        }
        invalidate();
    }

    /**
     * Draw a bitmap with the result points highlighted instead of the live scanning display.
     *
     * @param barcode An image of the decoded barcode.
     */
    public void drawResultBitmap(Bitmap barcode) {
        resultBitmap = barcode;
        invalidate();
    }

    public void addPossibleResultPoint(ResultPoint point) {
        List<ResultPoint> points = possibleResultPoints;
        synchronized (points) {
            points.add(point);
            int size = points.size();
            if (size > MAX_RESULT_POINTS) {
                // trim it
                points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
            }
        }
    }

}