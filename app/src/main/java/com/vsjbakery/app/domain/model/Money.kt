package com.vsjbakery.app.domain.model

/**
 * Money is always held as a whole number of paise (1 rupee = 100 paise). Using whole numbers
 * avoids the rounding errors that floating-point types introduce into prices and totals.
 */
object Money {

    /** Formats paise as Indian rupees with Indian digit grouping, e.g. 12345600 -> "₹1,23,456.00". */
    fun formatRupees(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val absolute = Math.abs(paise)
        val rupees = absolute / 100
        val remainder = absolute % 100
        return "$sign₹${groupIndian(rupees)}.${remainder.toString().padStart(2, '0')}"
    }

    /** Indian grouping: the last three digits form one group, every two digits before that another. */
    private fun groupIndian(value: Long): String {
        val digits = value.toString()
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        val leading = digits.dropLast(3)
        val leadingGroups = leading.reversed().chunked(2).joinToString(",").reversed()
        return "$leadingGroups,$lastThree"
    }
}
