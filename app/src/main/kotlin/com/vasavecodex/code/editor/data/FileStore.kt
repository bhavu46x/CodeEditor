package com.vasavecodex.code.editor.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything that touches the file system. All files live in ONE folder. */
object FileStore {
    val EXTENSIONS = listOf("csv", "jsonl", "txt", "json", "md", "xml", "py", "js", "sh", "log")
    private val conversationHeaders = setOf("instruction", "input", "question", "prompt", "user")

/** True for CSV files that should open in the multi-column (language) editor. */
fun isTableCsv(file: File): Boolean {
    if (file.extension.lowercase() != "csv") return false
    return try {
        val line = file.bufferedReader(Charsets.UTF_8).use { it.readLine() } ?: return false
        val header = (Codec.parseCsv(line.removePrefix("\uFEFF")).firstOrNull() ?: return false)
            .map { it.trim() }.dropLastWhile { it.isEmpty() }
        header.size >= 3 || (header.size == 2 && header[0].lowercase() !in conversationHeaders)
    } catch (e: Exception) { false }
}

fun createTable(folder: File, baseName: String, prefix: String, columns: List<String>): File? {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val clean = baseName.trim().removeSuffix(".csv")
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .ifEmpty { "${prefix}_$stamp" }
    folder.mkdirs()
    var f = File(folder, "$clean.csv")
    var n = 1
    while (f.exists()) { f = File(folder, "$clean ($n).csv"); n++ }
    return try {
        f.writeText(TableCodec.encode(columns, emptyList()), Charsets.UTF_8); f
    } catch (e: Exception) { null }
}

    fun defaultFolder(): String =
        File(Environment.getExternalStorageDirectory(), "CodeEditor").absolutePath

    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

    /** The working folder. Falls back to app-private external storage if permission is missing. */
    fun root(ctx: Context, path: String): File {
        val dir = if (hasAccess(ctx)) File(path)
        else File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "CodeEditor")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun list(folder: File): List<File> =
        folder.listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") && it.extension != "tmp" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun create(folder: File, baseName: String, ext: String, colA: String, colB: String): File? {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val clean = baseName.trim()
            .removeSuffix(".$ext")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifEmpty { "conversation_$stamp" }
        folder.mkdirs()
        var f = File(folder, "$clean.$ext")
        var n = 1
        while (f.exists()) { f = File(folder, "$clean ($n).$ext"); n++ }
        val initial = if (ext == "csv") Codec.encode("csv", colA, colB, emptyList()) else ""
        return try { f.writeText(initial, Charsets.UTF_8); f } catch (e: Exception) { null }
    }

    /** Returns null when the file can't be read (so we never overwrite it with empty text). */
    fun read(file: File): String? =
        try { file.readText(Charsets.UTF_8) } catch (e: Exception) { null }

    fun write(file: File, text: String): Boolean = try {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(file)) { file.writeText(text, Charsets.UTF_8); tmp.delete() }
        true
    } catch (e: Exception) { false }

    fun uniqueCopy(file: File): File {
        val ext = if (file.extension.isEmpty()) "" else "." + file.extension
        var f = File(file.parentFile, file.nameWithoutExtension + " copy" + ext)
        var n = 2
        while (f.exists()) { f = File(file.parentFile, file.nameWithoutExtension + " copy $n" + ext); n++ }
        return f
    }
}
