package com.moneytracker.app.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Keeps all app data in memory and persists it to a JSON file in the app's private storage.
 * Every change is written to disk right away (atomically, so a crash can't corrupt the file).
 */
class Repository private constructor(context: Context) {

    private val file = AtomicFile(File(context.filesDir, "money.json"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    fun update(transform: (AppData) -> AppData) {
        _data.update(transform)
        scope.launch { persist() }
    }

    private suspend fun persist() = writeLock.withLock {
        val snapshot = _data.value
        val out = file.startWrite()
        try {
            out.write(Json.encode(snapshot).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.e(TAG, "Failed to save data", e)
        }
    }

    private fun load(): AppData {
        if (!file.baseFile.exists()) return AppData()
        return try {
            Json.decode(String(file.readFully(), Charsets.UTF_8))
        } catch (e: Exception) {
            // Never silently drop the user's data: keep the broken file aside.
            Log.e(TAG, "Failed to read data, keeping a copy", e)
            file.baseFile.copyTo(
                File(file.baseFile.parentFile, "money.corrupt.${System.currentTimeMillis()}.json"),
                overwrite = true,
            )
            AppData()
        }
    }

    companion object {
        private const val TAG = "Repository"

        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
    }
}

object Json {
    private const val VERSION = 1

    fun encode(data: AppData): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put(
            "settings",
            JSONObject()
                .put("currency", data.settings.currency)
                .put("monthlyBudget", data.settings.monthlyBudget),
        )
        root.put("categories", JSONArray().apply {
            data.categories.forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).put("emoji", it.emoji))
            }
        })
        root.put("expenses", JSONArray().apply {
            data.expenses.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("amount", it.amount)
                        .put("category", it.categoryId)
                        .put("note", it.note)
                        .put("date", it.date.toString())
                        .put("createdAt", it.createdAt),
                )
            }
        })
        return root.toString()
    }

    /** Throws on malformed input. */
    fun decode(text: String): AppData {
        val root = JSONObject(text)
        val s = root.optJSONObject("settings")
        val settings = Settings(
            currency = s?.optString("currency", "₸")?.ifBlank { "₸" } ?: "₸",
            monthlyBudget = s?.optLong("monthlyBudget", 0) ?: 0,
        )
        val categories = root.optJSONArray("categories")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Category(o.getString("id"), o.getString("name"), o.optString("emoji", "📦"))
            }
        }?.takeIf { it.isNotEmpty() } ?: DefaultCategories

        val withOther = if (categories.any { it.id == OTHER_CATEGORY_ID }) categories
        else categories + DefaultCategories.last()

        val expenses = root.optJSONArray("expenses")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Expense(
                    id = o.getString("id"),
                    amount = o.getLong("amount"),
                    categoryId = o.optString("category", OTHER_CATEGORY_ID),
                    note = o.optString("note", ""),
                    date = LocalDate.parse(o.getString("date")),
                    createdAt = o.optLong("createdAt", 0),
                )
            }
        } ?: emptyList()

        return AppData(expenses, withOther, settings)
    }
}
