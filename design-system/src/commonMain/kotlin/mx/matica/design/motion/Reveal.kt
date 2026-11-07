package mx.matica.design.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * El revelado del sistema: 0 a `target` con la spec de contenido, y directo a `target` cuando el
 * teléfono pidió movimiento reducido.
 *
 * Vive aquí porque era la misma cuatro veces —gráfica, reparto, celdas y mapa de ruta—, cada una con
 * su `remember { Animatable(...) }` y su `LaunchedEffect` escritos a mano. Cuatro copias significan
 * cuatro sitios que actualizar cuando cambie el revelado, y la diferencia entre ellas ya coló
 * inconsistencias: una se saltaba el `snapTo` y la animación seguía corriendo con la bandera puesta.
 *
 * Devuelve el `Animatable` y no el número: quien dibuja tiene que leer el valor dentro de su fase de
 * dibujo (`Canvas { reveal.value }`). Leído en el cuerpo del composable, cada cuadro del revelado
 * recomponía la vista entera.
 */
@Composable
fun rememberReveal(
    key: Any? = Unit,
    target: Float = 1f,
    motion: MotionStyle = LocalMotionStyle.current,
): Animatable<Float, AnimationVector1D> {
    val reveal = remember { Animatable(if (motion.reduced) target else 0f) }
    LaunchedEffect(key, motion.reduced, target) {
        if (motion.reduced) reveal.snapTo(target) else reveal.animateTo(target, MaticaMotion.contentSpec(motion))
    }
    return reveal
}
