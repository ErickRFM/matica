package mx.matica.presentation.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El host decide el tema antes de pintar nada. Estas cuatro combinaciones son las únicas que existen:
 * si una se cayera al `MaticaTheme` por defecto, la app ignoraría el ajuste del estudiante en silencio.
 */
class DarkThemeResolutionTest {

    @Test
    fun elModoSistemaEsElUnicoQueMiraAlDispositivo() {
        assertTrue(darkThemeOf(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(darkThemeOf(ThemeMode.SYSTEM, systemDark = false))
    }

    @Test
    fun claroYOscuroForzadosIgnoranAlDispositivo() {
        assertFalse(darkThemeOf(ThemeMode.LIGHT, systemDark = true))
        assertTrue(darkThemeOf(ThemeMode.DARK, systemDark = false))
    }
}
