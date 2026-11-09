package com.vasavecodex.code.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vasavecodex.code.editor.data.AppSettings
import com.vasavecodex.code.editor.data.FileStore
import java.io.File

@Composable
fun AppRoot(settings: AppSettings, tick: Int, onRequestAccess: () -> Unit) {
    val ctx = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }
    var openPath by remember { mutableStateOf<String?>(null) }

    val folder = remember(tick, settings.folderPath) { FileStore.root(ctx, settings.folderPath) }
    val hasAccess = remember(tick) { FileStore.hasAccess(ctx) }

    val path = openPath
    if (path != null) {
    val f = File(path)
    val table = remember(path) { FileStore.isTableCsv(f) }
    if (table) TableEditorScreen(file = f, settings = settings, onClose = { openPath = null })
    else EditorScreen(file = f, settings = settings, onClose = { openPath = null })
} else {
        MainTabs(
            tabIndex = tabIndex,
            onTab = { tabIndex = it },
            settings = settings,
            folder = folder,
            hasAccess = hasAccess,
            tick = tick,
            onOpen = { openPath = it.absolutePath },
            onRequestAccess = onRequestAccess
        )
    }
}

@Composable
private fun MainTabs(
    tabIndex: Int,
    onTab: (Int) -> Unit,
    settings: AppSettings,
    folder: File,
    hasAccess: Boolean,
    tick: Int,
    onOpen: (File) -> Unit,
    onRequestAccess: () -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tabIndex == 0, onClick = { onTab(0) },
                    icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = tabIndex == 1, onClick = { onTab(1) },
                    icon = { Icon(Icons.Filled.List, null) }, label = { Text("Files") }
                )
                NavigationBarItem(
                    selected = tabIndex == 2, onClick = { onTab(2) },
                    icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            if (tabIndex == 0) {
                HomeScreen(folder, hasAccess, settings, tick, onOpen, { onTab(1) }, onRequestAccess)
            } else if (tabIndex == 1) {
                FilesScreen(folder, settings, tick, onOpen)
            } else {
                SettingsScreen(settings, folder, hasAccess, onRequestAccess)
            }
        }
    }
}
