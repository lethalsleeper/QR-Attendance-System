package com.example.qrattendance.presentation.student

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.qrattendance.domain.model.AttendanceStats
import com.example.qrattendance.domain.model.CourseStats
import com.example.qrattendance.presentation.attendance.AttendanceHistoryItem
import com.example.qrattendance.presentation.components.AppCard
import com.example.qrattendance.presentation.components.ConfirmationDialog
import com.example.qrattendance.presentation.components.EmptyState
import com.example.qrattendance.presentation.components.ErrorBanner
import com.example.qrattendance.presentation.components.GradientHeader
import com.example.qrattendance.presentation.components.InitialsAvatar
import com.example.qrattendance.presentation.components.InlineLoading
import com.example.qrattendance.presentation.components.PrimaryButton
import com.example.qrattendance.presentation.components.SectionHeader
import com.example.qrattendance.presentation.components.StatusPill
import com.example.qrattendance.presentation.components.headerBrush
import com.example.qrattendance.presentation.theme.Spacing
import com.example.qrattendance.presentation.theme.extendedColors
import java.util.Locale

@Composable
fun StudentDashboardScreen(
    onScanQr: () -> Unit,
    onViewHistory: () -> Unit,
    onLogout: () -> Unit,
    viewModel: StudentDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    if (showLogoutDialog) {
        ConfirmationDialog(
            title = "Log out?",
            message = "You'll need to log in again to scan attendance QR codes.",
            confirmLabel = "Log out",
            icon = Icons.AutoMirrored.Rounded.Logout,
            isDestructive = true,
            onConfirm = {
                showLogoutDialog = false
                onLogout()
            },
            onDismiss = { showLogoutDialog = false },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "header") {
                StudentHeader(
                    greeting = state.greeting,
                    name = state.user?.name.orEmpty(),
                    rollNumber = state.user?.rollNumber,
                    onLogoutClick = { showLogoutDialog = true },
                )
            }

            item(key = "scan") {
                ScanCard(
                    onScanQr = onScanQr,
                    modifier = Modifier
                        .contentWidth()
                        .offset(y = (-32).dp),
                )
            }

            item(key = "stats") {
                StatsCard(
                    state = state.stats,
                    attendedFromHistory = state.totalAttended,
                    onRetry = viewModel::refreshStats,
                    modifier = Modifier
                        .contentWidth()
                        .offset(y = (-16).dp),
                )
            }

            item(key = "recentHeader") {
                SectionHeader(
                    title = "Recent attendance",
                    actionLabel = if (state.recent.isNotEmpty()) "View all" else null,
                    onAction = onViewHistory,
                    modifier = Modifier.contentWidth(),
                )
            }

            when {
                state.isHistoryLoading -> item(key = "historyLoading") { InlineLoading() }
                state.historyError != null -> item(key = "historyError") {
                    ErrorBanner(
                        message = state.historyError!!,
                        onRetry = viewModel::retryHistory,
                        modifier = Modifier.contentWidth(),
                    )
                }

                state.recent.isEmpty() -> item(key = "historyEmpty") {
                    EmptyState(
                        icon = Icons.Rounded.History,
                        title = "No attendance yet",
                        message = "When your teacher starts a session, tap “Scan QR” to mark yourself present.",
                        modifier = Modifier.contentWidth(),
                    )
                }

                else -> items(state.recent, key = { it.sessionId }) { record ->
                    AttendanceHistoryItem(
                        record = record,
                        modifier = Modifier
                            .contentWidth()
                            .padding(vertical = Spacing.xs)
                            .animateItem(),
                    )
                }
            }

            item(key = "nav") { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

private fun Modifier.contentWidth(): Modifier = this
    .widthIn(max = Spacing.maxContentWidth)
    .fillMaxWidth()
    .padding(horizontal = Spacing.lg)

@Composable
private fun StudentHeader(
    greeting: String,
    name: String,
    rollNumber: String?,
    onLogoutClick: () -> Unit,
) {
    val extended = MaterialTheme.extendedColors
    GradientHeader(bottomPadding = 52.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(
                name = name,
                size = 52.dp,
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$greeting,",
                    style = MaterialTheme.typography.bodyMedium,
                    color = extended.onHeaderMuted,
                )
                Text(
                    text = name.ifBlank { "Student" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = extended.onHeader,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onLogoutClick) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Log out", tint = extended.onHeader)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusPill(
                text = "Student",
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White,
            )
            StatusPill(
                text = rollNumber?.let { "Roll No. $it" } ?: "No roll number",
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White,
            )
        }
    }
}

@Composable
private fun ScanCard(onScanQr: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(headerBrush(), MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text(
                        text = "Mark your attendance",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Scan the QR code displayed by your teacher",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            PrimaryButton(text = "Scan QR", onClick = onScanQr, leadingIcon = Icons.Rounded.QrCodeScanner)
        }
    }
}

private fun formatPercent(value: Double?): String =
    value?.let { String.format(Locale.getDefault(), "%.0f%%", it) } ?: "—"

@Composable
private fun StatsCard(
    state: StatsState,
    attendedFromHistory: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats: AttendanceStats? = state.stats
    val extended = MaterialTheme.extendedColors
    AppCard(modifier = modifier, contentPadding = PaddingValues(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Insights,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = "Attendance overview",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh statistics", modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val percentage = stats?.percentage
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                CircularProgressIndicator(
                    progress = { ((percentage ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.size(96.dp),
                    strokeWidth = 9.dp,
                    color = when {
                        percentage == null -> MaterialTheme.colorScheme.outline
                        percentage >= 75.0 -> extended.success
                        percentage >= 50.0 -> extended.warning
                        else -> MaterialTheme.colorScheme.error
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    text = formatPercent(percentage),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(Spacing.lg))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatLine(label = "Sessions attended", value = (stats?.attended ?: attendedFromHistory).toString())
                StatLine(label = "Sessions conducted", value = stats?.conducted?.toString() ?: "—")
            }
        }
        if (state.errorMessage != null && stats == null) {
            Spacer(Modifier.height(Spacing.md))
            ErrorBanner(message = "Statistics unavailable: ${state.errorMessage}", onRetry = onRetry)
        }
        if (stats != null && stats.courses.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = "By subject",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                stats.courses.forEach { CourseRow(it) }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "Percentage = sessions you attended ÷ sessions held for your subjects " +
                "(same subject and teacher) since you registered.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Column {
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CourseRow(course: CourseStats) {
    val extended = MaterialTheme.extendedColors
    val percentage = course.percentage
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.subject,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (course.teacherName != null) {
                    Text(
                        text = course.teacherName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "${course.attended}/${course.conducted} · ${formatPercent(percentage)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { ((percentage ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = if ((percentage ?: 0.0) >= 75.0) extended.success else extended.warning,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
    }
}
