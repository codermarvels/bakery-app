package com.vsjbakery.app

import com.vsjbakery.app.data.reports.SalesReportParser
import com.vsjbakery.app.domain.model.ReportLayout
import com.vsjbakery.app.domain.model.ReportPreset
import com.vsjbakery.app.domain.model.ReportRange
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportsTest {

    private val today = LocalDate.of(2026, 10, 12)

    @Test
    fun presetsCoverTheRightDays() {
        assertEquals(ReportRange(today, today), ReportRange.forPreset(ReportPreset.TODAY, today))
        assertEquals(
            ReportRange(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 11)),
            ReportRange.forPreset(ReportPreset.YESTERDAY, today),
        )
        assertEquals(
            ReportRange(LocalDate.of(2026, 10, 1), today),
            ReportRange.forPreset(ReportPreset.THIS_MONTH, today),
        )
        assertEquals(
            ReportRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
            ReportRange.forPreset(ReportPreset.LAST_MONTH, today),
        )
        // Last month from 31 March is all of February.
        assertEquals(
            ReportRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)),
            ReportRange.forPreset(ReportPreset.LAST_MONTH, LocalDate.of(2026, 3, 31)),
        )
    }

    @Test
    fun rangeLabels() {
        assertEquals("12 Oct 2026", ReportRange(today, today).label)
        assertEquals("1 Oct 2026 – 12 Oct 2026", ReportRange(LocalDate.of(2026, 10, 1), today).label)
    }

    private val sample = """
        {"from":"2026-10-10","to":"2026-10-10","paid_orders":5,"app_orders":2,"counter_sales":3,
         "gross_paise":110000,"app_gross_paise":92500,"counter_gross_paise":17500,
         "refund_count":2,"refunds_paise":7500,"net_paise":102500,
         "by_method":[{"method":"cash","count":1,"amount_paise":10000},{"method":"razorpay","count":2,"amount_paise":92500}],
         "items":[{"name":"Veg Puff","quantity":5,"amount_paise":12500}],
         "production":[{"name":"Veg Puff","quantity":20}],
         "wastage":[{"name":"Veg Puff","quantity":3,"value_paise":7500}],
         "count_differences":[{"name":"Plum Cake","quantity":-2,"value_paise":-90000}],
         "daily":[{"day":"2026-10-10","orders":5,"gross_paise":110000,"refunds_paise":7500}]}
    """.trimIndent()

    @Test
    fun parsesServerReport() {
        val report = SalesReportParser.parse(Json.parseToJsonElement(sample).jsonObject, ReportRange(today, today))
        assertEquals(5, report.paidOrders)
        assertEquals(102500L, report.netPaise)
        assertEquals("Online (Razorpay)", report.byMethod[1].label)
        assertEquals(5L, report.items.single().quantity)
        assertEquals(-90000L, report.countDifferences.single().valuePaise)
        assertEquals(102500L, report.daily.single().netPaise)
    }

    @Test
    fun missingSectionsBecomeEmpty() {
        val report = SalesReportParser.parse(Json.parseToJsonElement("{}").jsonObject, ReportRange(today, today))
        assertEquals(0L, report.grossPaise)
        assertTrue(report.items.isEmpty())
    }

    @Test
    fun layoutShowsSummaryAndTables() {
        val report = SalesReportParser.parse(Json.parseToJsonElement(sample).jsonObject, ReportRange(today, today))
        val tables = ReportLayout.tables(report)
        val summary = tables.first()
        assertEquals(listOf("Net sales", "₹1,025.00"), summary.rows.last())
        assertEquals(listOf("Refunds (2)", "− ₹75.00"), summary.rows[4])
        val diffs = tables.first { it.title.startsWith("Unrecorded") }
        assertEquals(listOf("Plum Cake", "-2", "-₹900.00"), diffs.rows.single())
        val daily = tables.first { it.title == "Daily breakdown" }
        assertEquals("Sat 10 Oct", daily.rows.single().first())
    }
}
