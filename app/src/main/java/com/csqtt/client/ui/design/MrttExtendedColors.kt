package com.csqtt.client.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class MrttExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val terminalBackground: Color,
    val terminalText: Color,
    val terminalMuted: Color,
    val terminalError: Color,
    val terminalSuccess: Color,
)

internal val MrttLightExtendedColors = MrttExtendedColors(
    success = Color(0xFF356A4C),
    onSuccess = Color.White,
    successContainer = Color(0xFFB8F2CE),
    onSuccessContainer = Color(0xFF082014),
    warning = Color(0xFF7B5800),
    onWarning = Color.White,
    warningContainer = Color(0xFFFFDEA2),
    onWarningContainer = Color(0xFF271900),
    info = Color(0xFF5C626A),
    onInfo = Color.White,
    infoContainer = Color(0xFFE3E5E8),
    onInfoContainer = Color(0xFF17191C),
    terminalBackground = Color(0xFF15171B),
    terminalText = Color(0xFFDDE1E8),
    terminalMuted = Color(0xFF9DA3AE),
    terminalError = Color(0xFFFFB4AB),
    terminalSuccess = Color(0xFF9BD4AF),
)

internal val MrttDarkExtendedColors = MrttExtendedColors(
    success = Color(0xFF9BD4AF),
    onSuccess = Color(0xFF06391E),
    successContainer = Color(0xFF1D5134),
    onSuccessContainer = Color(0xFFB7F1CD),
    warning = Color(0xFFF2C25B),
    onWarning = Color(0xFF402D00),
    warningContainer = Color(0xFF5C4200),
    onWarningContainer = Color(0xFFFFDEA2),
    info = Color(0xFFB9BEC4),
    onInfo = Color(0xFF17191C),
    infoContainer = Color(0xFF3A3E44),
    onInfoContainer = Color(0xFFE3E5E8),
    terminalBackground = Color(0xFF101216),
    terminalText = Color(0xFFDDE1E8),
    terminalMuted = Color(0xFF9DA3AE),
    terminalError = Color(0xFFFFB4AB),
    terminalSuccess = Color(0xFF9BD4AF),
)

internal val LocalMrttExtendedColors = staticCompositionLocalOf { MrttDarkExtendedColors }

object MrttTheme {
    val extendedColors: MrttExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMrttExtendedColors.current
}
