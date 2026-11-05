package mx.matica.design.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Escala de espaciado: múltiplos de 4 dp, el paso base del layout. */
object MaticaSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** De menor a mayor; las pruebas dependen de que este orden sea estricto. */
    val scale: List<Dp> = listOf(xs, sm, md, lg, xl, xxl)
}
