package com.moneytracker.app.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RU = Locale.forLanguageTag("ru")
private val dayFormat = DateTimeFormatter.ofPattern("d MMMM, EEEE", RU)
private val shortDayFormat = DateTimeFormatter.ofPattern("d MMMM", RU)
private val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", RU)
private val shortMonthFormat = DateTimeFormatter.ofPattern("LLL", RU)

fun formatMoney(minor: Long, currency: String, withKopecks: Boolean = true): String {
    val negative = minor < 0
    val abs = kotlin.math.abs(minor)
    val whole = abs / 100
    val frac = abs % 100
    val grouped = whole.toString().reversed().chunked(3).joinToString(" ").reversed()
    val body = if (frac != 0L && withKopecks) "$grouped,${frac.toString().padStart(2, '0')}" else grouped
    return (if (negative) "−" else "") + body + if (currency.isNotBlank()) " $currency" else ""
}

/** Plain number for edit fields: 150050 -> "1500.5". */
fun minorToInput(minor: Long): String =
    BigDecimal.valueOf(minor).movePointLeft(2).stripTrailingZeros().toPlainString()

/**
 * Parses user input into minor units. Supports "1500", "1 500,50" and sums like "500+250+80".
 * Returns null for invalid or non-positive input.
 */
fun parseAmount(input: String): Long? {
    val cleaned = input.replace(" ", "").replace(" ", "").replace(',', '.')
    if (cleaned.isEmpty()) return null
    val parts = cleaned.split('+')
    var total = BigDecimal.ZERO
    for (p in parts) {
        if (p.isEmpty()) return null
        val v = p.toBigDecimalOrNull() ?: return null
        if (v.signum() < 0) return null
        total += v
    }
    if (total.signum() <= 0) return null
    return try {
        total.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    } catch (e: ArithmeticException) {
        null
    }
}

fun String.capitalizeFirst(): String = replaceFirstChar { it.titlecase(RU) }

fun formatDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Сегодня, " + date.format(shortDayFormat)
    today.minusDays(1) -> "Вчера, " + date.format(shortDayFormat)
    else -> date.format(dayFormat).capitalizeFirst()
}

fun formatShortDay(date: LocalDate): String = date.format(shortDayFormat)

fun formatMonth(month: YearMonth): String = month.format(monthFormat).capitalizeFirst()

fun formatShortMonth(month: YearMonth): String = month.format(shortMonthFormat).trimEnd('.').capitalizeFirst()

fun pluralRu(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
