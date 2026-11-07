package mx.matica.design

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import mx.matica.design.a11y.Contrast
import mx.matica.design.a11y.maticaContrastPairs
import mx.matica.design.theme.MaticaDarkColorScheme
import mx.matica.design.theme.MaticaLightColorScheme
import mx.matica.design.theme.MaticaSemanticColors
import mx.matica.design.tokens.MaticaLayout
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.design.tokens.MaticaTypography

class ThemeTokensTest {
    @Test
    fun elTemaClaroCumpleAaEnTodosSusParesDeTexto() {
        val pairs = maticaContrastPairs(MaticaLightColorScheme, MaticaSemanticColors.Light)
        val failed = pairs.filter { !Contrast.meetsAa(it.foreground, it.background) }
        assertTrue(
            failed.isEmpty(),
            "Fallas AA: " + failed.joinToString { "${it.usage} = ${Contrast.ratio(it.foreground, it.background)}" },
        )
    }

    @Test
    fun elTemaOscuroCumpleAaEnTodosSusParesDeTexto() {
        val pairs = maticaContrastPairs(MaticaDarkColorScheme, MaticaSemanticColors.Dark)
        val failed = pairs.filter { !Contrast.meetsAa(it.foreground, it.background) }
        assertTrue(
            failed.isEmpty(),
            "Fallas AA: " + failed.joinToString { "${it.usage} = ${Contrast.ratio(it.foreground, it.background)}" },
        )
    }

    @Test
    fun losParesMedidosCubrenLasDosCapasDelTema() {
        assertEquals(20, maticaContrastPairs(MaticaLightColorScheme, MaticaSemanticColors.Light).size)
        assertEquals(15, maticaContrastPairs(MaticaLightColorScheme, MaticaSemanticColors.Light).count {
            it.usage in setOf(
                "botón principal", "chip seleccionado", "acción secundaria", "etiqueta de habilidad",
                "acción de refuerzo", "insignia de rama", "mensaje de error sólido", "aviso de validación",
                "texto de pantalla", "texto de tarjeta", "texto secundario", "tooltip",
                "valor seguido sobre la pantalla", "valor seguido sobre la superficie",
                "valor seguido sobre la tarjeta",
            )
        })
        assertEquals(5, MaticaSemanticColors.Light.textPairs().size)
    }

    @Test
    fun ningunParMideTextoDelMismoColorQueSuFondo() {
        listOf(
            maticaContrastPairs(MaticaLightColorScheme, MaticaSemanticColors.Light),
            maticaContrastPairs(MaticaDarkColorScheme, MaticaSemanticColors.Dark),
        ).flatten().forEach { pair ->
            assertNotEquals(pair.foreground, pair.background, "Par inútil: ${pair.usage}")
        }
    }

    @Test
    fun claroYOscuroNoCompartenFondo() {
        assertNotEquals(MaticaLightColorScheme.background, MaticaDarkColorScheme.background)
        assertNotEquals(MaticaLightColorScheme.surface, MaticaDarkColorScheme.surface)
        assertNotEquals(MaticaLightColorScheme.onSurface, MaticaDarkColorScheme.onSurface)
    }

    @Test
    fun ningunEstiloMaterialQuedaEnElDefault() {
        val t = MaticaTypography.typography
        val ours = listOf(
            t.displayLarge to MaticaTypography.displayLarge,
            t.displayMedium to MaticaTypography.displayMedium,
            t.displaySmall to MaticaTypography.displaySmall,
            t.headlineLarge to MaticaTypography.headlineLarge,
            t.headlineMedium to MaticaTypography.headlineMedium,
            t.headlineSmall to MaticaTypography.headlineSmall,
            t.titleLarge to MaticaTypography.titleLarge,
            t.titleMedium to MaticaTypography.titleMedium,
            t.titleSmall to MaticaTypography.titleSmall,
            t.bodyLarge to MaticaTypography.bodyLarge,
            t.bodyMedium to MaticaTypography.bodyMedium,
            t.bodySmall to MaticaTypography.bodySmall,
            t.labelLarge to MaticaTypography.labelLarge,
            t.labelMedium to MaticaTypography.labelMedium,
            t.labelSmall to MaticaTypography.labelSmall,
        )
        ours.forEach { (fromTheme, token) ->
            assertEquals(fromTheme, token, "Un rol de Typography quedó en el default de Material.")
        }
        assertEquals(15, MaticaTypography.descending.size)
    }

    @Test
    fun laEscalaTipograficaNoCreceYDejaAire() {
        val sizes = MaticaTypography.descending.map { it.fontSize.value }
        sizes.zip(sizes.drop(1)).forEach { (current, next) ->
            assertTrue(current >= next, "La escala sube de $current a $next sp.")
        }
        MaticaTypography.descending.forEach { style ->
            assertTrue(
                style.lineHeight.value >= style.fontSize.value,
                "Interlineado ${style.lineHeight} menor que el cuerpo ${style.fontSize}.",
            )
        }
        assertEquals(36f, sizes.first())
        assertEquals(11f, sizes.last())
    }

    @Test
    fun elEspaciadoEsBaseCuatroEstrictamenteCreciente() {
        MaticaSpacing.scale.forEach { step ->
            assertEquals(0f, step.value % 4f, "El paso $step no es múltiplo de 4 dp.")
        }
        MaticaSpacing.scale.zip(MaticaSpacing.scale.drop(1)).forEach { (small, big) ->
            assertTrue(small < big, "$small no es menor que $big.")
        }
    }

    @Test
    fun lasMedidasDeLayoutSostienenLaRegraDeCuarentaY_ochoDp() {
        assertEquals(48f, MaticaLayout.touchTarget.value, "Material fija 48 dp como objetivo táctil mínimo.")
        assertEquals(0f, MaticaLayout.glyphFrame.value % 4f, "El marco de glifo sale de la retícula de 4 dp.")
        assertEquals(24f, MaticaLayout.glyphFrame.value)
        assertTrue(
            MaticaLayout.maxContentWidth > 411.dp,
            "El tope de contenido cortaría un teléfono corriente antes de tiempo.",
        )
        assertTrue(
            MaticaLayout.maxContentWidth < 840.dp,
            "Con ${MaticaLayout.maxContentWidth} el texto seguiría cruzando toda una tableta.",
        )
    }

    @Test
    fun losRadiosOrdenanComoLasTarjetasDelDiseno() {        MaticaShapes.radii.zip(MaticaShapes.radii.drop(1)).forEach { (tight, loose) ->
            assertTrue(tight < loose, "El radio $tight no es menor que $loose.")
        }
        assertEquals(16f, MaticaShapes.medium.value)
        assertEquals(24f, MaticaShapes.large.value)
        assertEquals(32f, MaticaShapes.extraLarge.value)
    }
}
