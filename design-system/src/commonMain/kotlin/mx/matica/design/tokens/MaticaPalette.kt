package mx.matica.design.tokens

import androidx.compose.ui.graphics.Color

/**
 * Colores crudos de Mática.
 *
 * Son la única paleta base del sistema: todo token semántico del tema se compone a partir de ellos
 * y ninguna pantalla debe pintar uno de estos valores por su cuenta.
 */
object MaticaPalette {
    val Ink = Color(0xFF17263F)
    val InkSoft = Color(0xFF4C596F)
    val Blue = Color(0xFF426BEB)
    val BlueSoft = Color(0xFFD5E1FF)
    val BlueMist = Color(0xFFEAF2FF)
    val Violet = Color(0xFF9075EF)
    val VioletSoft = Color(0xFFF0EAFF)
    val VioletInk = Color(0xFF3A2A6B)
    val Lime = Color(0xFFB7EF59)
    val Teal = Color(0xFF14BFA6)
    val TealInk = Color(0xFF04271F)
    val Mint = Color(0xFFE7FAF3)
    val GreenInk = Color(0xFF0A512A)
    val Danger = Color(0xFFB3001B)
    val Paper = Color(0xFFFFFFFF)
    val Mist = Color(0xFFF5F8FF)

    val Night = Color(0xFF0E1626)
    val NightSurface = Color(0xFF17263F)
    val NightRaised = Color(0xFF1B2A44)
    val BlueLight = Color(0xFF8FA8FF)
    val BlueDeep = Color(0xFF2C3E6E)
    val MistText = Color(0xFFEAF0FF)
    val MistTextSoft = Color(0xFF9FB0C9)
    val DangerLight = Color(0xFFFFB4AB)

    val Transparent = Color(0x00000000)
}
