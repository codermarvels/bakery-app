package com.vsjbakery.app.data.reports

import com.vsjbakery.app.domain.model.DayLine
import com.vsjbakery.app.domain.model.ItemLine
import com.vsjbakery.app.domain.model.MethodTotal
import com.vsjbakery.app.domain.model.ReportRange
import com.vsjbakery.app.domain.model.SalesReport
import com.vsjbakery.app.domain.model.StockLine
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

interface ReportsRepository {
    suspend fun salesReport(range: ReportRange): SalesReport
}

/** Calls the read-only `sales_report` database function (reports and admin accounts only). */
class SupabaseReportsRepository(private val client: SupabaseClient) : ReportsRepository {
    override suspend fun salesReport(range: ReportRange): SalesReport {
        val json = client.postgrest.rpc(
            "sales_report",
            buildJsonObject {
                put("p_from", range.from.toString())
                put("p_to", range.to.toString())
            },
        ).decodeAs<JsonObject>()
        return SalesReportParser.parse(json, range)
    }
}

/** Reads the server's JSON. Kept separate so it can be unit-tested. */
object SalesReportParser {
    fun parse(json: JsonObject, range: ReportRange): SalesReport = SalesReport(
        range = range,
        paidOrders = json.long("paid_orders").toInt(),
        appOrders = json.long("app_orders").toInt(),
        counterSales = json.long("counter_sales").toInt(),
        grossPaise = json.long("gross_paise"),
        appGrossPaise = json.long("app_gross_paise"),
        counterGrossPaise = json.long("counter_gross_paise"),
        refundCount = json.long("refund_count").toInt(),
        refundsPaise = json.long("refunds_paise"),
        netPaise = json.long("net_paise"),
        byMethod = json.list("by_method").map {
            MethodTotal(it.string("method"), it.long("count").toInt(), it.long("amount_paise"))
        },
        items = json.list("items").map { ItemLine(it.string("name"), it.long("quantity"), it.long("amount_paise")) },
        production = json.list("production").map { StockLine(it.string("name"), it.long("quantity"), null) },
        wastage = json.list("wastage").map { StockLine(it.string("name"), it.long("quantity"), it.long("value_paise")) },
        countDifferences = json.list("count_differences").map {
            StockLine(it.string("name"), it.long("quantity"), it.long("value_paise"))
        },
        daily = json.list("daily").map {
            DayLine(
                day = LocalDate.parse(it.string("day")),
                orders = it.long("orders").toInt(),
                grossPaise = it.long("gross_paise"),
                refundsPaise = it.long("refunds_paise"),
            )
        },
    )

    private fun JsonObject.long(key: String): Long = this[key]?.jsonPrimitive?.longOrNull ?: 0L
    private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.list(key: String): List<JsonObject> =
        (this[key] as? JsonArray)?.map(JsonElement::jsonObject).orEmpty()

}
