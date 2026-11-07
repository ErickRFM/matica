package mx.matica.presentation.components

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import mx.matica.design.motion.MotionStyle
import mx.matica.design.tokens.MaticaPalette

/**
 * Pruebas unitarias para el sistema de confeti y partículas festivas de celebración (Figma 11:215).
 *
 * Verifica:
 * - La física de partículas: aceleración por gravedad hacia abajo, fricción de aire horizontal y rotación.
 * - La composición de colores y formas geométricas de la celebración (Blue, Lime, Violet, Teal, Mint).
 * - La generación de ráfaga y distribución angular.
 * - La solución analítica continua para animaciones fluidas a 60/120 FPS.
 * - La degradación accesible bajo movimiento reducido.
 */
class MaticaConfettiTest {

    @Test
    fun laFisicaDeParticulaAplicaGravedadHaciaAbajo() {
        val particle = ConfettiParticle(
            x = 100f,
            y = 100f,
            vx = 40f,
            vy = -150f, // Ráfaga ascendente inicial
            color = MaticaPalette.Blue,
            size = 12f,
            rotation = 0f,
            vRot = 60f,
            shape = ParticleShape.CIRCLE,
        )

        val next = particle.step(dtSeconds = 0.1f)

        // La gravedad atrae la partícula hacia abajo en la pantalla (eje Y positivo), por lo que vy aumenta
        assertTrue(next.vy > particle.vy, "La velocidad vertical debe aumentar hacia abajo por la gravedad")
        assertTrue(next.y > particle.y + particle.vy * 0.1f, "El desplazamiento debe incorporar la aceleración de gravedad")
    }

    @Test
    fun laFisicaDeParticulaAplicaResistenciaDelAireHorizontal() {
        val particleRight = ConfettiParticle(
            x = 100f,
            y = 100f,
            vx = 180f,
            vy = -80f,
            color = MaticaPalette.Lime,
            size = 10f,
            rotation = 15f,
            vRot = 90f,
            shape = ParticleShape.RECTANGLE,
        )
        val nextRight = particleRight.step(dtSeconds = 0.1f)
        assertTrue(abs(nextRight.vx) < abs(particleRight.vx), "La resistencia del aire debe reducir |vx| hacia la derecha")

        val particleLeft = particleRight.copy(vx = -180f)
        val nextLeft = particleLeft.step(dtSeconds = 0.1f)
        assertTrue(abs(nextLeft.vx) < abs(particleLeft.vx), "La resistencia del aire debe reducir |vx| hacia la izquierda")
    }

    @Test
    fun laRotacionAvanzaYSeNormalizaEnRango360() {
        val particle = ConfettiParticle(
            x = 50f,
            y = 50f,
            vx = 10f,
            vy = 10f,
            color = MaticaPalette.Violet,
            size = 8f,
            rotation = 350f,
            vRot = 150f,
            shape = ParticleShape.STAR,
        )

        val next = particle.step(dtSeconds = 0.1f) // 350 + 15 = 365 -> 5 grados
        assertEquals(5f, next.rotation, 0.001f, "La rotación debe avanzar y normalizarse módulo 360")
        assertTrue(next.rotation in 0f..360f)

        val negativeRotParticle = particle.copy(rotation = 10f, vRot = -200f)
        val nextNegative = negativeRotParticle.step(dtSeconds = 0.1f) // 10 - 20 = -10 -> 350 grados
        assertEquals(350f, nextNegative.rotation, 0.001f, "La rotación negativa debe normalizarse a rango [0, 360)")
    }

    @Test
    fun laPaletaOficialContieneLosCincoColoresMatica() {
        assertEquals(5, ConfettiColors.size, "El confeti debe usar los 5 colores definidos de la marca")
        assertTrue(ConfettiColors.contains(MaticaPalette.Blue), "Debe contener Blue")
        assertTrue(ConfettiColors.contains(MaticaPalette.Lime), "Debe contener Lime")
        assertTrue(ConfettiColors.contains(MaticaPalette.Violet), "Debe contener Violet")
        assertTrue(ConfettiColors.contains(MaticaPalette.Teal), "Debe contener Teal")
        assertTrue(ConfettiColors.contains(MaticaPalette.Mint), "Debe contener Mint")
    }

