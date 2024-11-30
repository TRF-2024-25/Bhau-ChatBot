package com.example.myapplication;

import static com.example.myapplication.Constants.LABELS_PATH;
import static com.example.myapplication.Constants.MODEL_PATH;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;

import com.google.common.util.concurrent.ListenableFuture;
import com.example.myapplication.databinding.ActivityCameraBinding;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.tensorflow.lite.Interpreter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.io.FileInputStream;
import android.content.res.AssetFileDescriptor;
import java.io.IOException;

public class CameraAct extends AppCompatActivity implements Detector.DetectorListener {

    private ActivityCameraBinding binding;
    private boolean isFrontCamera = false;

    private Preview preview;
    private ImageAnalysis imageAnalyzer;
    private Camera camera;
    private ProcessCameraProvider cameraProvider;
    private Detector detector;

    private ExecutorService cameraExecutor;
    private Set<String> previouslyDetectedObjects;

    {
        previouslyDetectedObjects = new HashSet<>();
    }

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA))) {
                    try {
                        startCamera();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCameraBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        detector = new Detector(getBaseContext(), MODEL_PATH, LABELS_PATH, this);
        detector.setup();

        if (allPermissionsGranted()) {
            try {
                startCamera();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        // Use ListenableFuture to add a listener
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get(); // This is now safe to use within the listener
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            throw new IllegalStateException("Camera initialization failed.");
        }

        Display display = getWindowManager().getDefaultDisplay();
        if (display == null) {
            Log.e("CameraAct", "Display is null. Cannot bind camera use cases.");
            return;
        }

        int rotation = display.getRotation();

        // Always use the front-facing camera
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .build();

        imageAnalyzer = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(rotation)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();

        imageAnalyzer.setAnalyzer(cameraExecutor, imageProxy -> {
            // Create a bitmap buffer from the image proxy
            Bitmap bitmapBuffer = Bitmap.createBitmap(imageProxy.getWidth(), imageProxy.getHeight(), Bitmap.Config.ARGB_8888);
            bitmapBuffer.copyPixelsFromBuffer(imageProxy.getPlanes()[0].getBuffer());

            // Prepare rotation matrix for front camera
            Matrix matrix = new Matrix();
            matrix.postRotate(imageProxy.getImageInfo().getRotationDegrees());
            matrix.postScale(-1f, 1f, imageProxy.getWidth() / 2f, 0); // Mirror image for front camera

            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.getWidth(), bitmapBuffer.getHeight(), matrix, true);

            // Perform detection
            detector.detect(rotatedBitmap);

            bitmapBuffer.recycle(); // Recycle the bitmap buffer
            rotatedBitmap.recycle(); // Recycle the rotated bitmap

            imageProxy.close();
        });

        cameraProvider.unbindAll();

        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer);
            preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());
        } catch (Exception exc) {
            Log.e(TAG, "Use case binding failed", exc);
        }
    }


    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(getBaseContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            cameraExecutor.shutdown();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll(); // Unbind any camera use cases
        }
        if (detector != null) {
            detector.clear(); // Ensure TensorFlow or other detector resources are released
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        if (cameraProvider != null) {
            cameraProvider.unbindAll(); // This should stop all camera use cases
        }
        if (imageAnalyzer != null) {
            imageAnalyzer.clearAnalyzer(); // Stop image analysis
        } // Unbind camera when not in the foreground
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (allPermissionsGranted()) {
            try {
                startCamera();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS);
        }
    }

    @Override
    public void onEmptyDetect() {
        binding.overlay.invalidate();
    }

    @Override
    public void onDetect(@NonNull List<BoundingBox> boundingBoxes, long inferenceTime) {
        runOnUiThread(() -> {
            binding.inferenceTime.setText(inferenceTime + "ms");
            binding.overlay.setResults(boundingBoxes);
            binding.overlay.invalidate();
        });

        Set<String> currentDetectedObjects = new HashSet<>();
        for (BoundingBox box : boundingBoxes) {
            String className = box.getClsName();
            currentDetectedObjects.add(className);
        }

        StringBuilder detectedObj = new StringBuilder();
        for (String obj : currentDetectedObjects) {
            if (detectedObj.length() > 0) {
                detectedObj.append(", ");
            }
            detectedObj.append(obj);
        }
        String jsonString = "{objects: " + detectedObj.toString() + "}";

        // Return JSON string to HomeActivity
        Intent resultIntent = new Intent();
        resultIntent.putExtra("detectedObjects", jsonString);
        setResult(RESULT_OK, resultIntent);
        finish();  // End MainActivity and go back to HomeActivity
    }


    private static final String TAG = "Camera";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
}
