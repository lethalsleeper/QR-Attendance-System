package com.example.qrattendance.presentation.teacher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.qrattendance.domain.model.AttendanceRecord
import com.example.qrattendance.presentation.components.AppCard
import com.example.qrattendance.presentation.components.AppTopBar
import com.example.qrattendance.presentation.components.ConfirmationDialog
import com.example.qrattendance.presentation.components.EmptyState
import com.example.qrattendance.presentation.components.ErrorBanner
import com.example.qrattendance.presentation.components.FullScreenError
import com.example.qrattendance.presentation.components.FullScreenLoading
import com.example.qrattendance.presentation.components.HeaderTextAction
import com.example.qrattendance.presentation.components.InitialsAvatar
import com.example.qrattendance.presentation.components.InlineLoading
import com.example.qrattendance.presentation.components.ObserveAsEvents
import com.example.qrattendance.presentation.components.PresentPill
import com.example.qrattendance.presentation.components.PrimaryButton
import com.example.qrattendance.presentation.components.SessionStatusPill
import com.example.qrattendance.presentation.theme.Spacing
import com.example.qrattendance.presentation.theme.extendedColors
import com.example.qrattendance.utils.DateTimeUtils
import kotlinx.coroutines.launch

@Composable
fun ActiveSessionScreen(
    onBackToDashboard: () -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showEndDialog by rememberSaveable { mutableStateOf(false) }

    ObserveAsEvents(viewModel.messages) { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Keep the display awake while students are scanning.
    val view = LocalView.current
    DisposableEffect(state.isActive) {
        view.keepScreenOn = state.isActive
        onDispose { view.keepScreenOn = false }
    }

    if (showEndDialog) {
        ConfirmationDialog(
            title = "End session now?",
            message = "The QR code will stop working immediately. Students who haven't scanned yet won't be able to mark attendance.",
            confirmLabel = "End session",
            icon = Icons.Rounded.StopCircle,
            isDestructive = true,
            onConfirm = {
                showEndDialog = false
                viewModel.endSession()
            },
            onDismiss = { showEndDialog = false },
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = state.subject.ifBlank { "Session" },
                onBack = onBackToDashboard,
                actions = {
                    if (state.isActive) {
                        HeaderTextAction(
                            text = "End",
                            onClick = { showEndDialog = true },
                            enabled = !state.isEnding,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> FullScreenLoading(message = "Loading session…")
                state.errorMessage != null -> FullScreenError(
                    title = "Session unavailable",
                    message = state.errorMessage!!,
                    icon = Icons.Rounded.SearchOff,
                    primaryActionLabel = "Back to dashboard",
                    onPrimaryAction = onBackToDashboard,
                )

                else -> ActiveSessionContent(state = state, onBackToDashboard = onBackToDashboard)
            }
        }
    }
}

@Composable
private fun ActiveSessionContent(
    state: ActiveSessionUiState,
    onBackToDashboard: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 720.dp
        if (isWide) {
            // Tablet / landscape: QR on the left, live list on the right.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    item { SessionInfoCard(state) }
                    item { QrSection(state, onBackToDashboard) }
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    attendanceItems(state)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.lg,
                    bottom = 32.dp,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "info") {
                    SessionInfoCard(state, Modifier.widthIn(max = Spacing.maxContentWidth))
                    Spacer(Modifier.height(Spacing.md))
                }
                item(key = "qr") {
                    QrSection(state, onBackToDashboard, Modifier.widthIn(max = Spacing.maxContentWidth))
                    Spacer(Modifier.height(Spacing.lg))
                }
                attendanceItems(state)
                item(key = "nav") { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

@Composable
private fun SessionInfoCard(state: ActiveSessionUiState, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.subject,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Started ${DateTimeUtils.formatTime(state.createdAtMillis)} · " +
                        "${DateTimeUtils.formatDate(state.createdAtMillis)} · ${state.durationMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            SessionStatusPill(isActive = state.isActive)
        }
    }
}

@Composable
private fun QrSection(
    state: ActiveSessionUiState,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state.isActive,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "qrSection",
        modifier = modifier.fillMaxWidth(),
    ) { active ->
        if (active) {
            ActiveQrCard(state)
        } else {
            ExpiredCard(attendanceCount = state.attendanceCount, onBackToDashboard = onBackToDashboard)
        }
    }
}

@Composable
private fun ActiveQrCard(state: ActiveSessionUiState) {
    val extended = MaterialTheme.extendedColors
    AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.lg)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // QR codes always render dark-on-white (even in dark mode) for reliable scanning.
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.qrCode != null -> Image(
                        bitmap = state.qrCode,
                        contentDescription = "Attendance QR code for ${state.subject}",
                        modifier = Modifier.fillMaxSize(),
                        filterQuality = FilterQuality.None,
                    )

                    state.qrError != null -> Text(
                        text = state.qrError,
                        color = Color(0xFF991B1B),
                        textAlign = TextAlign.Center,
                    )

                    else -> CircularProgressIndicator()
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = state.countdownText,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
                color = if (state.progress < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "remaining · expires at ${DateTimeUtils.formatTimeWithSeconds(state.expiresAtMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = if (state.progress < 0.2f) MaterialTheme.colorScheme.error else extended.success,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Ask students to open the app, tap “Scan QR” and point their camera at this code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ExpiredCard(attendanceCount: Int, onBackToDashboard: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.xl)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.EventBusy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Session Expired",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "This QR code no longer accepts attendance. " +
                    "$attendanceCount student${if (attendanceCount == 1) "" else "s"} marked present.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(text = "Back to Dashboard", onClick = onBackToDashboard)
        }
    }
}

