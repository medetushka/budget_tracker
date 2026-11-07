package com.moneytracker.app.data

import java.time.LocalDate

/** Amounts are stored in minor units (тиыны / копейки): 150050 == 1500,50. */
data class Expense(
    val id: String,
    val amount: Long,
    val categoryId: String,
    val note: String,
    val date: LocalDate,
    val createdAt: Long,
)

data class Category(
    val id: String,
    val name: String,
    val emoji: String,
)

data class Settings(
    val currency: String = "₸",
    /** Monthly budget in minor units, 0 = not set. */
    val monthlyBudget: Long = 0,
    /** Fixed daily spending limit in minor units, 0 = not set. */
    val dailyLimit: Long = 0,
)

data class AppData(
    val expenses: List<Expense> = emptyList(),
    val categories: List<Category> = DefaultCategories,
    val settings: Settings = Settings(),
) {
    fun category(id: String): Category =
        categories.firstOrNull { it.id == id }
            ?: categories.firstOrNull { it.id == OTHER_CATEGORY_ID }
            ?: Category(OTHER_CATEGORY_ID, "Другое", "📦")
}

const val OTHER_CATEGORY_ID = "other"

val DefaultCategories = listOf(
    Category("food", "Еда", "🍔"),
    Category("groceries", "Продукты", "🛒"),
    Category("transport", "Транспорт", "🚕"),
    Category("home", "Жильё", "🏠"),
    Category("bills", "Связь и ЖКХ", "💡"),
    Category("fun", "Развлечения", "🎮"),
    Category("clothes", "Одежда", "👕"),
    Category("health", "Здоровье", "💊"),
    Category("gifts", "Подарки", "🎁"),
    Category(OTHER_CATEGORY_ID, "Другое", "📦"),
)
