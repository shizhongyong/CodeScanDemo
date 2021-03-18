package com.shizy.scan;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.shizy.scan.view.ViewfinderView;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CodeScanFragment extends Fragment {

    private static final String TAG = CodeScanFragment.class.getSimpleName();

    private static final int PERMISSIONS_REQUEST_CODE = 1;
    private static final String[] PERMISSIONS_REQUIRED = {Manifest.permission.CAMERA};

    private CodeAnalyzer.AnalyzerListener analyzerListener = new CodeAnalyzer.AnalyzerListener() {
        @Override
        public void analyzerResult(Result result) {
            beepManager.playBeepSoundAndVibrate();
            stopScan();
            Log.d(TAG, "analyze text: " + result.getText());
            ResultPoint[] points = result.getResultPoints();
            if (points != null) {
                for (ResultPoint point : points) {
                    Log.d(TAG, "analyze x = " + point.getX() + "   y = " + point.getY());
                }
            }
        }
    };

    private ExecutorService cameraExecutor;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    private PreviewView previewView;
    private ViewfinderView viewfinderView;
    private BeepManager beepManager;
    private CodeAnalyzer codeAnalyzer;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getContext(), "Permission request denied", Toast.LENGTH_LONG).show();
                    return;
                }
            }

            startCamera();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_code_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        previewView = view.findViewById(R.id.previewView);
        viewfinderView = view.findViewById(R.id.viewfinderView);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!hasPermissions(requireContext())) {
                requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE);
            } else {
                startCamera();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Shut down our background executor
        cameraExecutor.shutdown();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        beepManager = new BeepManager(requireActivity());
    }

    @Override
    public void onResume() {
        super.onResume();
        beepManager.updatePrefs();
    }

    @Override
    public void onPause() {
        super.onPause();
        beepManager.close();
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraExecutor = Executors.newSingleThreadExecutor();

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        codeAnalyzer = new CodeAnalyzer(analyzerListener);
        analysis.setAnalyzer(cameraExecutor, codeAnalyzer);

        cameraProvider.unbindAll();
        Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis);
    }

    public void startScan() {
        requireActivity().runOnUiThread(() -> {
            codeAnalyzer.setAnalyzeEnable(true);
            viewfinderView.startScan();
        });
    }

    public void stopScan() {
        requireActivity().runOnUiThread(() -> {
            codeAnalyzer.setAnalyzeEnable(false);
            viewfinderView.stopScan();
        });
    }

    private boolean hasPermissions(@NonNull Context context) {
        for (String permission : PERMISSIONS_REQUIRED) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

}
