package com.vasavecodex.code.editor.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vasavecodex.code.editor.data.AppSettings
import com.vasavecodex.code.editor.data.Codec
import com.vasavecodex.code.editor.data.DataRow
import com.vasavecodex.code.editor.data.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshotFlow
import java.io.File
import androidx.compose.material3.IconButtonDefaults


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(file: File, settings: AppSettings, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ext = file.extension.lowercase()
    val dataset = Codec.isDataset(ext)

    var loaded by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var readOnly by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val rows = remember { mutableStateListOf<DataRow>() }
    var version by remember { mutableIntStateOf(0) }
    var savedVersion by remember { mutableIntStateOf(0) }
    var showExit by remember { mutableStateOf(false) }
    var showAnalysis by remember { mutableStateOf(false) }
    var jumpRow by remember { mutableIntStateOf(-1) }
    var jumpNonce by remember { mutableIntStateOf(0) }
    val analysisCache = remember { AnalysisCache() }

    // ---- load ----
    LaunchedEffect(file) {
        val content = withContext(Dispatchers.IO) { FileStore.read(file) }
        if (content == null) {
            loadFailed = true; readOnly = true
            Toast.makeText(ctx, "Could not read file", Toast.LENGTH_LONG).show()
        } else if (dataset) {
            rows.clear(); rows.addAll(Codec.decode(ext, content, settings.colA, settings.colB))
        } else text = content
        loaded = true
    }

    fun serialize(): String =
        if (dataset) Codec.encode(ext, settings.colA, settings.colB, rows.toList()) else text

    /** Synchronous save (manual button, back, pause). */
    fun saveNow(force: Boolean = false) {
        if (!loaded || loadFailed || version == savedVersion) return
        if (!force && !settings.autoSave) return
        if (FileStore.write(file, serialize())) savedVersion = version
        else Toast.makeText(ctx, "Save failed", Toast.LENGTH_SHORT).show()
    }

    // ---- auto save (debounced) ----
    LaunchedEffect(version) {
        if (loaded && !loadFailed && settings.autoSave && !readOnly && version != savedVersion) {
            delay(600)
            val v = version
            val content = serialize()
            val ok = withContext(Dispatchers.IO) { FileStore.write(file, content) }
            if (ok) savedVersion = v
        }
    }

    // ---- save when app goes to background / screen closes ----
    val latestSave by rememberUpdatedState({ saveNow() })
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) latestSave() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); latestSave() }
    }

    fun exit() {
        if (version != savedVersion && !settings.autoSave && !readOnly) showExit = true
        else { saveNow(); onClose() }
    }
    BackHandler { exit() }

    BackHandler(enabled = showAnalysis) { showAnalysis = false }
    val dirty = version != savedVersion
    val status = when {
        !loaded -> "Loading…"
        readOnly -> "Read-only"
        dirty -> if (settings.autoSave) "Saving…" else "Unsaved changes"
        else -> "Saved"
    } + if (dataset && loaded) " · ${rows.size} rows" else ""

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium)
                        Text(if (showAnalysis) "Word Analysis" else status, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { exit() }) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (dataset && loaded) {
                        IconButton(
                            onClick = { showAnalysis = !showAnalysis },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (showAnalysis) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else androidx.compose.ui.graphics.Color.Transparent
                            )
                        ) { Icon(AnalysisIcon, "Word analysis") }
                    }
                    IconButton(onClick = { if (!loadFailed) readOnly = !readOnly }) {
                        Icon(
                            if (readOnly) Icons.Filled.Lock else Icons.Filled.Create,
                            if (readOnly) "Read-only (tap to edit)" else "Editing (tap for read-only)"
                        )
                    }
                    IconButton(onClick = { saveNow(force = true) }) { Icon(Icons.Filled.Check, "Save") }
                }
            )
        }
    ) { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        ) {
            when {
                !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                dataset -> DatasetBody(rows, settings, readOnly, jumpRow, jumpNonce, onChanged = { version++ })
                else -> PlainBody(text, settings, readOnly) { text = it; version++ }
            }
            if (showAnalysis && dataset && loaded) {
                AnalysisScreen(
                    cache = analysisCache,
                    dataVersion = version,
                    snapshot = { rows.toList() },
                    colA = settings.colA,
                    colB = settings.colB,
                    onGoToRow = { i -> showAnalysis = false; jumpRow = i; jumpNonce++ }
               )
           }
        }
    }

    if (showExit) {
        AlertDialog(
            onDismissRequest = { showExit = false },
            title = { Text("Unsaved changes") },
            text = { Text("Save before leaving?") },
            confirmButton = { TextButton(onClick = { saveNow(force = true); onClose() }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { onClose() }) { Text("Discard") } }
        )
    }
}

