package mx.matica.design.motion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MotionStyleTest {

    @Test
    fun laEscalaUnoConservaLasDuracionesBase() {
        val style = MotionStyle(1f)
        assertEquals(240, style.ms(MaticaMotion.durations.medium))
        assertFalse(style.reduced)
    }

    @Test
    fun laEscalaMitadRecortaLasDuracionesALaMitad() {
        val style = MotionStyle(0.5f)
        assertEquals(120, style.ms(MaticaMotion.durations.medium))
        assertFalse(style.reduced)
    }

    @Test
    fun conEscalaCeroNadaSeMueve() {
        val style = MotionStyle(0f)
        assertTrue(style.reduced)
        assertEquals(0, style.ms(MaticaMotion.durations.xLong))
    }

    @Test
    fun elFundidoDeRespaldoNuncaPasaDe150Ms() {
        val reduced = MotionStyle(0f)
        assertEquals(MotionStyle.FADE_REDUCED_MAX, reduced.fadeMs(MaticaMotion.durations.xLong))
        assertEquals(80, reduced.fadeMs(MaticaMotion.durations.instant))
    }

    @Test
    fun unaEscalaNegativaSeTrataComoMovimientoReducido() {
        val style = MotionStyle(-1f)
        assertTrue(style.reduced)
        assertEquals(0, style.ms(240))
    }

    @Test
    fun laEscalaDeDuracionesEsCrecienteYMultiploDeCuarenta() {
        val scale = MaticaMotion.durations.scale
        assertTrue(scale.zipWithNext().all { (a, b) -> a < b }, "orden estricto: $scale")
        assertTrue(scale.all { it % 40 == 0 }, "todas múltiplo de 40: $scale")
    }

    @Test
    fun losMuellesSeApaganCuandoElMovimientoEstaReducido() {
        val calm = MotionStyle(0f)
        val live = MotionStyle(1f)
        assertEquals(1f, MaticaMotion.contentSpec<Float>(calm).dampingRatio)
        assertEquals(1f, MaticaMotion.popSpec<Float>(calm).dampingRatio)
        assertTrue(MaticaMotion.contentSpec<Float>(live).dampingRatio < 1f)
        assertTrue(MaticaMotion.popSpec<Float>(live).dampingRatio < 1f)
    }

    @Test
    fun elFundidoDeRespaldoUsaLaEscalaDelSistema() {
        val style = MotionStyle(1f)
        assertEquals(160, MaticaMotion.fadeSpec(style).durationMillis)
        assertEquals(150, MaticaMotion.fadeSpec(MotionStyle(0f), baseMs = 160).durationMillis)
        assertEquals(80, MaticaMotion.fadeSpec(MotionStyle(0.5f), baseMs = 160).durationMillis)
    }

    @Test
    fun laRigidezDelContenidoCreceConLaEscalaDelSistema() {
        val slow = MaticaMotion.contentSpec<Float>(MotionStyle(0.5f)).stiffness
        val normal = MaticaMotion.contentSpec<Float>(MotionStyle(1f)).stiffness
        val fast = MaticaMotion.contentSpec<Float>(MotionStyle(1.5f)).stiffness
        assertTrue(slow < normal && normal < fast, "rigidez creciente 0.5x<$slow 1x<$normal 1.5x<$fast")
    }

    @Test
    fun laRigidezDelReboteCreceConLaEscalaDelSistema() {
        val slow = MaticaMotion.popSpec<Float>(MotionStyle(0.5f)).stiffness
        val normal = MaticaMotion.popSpec<Float>(MotionStyle(1f)).stiffness
        val fast = MaticaMotion.popSpec<Float>(MotionStyle(1.5f)).stiffness
        assertTrue(slow < normal && normal < fast, "rigidez creciente 0.5x<$slow 1x<$normal 1.5x<$fast")
    }

    @Test
    fun conMovimientoReducidoLaRigidezNoDesaparecePeroElReboteSi() {
        val calm = MotionStyle(0f)
        assertEquals(1f, MaticaMotion.contentSpec<Float>(calm).dampingRatio)
        assertEquals(1f, MaticaMotion.popSpec<Float>(calm).dampingRatio)
        assertEquals(1f, MaticaMotion.stiffnessFactor(calm.scale))
    }

    @Test
    fun elFactorDeRigidezQuedaDentroDelCoercitivo() {
        assertEquals(MaticaMotion.STIFFNESS_FACTOR_MIN, MaticaMotion.stiffnessFactor(0.05f))
        assertEquals(MaticaMotion.STIFFNESS_FACTOR_MAX, MaticaMotion.stiffnessFactor(20f))
        val extreme = MotionStyle(20f)
        assertTrue(MaticaMotion.contentSpec<Float>(extreme).stiffness > 0f)
        assertTrue(MaticaMotion.popSpec<Float>(MotionStyle(0.05f)).stiffness > 0f)
        assertTrue(
            MaticaMotion.popSpec<Float>(extreme).stiffness <=
                MaticaMotion.popSpec<Float>(MotionStyle(1f)).stiffness * MaticaMotion.STIFFNESS_FACTOR_MAX,
            "ninguna escala extrema deja el muelle fuera del coercitivo",
        )
    }
}
