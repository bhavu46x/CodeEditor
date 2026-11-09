package com.vasavecodex.code.editor.data

import androidx.compose.runtime.Immutable

val PRESET_LANGUAGES = listOf("Dehvali", "English", "Hindi", "Marathi", "Gujarati")

enum class TableKind(val title: String, val prefix: String) {
    TRANSLATION("Translation", "translation"),
    WORD_MEANING("Word Meaning", "wordmeaning")
}

/** One entry of a multi-column dataset. cells[i] belongs to columns[i]. */
@Immutable
data class TableRow(val id: Long, val cells: List<String>)

fun TableRow.cell(i: Int): String = cells.getOrElse(i) { "" }

fun TableRow.withCell(i: Int, value: String, columnCount: Int): TableRow {
    val l = ArrayList<String>(maxOf(columnCount, cells.size))
    l.addAll(cells)
    while (l.size < columnCount) l.add("")
    l[i] = value
    return copy(cells = l)
}

object TableCodec {
    private fun esc(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** Returns (columns, rows). Never drops cells: extra cells create extra columns. */
    fun decode(text: String): Pair<List<String>, List<TableRow>> {
        val table = Codec.parseCsv(text).filter { r -> r.any { it.isNotEmpty() } }
        if (table.isEmpty()) return Pair(emptyList(), emptyList())
        val head = table[0].map { it.trim() }.dropLastWhile { it.isEmpty() }
        val body = table.drop(1)
        val n = maxOf(head.size, body.maxOfOrNull { it.size } ?: 0)
        val columns = List(n) { i -> head.getOrNull(i).orEmpty().ifEmpty { "Column ${i + 1}" } }
        val rows = body.map { r -> TableRow(Codec.newId(), List(n) { i -> r.getOrElse(i) { "" } }) }
        return Pair(columns, rows)
    }

    fun encode(columns: List<String>, rows: List<TableRow>): String {
        val sb = StringBuilder()
        sb.append(columns.joinToString(",") { esc(it) }).append('\n')
        for (r in rows) {
            if (r.cells.all { it.isEmpty() }) continue
            for (i in columns.indices) {
                if (i > 0) sb.append(',')
                sb.append(esc(r.cell(i)))
            }
            sb.append('\n')
        }
        return sb.toString()
    }
}