package com.vasavecodex.code.editor.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vasavecodex.code.editor.data.DataRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

// ---------------------------------------------------------------- icon
val AnalysisIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(5f, 9.2f); horizontalLineTo(8.2f); verticalLineTo(19f); horizontalLineTo(5f); close()
            moveTo(10.4f, 5f); horizontalLineTo(13.6f); verticalLineTo(19f); horizontalLineTo(10.4f); close()
            moveTo(15.8f, 12f); horizontalLineTo(19f); verticalLineTo(19f); horizontalLineTo(15.8f); close()
        }
    }.build()
}

// ---------------------------------------------------------------- models
enum class WordSort(val label: String, val short: String) {
    FREQ_DESC("Frequency (High to Low)", "Freq ↓"),
    FREQ_ASC("Frequency (Low to High)", "Freq ↑"),
    ALPHA_ASC("A to Z", "A–Z"),
    ALPHA_DESC("Z to A", "Z–A"),
    LONGEST("Longest words first", "Longest")
}

enum class FreqFilter(val label: String, val min: Int, val max: Int) {
    ALL("All words", 1, Int.MAX_VALUE),
    TWO("2+ times", 2, Int.MAX_VALUE),
    FIVE("5+ times", 5, Int.MAX_VALUE),
    TEN("10+ times", 10, Int.MAX_VALUE),
    ONCE("Only once", 1, 1)
}

private val SORTS = WordSort.values().toList()
private val FILTERS = FreqFilter.values().toList()

/**
 * One word. [ids] is the inverted index: every (row, field) where the word occurs,
 * encoded as row * 2 + field (0 = first column, 1 = second), ascending, no duplicates.
 */
@Immutable
class WordStat(val word: String, val count: Int, val rows: Int, val ids: IntArray) {
    val countText: String = count.toString() // formatted once, not on every recomposition
}

@Stable
class AnalysisResult(val totalRows: Int, val totalWords: Int, val words: List<WordStat>) {
    private val sortedCache = HashMap<WordSort, List<WordStat>>()

    /** Each sort order is computed once and then reused. */
    @Synchronized
    fun sorted(sort: WordSort): List<WordStat> = sortedCache.getOrPut(sort) {
        when (sort) {
            WordSort.FREQ_DESC -> words
            WordSort.FREQ_ASC -> words.sortedWith(compareBy<WordStat> { it.count }.thenBy { it.word })
            WordSort.ALPHA_ASC -> words.sortedBy { it.word }
            WordSort.ALPHA_DESC -> words.sortedByDescending { it.word }
            WordSort.LONGEST -> words.sortedWith(
                compareByDescending<WordStat> { it.word.length }.thenByDescending { it.count }
            )
        }
    }
}

/** Lives in EditorScreen. Analysis is recomputed only when the data version changes. */
class AnalysisCache {
    var version = -1
    var rows: List<DataRow> = emptyList()
    var result: AnalysisResult? = null
}

private class Acc {
    var count = 0
    var rows = 0
    var lastRow = -1
    var ids = IntArray(2)
    var n = 0
}

// ---------------------------------------------------------------- engine
/** Letters/digits + combining marks (so Hindi/Devanagari words are not split). */
private fun isWordChar(c: Char): Boolean {
    if (c.isLetterOrDigit()) return true
    val t = Character.getType(c)
    return t == Character.NON_SPACING_MARK.toInt() ||
        t == Character.COMBINING_SPACING_MARK.toInt() ||
        t == Character.ENCLOSING_MARK.toInt()
}

/** Calls action(start, end) for every word. Punctuation ignored; inner apostrophes stay (don't). */
private fun forEachWord(text: String, action: (Int, Int) -> Unit) {
    var i = 0
    val n = text.length
    while (i < n) {
        if (isWordChar(text[i])) {
            val s = i
            while (i < n && (isWordChar(text[i]) ||
                    ((text[i] == '\'' || text[i] == '’') && i + 1 < n && isWordChar(text[i + 1])))
            ) i++
            action(s, i)
        } else i++
    }
}

