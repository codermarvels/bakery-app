package com.vsjbakery.app.domain.model

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The bakery's calendar: reports use Indian Standard Time, the same as the server. */
val BAKERY_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

enum class ReportPreset(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    CUSTOM("Custom"),
}

/** An inclusive range of bakery days. */
data class ReportRange(val from: LocalDate, val to: LocalDate) {
    init {
        require(!from.isAfter(to)) { "from must not be after to" }
    }

    val label: String
        get() = if (from == to) DAY.format(from) else "${DAY.format(from)} – ${DAY.format(to)}"

    companion object {
        private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        const val MAX_DAYS = 366L

        fun forPreset(preset: ReportPreset, today: LocalDate): ReportRange = when (preset) {
            ReportPreset.TODAY, ReportPreset.CUSTOM -> ReportRange(today, today)
            ReportPreset.YESTERDAY -> today.minusDays(1).let { ReportRange(it, it) }
            ReportPreset.THIS_MONTH -> ReportRange(today.withDayOfMonth(1), today)
            ReportPreset.LAST_MONTH -> today.minusMonths(1).let {
                ReportRange(it.withDayOfMonth(1), it.withDayOfMonth(it.lengthOfMonth()))
            }
        }

        fun today(): LocalDate = LocalDate.now(BAKERY_ZONE)
    }
}

data class MethodTotal(val method: String, val count: Int, val amountPaise: Long) {
    val label: String
        get() = when (method) {
            "razorpay" -> "Online (Razorpay)"
            "cash" -> "Cash (counter)"
            "upi" -> "UPI (counter)"
            "card" -> "Card (counter)"
            else -> method
        }
}

data class ItemLine(val name: String, val quantity: Long, val amountPaise: Long)
data class StockLine(val name: String, val quantity: Long, val valuePaise: Long?)
data class DayLine(val day: LocalDate, val orders: Int, val grossPaise: Long, val refundsPaise: Long) {
    val netPaise: Long get() = grossPaise - refundsPaise
}

/** Everything in one sales report, as calculated by the server. */
data class SalesReport(
    val range: ReportRange,
    val paidOrders: Int,
    val appOrders: Int,
    val counterSales: Int,
    val grossPaise: Long,
    val appGrossPaise: Long,
    val counterGrossPaise: Long,
    val refundCount: Int,
    val refundsPaise: Long,
    val netPaise: Long,
    val byMethod: List<MethodTotal>,
    val items: List<ItemLine>,
    val production: List<StockLine>,
    val wastage: List<StockLine>,
    val countDifferences: List<StockLine>,
    val daily: List<DayLine>,
)

/** A titled table, used both on screen and in the PDF so they always match. */
data class ReportTable(
    val title: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val emptyText: String = "None in this period",
    val note: String? = null,
)

object ReportLayout {
    private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    fun summary(r: SalesReport): ReportTable = ReportTable(
        title = "Summary",
        headers = listOf("", "Amount"),
        rows = listOf(
            listOf("Paid orders", "${r.paidOrders}  (app ${r.appOrders}, counter ${r.counterSales})"),
            listOf("Gross sales", Money.formatRupees(r.grossPaise)),
            listOf("   from the app (online)", Money.formatRupees(r.appGrossPaise)),
            listOf("   at the counter", Money.formatRupees(r.counterGrossPaise)),
            listOf("Refunds (${r.refundCount})", "− " + Money.formatRupees(r.refundsPaise)),
            listOf("Net sales", Money.formatRupees(r.netPaise)),
        ),
    )

    fun tables(r: SalesReport): List<ReportTable> = listOf(
        summary(r),
        ReportTable(
            title = "Money received by payment method",
            headers = listOf("Method", "Payments", "Amount"),
            rows = r.byMethod.map { listOf(it.label, it.count.toString(), Money.formatRupees(it.amountPaise)) },
            note = "Before refunds. Counter amounts are as entered by staff.",
        ),
        ReportTable(
            title = "Items sold",
            headers = listOf("Item", "Qty", "Amount"),
            rows = r.items.map { listOf(it.name, it.quantity.toString(), Money.formatRupees(it.amountPaise)) },
            note = "Refunded and undone orders are not included.",
        ),
        ReportTable(
            title = "Daily breakdown",
            headers = listOf("Day", "Orders", "Gross", "Refunds", "Net"),
            rows = r.daily.map {
                listOf(
                    DAY.format(it.day),
                    it.orders.toString(),
                    Money.formatRupees(it.grossPaise),
                    Money.formatRupees(it.refundsPaise),
                    Money.formatRupees(it.netPaise),
                )
            },
        ),
        ReportTable(
            title = "Produced (stock added)",
            headers = listOf("Item", "Qty"),
            rows = r.production.map { listOf(it.name, it.quantity.toString()) },
        ),
        ReportTable(
            title = "Wastage",
            headers = listOf("Item", "Qty", "Value"),
            rows = r.wastage.map { listOf(it.name, it.quantity.toString(), Money.formatRupees(it.valuePaise ?: 0)) },
            note = "Valued at the current selling price.",
        ),
        ReportTable(
            title = "Unrecorded differences (end-of-day counts)",
            headers = listOf("Item", "Qty", "Value"),
            rows = r.countDifferences.map {
                listOf(
                    it.name,
                    (if (it.quantity > 0) "+" else "") + it.quantity,
                    Money.formatRupees(it.valuePaise ?: 0),
                )
            },
            note = "Negative means items missing compared with what the app expected.",
        ),
    )
}
