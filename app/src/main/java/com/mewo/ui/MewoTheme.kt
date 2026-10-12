package com.mewo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MewoTheme(content: @Composable () -> Unit) {
    val colors =
        if (isSystemInDarkTheme()) {
            darkColorScheme(
                primary = Color(0xFFA1D4B4),
                secondary = Color(0xFFBBCBBF),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF386A51),
                secondary = Color(0xFF526457),
                background = Color(0xFFF7FAF6),
                surface = Color(0xFFF7FAF6),
            )
        }
    MaterialTheme(colorScheme = colors, content = content)
}
