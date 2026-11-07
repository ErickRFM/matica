package mx.matica.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.tokens.MaticaPalette

/**
 * Formas geométricas de las partículas de confeti de celebración.
 * Corresponden a los círculos (burbujas) y formas festivas del marco "fondo celebración" en Figma (iPhone 16 - 5, nodo 11:215).
 */
enum class ParticleShape {
    CIRCLE,
    RECTANGLE,
    OVAL,
    STAR,
}

/**
 * Paleta de colores para el confeti festivo, basada en la paleta oficial de Mática.
 * Incluye Blue, Lime, Violet, Teal y Mint.
 */
val ConfettiColors: List<Color> = listOf(
    MaticaPalette.Blue,
    MaticaPalette.Lime,
    MaticaPalette.Violet,
    MaticaPalette.Teal,
    MaticaPalette.Mint,
)

/**
 * Partícula individual del sistema de confeti.
 *
 * @property x Coordenada X actual en píxeles.
 * @property y Coordenada Y actual en píxeles.
 * @property vx Velocidad horizontal en px/s.
 * @property vy Velocidad vertical en px/s.
 * @property color Color de la partícula extraído de la paleta de Mática.
 * @property size Tamaño de la partícula (diámetro o dimensión principal).
 * @property rotation Ángulo de rotación actual en grados (0..360).
 * @property vRot Velocidad angular de rotación en grados/s.
 * @property shape Forma geométrica de la partícula.
 * @property alpha Opacidad actual de la partícula (0f..1f).
 */
data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float,
    val rotation: Float,
    val vRot: Float,
    val shape: ParticleShape,
    val alpha: Float = 1f,
) {
    /**
     * Avanza el estado de la partícula mediante un paso discreto de simulación física.
     */
    fun step(
        dtSeconds: Float,
        gravity: Float = ConfettiPhysics.DEFAULT_GRAVITY,
        drag: Float = ConfettiPhysics.DEFAULT_DRAG,
        drift: Float = 0f,
    ): ConfettiParticle = ConfettiPhysics.step(this, dtSeconds, gravity, drag, drift)
}

/**
 * Motor físico para las partículas de confeti.
 * Modela gravedad hacia abajo, resistencia del aire / arrastre aerodinámico y rotación angular.
 */
object ConfettiPhysics {
    /** Gravedad por defecto en píxeles/segundo² (hacia abajo en coordenadas de pantalla). */
    const val DEFAULT_GRAVITY = 650f

    /** Coeficiente de resistencia del aire (frena la velocidad horizontal con el tiempo). */
    const val DEFAULT_DRAG = 0.85f

    /**
     * Paso discreto de integración numérica (Euler).
     */
    fun step(
        particle: ConfettiParticle,
        dtSeconds: Float,
        gravity: Float = DEFAULT_GRAVITY,
        drag: Float = DEFAULT_DRAG,
        drift: Float = 0f,
    ): ConfettiParticle {
        val dt = dtSeconds.coerceIn(0f, 0.5f)
        if (dt == 0f) return particle

        val dragFactor = (1f - drag * dt).coerceIn(0f, 1f)
        val newVx = particle.vx * dragFactor
        val newVy = particle.vy + gravity * dt
        val newX = particle.x + (newVx + drift) * dt
        val newY = particle.y + newVy * dt
        val newRot = (particle.rotation + particle.vRot * dt) % 360f
        val safeRot = if (newRot < 0f) newRot + 360f else newRot

        return particle.copy(
            x = newX,
            y = newY,
            vx = newVx,
            vy = newVy,
            rotation = safeRot,
        )
    }

