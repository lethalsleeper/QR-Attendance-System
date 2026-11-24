package com.example.qrattendance.presentation.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.CoPresent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.qrattendance.domain.model.UserRole
import com.example.qrattendance.presentation.components.AppLogo
import com.example.qrattendance.presentation.components.GradientHeader
import com.example.qrattendance.presentation.theme.Spacing
import com.example.qrattendance.presentation.theme.extendedColors

/** Gradient header shared by the login and registration screens. */
@Composable
fun AuthHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    GradientHeader(modifier = modifier, bottomPadding = 56.dp) {
        Spacer(Modifier.height(Spacing.lg))
        AppLogo(size = 60.dp)
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.extendedColors.onHeader,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extendedColors.onHeaderMuted,
        )
    }
}

/** Two selectable cards: Student / Teacher. */
@Composable
fun RoleSelector(
    selected: UserRole,
    onSelect: (UserRole) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        RoleOption(
            role = UserRole.STUDENT,
            description = "Scan QR codes",
            isSelected = selected == UserRole.STUDENT,
            enabled = enabled,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.md))
        RoleOption(
            role = UserRole.TEACHER,
            description = "Run sessions",
            isSelected = selected == UserRole.TEACHER,
            enabled = enabled,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RoleOption(
    role: UserRole,
    description: String,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: (UserRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.selectable(
            selected = isSelected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = { onSelect(role) },
        ),
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) colors.primaryContainer else colors.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) colors.primary else colors.outline,
        ),
    ) {
        Box(Modifier.padding(14.dp)) {
            Column {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(if (isSelected) colors.primary else colors.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (role == UserRole.STUDENT) Icons.Rounded.School else Icons.Rounded.CoPresent,
                        contentDescription = null,
                        tint = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(role.displayName, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (isSelected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "Selected",
                    tint = colors.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp),
                )
            }
        }
    }
}

@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    val extended = MaterialTheme.extendedColors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = extended.warningContainer,
        contentColor = extended.onWarningContainer,
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