// =====================================================================
//  PLAIN TEXT MODE  (txt, json, md, py, js, xml, sh, log ...)
// =====================================================================
@Composable
private fun PlainBody(text: String, settings: AppSettings, readOnly: Boolean, onText: (String) -> Unit) {
    val style = settings.editorStyle()
    val stats = remember(text) {
        val words = text.split(Regex("\\s+")).count { it.isNotEmpty() }
        "${text.count { it == '\n' } + 1} lines · $words words · ${text.length} chars"
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
            BasicTextField(
                value = text,
                onValueChange = onText,
                readOnly = readOnly,
                textStyle = style,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 400.dp)
            )
        }
        Surface(tonalElevation = 3.dp) {
            Text(stats, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

// =====================================================================
//  DATASET MODE  (csv / jsonl : instruction + output rows)
// =====================================================================
@Composable
private fun DatasetBody(
    rows: SnapshotStateList<DataRow>,
    settings: AppSettings,
    readOnly: Boolean,
    jumpRow: Int,
    jumpNonce: Int,
    onChanged: () -> Unit
) {
    val style = settings.editorStyle()
    val listState = rememberLazyListState()
    LaunchedEffect(jumpNonce) {
    if (jumpNonce > 0 && jumpRow in rows.indices) listState.scrollToItem(jumpRow)
}
    var input by remember { mutableStateOf("") }
    var stash by remember { mutableStateOf("") }
    var editIndex by remember { mutableIntStateOf(-1) }
    var editA by remember { mutableStateOf(true) }
    var deleteIndex by remember { mutableIntStateOf(-1) }

    // New row added, or the last row changed -> show it
LaunchedEffect(rows.size, rows.lastOrNull()) {
    if (rows.isNotEmpty() && editIndex < 0) listState.scrollToItem(rows.lastIndex)
}

// Keyboard opened/closed (list size changed) -> keep the latest row visible
LaunchedEffect(Unit) {
    snapshotFlow { listState.layoutInfo.viewportEndOffset }.collect {
        if (rows.isNotEmpty()) {
            val target = if (editIndex in rows.indices) editIndex else rows.lastIndex
            listState.scrollToItem(target)
        }
    }
}

    /** Put the typed text into the instruction (toA) or output column of the current row. */
    fun send(toA: Boolean) {
        val t = input.trim()
        if (t.isEmpty() || readOnly) return
        val last = rows.lastOrNull()
        if (last == null || (last.a.isNotEmpty() && last.b.isNotEmpty())) {
            rows.add(DataRow(Codec.newId(), "", ""))
        }
        val i = rows.lastIndex
        rows[i] = if (toA) rows[i].copy(a = t) else rows[i].copy(b = t)
        input = ""
        onChanged()
    }

    fun startEdit(i: Int, a: Boolean) {
        if (readOnly) return
        if (editIndex == -1) stash = input
        editIndex = i; editA = a
        input = if (a) rows[i].a else rows[i].b
    }

    fun finishEdit(save: Boolean) {
        val i = editIndex
        if (save && i in rows.indices) {
            rows[i] = if (editA) rows[i].copy(a = input.trim()) else rows[i].copy(b = input.trim())
            onChanged()
        }
        editIndex = -1; input = stash; stash = ""
    }

    val last = rows.lastOrNull()
    val needOutput = last != null && last.a.isNotEmpty() && last.b.isEmpty()

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (rows.isEmpty()) {
                item {
                    Text(
                        "Type below, then tap “${settings.colA}” or “${settings.colB}” to place the text in that column.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            itemsIndexed(rows, key = { _, r -> r.id }) { i, r ->
                RowCard(
                    index = i, row = r, colA = settings.colA, colB = settings.colB, style = style,
                    editingField = if (editIndex == i) (if (editA) 0 else 1) else -1,
                    canEdit = !readOnly,
                    onEditA = { startEdit(i, true) }, onEditB = { startEdit(i, false) },
                    onDelete = { deleteIndex = i }
                )
            }
        }

        // ---------- input bar ----------
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editIndex >= 0) {
                    Text(
                        "Editing #${editIndex + 1} · ${if (editA) settings.colA else settings.colB}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !readOnly,
                    textStyle = style,
                    placeholder = { Text(if (readOnly) "Read-only mode" else "Write here…") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 180.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editIndex >= 0) {
                        Button(onClick = { finishEdit(true) }, modifier = Modifier.weight(1f)) { Text("Save change") }
                        OutlinedButton(onClick = { finishEdit(false) }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    } else {
                        SendButton("→ ${settings.colA}", !needOutput, !readOnly, Modifier.weight(1f)) { send(true) }
                        SendButton("→ ${settings.colB}", needOutput, !readOnly, Modifier.weight(1f)) { send(false) }
                    }
                }
            }
        }
    }

    if (deleteIndex in rows.indices) {
        AlertDialog(
            onDismissRequest = { deleteIndex = -1 },
            title = { Text("Delete row #${deleteIndex + 1}?") },
            confirmButton = {
                TextButton(onClick = {
                    if (editIndex >= 0) finishEdit(false)
                    rows.removeAt(deleteIndex); deleteIndex = -1; onChanged()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteIndex = -1 }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SendButton(label: String, primary: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (primary) Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label, maxLines = 1) }
    else OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label, maxLines = 1) }
}

@Composable
private fun RowCard(
    index: Int,
    row: DataRow,
    colA: String,
    colB: String,
    style: TextStyle,
    editingField: Int,
    canEdit: Boolean,
    onEditA: () -> Unit,
    onEditB: () -> Unit,
    onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${index + 1}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold)
                if (canEdit) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, "Delete row")
                    }
                }
            }
            FieldBlock(colA, row.a, editingField == 0, canEdit, style, onEditA)
            FieldBlock(colB, row.b, editingField == 1, canEdit, style, onEditB)
        }
    }
}

@Composable
private fun FieldBlock(label: String, value: String, editing: Boolean, canEdit: Boolean, style: TextStyle, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (editing) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(enabled = canEdit, onClick = onClick)
            .padding(10.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value.ifEmpty { "—" }, style = style)
    }
}