private fun LazyListScope.attendanceItems(state: ActiveSessionUiState) {
    item(key = "attendanceHeader") {
        AttendanceHeader(count = state.attendanceCount, isActive = state.isActive)
    }
    when {
        state.isAttendanceLoading -> item(key = "attendanceLoading") { InlineLoading() }
        state.attendanceError != null -> item(key = "attendanceError") {
            ErrorBanner(message = state.attendanceError)
        }

        state.attendees.isEmpty() -> item(key = "attendanceEmpty") {
            EmptyState(
                icon = if (state.isActive) Icons.Rounded.HourglassEmpty else Icons.Rounded.Groups,
                title = if (state.isActive) "Waiting for students…" else "No attendance recorded",
                message = if (state.isActive) {
                    "Students appear here instantly as they scan the QR code."
                } else {
                    "No students marked attendance during this session."
                },
            )
        }

        else -> items(state.attendees, key = { it.studentId }) { record ->
            AttendeeRow(
                record = record,
                modifier = Modifier
                    .widthIn(max = Spacing.maxContentWidth)
                    .fillMaxWidth()
                    .padding(bottom = Spacing.sm)
                    .animateItem(),
            )
        }
    }
}

@Composable
private fun AttendanceHeader(count: Int, isActive: Boolean) {
    val extended = MaterialTheme.extendedColors
    Row(
        modifier = Modifier
            .widthIn(max = Spacing.maxContentWidth)
            .fillMaxWidth()
            .padding(bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Attendance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (isActive) "Updating live" else "Final list",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .background(extended.successContainer, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = extended.onSuccessContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$count present",
                style = MaterialTheme.typography.titleSmall,
                color = extended.onSuccessContainer,
            )
        }
    }
}

@Composable
private fun AttendeeRow(record: AttendanceRecord, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(name = record.studentName, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.studentName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = record.rollNumber?.let { "Roll No. $it" } ?: "No roll number",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                PresentPill()
                Spacer(Modifier.height(4.dp))
                Text(
                    text = DateTimeUtils.formatTimeWithSeconds(record.markedAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
