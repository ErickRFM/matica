package mx.matica.design.a11y

import androidx.compose.material3.ColorScheme
import mx.matica.design.theme.MaticaColorPair
import mx.matica.design.theme.MaticaSemanticColors

/**
 * Todos los pares texto-sobre-fondo que el tema expone, listos para medirse.
 *
 * Se recubren sólo los roles que llevan texto: `scrim` y `outline` no son texto y se evalúan
 * aparte en la auditoría del Bloque 7.
 */
fun maticaContrastPairs(scheme: ColorScheme, semantic: MaticaSemanticColors): List<MaticaColorPair> = listOf(
    MaticaColorPair(scheme.onPrimary, scheme.primary, "botón principal"),
    MaticaColorPair(scheme.onPrimaryContainer, scheme.primaryContainer, "chip seleccionado"),
    MaticaColorPair(scheme.onSecondary, scheme.secondary, "acción secundaria"),
    MaticaColorPair(scheme.onSecondaryContainer, scheme.secondaryContainer, "etiqueta de habilidad"),
    MaticaColorPair(scheme.onTertiary, scheme.tertiary, "acción de refuerzo"),
    MaticaColorPair(scheme.onTertiaryContainer, scheme.tertiaryContainer, "insignia de rama"),
    MaticaColorPair(scheme.onError, scheme.error, "mensaje de error sólido"),
    MaticaColorPair(scheme.onErrorContainer, scheme.errorContainer, "aviso de validación"),
    MaticaColorPair(scheme.onBackground, scheme.background, "texto de pantalla"),
    MaticaColorPair(scheme.onSurface, scheme.surface, "texto de tarjeta"),
    MaticaColorPair(scheme.onSurfaceVariant, scheme.surfaceVariant, "texto secundario"),
    MaticaColorPair(scheme.inverseOnSurface, scheme.inverseSurface, "tooltip"),
    // El texto de acento se mide CONTRA EL FONDO DONDE SE PINTA, no contra su propio contenedor:
    // así apareció el fallo de `primary` como texto (4,36:1 sobre la pantalla y 4,11:1 sobre la
    // tarjeta), invisible mientras sólo se medían pares onX/sobreX.
    MaticaColorPair(semantic.accentText, scheme.background, "valor seguido sobre la pantalla"),
    MaticaColorPair(semantic.accentText, scheme.surface, "valor seguido sobre la superficie"),
    MaticaColorPair(semantic.accentText, scheme.surfaceVariant, "valor seguido sobre la tarjeta"),
) + semantic.textPairs()
