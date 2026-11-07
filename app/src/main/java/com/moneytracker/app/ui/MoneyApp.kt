package com.moneytracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneytracker.app.ui.screens.HistoryScreen
import com.moneytracker.app.ui.screens.SettingsScreen
import com.moneytracker.app.ui.screens.StatsScreen
import com.moneytracker.app.ui.screens.TodayScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Сегодня", Icons.Filled.Today),
    HISTORY("История", Icons.Filled.History),
    STATS("Статистика", Icons.Filled.BarChart),
    SETTINGS("Настройки", Icons.Filled.Settings),
}

@Composable
fun MoneyApp(vm: MoneyViewModel = viewModel()) {
    val data by vm.data.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    val snackbar = remember { SnackbarHostState() }

    BackHandler(enabled = tab != Tab.TODAY) { tab = Tab.TODAY }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tab) {
                Tab.TODAY -> TodayScreen(data, vm, snackbar)
                Tab.HISTORY -> HistoryScreen(data, vm, snackbar)
                Tab.STATS -> StatsScreen(data)
                Tab.SETTINGS -> SettingsScreen(data, vm, snackbar)
            }
        }
    }
}
