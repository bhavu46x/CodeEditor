package com.vasavecodex.code.editor.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vasavecodex.code.editor.data.AppSettings
import com.vasavecodex.code.editor.data.ExportFormat
import com.vasavecodex.code.editor.data.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ExportDialog(file: File, settings: AppSettings, folder: File, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf(ExportFormat.MESSAGES) }
    var busy by remember { mutableStateOf(false) }
    val outDir = remember(folder) { File(folder, "exports") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Export ${file.name}") },
        text = {
            Column {
                ExportFormat.values().forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !busy) { format = f }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = format == f, onClick = { format = f }, enabled = !busy)
                        Column {
                            Text(f.label, fontWeight = FontWeight.Medium)
                            Text(f.hint, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Saved to: ${outDir.absolutePath}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val res = withContext(Dispatchers.IO) {
                        Exporter.export(file, outDir, format, settings.colA, settings.colB)
                    }
                    busy = false
                    if (res.error != null) {
                        Toast.makeText(ctx, res.error, Toast.LENGTH_LONG).show()
                    } else {
                        val extra = if (res.skipped > 0) ", ${res.skipped} incomplete rows skipped" else ""
                        Toast.makeText(ctx, "Exported ${res.exported} rows → ${res.file?.name}$extra",
                            Toast.LENGTH_LONG).show()
                        onDismiss()
                    }
                }
            }) { Text("Export") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } }
    )
}