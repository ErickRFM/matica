package mx.matica.presentation.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import mx.matica.math.MathEngine
import mx.matica.math.numeric.Rational

/**
 * `parseNumber` es la única puerta por la que un número escrito a mano entra en la app: la respuesta
 * del estudiante y el valor que el marcador de la recta recuerda viajan por aquí. Si sólo acepta la
 * forma `Const`, cualquier marca negativa o fracción se convierte en `null` y la pantalla vuelve a
 * su valor por defecto sin avisar.
 */
class ParseNumberTest {

    private val math = MathEngine()

    @Test
    fun losNumerosEscritosPorElEstudianteLleganExactos() {
        assertEquals(Rational.of(7, 1), parseNumber(math, "7"))
        assertEquals(Rational.of(-9, 1), parseNumber(math, "-9"), "El negativo se pierde: la recta no puede marcar -9.")
        assertEquals(Rational.of(3, 4), parseNumber(math, "3/4"), "La fracción se pierde: la respuesta 3/4 no existe.")
        assertEquals(Rational.of(-3, 4), parseNumber(math, "-3/4"))
        assertEquals(Rational.of(5, 2), parseNumber(math, "10/4"), "Un cociente reducible debe leerse, no rechazarse.")
    }

    @Test
    fun loQueNoEsUnNumeroSigueSiendoNull() {
        assertNull(parseNumber(math, "hola"))
        assertNull(parseNumber(math, "3,x"))
        assertNull(parseNumber(math, ""), "La pantalla empieza vacía; vacío no es cero.")
        assertNull(parseNumber(math, "2x+1"), "Una expresión con variable no es un número.")
        assertNull(parseNumber(math, "2=3"), "Una ecuación no es un número.")
        assertNull(parseNumber(math, "1/0"), "Una división imposible no se convierte en un valor inventado.")
    }
}