/** ONE pass over the data: counts + inverted index together. */
private suspend fun analyze(rows: List<DataRow>): AnalysisResult = withContext(Dispatchers.Default) {
    val map = HashMap<String, Acc>(4096)
    var total = 0
    rows.forEachIndexed { r, row ->
        if ((r and 0x3FF) == 0) ensureActive() // cooperative cancellation
        for (fi in 0..1) {
            val text = if (fi == 0) row.a else row.b
            if (text.isEmpty()) continue
            val code = r * 2 + fi
            forEachWord(text) { s, e ->
                val acc = map.getOrPut(text.substring(s, e).lowercase()) { Acc() }
                acc.count++
                total++
                if (acc.lastRow != r) { acc.rows++; acc.lastRow = r }
                if (acc.n == 0 || acc.ids[acc.n - 1] != code) {
                    if (acc.n == acc.ids.size) acc.ids = acc.ids.copyOf(acc.n * 2)
                    acc.ids[acc.n++] = code
                }
            }
        }
    }
    val list = ArrayList<WordStat>(map.size)
    for ((w, a) in map) list.add(WordStat(w, a.count, a.rows, a.ids.copyOf(a.n)))
    list.sortWith(compareByDescending<WordStat> { it.count }.thenBy { it.word })
    AnalysisResult(rows.size, total, list)
}

private fun highlighted(text: String, word: String, fg: Color, bg: Color): AnnotatedString =
    buildAnnotatedString {
        var last = 0
        forEachWord(text) { s, e ->
            if (text.substring(s, e).lowercase() == word) {
                append(text.substring(last, s))
                withStyle(SpanStyle(color = fg, background = bg, fontWeight = FontWeight.Bold)) {
                    append(text.substring(s, e))
                }
                last = e
            }
        }
        append(text.substring(last))
    }

/** Sorted lists are cached, so this is only a linear filter (and a no-op when nothing is filtered). */
private fun filterAndSort(result: AnalysisResult, query: String, sort: WordSort, f: FreqFilter): List<WordStat> {
    val base = result.sorted(sort)
    val q = query.trim().lowercase()
    if (q.isEmpty() && f == FreqFilter.ALL) return base
    return base.filter { it.count >= f.min && it.count <= f.max && (q.isEmpty() || it.word.contains(q)) }
}

private fun fmt(n: Int): String = String.format(Locale.US, "%,d", n)

// ---------------------------------------------------------------- screen
@Composable
fun AnalysisScreen(
    cache: AnalysisCache,
    dataVersion: Int,
    snapshot: () -> List<DataRow>,
    colA: String,
    colB: String,
    onGoToRow: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var result by remember { mutableStateOf(if (cache.version == dataVersion) cache.result else null) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current

    LaunchedEffect(Unit) { focus.clearFocus() }
    LaunchedEffect(dataVersion) {
        val cached = cache.result
        if (cached != null && cache.version == dataVersion) {
            result = cached // reuse: nothing changed since last analysis
            return@LaunchedEffect
        }
        result = null; error = null
        try {
            val snap = snapshot() // cheap reference copy, only when data changed
            val res = analyze(snap)
            cache.rows = snap; cache.result = res; cache.version = dataVersion
            result = res
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = e.message ?: "Analysis failed"
        }
    }

    val r = result
    val err = error
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            err != null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Could not analyze: $err") }
            r == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            else -> AnalysisContent(r, cache.rows, colA, colB, onGoToRow)
        }
    }
}

@Composable
private fun AnalysisContent(
    result: AnalysisResult,
    rows: List<DataRow>,
    colA: String,
    colB: String,
    onGoToRow: (Int) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(WordSort.FREQ_DESC) }
    var filter by remember { mutableStateOf(FreqFilter.ALL) }
    var visible by remember { mutableStateOf(result.sorted(WordSort.FREQ_DESC)) }
    // Selected word lives in a holder so the grid is NOT recomposed when a sheet opens/closes.
    val selected = remember { mutableStateOf<WordStat?>(null) }
    val onWordClick = remember { { w: WordStat -> selected.value = w } }

    LaunchedEffect(result, query, sort, filter) {
        if (query.isNotEmpty()) delay(150) // debounce typing
        val next = withContext(Dispatchers.Default) { filterAndSort(result, query, sort, filter) }
        if (next !== visible) visible = next
    }

    Column(Modifier.fillMaxSize()) {
        StatsRow(result)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search word…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp)
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DropdownButton("Sort: ${sort.short}", SORTS, { it.label }, { sort = it }, Modifier.weight(1f))
            DropdownButton("Show: ${filter.label}", FILTERS, { it.label }, { filter = it }, Modifier.weight(1f))
        }

        Text(
            "${fmt(visible.size)} of ${fmt(result.words.size)} words",
            Modifier.padding(horizontal = 14.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        WordGrid(visible, onWordClick, Modifier.weight(1f))
    }

    SheetHost(selected, result, rows, colA, colB, onGoToRow)
}