    /**
     * Solución analítica continua para la trayectoria física en cualquier instante de tiempo [elapsedSeconds].
     * Garantiza una animación 60/120 FPS sin pérdida numérica ni oscilaciones si hay saltos de fotograma.
     */
    fun evaluate(
        initial: ConfettiParticle,
        elapsedSeconds: Float,
        durationSeconds: Float = 2.2f,
        gravity: Float = DEFAULT_GRAVITY,
        drag: Float = DEFAULT_DRAG,
    ): ConfettiParticle {
        if (elapsedSeconds <= 0f) return initial
        val t = elapsedSeconds
        val k = drag.coerceAtLeast(0.001f)
        val expFactor = exp(-k * t)
        val intExp = (1f - expFactor) / k

        val curVx = initial.vx * expFactor
        val curVy = (gravity / k) + (initial.vy - gravity / k) * expFactor

        // Ligera oscilación horizontal por el aleteo del confeti en el aire
        val flutter = sin(t * 5f + initial.rotation * (PI.toFloat() / 180f)) * (initial.size * 0.6f)

        val curX = initial.x + initial.vx * intExp + flutter
        val curY = initial.y + (gravity / k) * t + (initial.vy - gravity / k) * intExp
        val rawRot = (initial.rotation + initial.vRot * t) % 360f
        val curRot = if (rawRot < 0f) rawRot + 360f else rawRot

        // Desvanecimiento gradual en el último 30 % del tiempo de animación
        val progress = if (durationSeconds > 0f) (t / durationSeconds).coerceIn(0f, 1f) else 1f
        val alpha = if (progress > 0.7f) {
            ((1f - progress) / 0.3f).coerceIn(0f, 1f)
        } else {
            1f
        }

        return initial.copy(
            x = curX,
            y = curY,
            vx = curVx,
            vy = curVy,
            rotation = curRot,
            alpha = alpha,
        )
    }
}

/**
 * Genera una lista de partículas iniciales simulando una ráfaga festiva desde el centro.
 */
fun generateConfettiParticles(
    count: Int = 40,
    width: Float = 360f,
    height: Float = 240f,
    colors: List<Color> = ConfettiColors,
    random: Random = Random(42),
): List<ConfettiParticle> {
    if (count <= 0) return emptyList()
    val safeWidth = if (width > 0f) width else 360f
    val safeHeight = if (height > 0f) height else 240f
    val originX = safeWidth * 0.5f
    val originY = safeHeight * 0.35f
    val shapes = ParticleShape.entries

    return List(count) { i ->
        val color = colors[i % colors.size]
        val shape = shapes[i % shapes.size]

        // Ráfaga en arco hacia arriba (entre -165° y -15°)
        val angleNorm = i.toFloat() / count.toFloat()
        val baseAngleDeg = -165f + angleNorm * 150f
        val angleJitter = (random.nextFloat() - 0.5f) * 20f
        val angleRad = (baseAngleDeg + angleJitter) * (PI.toFloat() / 180f)

        val speed = 220f + random.nextFloat() * 260f
        val vx = speed * cos(angleRad)
        val vy = speed * sin(angleRad)

        val size = 10f + random.nextFloat() * 12f
        val rotation = random.nextFloat() * 360f
        val vRot = -280f + random.nextFloat() * 560f

        val startX = originX + (random.nextFloat() - 0.5f) * (safeWidth * 0.25f)
        val startY = originY + (random.nextFloat() - 0.5f) * (safeHeight * 0.15f)

        ConfettiParticle(
            x = startX,
            y = startY,
            vx = vx,
            vy = vy,
            color = color,
            size = size,
            rotation = rotation,
            vRot = vRot,
            shape = shape,
        )
    }
}

/**
 * Sistema de partículas de confeti en Compose Multiplatform puro utilizando [Canvas].
 *
 * Cumple con accesibilidad: respeta [LocalMotionStyle.current.reduced] y omite la animación si el usuario
 * tiene el movimiento reducido activado, proveyendo semántica con [contentDescription].
 */
