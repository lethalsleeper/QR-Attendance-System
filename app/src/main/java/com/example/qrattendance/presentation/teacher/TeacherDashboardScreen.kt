package com.example.qrattendance.presentation.teacher

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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.qrattendance.presentation.components.AppCard
import com.example.qrattendance.presentation.components.ConfirmationDialog
import com.example.qrattendance.presentation.components.EmptyState
import com.example.qrattendance.presentation.components.ErrorBanner
import com.example.qrattendance.presentation.components.GradientHeader
import com.example.qrattendance.presentation.components.InitialsAvatar
import com.example.qrattendance.presentation.components.InlineLoading
import com.example.qrattendance.presentation.components.PrimaryButton
import com.example.qrattendance.presentation.components.SectionHeader
import com.example.qrattendance.presentation.components.StatCard
import com.example.qrattendance.presentation.components.StatusPill
import com.example.qrattendance.presentation.theme.Spacing
import com.example.qrattendance.presentation.theme.extendedColors

@Composable
fun TeacherDashboardScreen(
    onStartSession: () -> Unit,
    onOpenSession: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: TeacherDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    if (showLogoutDialog) {
        ConfirmationDialog(
            title = "Log out?",
            message = "Any active session keeps running until it expires. You can reopen it after logging in again.",
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
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "header") {
                TeacherHeader(
                    greeting = state.greeting,
                    name = state.user?.name.orEmpty(),
                    email = state.user?.email.orEmpty(),
                    onLogoutClick = { showLogoutDialog = true },
                )
            }

            item(key = "stats") {
                Row(
                    modifier = Modifier
                        .contentWidth()
                        .offset(y = (-28).dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    StatCard(
                        label = "Sessions",
                        value = state.totalSessions.toString(),
                        icon = Icons.Rounded.Event,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Check-ins",
                        value = state.totalCheckIns.toString(),
                        icon = Icons.Rounded.Groups,
                        modifier = Modifier.weight(1f),
                        accentColor = MaterialTheme.colorScheme.secondary,
                        accentContainer = MaterialTheme.colorScheme.secondaryContainer,
                    )
                    StatCard(
                        label = "Live now",
                        value = state.activeSessions.toString(),
                        icon = Icons.Rounded.Bolt,
                        modifier = Modifier.weight(1f),
                        accentColor = MaterialTheme.extendedColors.success,
                        accentContainer = MaterialTheme.extendedColors.successContainer,
                    )
                }
            }

            item(key = "start") {
                StartSessionCard(
                    onStartSession = onStartSession,
                    modifier = Modifier
                        .contentWidth()
                        .offset(y = (-12).dp),
                )
            }
            if (state.subjectCounts.isNotEmpty()) {
                item(key = "subjects") {
                    SubjectSummaryCard(
                        counts = state.subjectCounts,
                        modifier = Modifier.contentWidth().padding(top = Spacing.sm),
                    )
                }
            }

            item(key = "sectionTitle") {
                SectionHeader(
                    title = "Your sessions",
                    modifier = Modifier
                        .contentWidth()
                        .padding(top = Spacing.sm, bottom = Spacing.sm),
                )
            }

            when {
                state.isLoading -> item(key = "loading") { InlineLoading() }

                state.errorMessage != null -> item(key = "error") {
                    ErrorBanner(
                        message = state.errorMessage!!,
                        onRetry = viewModel::retry,
                        modifier = Modifier.contentWidth(),
                    )
                }

                state.sessions.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.QrCode2,
                        title = "No sessions yet",
                        message = "Start your first session to generate a QR code your students can scan.",
                        actionLabel = "Start a session",
                        onAction = onStartSession,
                        modifier = Modifier.contentWidth(),
                    )
                }

                else -> items(state.sessions, key = { it.session.sessionId }) { item ->
                    SessionCard(
                        item = item,
                        onClick = { onOpenSession(item.session.sessionId) },
                        modifier = Modifier
                            .contentWidth()
                            .padding(bottom = Spacing.sm)
                            .animateItem(),
                    )
                }
            }

            item(key = "bottomSpacer") {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

private fun Modifier.contentWidth(): Modifier = this
    .widthIn(max = Spacing.maxContentWidth)
    .fillMaxWidth()
    .padding(horizontal = Spacing.lg)

@Composable
private fun TeacherHeader(
    greeting: String,
    name: String,
    email: String,
    onLogoutClick: () -> Unit,
) {
    val extended = MaterialTheme.extendedColors
    GradientHeader(bottomPadding = 48.dp) {
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
                    text = name.ifBlank { "Teacher" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = extended.onHeader,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onLogoutClick) {
                Icon(
                    Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = "Log out",
                    tint = extended.onHeader,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(
                text = "Teacher",
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = email,
                style = MaterialTheme.typography.bodySmall,
                color = extended.onHeaderMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StartSessionCard(onStartSession: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier, contentPadding = PaddingValues(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.QrCode2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(
                    text = "Take attendance",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Generate a time-limited QR code for your class",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        PrimaryButton(
            text = "Start Session",
            onClick = onStartSession,
            leadingIcon = Icons.Rounded.PlayArrow,
        )
    }
}
@Composable
private fun SubjectSummaryCard(counts: List<SubjectCount>, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Text(
            text = "By subject",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.sm))
        counts.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${row.sessions} session${if (row.sessions == 1) "" else "s"} · ${row.checkIns} check-ins",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}