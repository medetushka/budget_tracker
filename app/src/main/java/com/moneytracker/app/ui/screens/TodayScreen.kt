package com.moneytracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.moneytracker.app.data.AppData
import com.moneytracker.app.data.Expense
import com.moneytracker.app.data.formatDay
import com.moneytracker.app.data.formatMoney
import com.moneytracker.app.data.parseAmount
import com.moneytracker.app.data.pluralRu
import com.moneytracker.app.ui.MoneyViewModel
import com.moneytracker.app.ui.components.CategoryPicker
import com.moneytracker.app.ui.components.DatePickDialog
import com.moneytracker.app.ui.components.EditExpenseDialog
import com.moneytracker.app.ui.components.EmptyState
import com.moneytracker.app.ui.components.ExpenseRow
import com.moneytracker.app.ui.components.PeriodSwitcher
import com.moneytracker.app.ui.components.SectionTitle
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun TodayScreen(data: AppData, vm: MoneyViewModel, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var today by remember { mutableStateOf(LocalDate.now()) }
    var dateEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val date = LocalDate.ofEpochDay(dateEpoch)

    // If the app stays open past midnight, follow the new day.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val now = LocalDate.now()
            if (now != today) {
                if (dateEpoch == today.toEpochDay()) dateEpoch = now.toEpochDay()
                today = now
            }
        }
    }

    var amountText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf(data.categories.first().id) }
    if (data.categories.none { it.id == categoryId }) categoryId = data.categories.first().id
    var amountError by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }

    val currency = data.settings.currency
    val dayExpenses = data.expenses.filter { it.date == date }.sortedByDescending { it.createdAt }
    val dayTotal = dayExpenses.sumOf { it.amount }

    val month = YearMonth.from(date)
    val monthExpenses = data.expenses.filter { YearMonth.from(it.date) == month }
    val monthTotal = monthExpenses.sumOf { it.amount }
    val spentBeforeDay = monthExpenses.filter { it.date < date }.sumOf { it.amount }
    val budget = data.settings.monthlyBudget

    fun deleteWithUndo(e: Expense) {
        vm.deleteExpense(e)
        scope.launch {
            val r = snackbar.showSnackbar(
                "Удалено: ${formatMoney(e.amount, currency)}",
                actionLabel = "Вернуть",
                duration = SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) vm.restoreExpense(e)
        }
    }

    fun submit() {
        val amount = parseAmount(amountText)
        if (amount == null) {
            amountError = true
            return
        }
        vm.addExpense(amount, categoryId, note, date)
        amountText = ""
        note = ""
    }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            PeriodSwitcher(
                title = formatDay(date, today),
                onPrev = { dateEpoch -= 1 },
                onNext = { dateEpoch += 1 },
                nextEnabled = date < today,
                onTitleClick = { showDatePicker = true },
            )
        }

        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (date == today) "Потрачено сегодня" else "Потрачено за день",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        formatMoney(dayTotal, currency),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (budget > 0) {
                        val daysLeft = month.lengthOfMonth() - date.dayOfMonth + 1
                        val allowance = ((budget - spentBeforeDay) / daysLeft).coerceAtLeast(0)
                        val leftToday = allowance - dayTotal
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (leftToday >= 0) "Можно ещё ${formatMoney(leftToday, currency, false)} из ${formatMoney(allowance, currency, false)} на день"
                            else "Перерасход на ${formatMoney(-leftToday, currency, false)} (лимит дня ${formatMoney(allowance, currency, false)})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (leftToday >= 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { (monthTotal.toFloat() / budget).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (monthTotal > budget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "За месяц: ${formatMoney(monthTotal, currency, false)} из ${formatMoney(budget, currency, false)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(
                            "За месяц: ${formatMoney(monthTotal, currency, false)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it; amountError = false },
                        label = { Text("Сумма, $currency") },
                        isError = amountError,
                        supportingText = if (amountError) {
                            { Text("Введите сумму больше нуля") }
                        } else null,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Комментарий (необязательно)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CategoryPicker(data.categories, categoryId, { categoryId = it }, scrollable = true)
                    Button(onClick = { submit() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("Добавить трату", Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(
                    if (dayExpenses.isEmpty()) "Траты"
                    else "${dayExpenses.size} ${pluralRu(dayExpenses.size, "трата", "траты", "трат")}",
                    Modifier.weight(1f),
                )
                if (date != today) {
                    TextButton(onClick = { dateEpoch = today.toEpochDay() }) { Text("К сегодня") }
                }
            }
        }

        if (dayExpenses.isEmpty()) {
            item { EmptyState("🪙", "За этот день трат пока нет.\nДобавьте первую выше.") }
        } else {
            items(dayExpenses, key = { it.id }) { e ->
                ExpenseRow(e, data.category(e.categoryId), currency, onClick = { editing = e })
                HorizontalDivider(Modifier.padding(start = 70.dp))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showDatePicker) {
        DatePickDialog(
            initial = date,
            onPicked = { dateEpoch = minOf(it, today).toEpochDay() },
            onDismiss = { showDatePicker = false },
        )
    }

    editing?.let { e ->
        EditExpenseDialog(
            expense = e,
            categories = data.categories,
            onSave = { vm.updateExpense(it); editing = null },
            onDelete = { editing = null; deleteWithUndo(e) },
            onDismiss = { editing = null },
        )
    }
}
