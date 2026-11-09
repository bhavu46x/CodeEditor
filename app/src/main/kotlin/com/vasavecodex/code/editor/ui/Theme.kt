package com.vasavecodex.code.editor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.vasavecodex.code.editor.data.AppSettings

private val Light = lightColorScheme(
    primary = Color(0xFF1A56DB),
    secondary = Color(0xFF0E7490),
    background = Color(0xFFF2F4F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8ECF2)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFF67D4E8),
    background = Color(0xFF111418),
    surface = Color(0xFF1A1E24),
    surfaceVariant = Color(0xFF262C34)
)

@Composable
fun AppTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}

/** Text style used for everything the user writes (driven by Settings). */
@Composable
fun AppSettings.editorStyle(): TextStyle = TextStyle(
    fontSize = fontSize.sp,
    lineHeight = (fontSize * lineSpacing).sp,
    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
    color = MaterialTheme.colorScheme.onSurface
)
