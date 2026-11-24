package com.example.qrattendance.presentation.scanner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.qrattendance.domain.model.AttendanceFailureReason
import com.example.qrattendance.presentation.components.FullScreenError
import com.example.qrattendance.presentation.components.PrimaryButton
import com.example.qrattendance.presentation.components.SecondaryButton
import com.example.qrattendance.presentation.theme.Indigo300
import com.example.qrattendance.presentation.theme.Spacing
import com.example.qrattendance.presentation.theme.extendedColors
import com.example.qrattendance.utils.DateTimeUtils

private enum class CameraPermission { UNKNOWN, GRANTED, DENIED, PERMANENTLY_DENIED }

private val FrameSize = 260.dp

@Composable
fun ScannerScreen(
    onClose: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var permission by rememberSaveable {
        mutableStateOf(if (context.hasCameraPermission()) CameraPermission.GRANTED else CameraPermission.UNKNOWN)
    }
    var cameraError by rememberSaveable { mutableStateOf<CameraError?>(null) }
    var isTorchOn by rememberSaveable { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permission = when {
            granted -> CameraPermission.GRANTED
            context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true ->
                CameraPermission.DENIED

            else -> CameraPermission.PERMANENTLY_DENIED
        }
    }

    // Ask automatically only on first entry; after a denial the user decides via the buttons.
    LaunchedEffect(Unit) {
        if (permission == CameraPermission.UNKNOWN) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // The user may grant the permission from system settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (context.hasCameraPermission()) permission = CameraPermission.GRANTED
    }

    when {
        permission == CameraPermission.UNKNOWN -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        )

        permission != CameraPermission.GRANTED -> PermissionDeniedContent(
            permanentlyDenied = permission == CameraPermission.PERMANENTLY_DENIED,
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { context.openAppSettings() },
            onClose = onClose,
        )

        cameraError != null -> FullScreenError(
            title = "Camera unavailable",
            message = if (cameraError == CameraError.NO_CAMERA) {
                "This device doesn't have a usable rear camera, so QR codes can't be scanned here."
            } else {
                "The camera couldn't be started. It may be in use by another app. Close other camera apps and try again."
            },
            icon = Icons.Rounded.VideocamOff,
            primaryActionLabel = "Try again",
            onPrimaryAction = { cameraError = null },
            secondaryActionLabel = "Go back",
            onSecondaryAction = onClose,
        )

        else -> ScannerContent(
            state = state,
            isTorchOn = isTorchOn,
            hasTorch = hasTorch,
            onToggleTorch = { isTorchOn = !isTorchOn },
            onQrCodeDetected = viewModel::onQrCodeDetected,
            onCameraError = { cameraError = it },
            onTorchAvailabilityChanged = { hasTorch = it },
            onScanAgain = viewModel::scanAgain,
            onClose = onClose,
        )
    }
}

@Composable
private fun ScannerContent(
    state: ScannerUiState,
    isTorchOn: Boolean,
    hasTorch: Boolean,
    onToggleTorch: () -> Unit,
    onQrCodeDetected: (String) -> Unit,
    onCameraError: (CameraError) -> Unit,
    onTorchAvailabilityChanged: (Boolean) -> Unit,
    onScanAgain: () -> Unit,
    onClose: () -> Unit,
) {
    val isScanning = state is ScannerUiState.Scanning

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        QrCameraPreview(
            isScanningEnabled = isScanning,
            isTorchOn = isTorchOn,
            onQrCodeDetected = onQrCodeDetected,
            onCameraError = onCameraError,
            onTorchAvailabilityChanged = onTorchAvailabilityChanged,
            modifier = Modifier.fillMaxSize(),
        )

        ScannerOverlay(frameSize = FrameSize, isScanning = isScanning, accentColor = Indigo300)

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayIconButton(icon = Icons.Rounded.Close, contentDescription = "Close scanner", onClick = onClose)
            Text(
                text = "Scan QR Code",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            if (hasTorch) {
                OverlayIconButton(
                    icon = if (isTorchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    contentDescription = if (isTorchOn) "Turn flashlight off" else "Turn flashlight on",
                    onClick = onToggleTorch,
                )
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        if (isScanning) {
            Text(
                text = "Align the QR code within the frame",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = FrameSize / 2 + 32.dp)
                    .padding(horizontal = Spacing.xxl),
            )
        }

        // Result / progress panel
        AnimatedContent(
            targetState = state,
            transitionSpec = { (fadeIn() + slideInVertically { it / 3 }) togetherWith fadeOut() },
            contentKey = { it::class },
            label = "scannerResult",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(Spacing.lg),
        ) { target ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                when (target) {
                    ScannerUiState.Scanning -> Spacer(Modifier.height(1.dp))
                    ScannerUiState.Validating -> ValidatingPanel()
                    is ScannerUiState.Success -> SuccessPanel(target, onDone = onClose)
                    is ScannerUiState.Failure -> FailurePanel(target.reason, onScanAgain = onScanAgain, onClose = onClose)
                }
            }
        }
    }
}