    @Test
    fun lasFormasDeParticulasIncluyenTodasLasVariantesFigma() {
        val shapes = ParticleShape.entries
        assertEquals(4, shapes.size)
        assertTrue(shapes.contains(ParticleShape.CIRCLE), "Debe incluir CIRCLE para burbujas de celebración")
        assertTrue(shapes.contains(ParticleShape.RECTANGLE), "Debe incluir RECTANGLE para cintas de confeti")
        assertTrue(shapes.contains(ParticleShape.OVAL), "Debe incluir OVAL para burbujas ovales")
        assertTrue(shapes.contains(ParticleShape.STAR), "Debe incluir STAR para destellos festivos")
    }

    @Test
    fun laGeneracionProduce40ParticulasPorDefectoYDistribucionFisica() {
        val particles = generateConfettiParticles(
            count = 40,
            width = 360f,
            height = 240f,
        )

        assertEquals(40, particles.size, "Por defecto debe generar exactamente 40 partículas")

        // Todas las formas representadas
        ParticleShape.entries.forEach { shape ->
            assertTrue(particles.any { it.shape == shape }, "La forma $shape debe estar presente en la ráfaga")
        }

        // Todos los colores representados
        ConfettiColors.forEach { color ->
            assertTrue(particles.any { it.color == color }, "El color $color debe estar presente en la ráfaga")
        }

        // Ráfaga ascendente: las partículas salen disparadas hacia arriba
        assertTrue(particles.all { it.vy < 0f }, "Todas las partículas deben iniciar con velocidad ascendente (vy < 0)")

        // Dispersión horizontal simétrica: partículas hacia izquierda y derecha
        assertTrue(particles.any { it.vx < 0f }, "Debe haber partículas hacia la izquierda")
        assertTrue(particles.any { it.vx > 0f }, "Debe haber partículas hacia la derecha")
    }

    @Test
    fun laEvaluacionContinuaDesvaneceAlFinalDeLaAnimacion() {
        val initial = generateConfettiParticles(count = 1, width = 360f, height = 240f).first()
        val duration = 2.2f

        val atStart = ConfettiPhysics.evaluate(initial, elapsedSeconds = 0f, durationSeconds = duration)
        assertEquals(1f, atStart.alpha, 0.001f, "Al inicio la opacidad es 100%")

        val atMid = ConfettiPhysics.evaluate(initial, elapsedSeconds = 1.0f, durationSeconds = duration)
        assertEquals(1f, atMid.alpha, 0.001f, "Antes del 70% de la duración la opacidad sigue al 100%")

        val nearEnd = ConfettiPhysics.evaluate(initial, elapsedSeconds = 2.0f, durationSeconds = duration)
        assertTrue(nearEnd.alpha in 0.01f..0.99f, "En el último 30% la partícula se va desvaneciendo gradualmente")

        val atEnd = ConfettiPhysics.evaluate(initial, elapsedSeconds = duration, durationSeconds = duration)
        assertEquals(0f, atEnd.alpha, 0.001f, "Al completar los 2.2 segundos la opacidad llega a 0")
    }

    @Test
    fun laEvaluacionContinuaAplicaGravedadYResistencia() {
        val initial = ConfettiParticle(
            x = 180f,
            y = 120f,
            vx = 100f,
            vy = -200f,
            color = MaticaPalette.Teal,
            size = 14f,
            rotation = 0f,
            vRot = 120f,
            shape = ParticleShape.CIRCLE,
        )

        val evaluated = ConfettiPhysics.evaluate(initial, elapsedSeconds = 0.5f, durationSeconds = 2.2f)
        assertTrue(evaluated.vy > initial.vy, "La velocidad vertical debe aumentar por gravedad")
        assertTrue(abs(evaluated.vx) < abs(initial.vx), "La velocidad horizontal debe disminuir por resistencia aerodinámica")
        assertTrue(evaluated.rotation != initial.rotation, "La partícula debe rotar en el tiempo")
    }

    @Test
    fun laAccesibilidadRespetaElMovimientoReducido() {
        val normal = MotionStyle(1f)
        val reduced = MotionStyle(0f)

        assertFalse(normal.reduced, "Escala 1f es movimiento normal")
        assertTrue(reduced.reduced, "Escala 0f es movimiento reducido")

        // La duración base de 2200 ms se reduce a 0 ms cuando el usuario tiene movimiento reducido
        assertEquals(2200, normal.ms(2200))
        assertEquals(0, reduced.ms(2200))
    }
}
