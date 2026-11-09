package com.vasavecodex.code.editor.data

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/** One training example: a = instruction, b = output (column names are configurable). */
data class DataRow(val id: Long, val a: String, val b: String)

object Codec {
    private val counter = AtomicLong(0)
    fun newId(): Long = counter.incrementAndGet()

    fun isDataset(ext: String) = ext == "csv" || ext == "jsonl"

    fun decode(ext: String, text: String, colA: String, colB: String): List<DataRow> =
        if (ext == "jsonl") decodeJsonl(text, colA, colB) else decodeCsv(text, colA)

    fun encode(ext: String, colA: String, colB: String, rows: List<DataRow>): String {
        val data = rows.filter { it.a.isNotEmpty() || it.b.isNotEmpty() }
        return if (ext == "jsonl") {
            if (data.isEmpty()) "" else data.joinToString("\n", postfix = "\n") {
                JSONObject().put(colA, it.a).put(colB, it.b).toString()
            }
        } else {
            val sb = StringBuilder()
            sb.append(esc(colA)).append(',').append(esc(colB)).append('\n')
            data.forEach { sb.append(esc(it.a)).append(',').append(esc(it.b)).append('\n') }
            sb.toString()
        }
    }

    // ---------- CSV ----------
    private fun esc(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"" + s.replace("\"", "\"\"") + "\"" else s

    private val headerNames = setOf("instruction", "input", "question", "prompt", "user")

    private fun decodeCsv(src: String, colA: String): List<DataRow> {
        val table = parseCsv(src).filter { r -> r.any { it.isNotEmpty() } }
        if (table.isEmpty()) return emptyList()
        val first = table[0].firstOrNull()?.trim()?.lowercase() ?: ""
        val hasHeader = table[0].size >= 2 && (first == colA.lowercase() || first in headerNames)
        return (if (hasHeader) table.drop(1) else table).map {
            DataRow(newId(), it.getOrElse(0) { "" }, it.getOrElse(1) { "" })
        }
    }

    fun parseCsv(src: String): List<List<String>> {
        val text = src.removePrefix("\uFEFF")
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { sb.append('"'); i++ }
                    else inQuotes = false
                } else sb.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                ',' -> { row.add(sb.toString()); sb.setLength(0) }
                '\r' -> {}
                '\n' -> { row.add(sb.toString()); sb.setLength(0); rows.add(row); row = ArrayList() }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) { row.add(sb.toString()); rows.add(row) }
        return rows
    }

    // ---------- JSONL ----------
    private fun decodeJsonl(text: String, colA: String, colB: String): List<DataRow> =
        text.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
            try {
                val o = JSONObject(line)
                DataRow(newId(), o.optString(colA, ""), o.optString(colB, ""))
            } catch (e: Exception) { null }
        }.toList()
}
