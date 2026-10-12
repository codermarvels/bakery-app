package com.vsjbakery.app.ui.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vsjbakery.app.data.auth.AuthState
import com.vsjbakery.app.domain.model.AppRole
import com.vsjbakery.app.ui.components.BackTopBar
import com.vsjbakery.app.ui.components.EmptyState
import com.vsjbakery.app.ui.components.SectionHeader

private data class StaffTool(val icon: ImageVector, val title: String, val detail: String)

private val reportTools = listOf(
    StaffTool(Icons.Filled.Today, "Daily summary", "Paid orders, gross sales, refunds and net sales for a day"),
    StaffTool(Icons.Filled.CalendarMonth, "Monthly summary", "The same totals for a whole month"),
    StaffTool(Icons.Filled.DateRange, "Custom date range", "Totals and item-wise quantities for any period"),
    StaffTool(Icons.Filled.PictureAsPdf, "Export PDF", "Save or share any report as a PDF"),
)

@Composable
fun ReportsHomeScreen(authState: AuthState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    StaffHome(
        title = "Sales reports",
        requiredRole = AppRole.REPORTS,
        authState = authState,
        intro = "Read-only sales reports",
        note = "Reports are switched on in Stage 6.",
        tools = reportTools,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun StaffHome(
    title: String,
    requiredRole: AppRole,
    authState: AuthState,
    intro: String,
    note: String,
    tools: List<StaffTool>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        BackTopBar(title = title, onBack = onBack)
        val profile = (authState as? AuthState.SignedIn)?.profile
        if (profile?.role != requiredRole) {
            // Display-only guard; the database refuses the data to other roles anyway.
            EmptyState(
                icon = Icons.Outlined.Lock,
                title = "Access restricted",
                message = "Sign in with a ${requiredRole.shortTitle} account to open this page.",
            )
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionHeader(
                    title = "Hello, ${profile.fullName.ifBlank { requiredRole.title }}",
                    subtitle = "$intro · $note",
                )
            }
            items(tools) { tool -> ToolRow(tool) }
        }
    }
}

@Composable
private fun ToolRow(tool: StaffTool) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tool.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = tool.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Soon",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
