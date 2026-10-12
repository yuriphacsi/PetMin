package com.yuri.petbite.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF176B52), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCECCB), onPrimaryContainer = Color(0xFF173F32),
    secondary = Color(0xFF8E5D26), secondaryContainer = Color(0xFFF7E4C9),
    background = Color(0xFFF7F8F2), surface = Color(0xFFF7F8F2),
    surfaceVariant = Color(0xFFE9EEE3), onSurface = Color(0xFF203B32),
    onSurfaceVariant = Color(0xFF53645B), outline = Color(0xFF7B8C80)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF95D5B2), primaryContainer = Color(0xFF224D3E),
    background = Color(0xFF111D18), surface = Color(0xFF111D18),
    surfaceVariant = Color(0xFF26372D), onSurface = Color(0xFFE6EFE5)
)
@Composable fun PetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
