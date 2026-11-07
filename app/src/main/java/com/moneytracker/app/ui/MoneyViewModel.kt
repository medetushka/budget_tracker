package com.moneytracker.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.moneytracker.app.data.AppData
import com.moneytracker.app.data.Category
import com.moneytracker.app.data.Expense
import com.moneytracker.app.data.Json
import com.moneytracker.app.data.OTHER_CATEGORY_ID
import com.moneytracker.app.data.Repository
import com.moneytracker.app.data.minorToInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

class MoneyViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)
    val data: StateFlow<AppData> = repo.data

    // ---- expenses ----

    fun addExpense(amount: Long, categoryId: String, note: String, date: LocalDate) {
        val expense = Expense(
            id = UUID.randomUUID().toString(),
            amount = amount,
            categoryId = categoryId,
            note = note.trim(),
            date = date,
            createdAt = System.currentTimeMillis(),
        )
        repo.update { it.copy(expenses = it.expenses + expense) }
    }

    fun updateExpense(expense: Expense) {
        repo.update { d -> d.copy(expenses = d.expenses.map { if (it.id == expense.id) expense else it }) }
    }

    fun deleteExpense(expense: Expense) {
        repo.update { d -> d.copy(expenses = d.expenses.filterNot { it.id == expense.id }) }
    }

    fun restoreExpense(expense: Expense) {
        repo.update { d ->
            if (d.expenses.any { it.id == expense.id }) d else d.copy(expenses = d.expenses + expense)
        }
    }

    // ---- settings ----

    fun setCurrency(currency: String) {
        repo.update { it.copy(settings = it.settings.copy(currency = currency.trim())) }
    }

    fun setMonthlyBudget(amount: Long) {
        repo.update { it.copy(settings = it.settings.copy(monthlyBudget = amount.coerceAtLeast(0))) }
    }

    fun addCategory(name: String, emoji: String) {
        val category = Category(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            emoji = emoji.trim().ifEmpty { "🏷️" },
        )
        // Keep "Другое" last.
        repo.update { d ->
            val (other, rest) = d.categories.partition { it.id == OTHER_CATEGORY_ID }
            d.copy(categories = rest + category + other)
        }
    }

    fun updateCategory(category: Category) {
        repo.update { d -> d.copy(categories = d.categories.map { if (it.id == category.id) category else it }) }
    }

    /** Expenses of the deleted category move to "Другое". */
    fun deleteCategory(category: Category) {
        if (category.id == OTHER_CATEGORY_ID) return
        repo.update { d ->
            d.copy(
                categories = d.categories.filterNot { it.id == category.id },
                expenses = d.expenses.map {
                    if (it.categoryId == category.id) it.copy(categoryId = OTHER_CATEGORY_ID) else it
                },
            )
        }
    }

    fun moveCategory(category: Category, up: Boolean) {
        repo.update { d ->
            val list = d.categories.toMutableList()
            val i = list.indexOfFirst { it.id == category.id }
            val j = if (up) i - 1 else i + 1
            if (i < 0 || j !in list.indices) return@update d
            list[i] = list[j].also { list[j] = list[i] }
            d.copy(categories = list)
        }
    }

    fun clearExpenses() {
        repo.update { it.copy(expenses = emptyList()) }
    }

    fun replaceAll(newData: AppData) {
        repo.update { newData }
    }

    // ---- import / export ----

    suspend fun exportBackup(uri: Uri): Boolean = writeText(uri, Json.encode(data.value))

    suspend fun exportCsv(uri: Uri): Boolean {
        val d = data.value
        val sb = StringBuilder("﻿") // BOM so Excel opens UTF-8 correctly
        sb.append("Дата;Категория;Сумма;Комментарий\n")
        d.expenses.sortedWith(compareBy({ it.date }, { it.createdAt })).forEach { e ->
            sb.append(e.date).append(';')
                .append(csv(d.category(e.categoryId).name)).append(';')
                .append(minorToInput(e.amount).replace('.', ',')).append(';')
                .append(csv(e.note)).append('\n')
        }
        return writeText(uri, sb.toString())
    }

    /** Returns parsed data or null when the file is not a valid backup. */
    suspend fun readBackup(uri: Uri): AppData? = withContext(Dispatchers.IO) {
        try {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)
                ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@withContext null
            Json.decode(text)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun writeText(uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(text.toByteArray(Charsets.UTF_8))
            } != null
        } catch (e: Exception) {
            false
        }
    }

    private fun csv(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\""
        else value
}
