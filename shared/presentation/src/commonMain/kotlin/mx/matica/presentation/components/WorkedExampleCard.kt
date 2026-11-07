package mx.matica.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.matica.design.brand.MascotEmotion
import mx.matica.design.brand.MaticaMascot
import mx.matica.design.tokens.MaticaPalette
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.math.skills.SkillId
import mx.matica.presentation.math.EquationView

/**
 * Un paso modelado en un ejemplo resuelto (Worked Example) con autoexplicación (Think-Aloud).
 */
data class WorkedExampleStep(
    val stepNumber: Int,
    val expression: String,
    val actionLabel: String,
    val thinkAloudQuestion: String,
    val mathematicalRationale: String,
)

/**
 * Ejemplo trabajado modelo para una habilidad según la teoría de carga cognitiva (Sweller).
 */
data class WorkedExample(
    val skill: SkillId,
    val title: String,
    val problemStatement: String,
    val steps: List<WorkedExampleStep>,
    val conclusion: String,
)

object WorkedExamplesCatalog {

    val all: Map<SkillId, WorkedExample> = mapOf(
        SkillId.BALANCE_PRINCIPLE to WorkedExample(
            skill = SkillId.BALANCE_PRINCIPLE,
            title = "Despeje lineal en dos operaciones",
            problemStatement = "3x + 6 = 21",
            steps = listOf(
                WorkedExampleStep(
                    stepNumber = 1,
                    expression = "3x = 21 - 6",
                    actionLabel = "Restar 6 en ambos lados",
                    thinkAloudQuestion = "¿Qué operación deshace la suma de 6?",
                    mathematicalRationale = "La operación inversa de sumar 6 es restar 6 en ambos platillos para mantener el balance.",
                ),
                WorkedExampleStep(
                    stepNumber = 2,
                    expression = "3x = 15",
                    actionLabel = "Calcular la resta 21 - 6",
                    thinkAloudQuestion = "¿Cuánto queda en el lado derecho?",
                    mathematicalRationale = "21 - 6 = 15. Ahora el término con la incógnita está aislado.",
                ),
                WorkedExampleStep(
                    stepNumber = 3,
                    expression = "x = 5",
                    actionLabel = "Dividir ambos lados entre 3",
                    thinkAloudQuestion = "¿Cómo neutralizo el coeficiente 3 que multiplica a x?",
                    mathematicalRationale = "Dividiendo entre 3 en ambos lados: 15 / 3 = 5. La variable queda sola.",
                ),
            ),
            conclusion = "Comprobación: 3(5) + 6 = 15 + 6 = 21. ¡La igualdad se cumple exactamente!",
        ),

        SkillId.DISTRIBUTIVE_PROPERTY to WorkedExample(
            skill = SkillId.DISTRIBUTIVE_PROPERTY,
            title = "Propiedad distributiva con paréntesis",
            problemStatement = "4(x + 2) = 28",
            steps = listOf(
                WorkedExampleStep(
                    stepNumber = 1,
                    expression = "4x + 8 = 28",
                    actionLabel = "Distribuir el factor 4 dentro del paréntesis",
                    thinkAloudQuestion = "¿A quién afecta el factor 4?",
                    mathematicalRationale = "El 4 multiplica a todos los términos de adentro: 4×x = 4x y 4×2 = 8.",
                ),
                WorkedExampleStep(
                    stepNumber = 2,
                    expression = "4x = 20",
                    actionLabel = "Restar 8 en ambos lados",
                    thinkAloudQuestion = "¿Cómo muevo el término constante?",
                    mathematicalRationale = "28 - 8 = 20.",
                ),
                WorkedExampleStep(
                    stepNumber = 3,
                    expression = "x = 5",
                    actionLabel = "Dividir entre 4",
                    thinkAloudQuestion = "¿Cuál es el valor final de x?",
                    mathematicalRationale = "20 / 4 = 5.",
                ),
            ),
            conclusion = "Comprobación: 4(5 + 2) = 4(7) = 28. ¡Resultado impecable!",
        ),

        SkillId.FRACTION_ARITHMETIC to WorkedExample(
            skill = SkillId.FRACTION_ARITHMETIC,
            title = "Suma de fracciones con distinto denominador",
            problemStatement = "2/3 + 1/6 = x",
            steps = listOf(
                WorkedExampleStep(
                    stepNumber = 1,
                    expression = "4/6 + 1/6 = x",
                    actionLabel = "Convertir al común denominador (sextos)",
                    thinkAloudQuestion = "¿Se pueden sumar tercios y sextos directamente?",
                    mathematicalRationale = "No, los pedazos tienen distinto tamaño. Convertimos 2/3 multiplicando por 2/2 = 4/6.",
                ),
                WorkedExampleStep(
                    stepNumber = 2,
                    expression = "5/6 = x",
                    actionLabel = "Sumar los numeradores (4 + 1)",
                    thinkAloudQuestion = "¿Qué pasa con los denominadores?",
                    mathematicalRationale = "4 sextos más 1 sexto son 5 sextos. ¡Los denominadores jamás se suman directamente!",
                ),
            ),
            conclusion = "El resultado simplificado es 5/6.",
        ),

        SkillId.SIGN_HANDLING to WorkedExample(
            skill = SkillId.SIGN_HANDLING,
            title = "Ecuación con signo negativo y división",
            problemStatement = "-2x + 10 = 4",
            steps = listOf(
                WorkedExampleStep(
                    stepNumber = 1,
                    expression = "-2x = 4 - 10",
                    actionLabel = "Restar 10 a ambos lados",
                    thinkAloudQuestion = "¿Cómo despejo el término constante positivo?",
                    mathematicalRationale = "Restando 10 en ambos miembros de la ecuación.",
                ),
                WorkedExampleStep(
                    stepNumber = 2,
                    expression = "-2x = -6",
                    actionLabel = "Aritmética de números con signo (4 - 10 = -6)",
                    thinkAloudQuestion = "¿Por qué el resultado de 4 - 10 es negativo?",
                    mathematicalRationale = "El 10 tiene mayor valor absoluto y es negativo.",
                ),
                WorkedExampleStep(
                    stepNumber = 3,
                    expression = "x = 3",
                    actionLabel = "Dividir entre -2: (-6) / (-2) = 3",
                    thinkAloudQuestion = "¿Cuánto da un número negativo dividido entre otro negativo?",
                    mathematicalRationale = "Negativo entre negativo resulta en positivo: (-6) / (-2) = +3.",
                ),
            ),
            conclusion = "Comprobación: -2(3) + 10 = -6 + 10 = 4. ¡Correcto!",
        ),
    )

