package mx.matica.presentation.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import mx.matica.design.a11y.Contrast
import mx.matica.design.theme.MaticaDarkColorScheme
import mx.matica.design.theme.MaticaLightColorScheme
import mx.matica.design.tokens.MaticaLayout

class MathKeyboardBarTest {

    @Test
    fun todasLasTeclasRequeridasEstanPresentes() {
        val keys = MathKeyboardDefaults.defaultKeys()
        assertEquals(10, keys.size, "Debe tener 10 teclas por defecto (x, ², (, ), +, -, *, /, =, ⌫)")

        val labels = keys.map { it.label }
        assertTrue(labels.contains("x"), "Debe incluir la variable x")
        assertTrue(labels.contains("²"), "Debe incluir la potencia cuadrada")
        assertTrue(labels.contains("("), "Debe incluir abrir paréntesis")
        assertTrue(labels.contains(")"), "Debe incluir cerrar paréntesis")
        assertTrue(labels.contains("+"), "Debe incluir suma")
        assertTrue(labels.contains("-"), "Debe incluir resta")
        assertTrue(labels.contains("*"), "Debe incluir multiplicación")
        assertTrue(labels.contains("/"), "Debe incluir división")
        assertTrue(labels.contains("="), "Debe incluir igualdad")
        assertTrue(labels.contains("⌫"), "Debe incluir retroceso")
    }

    @Test
    fun laTeclaDeLimpiezaApareceCuandoSeSolicita() {
        val keysWithoutClear = MathKeyboardDefaults.defaultKeys(hasClear = false)
        assertFalse(keysWithoutClear.any { it.id == "clear" })

        val keysWithClear = MathKeyboardDefaults.defaultKeys(hasClear = true)
        assertEquals(11, keysWithClear.size)
        // «AC» y no «C»: la «C» convive en el reproductor con la alternativa C de primer paso.
        assertTrue(keysWithClear.any { it.id == "clear" && it.label == "AC" })
    }

    @Test
    fun accesibilidadYDescripcionesSemanticasCumplenElFormato() {
        val keys = MathKeyboardDefaults.defaultKeys(hasClear = true)

        keys.forEach { key ->
            when (key.type) {
                MathKeyType.VARIABLE -> {
                    assertTrue(key.isVariable)
                    assertEquals("Insertar ${key.symbol}", key.contentDescription)
                }
                MathKeyType.OPERATION, MathKeyType.GROUPING, MathKeyType.POWER -> {
                    assertFalse(key.isAction)
                    assertNotNull(key.symbol)
                    assertEquals("Insertar ${key.symbol}", key.contentDescription)
                }
                MathKeyType.ACTION -> {
                    assertTrue(key.isAction)
                    if (key.id == "delete") {
                        assertEquals("Borrar", key.contentDescription)
                    } else if (key.id == "clear") {
                        assertEquals("Borrar todo", key.contentDescription)
                    }
                }
            }
        }
    }

    @Test
    fun laVariableYPotenciaSonConfigurables() {
        val keysY = MathKeyboardDefaults.defaultKeys(variable = "y", powerSymbol = "^2")
        val varKey = keysY.first { it.type == MathKeyType.VARIABLE }
        assertEquals("y", varKey.label)
        assertEquals("y", varKey.symbol)
        assertEquals("Insertar y", varKey.contentDescription)

        val powerKey = keysY.first { it.type == MathKeyType.POWER }
        assertEquals("^2", powerKey.label)
        assertEquals("^2", powerKey.symbol)
        assertEquals("Insertar ^2", powerKey.contentDescription)
    }

    @Test
    fun funcionesAuxiliaresDeInsercionYBorrado() {
        var equation = ""
        equation = appendMathSymbol(equation, "2")
        assertEquals("2", equation)
        equation = appendMathSymbol(equation, "x")
        assertEquals("2x", equation)
        equation = appendMathSymbol(equation, "²")
        assertEquals("2x²", equation)
        equation = appendMathSymbol(equation, "+")
        assertEquals("2x²+", equation)
        equation = appendMathSymbol(equation, "5")
        assertEquals("2x²+5", equation)
        equation = appendMathSymbol(equation, "=")
        assertEquals("2x²+5=", equation)
        equation = appendMathSymbol(equation, "13")
        assertEquals("2x²+5=13", equation)

        equation = deleteLastMathSymbol(equation)
        assertEquals("2x²+5=1", equation)
        equation = deleteLastMathSymbol(equation)
        assertEquals("2x²+5=", equation)

        // Vaciado progresivo hasta string vacío
        while (equation.isNotEmpty()) {
            equation = deleteLastMathSymbol(equation)
        }
        assertEquals("", equation)
        // Borrar sobre vacío es seguro
        assertEquals("", deleteLastMathSymbol(equation))
    }

    @Test
    fun parametrosFisicosCumplenRequerimientosDeDiseno() {
        assertEquals(0.94f, MathKeyboardDefaults.PRESS_SCALE, "Escala de resorte debe ser 0.94 al presionar")
        assertTrue(MaticaLayout.touchTarget >= 44.dp, "El objetivo táctil debe ser al menos 44-48dp")
        assertEquals(48.dp, MaticaLayout.touchTarget)
    }

    @Test
    fun contrasteDeColoresCumpleWcagAaEnAmbosTemas() {
        listOf(
            "Claro" to MaticaLightColorScheme,
            "Oscuro" to MaticaDarkColorScheme,
        ).forEach { (tema, scheme) ->
            // Teclas normales: surfaceVariant vs onSurfaceVariant
            assertTrue(
                Contrast.meetsAa(scheme.onSurfaceVariant, scheme.surfaceVariant),
                "Teclas estándar en tema $tema deben cumplir AA (${Contrast.ratio(scheme.onSurfaceVariant, scheme.surfaceVariant)}:1)",
            )
            // Teclas de acción: secondaryContainer vs onSecondaryContainer
            assertTrue(
                Contrast.meetsAa(scheme.onSecondaryContainer, scheme.secondaryContainer),
                "Teclas de acción en tema $tema deben cumplir AA (${Contrast.ratio(scheme.onSecondaryContainer, scheme.secondaryContainer)}:1)",
            )
        }
    }
}
