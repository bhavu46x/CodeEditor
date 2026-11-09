package com.vasavecodex.code.editor.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.vasavecodex.code.editor.data.FileStore
import com.vasavecodex.code.editor.data.PRESET_LANGUAGES
import com.vasavecodex.code.editor.data.TableCodec
import com.vasavecodex.code.editor.data.TableKind
import com.vasavecodex.code.editor.data.TableRow
import com.vasavecodex.code.editor.data.cell
import com.vasavecodex.code.editor.data.withCell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

// =====================================================================
//  New file dialog for Translation / Word Meaning
// =====================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewDatasetDialog(kind: TableKind, folder: File, onDismiss: () -> Unit, onCreated: (File) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf(*PRESET_LANGUAGES.toTypedArray()) }
    val selected = remember { mutableStateListOf(*PRESET_LANGUAGES.toTypedArray()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New ${kind.title} file") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("File name (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Languages (columns)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { o ->
                        FilterChip(
                            selected = o in selected,
                            onClick = { if (o in selected) selected.remove(o) else selected.add(o) },
                            label = { Text(o) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = custom, onValueChange = { custom = it },
                        label = { Text("Add another (optional)") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        val c = custom.trim()
                        if (c.isNotEmpty() && options.none { it.equals(c, ignoreCase = true) }) {
                            options.add(c); selected.add(c)
                        }
                        custom = ""
                    }) { Text("Add") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cols = options.filter { it in selected }
                if (cols.size < 2) {
                    Toast.makeText(ctx, "Select at least 2 languages", Toast.LENGTH_SHORT).show()
                } else {
                    val f = FileStore.createTable(folder, name, kind.prefix, cols)
                    if (f != null) onCreated(f)
                    else Toast.makeText(ctx, "Could not create file", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// =====================================================================
//  Editor screen for multi-column CSV files
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableEditorScreen(file: File, settings: AppSettings, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var readOnly by remember { mutableStateOf(false) }
    val columns = remember { mutableStateListOf<String>() }
    val rows = remember { mutableStateListOf<TableRow>() }
    var version by remember { mutableIntStateOf(0) }
    var savedVersion by remember { mutableIntStateOf(0) }
    var showExit by remember { mutableStateOf(false) }
    var showAddCol by remember { mutableStateOf(false) }

    LaunchedEffect(file) {
        val content = withContext(Dispatchers.IO) { FileStore.read(file) }
        if (content == null) {
            loadFailed = true; readOnly = true
            Toast.makeText(ctx, "Could not read file", Toast.LENGTH_LONG).show()
        } else {
            val (cols, data) = withContext(Dispatchers.Default) { TableCodec.decode(content) }
            if (cols.size < 2) {
                loadFailed = true; readOnly = true
                Toast.makeText(ctx, "Invalid header in file", Toast.LENGTH_LONG).show()
            } else {
                columns.addAll(cols); rows.addAll(data)
            }
        }
        loaded = true
    }

    fun saveNow(force: Boolean = false) {
        if (!loaded || loadFailed || version == savedVersion) return
        if (!force && !settings.autoSave) return
        if (FileStore.write(file, TableCodec.encode(columns.toList(), rows.toList()))) savedVersion = version
        else Toast.makeText(ctx, "Save failed", Toast.LENGTH_SHORT).show()
    }

    // debounced auto save, encoding + file write off the main thread
    LaunchedEffect(version) {
        if (loaded && !loadFailed && settings.autoSave && !readOnly && version != savedVersion) {
            delay(600)
            val v = version
            val cols = columns.toList()
            val data = rows.toList()
            val ok = withContext(Dispatchers.IO) { FileStore.write(file, TableCodec.encode(cols, data)) }
            if (ok) savedVersion = v
        }
    }

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

    val dirty = version != savedVersion
    val status = when {
        !loaded -> "Loading…"
        readOnly -> "Read-only"
        dirty -> if (settings.autoSave) "Saving…" else "Unsaved changes"
        else -> "Saved"
    } + if (loaded && !loadFailed) " · ${rows.size} entries" else ""

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium)
                        Text(status, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { exit() }) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (loaded && !loadFailed) {
                        IconButton(onClick = { if (!readOnly) showAddCol = true }) {
                            Icon(Icons.Filled.Add, "Add language")
                        }
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
                loadFailed -> Text("This file can't be edited.", Modifier.align(Alignment.Center).padding(16.dp))
                else -> TableBody(columns, rows, settings, readOnly) { version++ }
            }
        }
    }

    if (showAddCol) {
        var n by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCol = false },
            title = { Text("Add language / column") },
            text = {
                OutlinedTextField(
                    value = n, onValueChange = { n = it }, singleLine = true,
                    label = { Text("Name (e.g. Telugu)") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = n.trim()
                    if (name.isEmpty() || columns.any { it.equals(name, ignoreCase = true) }) {
                        Toast.makeText(ctx, "Invalid or existing name", Toast.LENGTH_SHORT).show()
                    } else {
                        columns.add(name); version++; showAddCol = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddCol = false }) { Text("Cancel") } }
        )
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

@Composable
private fun TableBody(
    columns: SnapshotStateList<String>,
    rows: SnapshotStateList<TableRow>,
    settings: AppSettings,
    readOnly: Boolean,
    onChanged: () -> Unit
) {
    val style = settings.editorStyle()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var stash by remember { mutableStateOf("") }
    var cur by remember { mutableIntStateOf(0) }          // column the next Send goes to
    var startNew by remember { mutableStateOf(true) }     // next Send starts a new entry
    var editIndex by remember { mutableIntStateOf(-1) }
    var editCol by remember { mutableIntStateOf(0) }
    var deleteIndex by remember { mutableIntStateOf(-1) }

    val curSafe = cur.coerceIn(0, maxOf(columns.size - 1, 0))
    val curName = columns.getOrElse(curSafe) { "" }

    // keep the newest entry visible (also when the keyboard opens/closes)
    LaunchedEffect(rows.size, rows.lastOrNull()) {
        if (rows.isNotEmpty() && editIndex < 0) listState.scrollToItem(rows.lastIndex)
    }
    LaunchedEffect(Unit) {
        snapshotFlow { listState.layoutInfo.viewportEndOffset }.collect {
            if (rows.isNotEmpty()) {
                listState.scrollToItem(if (editIndex in rows.indices) editIndex else rows.lastIndex)
            }
        }
    }

    fun advance() {
        if (curSafe + 1 < columns.size) cur = curSafe + 1 else { cur = 0; startNew = true }
    }

    fun send() {
        val t = input.trim()
        if (t.isEmpty() || readOnly) return
        if (startNew || rows.isEmpty()) {
            rows.add(TableRow(Codec.newId(), List(columns.size) { "" }))
            startNew = false
        }
        val i = rows.lastIndex
        rows[i] = rows[i].withCell(curSafe, t, columns.size)
        input = ""
        onChanged()
        advance()
    }

    fun skip() { if (!readOnly) advance() }
    fun newEntry() { if (!readOnly) { startNew = true; cur = 0 } }

    fun startEdit(i: Int, c: Int) {
        if (readOnly) return
        if (editIndex == -1) stash = input
        editIndex = i; editCol = c
        input = rows[i].cell(c)
    }

    fun finishEdit(save: Boolean) {
        val i = editIndex
        if (save && i in rows.indices) {
            rows[i] = rows[i].withCell(editCol, input.trim(), columns.size)
            onChanged()
        }
        editIndex = -1; input = stash; stash = ""
    }

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
                        "Pick a language chip, write the text, and tap Send. The next language is selected automatically.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            itemsIndexed(rows, key = { _, r -> r.id }) { i, r ->
                EntryCard(
                    index = i, row = r, columns = columns, style = style,
                    editingCol = if (editIndex == i) editCol else -1,
                    canEdit = !readOnly,
                    onEdit = { c -> startEdit(i, c) },
                    onDelete = { deleteIndex = i }
                )
            }
        }

        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editIndex >= 0) {
                    Text(
                        "Editing #${editIndex + 1} · ${columns.getOrElse(editCol) { "" }}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        columns.forEachIndexed { i, c ->
                            FilterChip(selected = i == curSafe, onClick = { cur = i }, label = { Text(c) })
                        }
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !readOnly,
                    textStyle = style,
                    placeholder = {
                        Text(
                            if (readOnly) "Read-only mode"
                            else "Write ${if (editIndex >= 0) columns.getOrElse(editCol) { "" } else curName}…"
                        )
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 180.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editIndex >= 0) {
                        Button(onClick = { finishEdit(true) }, modifier = Modifier.weight(1f)) { Text("Save change") }
                        OutlinedButton(onClick = { finishEdit(false) }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    } else {
                        OutlinedButton(onClick = { skip() }, enabled = !readOnly) { Text("Skip") }
                        OutlinedButton(onClick = { newEntry() }, enabled = !readOnly) { Text("New entry") }
                        Button(onClick = { send() }, enabled = !readOnly, modifier = Modifier.weight(1f)) {
                            Text("Send → $curName", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    if (deleteIndex in rows.indices) {
        AlertDialog(
            onDismissRequest = { deleteIndex = -1 },
            title = { Text("Delete entry #${deleteIndex + 1}?") },
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
private fun EntryCard(
    index: Int,
    row: TableRow,
    columns: List<String>,
    style: TextStyle,
    editingCol: Int,
    canEdit: Boolean,
    onEdit: (Int) -> Unit,
    onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${index + 1}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold)
                if (canEdit) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, "Delete entry")
                    }
                }
            }
            columns.forEachIndexed { c, name ->
                CellBlock(name, row.cell(c), editingCol == c, canEdit, style) { onEdit(c) }
            }
        }
    }
}

@Composable
private fun CellBlock(label: String, value: String, editing: Boolean, canEdit: Boolean, style: TextStyle, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (editing) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(enabled = canEdit, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value.ifEmpty { "—" }, style = style)
    }
}