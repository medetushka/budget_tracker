package com.moneytracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneytracker.app.data.AppData
import com.moneytracker.app.data.Expense
import com.moneytracker.app.data.formatDay
import com.moneytracker.app.data.formatMoney
import com.moneytracker.app.data.formatMonth
import com.moneytracker.app.data.pluralRu
import com.moneytracker.app.ui.MoneyViewModel
import com.moneytracker.app.ui.components.EditExpenseDialog
import com.moneytracker.app.ui.components.EmptyState
import com.moneytracker.app.ui.components.ExpenseRow
import com.moneytracker.app.ui.components.PeriodSwitcher
import kotlinx.coroutines.launch
import java.time.YearMonth

@Composable
fun HistoryScreen(data: AppData, vm: MoneyViewModel, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val currentMonth = YearMonth.now()
    var monthIndex by rememberSaveable { mutableStateOf(currentMonth.year * 12 + currentMonth.monthValue - 1) }
    val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)
    var query by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    val currency = data.settings.currency

    val q = query.trim().lowercase()
    val filtered = data.expenses.filter { e ->
        YearMonth.from(e.date) == month &&
            (categoryFilter == null || e.categoryId == categoryFilter) &&
            (q.isEmpty() || e.note.lowercase().contains(q) || data.category(e.categoryId).name.lowercase().contains(q))
    }
    val byDay = filtered.groupBy { it.date }.toSortedMap(compareByDescending { it })
    val total = filtered.sumOf { it.amount }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            PeriodSwitcher(
                title = formatMonth(month),
                onPrev = { monthIndex -= 1 },
                onNext = { monthIndex += 1 },
                nextEnabled = month < currentMonth,
            )
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    formatMoney(total, currency),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${filtered.size} ${pluralRu(filtered.size, "трата", "траты", "трат")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Поиск по комментарию или категории") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Очистить") } }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = categoryFilter == null,
                        onClick = { categoryFilter = null },
                        label = { Text("Все") },
                    )
                }
                items(data.categories, key = { it.id }) { c ->
                    FilterChip(
                        selected = categoryFilter == c.id,
                        onClick = { categoryFilter = if (categoryFilter == c.id) null else c.id },
                        label = { Text("${c.emoji} ${c.name}") },
                    )
                }
            }
        }

        if (byDay.isEmpty()) {
            item { EmptyState("📭", "Нет трат за этот период") }
        }

        byDay.forEach { (day, list) ->
            item(key = "h$day") {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(formatDay(day), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            formatMoney(list.sumOf { it.amount }, currency),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            items(list.sortedByDescending { it.createdAt }, key = { it.id }) { e ->
                ExpenseRow(e, data.category(e.categoryId), currency, onClick = { editing = e })
                HorizontalDivider(Modifier.padding(start = 70.dp))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    editing?.let { e ->
        EditExpenseDialog(
            expense = e,
            categories = data.categories,
            onSave = { vm.updateExpense(it); editing = null },
            onDelete = {
                editing = null
                vm.deleteExpense(e)
                scope.launch {
                    val r = snackbar.showSnackbar(
                        "Удалено: ${formatMoney(e.amount, currency)}",
                        actionLabel = "Вернуть",
                        duration = SnackbarDuration.Short,
                    )
                    if (r == SnackbarResult.ActionPerformed) vm.restoreExpense(e)
                }
            },
            onDismiss = { editing = null },
        )
    }
}
