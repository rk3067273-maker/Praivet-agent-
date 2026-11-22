package com.example.data.hardware

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

private const val TAG = "SentryCameraHelper"

class SentryCameraHelper(private val context: Context) {

    suspend fun captureIntruderPhoto(lifecycleOwner: LifecycleOwner): String? {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w(TAG, "Camera permission not granted; skipping intruder capture.")
            return null
        }

        val captureDir = File(context.filesDir, "sentry_captures").apply { mkdirs() }
        val outputFile = File(captureDir, "sentry_${System.currentTimeMillis()}.jpg")

        return suspendCancellableCoroutine { cont ->
            val providerFuture = ProcessCameraProvider.getInstance(context)
            val mainExecutor = ContextCompat.getMainExecutor(context)

            providerFuture.addListener({
                try {
                    val cameraProvider = providerFuture.get()
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    val selector = when {
                        cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                            CameraSelector.DEFAULT_BACK_CAMERA
                        else -> null
                    }

                    if (selector == null) {
                        if (cont.isActive) cont.resume(null)
                        return@addListener
                    }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, imageCapture)

                    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                    imageCapture.takePicture(
                        outputOptions,
                        mainExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                cameraProvider.unbindAll()
                                if (cont.isActive) cont.resume(outputFile.absolutePath)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e(TAG, "Sentry photo capture failed", exception)
                                cameraProvider.unbindAll()
                                if (cont.isActive) cont.resume(null)
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "CameraX bind failed during Sentry capture", e)
                    if (cont.isActive) cont.resume(null)
                }
            }, mainExecutor)
        }
    }
}
