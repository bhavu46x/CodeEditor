package com.vasavecodex.code.editor.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vasavecodex.code.editor.data.AppSettings
import com.vasavecodex.code.editor.data.FileStore
import java.io.File

@Composable
fun FilesScreen(folder: File, settings: AppSettings, refreshKey: Int, onOpen: (File) -> Unit) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var exportTarget by remember { mutableStateOf<File?>(null) }

    val all = remember(folder, refreshKey, version) { FileStore.list(folder) }
    val files = all.filter { it.name.contains(query, ignoreCase = true) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                Text("Files", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    folder.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search files") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            if (files.isEmpty()) {
                Text(
                    if (all.isEmpty()) "This folder is empty. Tap + to create a file." else "No match.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(files, key = { it.absolutePath }) { f ->
                    FileRow(f, onClick = { onOpen(f) }, trailing = {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renameTarget = f })
                                if (f.extension.lowercase().let { it == "csv" || it == "jsonl" }) {
    DropdownMenuItem(text = { Text("Export") }, onClick = { menu = false; exportTarget = f })
}
                                DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                                    menu = false
                                    try { f.copyTo(FileStore.uniqueCopy(f)); version++ }
                                    catch (e: Exception) { Toast.makeText(ctx, "Copy failed", Toast.LENGTH_SHORT).show() }
                                })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleteTarget = f })
                            }
                        }
                    })
                }
            }
        }
        FloatingActionButton(
            onClick = { showNew = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Filled.Add, "New file") }
    }

    if (showNew) {
        NewFileDialog(settings, folder, settings.defaultExt, onDismiss = { showNew = false }) {
            showNew = false
            version++
            onOpen(it)
        }
    }

    renameTarget?.let { f ->
        var name by remember(f) { mutableStateOf(f.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val target = File(f.parentFile, name.trim())
                    if (name.isBlank() || target.exists()) {
                        Toast.makeText(ctx, "Invalid or existing name", Toast.LENGTH_SHORT).show()
                    } else if (f.renameTo(target)) {
                        renameTarget = null; version++
                    } else {
                        Toast.makeText(ctx, "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { f ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete file?") },
            text = { Text(f.name) },
            confirmButton = {
                TextButton(onClick = { f.delete(); deleteTarget = null; version++ }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
    exportTarget?.let { f ->
    ExportDialog(f, settings, folder, onDismiss = { exportTarget = null })
}
}
