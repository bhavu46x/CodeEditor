package com.vasavecodex.code.editor.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** All user settings, persisted in SharedPreferences and observable by Compose. */
class AppSettings(context: Context) {
    private val sp = context.getSharedPreferences("code_editor_settings", Context.MODE_PRIVATE)

    private var themeState by mutableIntStateOf(sp.getInt("theme", 0))
    private var fontState by mutableFloatStateOf(sp.getFloat("font", 16f))
    private var spacingState by mutableFloatStateOf(sp.getFloat("spacing", 1.4f))
    private var monoState by mutableStateOf(sp.getBoolean("mono", false))
    private var autoSaveState by mutableStateOf(sp.getBoolean("autosave", true))
    private var extState by mutableStateOf(sp.getString("ext", "csv") ?: "csv")
    private var colAState by mutableStateOf(sp.getString("colA", "instruction") ?: "instruction")
    private var colBState by mutableStateOf(sp.getString("colB", "output") ?: "output")
    private var folderState by mutableStateOf(
        sp.getString("folder", FileStore.defaultFolder()) ?: FileStore.defaultFolder()
    )

    /** 0 = system, 1 = light, 2 = dark */
    val themeMode: Int get() = themeState
    val fontSize: Float get() = fontState
    val lineSpacing: Float get() = spacingState
    val monospace: Boolean get() = monoState
    val autoSave: Boolean get() = autoSaveState
    val defaultExt: String get() = extState
    val colA: String get() = colAState
    val colB: String get() = colBState
    val folderPath: String get() = folderState

    fun setThemeMode(v: Int) { themeState = v; sp.edit().putInt("theme", v).apply() }
    fun setFontSize(v: Float) { fontState = v; sp.edit().putFloat("font", v).apply() }
    fun setLineSpacing(v: Float) { spacingState = v; sp.edit().putFloat("spacing", v).apply() }
    fun setMonospace(v: Boolean) { monoState = v; sp.edit().putBoolean("mono", v).apply() }
    fun setAutoSave(v: Boolean) { autoSaveState = v; sp.edit().putBoolean("autosave", v).apply() }
    fun setDefaultExt(v: String) { extState = v; sp.edit().putString("ext", v).apply() }
    fun setColA(v: String) { colAState = v; sp.edit().putString("colA", v).apply() }
    fun setColB(v: String) { colBState = v; sp.edit().putString("colB", v).apply() }
    fun setFolderPath(v: String) { folderState = v; sp.edit().putString("folder", v).apply() }
}