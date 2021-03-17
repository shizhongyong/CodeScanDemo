package com.shizy.scan;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;
import java.util.Map;

class CodeAnalyzer implements ImageAnalysis.Analyzer {

    interface AnalyzerListener {

        void analyzerResult(Result result);

    }

    private static final String TAG = CodeAnalyzer.class.getSimpleName();

    private MultiFormatReader multiFormatReader;
    private Map<DecodeHintType, ?> hints;
    private AnalyzerListener analyzerListener;
    private boolean analyzeEnable = true;

    public CodeAnalyzer(AnalyzerListener analyzerListener) {
        multiFormatReader = new MultiFormatReader();
        multiFormatReader.setHints(hints);
        this.analyzerListener = analyzerListener;
    }

    public boolean isAnalyzeEnable() {
        return analyzeEnable;
    }

    public void setAnalyzeEnable(boolean analyzeEnable) {
        this.analyzeEnable = analyzeEnable;
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        Log.d(TAG, "analyze: format = " + image.getFormat());
        Log.d(TAG, "analyze: getWidth = " + image.getWidth());
        Log.d(TAG, "analyze: getHeight = " + image.getHeight());
        Log.d(TAG, "analyze: getRotationDegrees = " + image.getImageInfo().getRotationDegrees());
        if (!analyzeEnable) {
            image.close();
            return;
        }

        Result rawResult = null;
        try {
            final ImageProxy.PlaneProxy[] planes = image.getPlanes();
            final ByteBuffer yBuf = planes[0].getBuffer();
            final ByteBuffer uBuf = planes[1].getBuffer();
            final ByteBuffer vBuf = planes[2].getBuffer();

            final int ySize = yBuf.remaining();
            final int uSize = uBuf.remaining();
            final int vSize = vBuf.remaining();

            // YCbCr_420_SP
            final byte[] yuvData = new byte[ySize + uSize + vSize];
            yBuf.get(yuvData, 0, ySize);
            uBuf.get(yuvData, ySize, uSize);
            vBuf.get(yuvData, ySize + uSize, vSize);

            final PlanarYUVLuminanceSource source =
                    new PlanarYUVLuminanceSource(yuvData, image.getWidth(), image.getHeight(), 0, 0, image.getWidth(), image.getHeight(), false);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            rawResult = multiFormatReader.decodeWithState(bitmap);
        } catch (ReaderException | ArrayIndexOutOfBoundsException e) {
            // continue
        } finally {
            multiFormatReader.reset();
            image.close();
        }

        if (rawResult != null && analyzerListener != null) {
            analyzerListener.analyzerResult(rawResult);
        }
    }
}
