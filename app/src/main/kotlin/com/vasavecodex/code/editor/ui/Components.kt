package com.vasavecodex.code.editor.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vasavecodex.code.editor.data.AppSettings
import com.vasavecodex.code.editor.data.Codec
import com.vasavecodex.code.editor.data.FileStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", b / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
}

fun formatDate(ms: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

/** Row showing one file (name, size, date) with an optional trailing menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileRow(file: File, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        file.extension.ifEmpty { "file" }.take(5),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            },
            headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text("${formatSize(file.length())} · ${formatDate(file.lastModified())}")
            },
            trailingContent = trailing
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewFileDialog(
    settings: AppSettings,
    folder: File,
    initialExt: String,
    onDismiss: () -> Unit,
    onCreated: (File) -> Unit
) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var ext by remember { mutableStateOf(initialExt) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New file") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("File name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Type", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FileStore.EXTENSIONS.forEach { e ->
                        FilterChip(selected = ext == e, onClick = { ext = e }, label = { Text(".$e") })
                    }
                }
                if (Codec.isDataset(ext)) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Dataset editor: ${settings.colA} / ${settings.colB}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val f = FileStore.create(folder, name, ext, settings.colA, settings.colB)
                if (f != null) onCreated(f)
                else Toast.makeText(ctx, "Could not create file", Toast.LENGTH_SHORT).show()
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