@Composable
private fun StatsRow(result: AnalysisResult) {
    val avg = if (result.totalRows > 0) result.totalWords.toDouble() / result.totalRows else 0.0
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(fmt(result.totalRows), "Rows", Modifier.weight(1f))
        StatCard(fmt(result.words.size), "Unique", Modifier.weight(1f))
        StatCard(fmt(result.totalWords), "Words", Modifier.weight(1f))
        StatCard(String.format(Locale.US, "%.1f", avg), "Avg/row", Modifier.weight(1f))
    }
}

@Composable
private fun WordGrid(words: List<WordStat>, onClick: (WordStat) -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxWidth()) {
        if (words.isEmpty()) {
            Text("No words match.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(words, key = { it.word }, contentType = { 0 }) { w -> WordChip(w, onClick) }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Card(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun <T> DropdownButton(
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 4.dp)
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(optionLabel(o)) }, onClick = { open = false; onSelect(o) })
            }
        }
    }
}

/** Deliberately light: no Surface, no ripple, no vector icon. Skippable (all params stable). */
@Composable
private fun WordChip(stat: WordStat, onClick: (WordStat) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(14.dp)
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(interactionSource = source, indication = null) { onClick(stat) }
            .padding(start = 12.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stat.word, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium)
        Text(
            stat.countText,
            color = primary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier
                .background(primary.copy(alpha = if (stat.count >= 2) 0.18f else 0.08f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Text("›", Modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetHost(
    selected: MutableState<WordStat?>,
    result: AnalysisResult,
    rows: List<DataRow>,
    colA: String,
    colB: String,
    onGoToRow: (Int) -> Unit
) {
    selected.value?.let { stat ->
        ModalBottomSheet(
            onDismissRequest = { selected.value = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            WordDetail(stat, result.totalWords, rows, colA, colB, onGoToRow)
        }
    }
}

@Composable
private fun WordDetail(
    stat: WordStat,
    totalWords: Int,
    rows: List<DataRow>,
    colA: String,
    colB: String,
    onGoToRow: (Int) -> Unit
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val primary = MaterialTheme.colorScheme.primary
    val ids = stat.ids // direct index lookup: no scan of the file
    val pct = if (totalWords > 0) stat.count * 100.0 / totalWords else 0.0

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stat.word, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(primary.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(if (stat.count == 1) "1 time" else "${fmt(stat.count)} times", color = primary,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(
            "Appears in ${fmt(stat.rows)} rows · ${String.format(Locale.US, "%.1f", pct)}% of all words",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Text("Found in ${fmt(ids.size)} fields", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))

        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(count = ids.size, key = { it }) { i ->
                val code = ids[i]
                val rowIndex = code shr 1
                val inA = (code and 1) == 0
                val row = rows.getOrNull(rowIndex)
                if (row != null) {
                    OccRow(
                        number = rowIndex + 1,
                        label = if (inA) colA else colB,
                        isA = inA,
                        text = if (inA) row.a else row.b,
                        word = stat.word,
                        onGo = { onGoToRow(rowIndex) }
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(
            onClick = {
                clipboard.setText(AnnotatedString(stat.word))
                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Copy word") }
    }
}

@Composable
private fun OccRow(number: Int, label: String, isA: Boolean, text: String, word: String, onGo: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val tagColor = if (isA) primary else MaterialTheme.colorScheme.secondary
    val annotated = remember(text, word) { highlighted(text, word, primary, primary.copy(alpha = 0.18f)) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#$number", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            Text(label.uppercase(), color = tagColor, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onGo, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Go to row")
                Icon(Icons.Filled.KeyboardArrowRight, null, Modifier.size(16.dp))
            }
        }
        Text(annotated, Modifier.padding(end = 6.dp), maxLines = 4, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium)
    }
}