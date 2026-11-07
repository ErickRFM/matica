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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.matica.design.brand.MascotEmotion
import mx.matica.design.brand.MaticaMascot
import mx.matica.design.feedback.rememberMaticaFeedback
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.exercises.ErrorDetectionExercise
import mx.matica.exercises.ErrorDetectionRepository
import mx.matica.exercises.ErrorReasonOption
import mx.matica.exercises.WorkedStep
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
fun ErrorSpottingScreen(
    exerciseId: String,
    session: MaticaSession,
    navigator: MaticaNavigator,
) {
    val exercise = remember(exerciseId) { ErrorDetectionRepository.byId(exerciseId) }
    if (exercise == null) {
        MaticaScreen(title = "Detecta el Error", session = session, onBack = { navigator.goBack() }) {
            MaticaStateView(
                state = MaticaViewState.Empty(
                    "Ejercicio no encontrado",
                    "No se encontró un reto de detección con el identificador '$exerciseId'.",
                ),
                onRetry = { navigator.goBack() },
            ) {}
        }
        return
    }

    val feedback = rememberMaticaFeedback()
    // 0 = opción "Todos los pasos son correctos", 1..N = paso específico
    var selectedStepIndex by rememberSaveable(exerciseId) { mutableStateOf<Int?>(null) }
    var stepEvaluated by rememberSaveable(exerciseId) { mutableStateOf(false) }
    var stepChoiceCorrect by rememberSaveable(exerciseId) { mutableStateOf(false) }
    var stepFeedbackText by rememberSaveable(exerciseId) { mutableStateOf<String?>(null) }

    var selectedReasonId by rememberSaveable(exerciseId) { mutableStateOf<String?>(null) }
    var reasonEvaluated by rememberSaveable(exerciseId) { mutableStateOf(false) }
    var reasonCorrect by rememberSaveable(exerciseId) { mutableStateOf(false) }

    val mascotEmotion = when {
        reasonCorrect -> MascotEmotion.CELEBRATING
        stepChoiceCorrect -> MascotEmotion.HAPPY
        stepEvaluated && !stepChoiceCorrect -> MascotEmotion.THINKING
        reasonEvaluated && !reasonCorrect -> MascotEmotion.THINKING
        else -> MascotEmotion.IDLE
    }

    MaticaScreen(
        title = "Detecta el Error",
        subtitle = exercise.title,
        session = session,
        onBack = { navigator.goBack() },
    ) {
        // Tarjeta de contexto con la mascota y el problema inicial
        MaticaCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.md),
            ) {
                MaticaMascot(
                    modifier = Modifier.size(64.dp),
                    emotion = mascotEmotion,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Procedimiento de ${exercise.studentName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = when {
                            reasonCorrect -> "¡Diagnóstico completado con maestría!"
                            stepChoiceCorrect -> "¡Paso detectado! Ahora justifica el motivo."
                            stepEvaluated && !stepChoiceCorrect -> stepFeedbackText ?: "Revisa de nuevo."
                            else -> "¿En qué paso ocurrió el error matemático, o todo está bien?"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Problema inicial
        MaticaCard {
            Text(
                text = "Ecuación planteada:",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EquationView(text = exercise.initialProblem, variable = "x")
        }

        // Lista de pasos resueltos
        MaticaSection("Pasos resueltos por ${exercise.studentName}") {
            exercise.steps.forEach { step ->
                val isSelected = selectedStepIndex == step.stepNumber
                val isTheFaultyStep = exercise.erroneousStepNumber == step.stepNumber
                val showAsTarget = stepChoiceCorrect && isTheFaultyStep

                val borderColor = when {
                    showAsTarget -> MaterialTheme.colorScheme.primary
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
                val bgColor = when {
                    showAsTarget -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    isSelected -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surface
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MaticaShapes.medium))
                        .background(bgColor)
                        .border(
                            width = if (isSelected || showAsTarget) 2.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(MaticaShapes.medium),
                        )
                        .clickable(enabled = !stepChoiceCorrect) {
                            selectedStepIndex = step.stepNumber
                            stepEvaluated = false
                            stepFeedbackText = null
                        }
                        .padding(MaticaSpacing.md),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(MaticaSpacing.xs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Paso ${step.stepNumber}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected || showAsTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (showAsTarget) {
                                MaticaGlyph(
                                    icon = MaticaIcon.WARNING,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        EquationView(text = step.expression, variable = "x")
                        MaticaCaption(step.explanation)
                    }
                }
            }

            // Opción de control: Todo es correcto
            val isAllCorrectSelected = selectedStepIndex == 0
            val isControlExercise = exercise.erroneousStepNumber == null
            val showControlTarget = stepChoiceCorrect && isControlExercise

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MaticaShapes.medium))
                    .background(
                        when {
                            showControlTarget -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            isAllCorrectSelected -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.surface
                        },
                    )
                    .border(
                        width = if (isAllCorrectSelected || showControlTarget) 2.dp else 1.dp,
                        color = if (isAllCorrectSelected || showControlTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(MaticaShapes.medium),
                    )
                    .clickable(enabled = !stepChoiceCorrect) {
                        selectedStepIndex = 0
                        stepEvaluated = false
                        stepFeedbackText = null
                    }
                    .padding(MaticaSpacing.md),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
                ) {
                    MaticaGlyph(
                        icon = MaticaIcon.CHECK,
                        tint = if (isAllCorrectSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Todos los pasos son matemáticamente correctos (no hay error)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isAllCorrectSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }

            // Botón de comprobación del paso
            if (!stepChoiceCorrect) {
                MaticaButton(
                    label = "Comprobar paso seleccionado",
                    onClick = {
                        val choice = selectedStepIndex ?: return@MaticaButton
                        stepEvaluated = true
                        val isTargetCorrect = if (exercise.erroneousStepNumber == null) {
                            choice == 0
                        } else {
                            choice == exercise.erroneousStepNumber
                        }
                        stepChoiceCorrect = isTargetCorrect
                        if (isTargetCorrect) {
                            feedback.correct()
                            stepFeedbackText = "¡Has localizado el paso con precisión!"
                        } else {
                            feedback.incorrect()
                            stepFeedbackText = if (choice == 0) {
                                "En este ejercicio sí hay un paso con un error sutil. Revísalos paso a paso."
                            } else {
                                "El Paso $choice es válido según las reglas del álgebra. Revisa los demás pasos."
                            }
                        }
                    },
                    fullWidth = true,
                    enabled = selectedStepIndex != null,
                )
            }
        }

        // FASE 2: Justificación Metacognitiva (Desplegada solo tras acertar el paso)
        AnimatedVisibility(visible = stepChoiceCorrect) {
            MaticaSection("¿Por qué ocurre este error?") {
                exercise.reasonOptions.forEach { option ->
                    val isOptionSelected = selectedReasonId == option.id
                    val isThisCorrect = option.isCorrect
                    val showEvaluation = reasonEvaluated

                    val border = when {
                        showEvaluation && isThisCorrect -> MaterialTheme.colorScheme.primary
                        showEvaluation && isOptionSelected && !isThisCorrect -> MaterialTheme.colorScheme.error
                        isOptionSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    val bg = when {
                        showEvaluation && isThisCorrect -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        showEvaluation && isOptionSelected && !isThisCorrect -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                        isOptionSelected -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surface
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(MaticaShapes.medium))
                            .background(bg)
                            .border(
                                width = if (isOptionSelected || (showEvaluation && isThisCorrect)) 2.dp else 1.dp,
                                color = border,
                                shape = RoundedCornerShape(MaticaShapes.medium),
                            )
                            .clickable(enabled = !reasonCorrect) {
                                selectedReasonId = option.id
                                reasonEvaluated = false
                            }
                            .padding(MaticaSpacing.md),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MaticaSpacing.xs)) {
                            Text(
                                text = option.text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isOptionSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (showEvaluation && (isOptionSelected || isThisCorrect)) {
                                Text(
                                    text = option.explanation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isThisCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                if (!reasonCorrect) {
                    MaticaButton(
                        label = "Comprobar justificación",
                        onClick = {
                            val chosenId = selectedReasonId ?: return@MaticaButton
                            val chosenOption = exercise.reasonOptions.firstOrNull { it.id == chosenId }
                            reasonEvaluated = true
                            if (chosenOption?.isCorrect == true) {
                                reasonCorrect = true
                                feedback.correct()
                                session.awardMetacognitiveEvidence(exercise.targetSkill)
                            } else {
                                reasonCorrect = false
                                feedback.incorrect()
                            }
                        },
                        fullWidth = true,
                        enabled = selectedReasonId != null,
                    )
                } else {
                    // Síntesis pedagógica final
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
                                text = "Síntesis didáctica",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        MaticaCaption(exercise.wrapUpPedagogy)
                    }

                    val exercises = ErrorDetectionRepository.all
                    val currentIndex = exercises.indexOfFirst { it.id == exercise.id }
                    val nextExercise = exercises.getOrNull(currentIndex + 1)

                    if (nextExercise != null) {
                        MaticaButton(
                            label = "Siguiente reto de detección",
                            onClick = {
                                navigator.go(MaticaRoute.ErrorSpotting(nextExercise.id))
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
    }
}
