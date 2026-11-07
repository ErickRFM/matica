package mx.matica.design.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import mx.matica.design.tokens.MaticaPalette

/** Un primer plano sobre un fondo, con el uso concreto que los puso juntos. */
data class MaticaColorPair(val foreground: Color, val background: Color, val usage: String)

/**
 * Colores semánticos de Mática que Material 3 no modela: acierto, refuerzo, ruta y ecuación.
 *
 * Cada par `sobre fondo` está elegido para texto normal, así que las pruebas exigen AA (4.5:1)
 * sobre todos los que expone [textPairs].
 *
 * `accentText` existe porque `colorScheme.primary` (#426BEB) como TEXTO no llega a AA sobre los
 * fondos reales del tema claro: medido, 4,36:1 sobre `background` y 4,11:1 sobre `surfaceVariant`.
 * Como rol gráfico el azul sí sirve (una barra de progreso no necesita 4,5:1), pero un número que el
 * estudiante tiene que leer sí. En claro se usa el azul profundo (9,77:1 y 9,22:1) y en oscuro
 * `BlueLight` (7,93:1 y 6,30:1).
 */
data class MaticaSemanticColors(
    val successContainer: Color,
    val onSuccessContainer: Color,
    val highlightContainer: Color,
    val onHighlightContainer: Color,
    val violetContainer: Color,
    val onVioletContainer: Color,
    val tealContainer: Color,
    val onTealContainer: Color,
    val equationSurface: Color,
    val onEquationSurface: Color,
    val accentText: Color,
    val divider: Color,
    val mascot: Color,
) {
    fun textPairs(): List<MaticaColorPair> = listOf(
        MaticaColorPair(onSuccessContainer, successContainer, "mensaje de lección completada"),
        MaticaColorPair(onHighlightContainer, highlightContainer, "texto sobre la insignia de refuerzo"),
        MaticaColorPair(onVioletContainer, violetContainer, "chip de la ruta matemática"),
        MaticaColorPair(onTealContainer, tealContainer, "insignia de rama dominada"),
        MaticaColorPair(onEquationSurface, equationSurface, "ecuación del ejercicio"),
    )

    companion object {
        val Light = MaticaSemanticColors(
            successContainer = MaticaPalette.Mint,
            onSuccessContainer = MaticaPalette.GreenInk,
            highlightContainer = MaticaPalette.Lime,
            onHighlightContainer = MaticaPalette.Ink,
            violetContainer = MaticaPalette.VioletSoft,
            onVioletContainer = MaticaPalette.VioletInk,
            tealContainer = MaticaPalette.Teal,
            onTealContainer = MaticaPalette.TealInk,
            equationSurface = MaticaPalette.Paper,
            onEquationSurface = MaticaPalette.Ink,
            accentText = MaticaPalette.BlueDeep,
            divider = MaticaPalette.BlueSoft,
            mascot = MaticaPalette.Lime,
        )

        val Dark = MaticaSemanticColors(
            successContainer = MaticaPalette.GreenInk,
            onSuccessContainer = MaticaPalette.Mint,
            highlightContainer = MaticaPalette.Lime,
            onHighlightContainer = MaticaPalette.Night,
            violetContainer = MaticaPalette.VioletInk,
            onVioletContainer = MaticaPalette.VioletSoft,
            tealContainer = MaticaPalette.Teal,
            onTealContainer = MaticaPalette.TealInk,
            equationSurface = MaticaPalette.NightSurface,
            onEquationSurface = MaticaPalette.MistText,
            accentText = MaticaPalette.BlueLight,
            divider = MaticaPalette.BlueDeep,
            mascot = MaticaPalette.Lime,
        )
    }
}

val LocalMaticaSemanticColors = staticCompositionLocalOf { MaticaSemanticColors.Light }
