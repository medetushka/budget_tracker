package com.moneytracker.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneytracker.app.data.AppData
import com.moneytracker.app.data.formatMoney
import com.moneytracker.app.data.formatMonth
import com.moneytracker.app.data.formatShortDay
import com.moneytracker.app.data.formatShortMonth
import com.moneytracker.app.ui.components.EmptyState
import com.moneytracker.app.ui.components.PeriodSwitcher
import com.moneytracker.app.ui.theme.ChartPalette
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun StatsScreen(data: AppData) {
    val currentMonth = YearMonth.now()
    var monthIndex by rememberSaveable { mutableStateOf(currentMonth.year * 12 + currentMonth.monthValue - 1) }
    val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)
    val currency = data.settings.currency

    val monthExpenses = data.expenses.filter { YearMonth.from(it.date) == month }
    val total = monthExpenses.sumOf { it.amount }
    val prevTotal = data.expenses.filter { YearMonth.from(it.date) == month.minusMonths(1) }.sumOf { it.amount }
    val today = LocalDate.now()
    val daysCounted = when {
        month == currentMonth -> today.dayOfMonth
        month < currentMonth -> month.lengthOfMonth()
        else -> 1
    }
    val perDay = LongArray(month.lengthOfMonth())
    monthExpenses.forEach { perDay[it.date.dayOfMonth - 1] += it.amount }
    val maxDayIndex = perDay.indices.maxByOrNull { perDay[it] } ?: 0

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        PeriodSwitcher(
            title = formatMonth(month),
            onPrev = { monthIndex -= 1 },
            onNext = { monthIndex += 1 },
            nextEnabled = month < currentMonth,
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTile("Всего", formatMoney(total, currency, false), Modifier.weight(1f))
            StatTile("В среднем за день", formatMoney(total / daysCounted, currency, false), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val diff = if (prevTotal > 0) ((total - prevTotal) * 100.0 / prevTotal).roundToInt() else null
            StatTile(
                "К прошлому месяцу",
                when {
                    diff == null -> "—"
                    diff > 0 -> "+$diff%"
                    else -> "$diff%"
                },
                Modifier.weight(1f),
                valueColor = when {
                    diff == null -> null
                    diff > 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
            )
            StatTile(
                "Самый дорогой день",
                if (total > 0) "${formatShortDay(month.atDay(maxDayIndex + 1))}\n${formatMoney(perDay[maxDayIndex], currency, false)}" else "—",
                Modifier.weight(1f),
            )
        }

        if (total == 0L) {
            EmptyState("📊", "В этом месяце трат нет")
        } else {
            ChartCard("По дням") {
                BarChart(
                    values = perDay.toList(),
                    labelFor = { i -> "${formatShortDay(month.atDay(i + 1))}: ${formatMoney(perDay[i], currency)}" },
                    axisLabels = listOf("1", "${month.lengthOfMonth() / 2}", "${month.lengthOfMonth()}"),
                )
            }

            ChartCard("По категориям") {
                val byCategory = monthExpenses.groupBy { data.category(it.categoryId) }
                    .mapValues { (_, l) -> l.sumOf { it.amount } }
                    .toList()
                    .sortedByDescending { it.second }
                byCategory.forEachIndexed { i, (cat, sum) ->
                    val color = ChartPalette[i % ChartPalette.size]
                    val share = sum.toFloat() / total
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${cat.emoji} ${cat.name}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${(share * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(formatMoney(sum, currency, false), fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(share.coerceAtLeast(0.01f))
                                    .fillMaxHeight()
                                    .background(color, RoundedCornerShape(4.dp)),
                            )
                        }
                    }
                }
            }

            ChartCard("Самые крупные траты") {
                monthExpenses.sortedByDescending { it.amount }.take(5).forEach { e ->
                    val c = data.category(e.categoryId)
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.emoji)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.note.ifBlank { c.name }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                formatShortDay(e.date),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(formatMoney(e.amount, currency), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Last 12 months, ending with the selected one.
        val months = (11 downTo 0).map { month.minusMonths(it.toLong()) }
        val monthTotals = months.map { m -> data.expenses.filter { YearMonth.from(it.date) == m }.sumOf { it.amount } }
        if (monthTotals.any { it > 0 }) {
            ChartCard("За 12 месяцев") {
                BarChart(
                    values = monthTotals,
                    labelFor = { i -> "${formatMonth(months[i])}: ${formatMoney(monthTotals[i], currency, false)}" },
                    axisLabels = listOf(formatShortMonth(months.first()), formatShortMonth(months[6]), formatShortMonth(months.last())),
                )
            }
        }

        if (data.settings.monthlyBudget > 0) {
            ChartCard("Бюджет") {
                val budget = data.settings.monthlyBudget
                val left = budget - total
                Text(
                    if (left >= 0) "Осталось ${formatMoney(left, currency, false)} из ${formatMoney(budget, currency, false)}"
                    else "Превышен на ${formatMoney(-left, currency, false)}",
                    color = if (left >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Simple bar chart; tap a bar to see its value. */
@Composable
private fun BarChart(values: List<Long>, labelFor: (Int) -> String, axisLabels: List<String>) {
    var selected by remember(values) { mutableStateOf(values.indices.lastOrNull { values[it] > 0 }) }
    val barColor = MaterialTheme.colorScheme.primary
    val dimColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(barColor, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            selected?.let(labelFor) ?: "Нажмите на столбик",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Spacer(Modifier.height(8.dp))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .pointerInput(values) {
                detectTapGestures { offset ->
                    val slot = size.width.toFloat() / values.size
                    selected = (offset.x / slot).toInt().coerceIn(0, values.size - 1)
                }
            },
    ) {
        val slot = size.width / values.size
        val gap = (slot * 0.25f).coerceAtMost(6.dp.toPx())
        val barWidth = slot - gap
        values.forEachIndexed { i, v ->
            val x = i * slot + gap / 2
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(x, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(3.dp.toPx()),
                alpha = 0.4f,
            )
            if (v > 0) {
                val h = (v.toFloat() / max) * size.height
                drawRoundRect(
                    color = if (selected == null || selected == i) barColor else dimColor,
                    topLeft = Offset(x, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        axisLabels.forEachIndexed { i, l ->
            Text(
                l,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = when (i) {
                    0 -> androidx.compose.ui.text.style.TextAlign.Start
                    axisLabels.lastIndex -> androidx.compose.ui.text.style.TextAlign.End
                    else -> androidx.compose.ui.text.style.TextAlign.Center
                },
            )
        }
    }
}
