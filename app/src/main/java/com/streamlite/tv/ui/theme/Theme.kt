package com.streamlite.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val SlBackground = Color(0xFF0B1622)
val SlSurface = Color(0xFF14273C)
val SlCard = Color(0xFF1B3249)
val SlAccent = Color(0xFF00C2FF)
val SlText = Color(0xFFFFFFFF)
val SlTextDim = Color(0xFF9FB3C8)

@Composable
fun StreamLiteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = SlBackground,
            surface = SlSurface,
            surfaceVariant = SlCard,
            primary = SlAccent,
            onBackground = SlText,
            onSurface = SlText,
        ),
        content = content,
    )
}
