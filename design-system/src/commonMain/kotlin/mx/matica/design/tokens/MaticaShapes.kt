package mx.matica.design.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Radios del sistema. El diseño de referencia muestra tarjetas muy redondeadas y botones tipo
 * píldora; el valor exacto no es exportable sin sesión, así que esta escala es derivación propia.
 */
object MaticaShapes {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val extraLarge = 32.dp

    val radii: List<Dp> = listOf(extraSmall, small, medium, large, extraLarge)

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(extraSmall),
        small = RoundedCornerShape(small),
        medium = RoundedCornerShape(medium),
        large = RoundedCornerShape(large),
        extraLarge = RoundedCornerShape(extraLarge),
    )

    /** Píldora completa para los botones principales. */
    val pill = RoundedCornerShape(percent = 50)
}
