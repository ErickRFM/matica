package mx.matica.design.tokens

import androidx.compose.ui.unit.dp

/**
 * Medidas de layout que no son espaciado entre elementos: cuánto puede ensanchar el contenido,
 * cuán grande tiene que ser un objetivo tocable y qué marco ocupa un glifo. Están separadas de
 * `MaticaSpacing` porque responden a reglas distintas (legibilidad en tableta, 48 dp de Material y
 * el cuadro común de los iconos) y no a la escala de 4 dp.
 */
object MaticaLayout {

    /**
     * Ancho máximo del cuerpo de una pantalla. En un móvil de 411 dp no se nota; en tableta o
     * ventana ancha es lo que impide que un párrafo de `MaticaCaption` cruce toda la pantalla y el
     * ojo pierda el renglón siguiente.
     */
    val maxContentWidth = 640.dp

    /** Objetivo táctil mínimo de Material: por debajo de esto el dedo falla. */
    val touchTarget = 48.dp

    /**
     * Cuadro común de los glifos de la cabecera. La Bienvenida usaba 26 dp y el mapa de ruta 24 dp
     * más 2 dp de relleno, dos medidas para el mismo hueco: la fila de la cabecera bailaba un
     * píxel según la pantalla.
     */
    val glyphFrame = 24.dp
}