    fun forSkill(skill: SkillId): WorkedExample? = all[skill]
}

/**
 * Tarjeta interactiva de Ejemplo Trabajado (Worked Example).
 * Ofrece andamiaje cognitivo previo a la resolución de problemas abiertos,
 * permitiendo estudiar la ruta ideal de razonamiento paso a paso.
 */
@Composable
fun WorkedExampleCard(
    skill: SkillId,
    modifier: Modifier = Modifier.fillMaxWidth(),
    initiallyExpanded: Boolean = false,
) {
    val example = remember(skill) { WorkedExamplesCatalog.forSkill(skill) } ?: return
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(MaticaShapes.medium))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                shape = RoundedCornerShape(MaticaShapes.medium),
            )
            .clickable { expanded = !expanded }
            .padding(MaticaSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MaticaSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
                ) {
                    MaticaGlyph(
                        icon = MaticaIcon.LESSON,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Ejemplo resuelto: ${example.title}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Text(
                    text = if (expanded) "Ocultar ▲" else "Ver guía ▼",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = MaticaSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
                ) {
                    Text(
                        text = "Problema modelo:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EquationView(text = example.problemStatement, variable = "x")

                    example.steps.forEach { step ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(MaticaShapes.small))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(MaticaShapes.small),
                                )
                                .padding(MaticaSpacing.sm),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(MaticaSpacing.xs)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Paso ${step.stepNumber}: ${step.actionLabel}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                EquationView(text = step.expression, variable = "x")

                                // Burbuja de razonamiento Think-Aloud
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(MaticaShapes.small))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                                        .padding(MaticaSpacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.xs),
                                ) {
                                    MaticaMascot(
                                        modifier = Modifier.size(28.dp),
                                        emotion = MascotEmotion.THINKING,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = step.thinkAloudQuestion,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = step.mathematicalRationale,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Conclusión
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(MaticaShapes.small))
                            .background(MaticaPalette.Mint.copy(alpha = 0.4f))
                            .padding(MaticaSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.xs),
                    ) {
                        MaticaGlyph(
                            icon = MaticaIcon.CHECK,
                            tint = MaticaPalette.GreenInk,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = example.conclusion,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaticaPalette.GreenInk,
                        )
                    }
                }
            }
        }
    }
}
