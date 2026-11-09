package com.vasavecodex.code.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vasavecodex.code.editor.data.AppSettings
import com.vasavecodex.code.editor.data.FileStore
import com.vasavecodex.code.editor.data.TableKind
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    folder: File,
    hasAccess: Boolean,
    settings: AppSettings,
    refreshKey: Int,
    onOpen: (File) -> Unit,
    onOpenFiles: () -> Unit,
    onRequestAccess: () -> Unit
) {
    var showNew by remember { mutableStateOf(false) }
    var ext by remember { mutableStateOf(settings.defaultExt) }
    var tableKind by remember { mutableStateOf<TableKind?>(null) }
    val recent = remember(folder, refreshKey, showNew) { FileStore.list(folder).take(5) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text("Code Editor", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Write training data comfortably",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!hasAccess) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Storage access needed", fontWeight = FontWeight.Bold)
                        Text(
                            "Without it, files are saved in app-private storage. Grant access to save " +
                                "in a normal folder you can see in your file manager."
                        )
                        Button(onClick = onRequestAccess) { Text("Grant access") }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard("New File", Icons.Filled.Add, Modifier.weight(1f)) {
                    ext = settings.defaultExt; showNew = true
                }
                ActionCard("Files", Icons.Filled.List, Modifier.weight(1f), onOpenFiles)
            }
        }
        item {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Data collection")
        SectionCard("Conversation", "Instruction + output rows") { ext = "csv"; showNew = true }
        SectionCard("Translation", "Dehvali, English, Hindi, Marathi, Gujarati…") {
            tableKind = TableKind.TRANSLATION
        }
        SectionCard("Word Meaning", "Dehvali, English, Hindi, Marathi, Gujarati…") {
            tableKind = TableKind.WORD_MEANING
        }
    }
}
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Quick start")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FileStore.EXTENSIONS.forEach { e ->
                        AssistChip(
                            onClick = { ext = e; showNew = true },
                            label = { Text(".$e") },
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }
        }
        item { SectionTitle("Recent files") }
        if (recent.isEmpty()) {
            item {
                Text(
                    "No files yet. Tap New File to start.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(recent, key = { it.absolutePath }) { f -> FileRow(f, onClick = { onOpen(f) }) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (showNew) {
        NewFileDialog(settings, folder, ext, onDismiss = { showNew = false }) {
            showNew = false
            onOpen(it)
        }
    }
    tableKind?.let { k ->
    NewDatasetDialog(k, folder, onDismiss = { tableKind = null }) {
        tableKind = null
        onOpen(it)
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionCard(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}