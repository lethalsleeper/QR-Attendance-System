package com.example.qrattendance.presentation.scanner

import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class CameraError { NO_CAMERA, UNAVAILABLE }

/** Camera objects owned by one composition of [QrCameraPreview]; released in [release]. */
private class CameraResources(
    isEnabled: () -> Boolean,
    onQrCodeDetected: (String) -> Unit,
) {
    val executor: ExecutorService = Executors.newSingleThreadExecutor()
    val analyzer = QrCodeAnalyzer(isEnabled = isEnabled, onQrCodeDetected = onQrCodeDetected)
    val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also { it.setAnalyzer(executor, analyzer) }
    val preview: Preview = Preview.Builder().build()
    var provider: ProcessCameraProvider? = null

    fun release() {
        imageAnalysis.clearAnalyzer()
        provider?.unbind(preview, imageAnalysis)
        analyzer.close()
        executor.shutdown()
    }
}

/**
 * CameraX preview + ML Kit analysis bound to the screen's lifecycle.
 * When the composable leaves the composition the use cases are unbound, the analyzer
 * is cleared and closed, and the analysis executor is shut down, so the camera is
 * released immediately and no frames are processed in the background.
 */
@Composable
fun QrCameraPreview(
    isScanningEnabled: Boolean,
    isTorchOn: Boolean,
    onQrCodeDetected: (String) -> Unit,
    onCameraError: (CameraError) -> Unit,
    onTorchAvailabilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnQrCodeDetected by rememberUpdatedState(onQrCodeDetected)
    val currentOnCameraError by rememberUpdatedState(onCameraError)
    val currentOnTorchAvailability by rememberUpdatedState(onTorchAvailabilityChanged)

    val scanningEnabled = remember { AtomicBoolean(isScanningEnabled) }
    scanningEnabled.set(isScanningEnabled)

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val resources = remember(lifecycleOwner) {
        CameraResources(
            isEnabled = { scanningEnabled.get() },
            onQrCodeDetected = { value -> currentOnQrCodeDetected(value) },
        )
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(resources) {
        onDispose {
            camera = null
            resources.release()
        }
    }

    LaunchedEffect(resources) {
        try {
            val provider = ProcessCameraProvider.getInstance(context).awaitOnMain(context)
            resources.provider = provider
            if (!context.hasCameraFeature() || !provider.hasBackCamera()) {
                currentOnCameraError(CameraError.NO_CAMERA)
                return@LaunchedEffect
            }
            resources.preview.setSurfaceProvider(previewView.surfaceProvider)
            provider.unbindAll()
            val boundCamera = provider.bind(lifecycleOwner, resources)
            camera = boundCamera
            currentOnTorchAvailability(boundCamera.cameraInfo.hasFlashUnit())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentOnCameraError(CameraError.UNAVAILABLE)
        }
    }

    LaunchedEffect(camera, isTorchOn) {
        val boundCamera = camera ?: return@LaunchedEffect
        if (boundCamera.cameraInfo.hasFlashUnit()) {
            boundCamera.cameraControl.enableTorch(isTorchOn)
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Suspends until the future completes; listener runs on the main thread. */
private suspend fun <T> ListenableFuture<T>.awaitOnMain(context: Context): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(get())
                } catch (e: ExecutionException) {
                    continuation.resumeWithException(e.cause ?: e)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        continuation.invokeOnCancellation { cancel(false) }
    }

private fun ProcessCameraProvider.bind(owner: LifecycleOwner, resources: CameraResources): Camera =
    bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, resources.preview, resources.imageAnalysis)

private fun ProcessCameraProvider.hasBackCamera(): Boolean = try {
    hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
} catch (e: Exception) {
    false
}

private fun Context.hasCameraFeature(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
