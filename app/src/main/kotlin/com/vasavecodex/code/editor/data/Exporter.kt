package com.vasavecodex.code.editor.data

import java.io.File

enum class ExportFormat(val label: String, val hint: String, val suffix: String) {
    MESSAGES("Chat messages", "OpenAI, TRL, Unsloth, Axolotl", "messages"),
    ALPACA("Alpaca", "instruction / input / output", "alpaca"),
    SHAREGPT("ShareGPT", "conversations / from / value", "sharegpt")
}

class ExportResult(val file: File?, val exported: Int, val skipped: Int, val error: String? = null)

object Exporter {
    /** Exact JSON string escaping. Unicode (Hindi etc.) is written as-is, not as \uXXXX. */
    private fun q(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    fun line(format: ExportFormat, a: String, b: String): String = when (format) {
        ExportFormat.MESSAGES ->
            "{\"messages\": [{\"role\": \"user\", \"content\": ${q(a)}}, " +
                "{\"role\": \"assistant\", \"content\": ${q(b)}}]}"
        ExportFormat.ALPACA ->
            "{\"instruction\": ${q(a)}, \"input\": \"\", \"output\": ${q(b)}}"
        ExportFormat.SHAREGPT ->
            "{\"conversations\": [{\"from\": \"human\", \"value\": ${q(a)}}, " +
                "{\"from\": \"gpt\", \"value\": ${q(b)}}]}"
    }

    /** Call from a background thread. The source file is only read, never changed. */
    fun export(source: File, outDir: File, format: ExportFormat, colA: String, colB: String): ExportResult {
        val ext = source.extension.lowercase()
        if (!Codec.isDataset(ext)) return ExportResult(null, 0, 0, "Only .csv / .jsonl conversation files can be exported")
        if (ext == "csv" && FileStore.isTableCsv(source)) {
            return ExportResult(null, 0, 0, "Translation / Word Meaning files can't be exported yet")
        }
        val text = FileStore.read(source) ?: return ExportResult(null, 0, 0, "Could not read file")
        return try {
            val rows = Codec.decode(ext, text, colA, colB)
            outDir.mkdirs()
            val target = File(outDir, "${source.nameWithoutExtension}_${format.suffix}.jsonl")
            val tmp = File(outDir, target.name + ".tmp")
            var done = 0
            var skipped = 0
            tmp.bufferedWriter(Charsets.UTF_8).use { w ->
                for (r in rows) {
                    // incomplete rows would teach the model empty answers -> skip, but count them
                    if (r.a.isBlank() || r.b.isBlank()) { skipped++; continue }
                    w.write(line(format, r.a, r.b))
                    w.write("\n")
                    done++
                }
            }
            if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
            ExportResult(target, done, skipped)
        } catch (e: Exception) {
            ExportResult(null, 0, 0, e.message ?: "Export failed")
        }
    }
}