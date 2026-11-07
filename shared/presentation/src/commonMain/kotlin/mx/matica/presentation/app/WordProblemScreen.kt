package mx.matica.presentation.app

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.matica.design.brand.MascotEmotion
import mx.matica.design.brand.MaticaMascot
import mx.matica.design.feedback.rememberMaticaFeedback
import mx.matica.design.tokens.MaticaPalette
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.exercises.WordProblemChoice
import mx.matica.exercises.WordProblemRepository
import mx.matica.presentation.components.MaticaButton
import mx.matica.presentation.components.MaticaButtonVariant
import mx.matica.presentation.components.MaticaGlyph
import mx.matica.presentation.components.MaticaIcon
import mx.matica.presentation.components.MaticaSection
import mx.matica.presentation.math.EquationView
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute
import mx.matica.presentation.state.MaticaStateView
import mx.matica.presentation.state.MaticaViewState

@Composable
fun WordProblemScreen(
    problemId: String,
    session: MaticaSession,
    navigator: MaticaNavigator,
) {
    val problem = remember(problemId) { WordProblemRepository.byId(problemId) }
    if (problem == null) {
        MaticaScreen(title = "Problema Verbal", session = session, onBack = { navigator.goBack() }) {
            MaticaStateView(
                state = MaticaViewState.Empty(
                    "Problema no encontrado",
                    "No se encontró un problema verbal con el identificador '$problemId'.",
                ),
                onRetry = { navigator.goBack() },
            ) {}
        }
        return
    }

    val feedback = rememberMaticaFeedback()
    var currentStage by rememberSaveable(problemId) { mutableStateOf(1) } // 1..4
    var selectedChoiceId by rememberSaveable(problemId, currentStage) { mutableStateOf<String?>(null) }
    var evaluated by rememberSaveable(problemId, currentStage) { mutableStateOf(false) }
    var isCorrect by rememberSaveable(problemId, currentStage) { mutableStateOf(false) }
    var completedAll by rememberSaveable(problemId) { mutableStateOf(false) }

    val mascotEmotion = when {
        completedAll -> MascotEmotion.CELEBRATING
        isCorrect -> MascotEmotion.HAPPY
        evaluated && !isCorrect -> MascotEmotion.THINKING
        else -> MascotEmotion.IDLE
    }

    val stageTitle = when (currentStage) {
        1 -> "Estación 1: Identificar la Incógnita"
        2 -> "Estación 2: Extraer Datos y Relaciones"
        3 -> "Estación 3: Construir la Ecuación"
        4 -> "Estación 4: Interpretar la Solución"
        else -> "Problema resuelto"
    }

    MaticaScreen(
        title = problem.title,
        subtitle = if (completedAll) "¡Problema modelado con éxito!" else stageTitle,
        session = session,
        onBack = { navigator.goBack() },
    ) {
        // Indicador de Progreso en 4 Estaciones
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaticaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (step in 1..4) {
                val stepDone = step < currentStage || completedAll
                val stepCurrent = step == currentStage && !completedAll
                val bg = when {
                    stepDone -> MaterialTheme.colorScheme.primary
                    stepCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(bg),
                )
            }
        }

        // Tarjeta de Contexto con Mascota
        MaticaCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.md),
            ) {
                MaticaMascot(
                    modifier = Modifier.size(60.dp),
                    emotion = mascotEmotion,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Situación real",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = problem.storyContext,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        if (!completedAll) {
            val (prompt, choices) = when (currentStage) {
                1 -> problem.stage1.prompt to problem.stage1.choices
                2 -> problem.stage2.prompt to problem.stage2.choices
                3 -> problem.stage3.prompt to problem.stage3.choices
                4 -> problem.stage4.prompt to problem.stage4.choices
                else -> "" to emptyList()
            }

            if (currentStage == 3) {
                MaticaCard {
                    Text(
                        text = "Ecuación planteada:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EquationView(text = problem.stage3.equationText, variable = "x")
                }
            }

            MaticaSection(stageTitle) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )

                choices.forEach { choice ->
                    val isSelected = selectedChoiceId == choice.id
                    val showEvaluation = evaluated

                    val border = when {
                        showEvaluation && choice.isCorrect -> MaterialTheme.colorScheme.primary
                        showEvaluation && isSelected && !choice.isCorrect -> MaterialTheme.colorScheme.error
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    val bg = when {
                        showEvaluation && choice.isCorrect -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        showEvaluation && isSelected && !choice.isCorrect -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                        isSelected -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surface
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(MaticaShapes.medium))
                            .background(bg)
                            .border(
                                width = if (isSelected || (showEvaluation && choice.isCorrect)) 2.dp else 1.dp,
                                color = border,
                                shape = RoundedCornerShape(MaticaShapes.medium),
                            )
                            .clickable(enabled = !isCorrect) {
                                selectedChoiceId = choice.id
                                evaluated = false
                            }
                            .padding(MaticaSpacing.md),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MaticaSpacing.xs)) {
                            Text(
                                text = choice.text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (showEvaluation && (isSelected || choice.isCorrect)) {
                                Text(
                                    text = choice.explanation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (choice.isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                if (!isCorrect) {
                    MaticaButton(
                        label = "Comprobar estación",
                        onClick = {
                            val chosen = choices.firstOrNull { it.id == selectedChoiceId } ?: return@MaticaButton
                            evaluated = true
                            if (chosen.isCorrect) {
                                isCorrect = true
                                feedback.correct()
                            } else {
                                isCorrect = false
                                feedback.incorrect()
                            }
                        },
                        fullWidth = true,
                        enabled = selectedChoiceId != null,
                    )
                } else {
                    MaticaButton(
                        label = if (currentStage < 4) "Continuar a la siguiente estación" else "Finalizar modelado",
                        onClick = {
                            if (currentStage < 4) {
                                currentStage += 1
                                selectedChoiceId = null
                                evaluated = false
                                isCorrect = false
                            } else {
                                completedAll = true
                                feedback.correct()
                                session.awardMetacognitiveEvidence(problem.skill)
                            }
                        },
                        fullWidth = true,
                    )
                }
            }
        } else {
            // Pantalla final de síntesis didáctica
            MaticaCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
                ) {
                    MaticaGlyph(
                        icon = MaticaIcon.CHECK,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "¡Problema descompuesto y resuelto!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                MaticaCaption(problem.wrapUpStory)
            }

            val problems = WordProblemRepository.all
            val currentIndex = problems.indexOfFirst { it.id == problem.id }
            val nextProblem = problems.getOrNull(currentIndex + 1)

            if (nextProblem != null) {
                MaticaButton(
                    label = "Siguiente problema verbal",
                    onClick = {
                        navigator.go(MaticaRoute.WordProblem(nextProblem.id))
                    },
                    fullWidth = true,
                )
            } else {
                MaticaButton(
                    label = "Volver a la ruta de aprendizaje",
                    onClick = { navigator.go(MaticaRoute.RouteMap) },
                    fullWidth = true,
                )
            }
        }
    }
}
