package com.example.qrattendance.presentation.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX analyzer that feeds frames to ML Kit (QR codes only).
 *
 * - At most one frame is processed at a time ([isProcessing]); extra frames are closed
 *   immediately, and STRATEGY_KEEP_ONLY_LATEST drops stale frames upstream.
 * - [isEnabled] lets the UI pause detection (e.g. while the backend validates a scan)
 *   without unbinding the camera.
 * - Every ImageProxy is closed exactly once, and [close] releases the ML Kit detector,
 *   so nothing leaks when the screen is disposed.
 */
class QrCodeAnalyzer(
    private val isEnabled: () -> Boolean,
    private val onQrCodeDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )
    private val isProcessing = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (isClosed.get() || !isEnabled() || mediaImage == null || !isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                // Listener runs on the main thread.
                val rawValue = barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                if (rawValue != null && isEnabled() && !isClosed.get()) {
                    onQrCodeDetected(rawValue)
                }
            }
            .addOnCompleteListener {
                isProcessing.set(false)
                imageProxy.close()
            }
    }

    fun close() {
        if (isClosed.compareAndSet(false, true)) {
            scanner.close()
        }
    }
}