@Composable
fun MaticaConfetti(
    modifier: Modifier = Modifier,
    particleCount: Int = 40,
    durationMs: Int = 2200,
) {
    val motion = LocalMotionStyle.current

    if (motion.reduced) {
        // En modo accesible sin movimiento, no mostramos partículas móviles
        // pero mantenemos la semántica para lectores de pantalla.
        Box(
            modifier = modifier.semantics {
                contentDescription = "Celebración con confeti"
            },
        )
        return
    }

    val progress = remember { Animatable(0f) }
    var canvasSize by remember { mutableStateOf<IntSize?>(null) }

    LaunchedEffect(canvasSize, motion.reduced) {
        if (canvasSize != null && !motion.reduced) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = motion.ms(durationMs),
                    easing = LinearEasing,
                ),
            )
        }
    }

    if (progress.value >= 1f) {
        return
    }

    val initialParticles = remember(canvasSize, particleCount) {
        val size = canvasSize ?: return@remember emptyList()
        if (size.width <= 0 || size.height <= 0) return@remember emptyList()
        generateConfettiParticles(
            count = particleCount,
            width = size.width.toFloat(),
            height = size.height.toFloat(),
        )
    }

    Canvas(
        modifier = modifier
            .semantics {
                contentDescription = "Celebración con confeti"
            }
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0 && canvasSize != size) {
                    canvasSize = size
                }
            },
    ) {
        if (initialParticles.isEmpty()) return@Canvas
        val elapsedSeconds = progress.value * (durationMs / 1000f)
        val durationSeconds = durationMs / 1000f

        initialParticles.forEach { particle ->
            val current = ConfettiPhysics.evaluate(
                initial = particle,
                elapsedSeconds = elapsedSeconds,
                durationSeconds = durationSeconds,
            )
            drawConfettiParticle(current)
        }
    }
}

/**
 * Dibuja una partícula individual en el lienzo de acuerdo a su forma geométrica y rotación.
 */
internal fun DrawScope.drawConfettiParticle(particle: ConfettiParticle) {
    if (particle.alpha <= 0.01f || particle.size <= 0f) return
    val colorWithAlpha = particle.color.copy(alpha = particle.alpha)

    when (particle.shape) {
        ParticleShape.CIRCLE -> {
            drawCircle(
                color = colorWithAlpha,
                radius = particle.size / 2f,
                center = Offset(particle.x, particle.y),
            )
        }
        ParticleShape.RECTANGLE -> {
            withTransform({
                rotate(particle.rotation, Offset(particle.x, particle.y))
            }) {
                val halfW = particle.size / 2f
                val halfH = particle.size * 0.25f
                drawRect(
                    color = colorWithAlpha,
                    topLeft = Offset(particle.x - halfW, particle.y - halfH),
                    size = Size(particle.size, halfH * 2f),
                )
            }
        }
        ParticleShape.OVAL -> {
            withTransform({
                rotate(particle.rotation, Offset(particle.x, particle.y))
            }) {
                val halfW = particle.size / 2f
                val halfH = particle.size * 0.35f
                drawOval(
                    color = colorWithAlpha,
                    topLeft = Offset(particle.x - halfW, particle.y - halfH),
                    size = Size(particle.size, halfH * 2f),
                )
            }
        }
        ParticleShape.STAR -> {
            withTransform({
                rotate(particle.rotation, Offset(particle.x, particle.y))
            }) {
                val r = particle.size / 2f
                val innerR = r * 0.35f
                val path = Path().apply {
                    moveTo(particle.x, particle.y - r)
                    lineTo(particle.x + innerR, particle.y - innerR)
                    lineTo(particle.x + r, particle.y)
                    lineTo(particle.x + innerR, particle.y + innerR)
                    lineTo(particle.x, particle.y + r)
                    lineTo(particle.x - innerR, particle.y + innerR)
                    lineTo(particle.x - r, particle.y)
                    lineTo(particle.x - innerR, particle.y - innerR)
                    close()
                }
                drawPath(path, color = colorWithAlpha)
            }
        }
    }
}
