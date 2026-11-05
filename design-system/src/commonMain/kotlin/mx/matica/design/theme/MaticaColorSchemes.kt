package mx.matica.design.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import mx.matica.design.tokens.MaticaPalette

/**
 * Roles Material 3 del tema claro. Cada rol de texto está medido contra su fondo y cumple AA.
 */
val MaticaLightColorScheme: ColorScheme = lightColorScheme(
    primary = MaticaPalette.Blue,
    onPrimary = MaticaPalette.Paper,
    primaryContainer = MaticaPalette.BlueSoft,
    onPrimaryContainer = MaticaPalette.Ink,
    inversePrimary = MaticaPalette.BlueLight,
    secondary = MaticaPalette.VioletInk,
    onSecondary = MaticaPalette.VioletSoft,
    secondaryContainer = MaticaPalette.VioletSoft,
    onSecondaryContainer = MaticaPalette.VioletInk,
    tertiary = MaticaPalette.TealInk,
    onTertiary = MaticaPalette.Mint,
    tertiaryContainer = MaticaPalette.Teal,
    onTertiaryContainer = MaticaPalette.TealInk,
    error = MaticaPalette.Danger,
    onError = MaticaPalette.Paper,
    errorContainer = MaticaPalette.Mist,
    onErrorContainer = MaticaPalette.Danger,
    background = MaticaPalette.Mist,
    onBackground = MaticaPalette.Ink,
    surface = MaticaPalette.Paper,
    onSurface = MaticaPalette.Ink,
    surfaceVariant = MaticaPalette.BlueMist,
    onSurfaceVariant = MaticaPalette.InkSoft,
    outline = MaticaPalette.InkSoft,
    outlineVariant = MaticaPalette.BlueSoft,
    inverseSurface = MaticaPalette.Night,
    inverseOnSurface = MaticaPalette.MistText,
    scrim = MaticaPalette.Night,
)

/** Roles Material 3 del tema oscuro, derivado del claro: el diseño sólo trae pantallas en claro. */
val MaticaDarkColorScheme: ColorScheme = darkColorScheme(
    primary = MaticaPalette.BlueLight,
    onPrimary = MaticaPalette.Night,
    primaryContainer = MaticaPalette.BlueDeep,
    onPrimaryContainer = MaticaPalette.BlueSoft,
    inversePrimary = MaticaPalette.Blue,
    secondary = MaticaPalette.Violet,
    onSecondary = MaticaPalette.Night,
    secondaryContainer = MaticaPalette.VioletInk,
    onSecondaryContainer = MaticaPalette.VioletSoft,
    tertiary = MaticaPalette.Teal,
    onTertiary = MaticaPalette.TealInk,
    tertiaryContainer = MaticaPalette.GreenInk,
    onTertiaryContainer = MaticaPalette.Mint,
    error = MaticaPalette.DangerLight,
    onError = MaticaPalette.Night,
    errorContainer = MaticaPalette.Danger,
    onErrorContainer = MaticaPalette.Paper,
    background = MaticaPalette.Night,
    onBackground = MaticaPalette.MistText,
    surface = MaticaPalette.NightSurface,
    onSurface = MaticaPalette.MistText,
    surfaceVariant = MaticaPalette.NightRaised,
    onSurfaceVariant = MaticaPalette.MistTextSoft,
    outline = MaticaPalette.MistTextSoft,
    outlineVariant = MaticaPalette.BlueDeep,
    inverseSurface = MaticaPalette.MistText,
    inverseOnSurface = MaticaPalette.Night,
    scrim = MaticaPalette.Night,
)