@Composable
private fun OverlayIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.4f),
            contentColor = Color.White,
        ),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun ResultPanel(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .widthIn(max = Spacing.maxContentWidth)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Box(Modifier.padding(Spacing.xl)) { content() }
    }
}

@Composable
private fun ValidatingPanel() {
    ResultPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(Spacing.lg))
            Column {
                Text(
                    "Verifying attendance…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Checking the QR code with the server",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SuccessPanel(result: ScannerUiState.Success, onDone: () -> Unit) {
    val extended = MaterialTheme.extendedColors
    ResultPanel {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            ResultIcon(Icons.Rounded.CheckCircle, extended.successContainer, extended.onSuccessContainer)
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Attendance Marked Successfully!",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                DetailRow(Icons.Rounded.Book, "Subject", result.subject.ifBlank { "—" })
                if (result.teacherName != null) DetailRow(Icons.Rounded.Person, "Teacher", result.teacherName)
                DetailRow(Icons.Rounded.AccessTime, "Marked at", DateTimeUtils.formatDateTime(result.markedAtMillis))
            }
            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(
                text = "Done",
                onClick = onDone,
                leadingIcon = Icons.Rounded.TaskAlt,
                containerColor = extended.success,
                contentColor = extended.onSuccess,
            )
        }
    }
}

@Composable
private fun FailurePanel(reason: AttendanceFailureReason, onScanAgain: () -> Unit, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    val isAlreadyMarked = reason == AttendanceFailureReason.ALREADY_MARKED
    ResultPanel {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            ResultIcon(
                icon = reason.icon(),
                container = if (isAlreadyMarked) extended.warningContainer else colors.errorContainer,
                content = if (isAlreadyMarked) extended.onWarningContainer else colors.onErrorContainer,
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = reason.title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = reason.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.lg))
            if (reason.canRetry) {
                PrimaryButton(text = "Scan Again", onClick = onScanAgain, leadingIcon = Icons.Rounded.QrCodeScanner)
                Spacer(Modifier.height(Spacing.sm))
                SecondaryButton(text = "Back to Dashboard", onClick = onClose)
            } else {
                PrimaryButton(text = "Back to Dashboard", onClick = onClose)
            }
        }
    }
}

private fun AttendanceFailureReason.icon(): ImageVector = when (this) {
    AttendanceFailureReason.SESSION_EXPIRED -> Icons.Rounded.EventBusy
    AttendanceFailureReason.INVALID_QR -> Icons.Rounded.LinkOff
    AttendanceFailureReason.ALREADY_MARKED -> Icons.Rounded.TaskAlt
    AttendanceFailureReason.SESSION_NOT_FOUND -> Icons.Rounded.SearchOff
    AttendanceFailureReason.NETWORK, AttendanceFailureReason.SERVICE_UNAVAILABLE -> Icons.Rounded.CloudOff
    else -> Icons.Rounded.ErrorOutline
}

@Composable
private fun ResultIcon(icon: ImageVector, container: Color, content: Color) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PermissionDeniedContent(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    FullScreenError(
        title = "Camera permission needed",
        message = if (permanentlyDenied) {
            "Camera access was denied. To scan attendance QR codes, enable the Camera permission for this app in Settings."
        } else {
            "The camera is used only to scan your teacher's QR code. Nothing is recorded or uploaded."
        },
        icon = Icons.Rounded.NoPhotography,
        primaryActionLabel = if (permanentlyDenied) "Open Settings" else "Grant Permission",
        onPrimaryAction = if (permanentlyDenied) onOpenSettings else onRequest,
        secondaryActionLabel = "Go back",
        onSecondaryAction = onClose,
    )
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
