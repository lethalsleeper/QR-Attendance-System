package com.example.qrattendance.presentation.teacher

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.qrattendance.presentation.components.AppCard
import com.example.qrattendance.presentation.components.AppTextField
import com.example.qrattendance.presentation.components.AppTopBar
import com.example.qrattendance.presentation.components.ErrorBanner
import com.example.qrattendance.presentation.components.ObserveAsEvents
import com.example.qrattendance.presentation.components.PrimaryButton
import com.example.qrattendance.presentation.theme.Spacing

@Composable
fun StartSessionScreen(
    onBack: () -> Unit,
    onSessionStarted: (String) -> Unit,
    viewModel: StartSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is StartSessionEvent.SessionStarted -> onSessionStarted(event.sessionId)
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Start Session", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = Spacing.maxContentWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                IntroCard()

                AppCard(contentPadding = PaddingValues(Spacing.lg)) {
                    Text(
                        text = "Subject",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    AppTextField(
                        value = state.subject,
                        onValueChange = viewModel::onSubjectChange,
                        label = "Subject name",
                        placeholder = "e.g. Data Structures",
                        leadingIcon = Icons.Rounded.Book,
                        errorMessage = state.subjectError,
                        capitalization = KeyboardCapitalization.Words,
                        enabled = !state.isLoading,
                        imeAction = ImeAction.Done,
                        onImeAction = { focusManager.clearFocus() },
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = "QR code valid for",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        state.durationOptions.forEach { minutes ->
                            DurationOption(
                                minutes = minutes,
                                selected = minutes == state.durationMinutes,
                                enabled = !state.isLoading,
                                onSelect = { viewModel.onDurationSelected(minutes) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Expiry is enforced by the server. Scans after ${state.durationMinutes} " +
                                "minutes are rejected automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (state.errorMessage != null) {
                    ErrorBanner(message = state.errorMessage!!, onDismiss = viewModel::dismissError)
                }

                PrimaryButton(
                    text = "Start Session",
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.start()
                    },
                    isLoading = state.isLoading,
                    leadingIcon = Icons.Rounded.PlayArrow,
                )
            }
        }
    }
}

@Composable
private fun IntroCard() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.QrCode2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text("New attendance session", style = MaterialTheme.typography.titleMedium)
                Text(
                    "A unique, single-use QR code is generated. Students scan it to mark themselves present.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DurationOption(
    minutes: Int,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onSelect,
        ),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.primary else colors.surface,
        contentColor = if (selected) colors.onPrimary else colors.onSurface,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outline),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text("$minutes min", style = MaterialTheme.typography.labelLarge)
        }
    }
}