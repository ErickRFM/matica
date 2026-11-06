package mx.matica.design.motion

import androidx.compose.runtime.Composable

/** En JVM no hay sistema operativo móvil que consulte: las pruebas de host corren con escala neutra. */
@Composable
actual fun platformMotionScale(): Float = 1f
