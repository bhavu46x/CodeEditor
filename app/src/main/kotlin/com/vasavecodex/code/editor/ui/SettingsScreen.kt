package com.vasavecodex.code.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vasavecodex.code.editor.data.AppSettings
import com.vasavecodex.code.editor.data.FileStore
import java.io.File
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings: AppSettings, folder: File, hasAccess: Boolean, onRequestAccess: () -> Unit) {
    var pathText by remember(settings.folderPath) { mutableStateOf(settings.folderPath) }
    var a by remember { mutableStateOf(settings.colA) }
    var b by remember { mutableStateOf(settings.colB) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        SectionTitle("Appearance")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("System", "Light", "Dark").forEachIndexed { i, label ->
                FilterChip(selected = settings.themeMode == i, onClick = { settings.setThemeMode(i) }, label = { Text(label) })
            }
        }

        SectionTitle("Text")
        Text("Font size: ${settings.fontSize.toInt()}")
        Slider(
            value = settings.fontSize, onValueChange = { settings.setFontSize(Math.round(it).toFloat()) },
            valueRange = 12f..28f, steps = 15
        )
        Text("Line spacing: ${String.format(Locale.US, "%.1f", settings.lineSpacing)}")
        Slider(
            value = settings.lineSpacing,
            onValueChange = { settings.setLineSpacing(Math.round(it * 10) / 10f) },
            valueRange = 1.0f..2.2f
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Monospace font", Modifier.weight(1f))
            Switch(checked = settings.monospace, onCheckedChange = { settings.setMonospace(it) })
        }
        Card(Modifier.fillMaxWidth()) {
            Text(
                "Preview: The quick brown fox.\nयह एक नमूना पाठ है।",
                style = settings.editorStyle(),
                modifier = Modifier.padding(12.dp)
            )
        }

        SectionTitle("Saving")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auto save")
                Text(
                    "Saves a moment after every change",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = settings.autoSave, onCheckedChange = { settings.setAutoSave(it) })
        }
        Text("Default file type")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FileStore.EXTENSIONS.forEach { e ->
                FilterChip(selected = settings.defaultExt == e, onClick = { settings.setDefaultExt(e) }, label = { Text(".$e") })
            }
        }

        SectionTitle("Dataset columns")
        OutlinedTextField(
            value = a, onValueChange = { a = it; if (it.isNotBlank()) settings.setColA(it.trim()) },
            label = { Text("First column") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = b, onValueChange = { b = it; if (it.isNotBlank()) settings.setColB(it.trim()) },
            label = { Text("Second column") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        SectionTitle("Storage")
        Text("Current folder: ${folder.absolutePath}", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = pathText, onValueChange = { pathText = it },
            label = { Text("Save folder path") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = { if (pathText.isNotBlank()) settings.setFolderPath(pathText.trim()) }) {
            Text("Apply folder")
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (hasAccess) "All-files access: granted" else "All-files access: not granted",
                Modifier.weight(1f)
            )
            if (!hasAccess) Button(onClick = onRequestAccess) { Text("Grant") }
        }
        Text(
            "Code Editor 1.0 · VasaveCodex",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
