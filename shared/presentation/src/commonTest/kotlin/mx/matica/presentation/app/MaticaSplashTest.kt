package mx.matica.presentation.app

import mx.matica.design.motion.MotionStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El splash es la primera cosa que ve el estudiante: si las fases se desordenan, la palabra aparece
 * antes que la marca o la mota viaja sobre un tramo que todavía no está dibujado. Estas reglas se
 * prueban sin pantalla porque son aritmética, no píxeles.
 */
class MaticaSplashTest {

    @Test
    fun elArranqueEmpiezaQuietoYTerminaCompleto() {
        listOf(splashPhases(0f), splashPhases(-2f)).forEach { phase ->
            assertEquals(0f, phase.progress)
            assertEquals(0f, phase.markDraw)
            assertEquals(0f, phase.travel)
            assertEquals(0f, phase.wordAlpha)
            assertEquals(0f, phase.taglineAlpha)
        }

        listOf(splashPhases(1f), splashPhases(4f)).forEach { phase ->
            assertEquals(1f, phase.progress)
            assertEquals(1f, phase.markDraw)
            assertEquals(1f, phase.travel)
            assertEquals(1f, phase.wordAlpha)
            assertEquals(1f, phase.taglineAlpha)
        }
    }

    @Test
    fun ningunaFaseRetrocede() {
        var previous = splashPhases(0f)
        (1..40).forEach { step ->
            val current = splashPhases(step / 40f)
            assertTrue(current.markDraw >= previous.markDraw, "El trazo retrocedió en el paso $step")
            assertTrue(current.travel >= previous.travel, "La mota retrocedió en el paso $step")
            assertTrue(current.wordAlpha >= previous.wordAlpha, "La palabra retrocedió en el paso $step")
            assertTrue(current.taglineAlpha >= previous.taglineAlpha, "La frase retrocedió en el paso $step")
            assertTrue(current.progress >= previous.progress, "El avance retrocedió en el paso $step")
            previous = current
        }
    }

    @Test
    fun laPalabraEsperaAQueLaMarcaTengaTrazo() {
        val temprano = splashPhases(0.40f)
        assertEquals(0f, temprano.wordAlpha, "A 40 % la palabra aún no puede estar visible")
        assertTrue(temprano.markDraw > 0.75f, "A 40 % la marca ya debería llevar la mayor parte del trazo")

        val tardio = splashPhases(0.80f)
        assertEquals(1f, tardio.wordAlpha, "A 80 % la palabra ya está completa")
        assertTrue(tardio.taglineAlpha in 0.01f..0.99f, "A 80 % la frase todavía está entrando")
    }

    @Test
    fun laMotaNoViajaAntesDeQueExistaRuta() {
        assertEquals(0f, splashPhases(0.10f).travel)
        assertTrue(splashPhases(0.30f).travel > 0f, "La mota tiene que empezar cuando ya hay trazo")
    }

    @Test
    fun laDuracionRespetaLaEscalaSinConvertirseEnUnParpadeo() {
        assertEquals(SPLASH_BASE_MS, splashDurationMs(MotionStyle(1f)))
        assertEquals(SPLASH_REDUCED_MS, splashDurationMs(MotionStyle(0f)))
        assertEquals(SPLASH_BASE_MS / 2, splashDurationMs(MotionStyle(0.5f)))
        assertEquals(SPLASH_MIN_MS, splashDurationMs(MotionStyle(0.1f)))
        assertEquals(SPLASH_MAX_MS, splashDurationMs(MotionStyle(3f)))
    }
}
