package com.vsjbakery.app.ui.reports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vsjbakery.app.data.auth.AuthState
import com.vsjbakery.app.domain.model.AppRole
import com.vsjbakery.app.domain.model.Money
import com.vsjbakery.app.domain.model.ReportLayout
import com.vsjbakery.app.domain.model.ReportPreset
import com.vsjbakery.app.domain.model.ReportRange
import com.vsjbakery.app.domain.model.ReportTable
import com.vsjbakery.app.domain.model.SalesReport
import com.vsjbakery.app.ui.admin.InfoButton
import com.vsjbakery.app.ui.components.BackTopBar
import com.vsjbakery.app.ui.components.EmptyState
import com.vsjbakery.app.ui.components.MessageCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset

private const val REPORTS_INFO =
    "Choose a period at the top: Today, Yesterday, This month, Last month, or Custom to pick " +
        "any dates (up to one year). Days run midnight to midnight, Indian time.\n\n" +
        "• Gross sales: all money received — online app payments plus counter sales (cash, UPI, card).\n" +
        "• Refunds: money given back, including undone counter sales.\n" +
        "• Net sales = gross sales − refunds.\n" +
        "• Paid orders: orders whose payment was confirmed in the period.\n" +
        "• Items sold: quantities from paid orders that were not refunded.\n" +
        "• Wastage and unrecorded differences (from end-of-day counts) are valued at today's price.\n\n" +
        "Online payments are only counted after the server has verified them with Razorpay. " +
        "Tap Export PDF to save or share the report. This page is read-only; nothing here can " +
        "change orders or stock."

/** Read-only sales reports for the reports and administrator accounts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(authState: AuthState, viewModel: ReportsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        BackTopBar(title = "Sales reports", onBack = onBack)

        val role = (authState as? AuthState.SignedIn)?.profile?.role
        if (role != AppRole.REPORTS && role != AppRole.ADMIN) {
            // Display-only guard; the database refuses report data to other accounts anyway.
            EmptyState(
                icon = Icons.Outlined.Lock,
                title = "Access restricted",
                message = "Sign in with a reports or administrator account to open this page.",
            )
            return@Column
        }
        LaunchedEffect(Unit) { if (state.report == null) viewModel.load() }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.range.label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = viewModel::load, enabled = !state.isLoading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
            }
            InfoButton(title = "Sales reports — how to read them", text = REPORTS_INFO)
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReportPreset.entries.forEach { preset ->
                FilterChip(
                    selected = state.preset == preset,
                    onClick = {
                        if (preset == ReportPreset.CUSTOM) showPicker = true else viewModel.choosePreset(preset)
                    },
                    label = { Text(preset.label) },
                )
            }
        }

        val error = state.errorMessage ?: exportError
        if (error != null) {
            MessageCard(
                message = error,
                isError = true,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        val report = state.report
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            report == null || report.range != state.range -> Spacer(Modifier.weight(1f))
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { SummaryCards(report) }
                // The summary table is shown as cards above; the PDF prints it as a table.
                items(ReportLayout.tables(report).drop(1)) { TableCard(it) }
            }
        }

        if (report != null && !state.isLoading && report.range == state.range) {
            Button(
                onClick = {
                    exporting = true
                    exportError = null
                    scope.launch {
                        try {
                            val file = withContext(Dispatchers.IO) { ReportPdf.write(context, report) }
                            ReportPdf.share(context, file)
                        } catch (e: Exception) {
                            exportError = "Could not create the PDF: ${e.message ?: "unknown error"}"
                        } finally {
                            exporting = false
                        }
                    }
                },
                enabled = !exporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
            ) {
                if (exporting) {
                    CircularProgressIndicator(Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export PDF")
                }
            }
        }
    }

    if (showPicker) {
        val todayMillis = ReportRange.today().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.range.from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = state.range.to.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        if (start != null) {
                            val end = pickerState.selectedEndDateMillis ?: start
                            viewModel.chooseCustom(ReportRange(start.toDate(), end.toDate()))
                        }
                        showPicker = false
                    },
                ) { Text("Show report") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.weight(1f))
        }
    }
}

/** The date picker works in UTC midnight milliseconds. */
private fun Long.toDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun SummaryCards(r: SalesReport) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Net sales", style = MaterialTheme.typography.labelLarge)
                Text(Money.formatRupees(r.netPaise), style = MaterialTheme.typography.headlineMedium)
                Text("Gross sales minus refunds", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                "Gross sales",
                Money.formatRupees(r.grossPaise),
                "App ${Money.formatRupees(r.appGrossPaise)} · Counter ${Money.formatRupees(r.counterGrossPaise)}",
                Modifier.weight(1f),
            )
            StatCard(
                "Refunds",
                Money.formatRupees(r.refundsPaise),
                "${r.refundCount} refund${if (r.refundCount == 1) "" else "s"}",
                Modifier.weight(1f),
            )
        }
        StatCard(
            "Paid orders",
            r.paidOrders.toString(),
            "${r.appOrders} from the app · ${r.counterSales} at the counter",
            Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, detail: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TableCard(t: ReportTable) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            TableRow(t.headers, header = true)
            HorizontalDivider(color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
            if (t.rows.isEmpty()) {
                Text(t.emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            t.rows.forEach { TableRow(it, header = false) }
            t.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TableRow(values: List<String>, header: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        values.forEachIndexed { i, value ->
            Text(
                value,
                style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(if (i == 0) 1.8f else 1f),
            )
        }
    }
}
