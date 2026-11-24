package com.example.qrattendance.presentation.attendance

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.qrattendance.presentation.components.AppTextField
import com.example.qrattendance.presentation.components.AppTopBar
import com.example.qrattendance.presentation.components.EmptyState
import com.example.qrattendance.presentation.components.ErrorBanner
import com.example.qrattendance.presentation.components.FullScreenLoading
import com.example.qrattendance.presentation.theme.Spacing

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttendanceHistoryScreen(
    onBack: () -> Unit,
    viewModel: AttendanceHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppTopBar(title = "Attendance History", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                state.isLoading -> FullScreenLoading()

                state.errorMessage != null -> ErrorBanner(
                    message = state.errorMessage!!,
                    onRetry = viewModel::retry,
                    modifier = Modifier
                        .widthIn(max = Spacing.maxContentWidth)
                        .padding(Spacing.lg),
                )

                state.totalCount == 0 -> EmptyState(
                    icon = Icons.Rounded.History,
                    title = "No attendance yet",
                    message = "Scan your teacher's QR code during class and your attendance will appear here.",
                    modifier = Modifier.widthIn(max = Spacing.maxContentWidth),
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .widthIn(max = Spacing.maxContentWidth)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Spacing.xxxl),
                ) {
                    item(key = "summary") {
                        Text(
                            text = "${state.totalCount} session${if (state.totalCount == 1) "" else "s"} attended",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg),
                        )
                    }
                    item(key = "search") {
                        AppTextField(
                            value = state.query,
                            onValueChange = viewModel::onQueryChange,
                            label = "Search by subject",
                            leadingIcon = Icons.Rounded.Search,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        )
                    }
                    if (state.groups.isEmpty()) {
                        item(key = "no-results") {
                            EmptyState(
                                icon = Icons.Rounded.SearchOff,
                                title = "No attendance for \"${state.query}\"",
                                message = "Try a different subject name.",
                            )
                        }
                    }

                    state.groups.forEach { group ->
                        stickyHeader(key = "header-${group.title}") {
                            Text(
                                text = group.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                            )
                        }
                        items(group.records, key = { it.sessionId }) { record ->
                            AttendanceHistoryItem(
                                record = record,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                            )
                        }
                    }
                    item(key = "nav") { Spacer(Modifier.navigationBarsPadding()) }
                }
            }
        }
    }
}
