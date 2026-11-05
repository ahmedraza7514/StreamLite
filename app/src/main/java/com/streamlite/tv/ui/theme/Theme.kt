package com.streamlite.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val SlBackground = Color(0xFF14141F)
val SlSurface = Color(0xFF232338)
val SlCard = Color(0xFF2E2E48)
val SlAccent = Color(0xFFA855F7)
val SlText = Color(0xFFFFFFFF)
val SlTextDim = Color(0xFFB0AEC9)

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
