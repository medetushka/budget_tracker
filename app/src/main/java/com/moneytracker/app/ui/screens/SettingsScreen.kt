package com.moneytracker.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneytracker.app.BuildConfig
import com.moneytracker.app.data.AppData
import com.moneytracker.app.data.Category
import com.moneytracker.app.data.OTHER_CATEGORY_ID
import com.moneytracker.app.data.minorToInput
import com.moneytracker.app.data.parseAmount
import com.moneytracker.app.data.pluralRu
import com.moneytracker.app.ui.MoneyViewModel
import com.moneytracker.app.ui.components.SectionTitle
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(data: AppData, vm: MoneyViewModel, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<AppData?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var addingCategory by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            snackbar.showSnackbar(if (vm.exportCsv(uri)) "Таблица сохранена" else "Не удалось сохранить файл")
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            snackbar.showSnackbar(if (vm.exportBackup(uri)) "Резервная копия сохранена" else "Не удалось сохранить файл")
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val parsed = vm.readBackup(uri)
            if (parsed == null) snackbar.showSnackbar("Это не файл резервной копии") else pendingImport = parsed
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle("Основное")
        Card(Modifier.padding(horizontal = 16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                var currency by rememberSaveable { mutableStateOf(data.settings.currency) }
                OutlinedTextField(
                    value = currency,
                    onValueChange = {
                        currency = it.take(5)
                        vm.setCurrency(currency)
                    },
                    label = { Text("Валюта (₸, ₽, $, сом…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                var budgetText by rememberSaveable {
                    mutableStateOf(if (data.settings.monthlyBudget > 0) minorToInput(data.settings.monthlyBudget) else "")
                }
                val budgetValid = budgetText.isBlank() || parseAmount(budgetText) != null
                LaunchedEffect(budgetText) {
                    if (budgetText.isBlank()) vm.setMonthlyBudget(0)
                    else parseAmount(budgetText)?.let { vm.setMonthlyBudget(it) }
                }
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    label = { Text("Бюджет на месяц") },
                    supportingText = {
                        Text(if (budgetValid) "Пусто — без бюджета на месяц." else "Неверная сумма")
                    },
                    isError = !budgetValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                var dailyText by rememberSaveable {
                    mutableStateOf(if (data.settings.dailyLimit > 0) minorToInput(data.settings.dailyLimit) else "")
                }
                val dailyValid = dailyText.isBlank() || parseAmount(dailyText) != null
                LaunchedEffect(dailyText) {
                    if (dailyText.isBlank()) vm.setDailyLimit(0)
                    else parseAmount(dailyText)?.let { vm.setDailyLimit(it) }
                }
                OutlinedTextField(
                    value = dailyText,
                    onValueChange = { dailyText = it },
                    label = { Text("Лимит на день") },
                    supportingText = {
                        Text(
                            if (!dailyValid) "Неверная сумма"
                            else if (dailyText.isBlank()) "Пусто — лимит считается из бюджета на месяц (если он задан)."
                            else "Сколько можно тратить каждый день.",
                        )
                    },
                    isError = !dailyValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Категории", Modifier.weight(1f))
            TextButton(onClick = { addingCategory = true }, modifier = Modifier.padding(end = 8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Добавить")
            }
        }
        Card(Modifier.padding(horizontal = 16.dp)) {
            data.categories.forEachIndexed { i, c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editingCategory = c }
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(c.emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(c.name, Modifier.weight(1f))
                    IconButton(onClick = { vm.moveCategory(c, up = true) }, enabled = i > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Выше")
                    }
                    IconButton(onClick = { vm.moveCategory(c, up = false) }, enabled = i < data.categories.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Ниже")
                    }
                    IconButton(onClick = { deletingCategory = c }, enabled = c.id != OTHER_CATEGORY_ID) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                    }
                }
                if (i < data.categories.lastIndex) HorizontalDivider()
            }
        }

        SectionTitle("Данные")
        Card(Modifier.padding(horizontal = 16.dp)) {
            val stamp = LocalDate.now().toString()
            ActionItem(Icons.Filled.Download, "Экспорт в таблицу (CSV)", "Открывается в Excel и Google Таблицах") {
                csvLauncher.launch("money-$stamp.csv")
            }
            HorizontalDivider()
            ActionItem(Icons.Filled.Save, "Сохранить резервную копию", "Файл со всеми тратами и настройками") {
                backupLauncher.launch("money-backup-$stamp.json")
            }
            HorizontalDivider()
            ActionItem(Icons.Filled.Restore, "Восстановить из копии", "Заменит текущие данные") {
                restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
            HorizontalDivider()
            ActionItem(Icons.Filled.DeleteForever, "Удалить все траты", "${data.expenses.size} ${pluralRu(data.expenses.size, "запись", "записи", "записей")}", danger = true) {
                confirmClear = true
            }
        }

        Text(
            "Версия ${BuildConfig.VERSION_NAME}\nДанные хранятся только на этом телефоне.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        Spacer(Modifier.height(16.dp))
    }

    pendingImport?.let { imported ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Восстановить данные?") },
            text = {
                Text(
                    "В копии ${imported.expenses.size} ${pluralRu(imported.expenses.size, "трата", "траты", "трат")} " +
                        "и ${imported.categories.size} ${pluralRu(imported.categories.size, "категория", "категории", "категорий")}. " +
                        "Текущие данные будут заменены.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.replaceAll(imported)
                    pendingImport = null
                    scope.launch { snackbar.showSnackbar("Данные восстановлены") }
                }) { Text("Восстановить") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Отмена") } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Удалить все траты?") },
            text = { Text("Это нельзя отменить. Сначала можно сохранить резервную копию.") },
            confirmButton = {
                TextButton(onClick = { vm.clearExpenses(); confirmClear = false }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена") } },
        )
    }

    deletingCategory?.let { c ->
        val count = data.expenses.count { it.categoryId == c.id }
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("Удалить «${c.name}»?") },
            text = {
                Text(
                    if (count == 0) "Трат в этой категории нет."
                    else "$count ${pluralRu(count, "трата", "траты", "трат")} перейдут в «Другое».",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteCategory(c); deletingCategory = null }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deletingCategory = null }) { Text("Отмена") } },
        )
    }

    if (addingCategory) {
        CategoryDialog(
            title = "Новая категория",
            initial = null,
            onSave = { name, emoji -> vm.addCategory(name, emoji); addingCategory = false },
            onDismiss = { addingCategory = false },
        )
    }

    editingCategory?.let { c ->
        CategoryDialog(
            title = "Изменить категорию",
            initial = c,
            onSave = { name, emoji ->
                vm.updateCategory(c.copy(name = name.trim(), emoji = emoji.trim().ifEmpty { c.emoji }))
                editingCategory = null
            },
            onDismiss = { editingCategory = null },
        )
    }
}

@Composable
private fun ActionItem(icon: ImageVector, title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null, tint = color) },
        headlineContent = { Text(title, color = color) },
        supportingContent = { Text(subtitle) },
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
    )
}

@Composable
private fun CategoryDialog(
    title: String,
    initial: Category?,
    onSave: (name: String, emoji: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(initial?.emoji ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(8) },
                    label = { Text("Эмодзи") },
                    placeholder = { Text("☕") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, emoji) }, enabled = name.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
