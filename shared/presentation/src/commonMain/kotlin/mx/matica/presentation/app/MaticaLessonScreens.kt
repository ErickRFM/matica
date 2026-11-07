package mx.matica.presentation.app

import androidx.compose.foundation.layout.Arrangement
import mx.matica.presentation.components.MaticaConfetti
import mx.matica.presentation.components.MaticaConfirmDialog
import androidx.compose.runtime.LaunchedEffect
import mx.matica.design.feedback.rememberMaticaFeedback
import mx.matica.design.brand.MascotEmotion
import mx.matica.design.brand.MaticaMascot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.math.hints.HintLevel
import mx.matica.math.numeric.Rational
import mx.matica.math.skills.SkillId
import mx.matica.math.verify.VerificationOutcome
import mx.matica.presentation.components.MathKeyboardBar
import mx.matica.presentation.components.appendMathSymbol
import mx.matica.presentation.components.deleteLastMathSymbol
import mx.matica.presentation.components.MaticaButton
import mx.matica.presentation.components.MaticaButtonVariant
import mx.matica.presentation.components.MaticaGlyph
import mx.matica.presentation.components.MaticaIcon
import mx.matica.presentation.components.MaticaIconButton
import mx.matica.presentation.components.MaticaSection
import mx.matica.presentation.components.StepChoiceCard
import mx.matica.presentation.components.StepOption
import mx.matica.presentation.components.WorkedExampleCard
import mx.matica.presentation.exercise.buildStepOptions
import mx.matica.presentation.exercise.ExerciseRenderPlan
import mx.matica.presentation.exercise.ExerciseSlot
import mx.matica.presentation.math.CartesianGraphView
import mx.matica.presentation.math.EquationView
import mx.matica.presentation.math.FractionStrip
import mx.matica.presentation.math.FractionView
import mx.matica.presentation.math.NumberLineView
import mx.matica.presentation.math.RectangleFigureView
import mx.matica.presentation.math.figureCandidates
import mx.matica.presentation.math.nearestTick
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute
import mx.matica.presentation.state.MaticaStateView
import mx.matica.presentation.state.MaticaViewState

@Composable
fun LessonListScreen(skill: SkillId, session: MaticaSession, navigator: MaticaNavigator) {
    val lessons = remember(skill) { session.lessonsFor(skill) }
    MaticaScreen(title = "Lecciones de ${skill.labelEs}", session = session, onBack = { navigator.goBack() }) {
        MaticaStateView(
            state = when {
                lessons.isEmpty() && session.templateIdFor(skill) == null -> MaticaViewState.Empty(
                    "Sin plantillas para esta habilidad",
                    "El generador del MVP sólo construye las tres ecuaciones lineales. " +
                        "Esta habilidad aparece en la ruta porque el grafo la define, pero aún no tiene práctica.",
                )

                lessons.isEmpty() -> MaticaViewState.Error(
                    title = "El generador rechazó las semillas",
                    detail = session.message ?: "Ninguna semilla produjo un ejercicio verificado.",
                )

                else -> MaticaViewState.success(lessons)
            },
            onRetry = { navigator.goBack() },
        ) { list ->
            MaticaCaption("Tres ejercicios por lección, con la misma semilla siempre: si sales y vuelves, no te cambia el problema.")
            list.forEach { lesson ->
                MaticaCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.md)) {
                        MaticaGlyph(
                            icon = if (lesson.number == 1) MaticaIcon.LESSON else MaticaIcon.FORWARD,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        // 4 dp entre nombre y cuenta: sin el hueco la pareja se lee como un solo
                        // bloque (lo midió `lasFilasDeLeccionTienenRitmoYTargetTactil` en 0 dp).
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(MaticaSpacing.xs),
                        ) {
                            Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                            MaticaCaption(lesson.plan.equationText)
                        }
                        MaticaButton(
                            label = "Abrir",
                            onClick = { navigator.go(MaticaRoute.LessonPlayer(skill, lesson.number)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LessonPlayerScreen(skill: SkillId, lessonNumber: Int, session: MaticaSession, navigator: MaticaNavigator) {
    val lesson = remember(skill, lessonNumber) { session.lessonsFor(skill).firstOrNull { it.number == lessonNumber } }
    if (lesson == null) {
        MaticaScreen(title = "Lección no disponible", session = session, onBack = { navigator.goBack() }) {
            MaticaStateView(
                state = MaticaViewState.Empty(
                    "Esta lección no se pudo generar",
                    session.message ?: "El generador devolvió otro ejercicio o ninguno.",
                    retryLabel = "Volver a la lista",
                ),
                onRetry = { navigator.goBack() },
            ) { }
        }
        return
    }

    val plan = lesson.plan
    val maticaFeedback = rememberMaticaFeedback()
    var workspace by rememberSaveable { mutableStateOf(plan.equationText) }
    var draft by rememberSaveable { mutableStateOf("") }
    var feedback by remember { mutableStateOf<Pair<String, String>?>(null) }
    // El campo lleva el motivo corto y la tarjeta el veredicto entero; con el mismo texto en los dos
    // la frase quedaba pegada debajo de sí misma (lo midió `elRechazoDelMotorSePintaUnaSolaVez…`).
    var rejection by remember { mutableStateOf<String?>(null) }
    var confirmarBorrado by remember { mutableStateOf(false) }
    var selectedOptionIndex by rememberSaveable(plan, workspace) { mutableStateOf<Int?>(null) }
    var optionEvaluatedCorrect by remember { mutableStateOf<Boolean?>(null) }
    val stepOptions = remember(plan, workspace) {
        if (workspace == plan.equationText) {
            plan.stepOptions.ifEmpty { buildStepOptions(plan) }
        } else {
            emptyList()
        }
    }

    MaticaScreen(
        title = lesson.title,
        subtitle = plan.labelEs,
        session = session,
        onBack = { navigator.goBack() },
        overlay = {
            if (confirmarBorrado) {
                MaticaConfirmDialog(
                    title = "¿Empezar de nuevo?",
                    detail = "Se borra la línea que llevas escrita en la pizarra. Los intentos que el motor ya revisó se conservan.",
                    confirmLabel = "Sí, borrar la pizarra",
                    dismissLabel = "Conservar",
                    onConfirm = {
                        workspace = plan.equationText
                        draft = ""
                        feedback = null
                        rejection = null
                        selectedOptionIndex = null
                        optionEvaluatedCorrect = null
                        confirmarBorrado = false
                    },
                    onDismissRequest = { confirmarBorrado = false },
                )
            }
        },
    ) {
        WorkedExampleCard(skill = skill, initiallyExpanded = lessonNumber == 1)

        MaticaCard {
            MaticaCaption("Lo que vas resolviendo")
            EquationView(text = workspace, variable = plan.variable)

            if (stepOptions.isNotEmpty()) {
                Text(
                    text = "¿Cuál es el primer paso correcto?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
                ) {
                    stepOptions.forEachIndexed { index, option ->
                        val isSelected = selectedOptionIndex == index
                        val isCorrect = if (isSelected) optionEvaluatedCorrect else null
                        StepChoiceCard(
                            letter = option.letter,
                            text = option.text,
                            isSelected = isSelected,
                            isCorrect = isCorrect,
                            onClick = {
                                selectedOptionIndex = index
                                optionEvaluatedCorrect = null
                                maticaFeedback.stepSelect()
                                if (option.resultingExpression != null) {
                                    draft = option.resultingExpression
                                }
                            },
                        )
                    }
                }
            }

            MaticaCaption("Escribe la siguiente línea. El motor decide si es una consecuencia válida, no un botón de «siguiente».")
            MaticaField(
                value = draft,
                onValueChange = {
                    draft = it
                    rejection = null
                    if (selectedOptionIndex != null && stepOptions.getOrNull(selectedOptionIndex!!)?.resultingExpression != it) {
                        selectedOptionIndex = null
                        optionEvaluatedCorrect = null
                    }
                },
                label = "Siguiente línea",
                placeholder = plan.equationText,
                error = rejection,
            )
            MathKeyboardBar(
                onInsert = { symbol ->
                    draft = appendMathSymbol(draft, symbol)
                    maticaFeedback.keyTap()
                },
                onDelete = {
                    draft = deleteLastMathSymbol(draft)
                    maticaFeedback.keyTap()
                },
                onClear = {
                    draft = ""
                    maticaFeedback.keyTap()
                },
                variable = plan.variable,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm)) {
                MaticaButton(
                    label = if (stepOptions.isNotEmpty() && selectedOptionIndex != null) "Comprobar respuesta" else "Comprobar paso",
                    onClick = {
                        val chosenOption = selectedOptionIndex?.let { stepOptions.getOrNull(it) }
                        val effectiveDraft = when {
                            chosenOption?.resultingExpression != null -> chosenOption.resultingExpression
                            draft.isNotBlank() -> draft.trim()
                            else -> null
                        }

                        if (effectiveDraft == null && chosenOption == null) {
                            feedback = "Falta la línea" to "Elige una opción o escribe la expresión a la que llegaste."
                            rejection = null
                        } else if (effectiveDraft != null) {
                            val verdict = session.submitStep(plan, workspace, effectiveDraft)
                            val (title, detail) = verdictMessage(verdict)
                            feedback = title to detail
                            rejection = verdictReason(verdict)
                            val isValid = verdict is mx.matica.math.analysis.StepVerdict.Valid
                            if (isValid) {
                                maticaFeedback.correct()
                            } else {
                                maticaFeedback.incorrect()
                            }
                            if (chosenOption != null) {
                                optionEvaluatedCorrect = isValid
                            }
                            if (isValid) {
                                workspace = effectiveDraft
                                draft = ""
                                selectedOptionIndex = null
                                optionEvaluatedCorrect = null
                            }
                        } else if (chosenOption != null) {
                            rejection = null
                            if (chosenOption.isCorrect) {
                                optionEvaluatedCorrect = true
                                maticaFeedback.correct()
                                feedback = "¡Paso correcto!" to "Excelente primer paso: ${chosenOption.text}."
                            } else {
                                optionEvaluatedCorrect = false
                                maticaFeedback.incorrect()
                                feedback = "Paso incorrecto" to "Ese no es el primer paso adecuado para esta ecuación. Intenta con otra operación."
                            }
                        }
                    },
                )
                MaticaButton(
                    label = "Pista",
                    onClick = {
                        session.markHintUsed(plan)
                        navigator.go(MaticaRoute.HintSheet(skill, lessonNumber))
                    },
                    variant = MaticaButtonVariant.NEUTRAL,
                )
            }
        }

        feedback?.let { (title, detail) -> FeedbackCard(title, detail) }

        MaticaCard {
            Text("Respuesta final", style = MaterialTheme.typography.titleMedium)
            AnswerRow(plan = plan, session = session) { outcome, proposal ->
                feedback = answerMessage(outcome, proposal)
                if (outcome is VerificationOutcome.Satisfies) {
                    maticaFeedback.correct()
                    navigator.go(MaticaRoute.LessonComplete(skill, lessonNumber))
                } else {
                    maticaFeedback.incorrect()
                }
            }
        }

        if (session.attempts.isNotEmpty()) {
            MaticaSection("Intentos de esta sesión") {
                session.attempts.forEachIndexed { index, attempt ->
                    MaticaCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.md)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Intento ${index + 1}", style = MaterialTheme.typography.titleSmall)
                                MaticaCaption("${attempt.before}  →  ${attempt.after}")
                            }
                            MaticaIconButton(
                                icon = MaticaIcon.CHECK,
                                contentDescription = "Revisar intento ${index + 1}",
                                onClick = { navigator.go(MaticaRoute.AttemptReview(skill, lessonNumber, index + 1)) },
                            )
                        }
                    }
                }
                MaticaButton(
                    label = "Empezar de nuevo",
                    onClick = { confirmarBorrado = true },
                    variant = MaticaButtonVariant.TEXT,
                    fullWidth = true,
                )
            }
        }
    }
}

@Composable
private fun FeedbackCard(title: String, detail: String) {
    val bad = title.contains("incorrecto") || title.contains("entiende") || title.contains("no resuelve")
    MaticaCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm)) {
            MaticaGlyph(
                icon = if (bad) MaticaIcon.WARNING else MaticaIcon.CHECK,
                tint = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                MaticaCaption(detail)
            }
        }
    }
}

@Composable
private fun AnswerRow(
    plan: ExerciseRenderPlan,
    session: MaticaSession,
    onResult: (VerificationOutcome, Rational) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    MaticaField(
        value = text,
        onValueChange = { text = it },
        label = "¿Cuánto vale ${plan.variable}?",
        placeholder = "fracción, entero o decimal",
        keyboard = KeyboardType.NumberPassword,
        error = localError,
    )
    MaticaButton(
        label = "Verificar por sustitución",
        onClick = {
            val proposal = parseNumber(session.math, text)
            if (proposal == null) {
                localError = "Escribe un número: fracción (3/4), entero (-2) o decimal (0.25)."
            } else {
                localError = null
                onResult(session.checkAnswer(plan, proposal), proposal)
            }
        },
        fullWidth = true,
    )
}

@Composable
fun HintSheetScreen(skill: SkillId, lessonNumber: Int, session: MaticaSession, navigator: MaticaNavigator) {
    val lesson = remember(skill, lessonNumber) { session.lessonsFor(skill).firstOrNull { it.number == lessonNumber } }
    val plan = lesson?.plan
    var revealed by rememberSaveable { mutableStateOf(1) }

    MaticaScreen(
        title = "Pistas",
        subtitle = plan?.labelEs,
        session = session,
        // Sin insignias: la hoja es una capa sobre el reproductor, que ya las muestra justo encima.
        // Repetirlas dentro del 78 % de alto de la hoja era quitar sitio a las pistas.
        showChips = false,
        onBack = { navigator.goBack() },
    ) {
        if (plan == null) {
            MaticaStateView(
                state = MaticaViewState.Empty("Sin lección abierta", "La capa de pistas se cerró porque la lección ya no existe."),
                onRetry = { navigator.goBack() },
            ) { }
            return@MaticaScreen
        }
        MaticaCard {
            Text(plan.equationText, style = MaterialTheme.typography.titleMedium)
            MaticaCaption(
                "Las pistas van de la idea a la línea concreta. Abrirlas no es trampa: registra evidencia " +
                    "«con pista», que sube de nivel más despacio a propósito.",
            )
        }
        val levels = listOf(HintLevel.CONCEPT, HintLevel.LOCATE, HintLevel.NAME_RULE, HintLevel.SHOW_LINE)
        val hints = session.hintsOf(plan, levels.getOrElse(revealed - 1) { HintLevel.NAME_RULE })
        hints.take(revealed).forEachIndexed { index, hint ->
            MaticaCard {
                Text("Pista ${index + 1} · ${hint.level.labelEs()}", style = MaterialTheme.typography.titleSmall)
                MaticaCaption(hint.text)
            }
        }
        MaticaButton(
            label = if (revealed >= levels.size) "Ya están todas las pistas" else "Mostrar una pista más",
            onClick = { revealed = (revealed + 1).coerceAtMost(levels.size) },
            enabled = revealed < levels.size,
            fullWidth = true,
        )
        MaticaButton(
            label = "Revelar la línea completa",
            onClick = {
                revealed = levels.size
                session.markHintUsed(plan)
            },
            variant = MaticaButtonVariant.OUTLINE,
            fullWidth = true,
        )
    }
}

private fun HintLevel.labelEs(): String = when (this) {
    HintLevel.CONCEPT -> "idea"
    HintLevel.LOCATE -> "dónde mirar"
    HintLevel.NAME_RULE -> "regla"
    HintLevel.SHOW_LINE -> "línea completa"
}

@Composable
fun AttemptReviewScreen(skill: SkillId, lessonNumber: Int, attemptNumber: Int, session: MaticaSession, navigator: MaticaNavigator) {
    val attempt = session.attempts.getOrNull(attemptNumber - 1)
    MaticaScreen(
        title = "Intento $attemptNumber",
        subtitle = skill.labelEs,
        session = session,
        onBack = { navigator.goBack() },
    ) {
        if (attempt == null) {
            MaticaStateView(
                state = MaticaViewState.Empty("Ese intento ya no está", "La sesión se reinició o el número quedó fuera de la lista."),
                onRetry = { navigator.goBack() },
            ) { }
            return@MaticaScreen
        }
        val (title, detail) = verdictMessage(attempt.verdict)
        MaticaCard {
            Text("Antes", style = MaterialTheme.typography.titleSmall)
            EquationView(text = attempt.before, variable = "x")
            Text("Después", style = MaterialTheme.typography.titleSmall)
            EquationView(text = attempt.after, variable = "x")
        }
        FeedbackCard(title, detail)
        MaticaCard {
            Text("Qué se registró", style = MaterialTheme.typography.titleMedium)
            MaticaCaption(
                if (attempt.usedHint) "Este paso se hizo con pista abierta: cuenta como evidencia asistida." else
                    "Paso sin pista: evidencia sin asistencia.",
            )
            MaticaCaption(
                when (val verdict = attempt.verdict) {
                    is mx.matica.math.analysis.StepVerdict.Valid -> "Regla: ${verdict.rule.labelEs}"
                    is mx.matica.math.analysis.StepVerdict.Invalid -> "Error: ${verdict.error.labelEs}"
                    else -> "El motor no clasificó este paso."
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm), modifier = Modifier.fillMaxWidth()) {
            MaticaButton(
                label = "Intento anterior",
                onClick = { navigator.replace(MaticaRoute.AttemptReview(skill, lessonNumber, attemptNumber - 1)) },
                modifier = Modifier.weight(1f),
                enabled = attemptNumber > 1,
                variant = MaticaButtonVariant.NEUTRAL,
            )
            MaticaButton(
                label = "Intento siguiente",
                onClick = { navigator.replace(MaticaRoute.AttemptReview(skill, lessonNumber, attemptNumber + 1)) },
                modifier = Modifier.weight(1f),
                enabled = attemptNumber < session.attempts.size,
                variant = MaticaButtonVariant.NEUTRAL,
            )
        }
    }
}

@Composable
fun LessonCompleteScreen(skill: SkillId, lessonNumber: Int, session: MaticaSession, navigator: MaticaNavigator) {
    val maticaFeedback = rememberMaticaFeedback()
    LaunchedEffect(Unit) {
        maticaFeedback.victory()
    }
    val recordings = session.masteryChanges.takeLast(3)
    MaticaScreen(
        title = "¡EXCELENTE!",
        subtitle = "¡Lección completada!",
        session = session,
        showChips = false,
        onBack = { navigator.goBack() },
    ) {
        MaticaStateView(state = MaticaViewState.success(lessonNumber)) { number ->
            // Hero Mascota de Celebración (Figma 1:148, 11:215) con ráfaga de confeti
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = MaticaSpacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
                ) {
                    MaticaMascot(
                        emotion = MascotEmotion.CELEBRATING,
                        modifier = Modifier.size(130.dp),
                    )
                    Text(
                        text = "¡Lección completada!",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Cada problema resuelto refuerza tu intuición y dominio algebraico.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MaticaConfetti(
                    modifier = Modifier.matchParentSize(),
                )
            }

            // Estadísticas Trío (Figma 1:154: XP, Precisión, Tiempo)
            MaticaCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = MaticaSpacing.xs),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚡", style = MaterialTheme.typography.titleMedium)
                        Text("+20 XP", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        Text("Ganados", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎯", style = MaterialTheme.typography.titleMedium)
                        Text("100%", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.secondary)
                        Text("Precisión", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⏱", style = MaterialTheme.typography.titleMedium)
                        Text("02:15", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.tertiary)
                        Text("Tiempo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            MaticaCard {
                MaticaGlyph(icon = MaticaIcon.CHECK, tint = MaterialTheme.colorScheme.primary)
                Text("Ejercicio $number resuelto con verificación", style = MaterialTheme.typography.titleMedium)
                MaticaCaption("La respuesta quedó registrada como evidencia; el nivel lo calcula el motor de aprendizaje, no esta pantalla.")
            }
            recordings.forEach { recording ->
                MaticaCard {
                    Text(recording.mastery.skill.labelEs, style = MaterialTheme.typography.titleSmall)
                    MaticaCaption("${recording.mastery.from.labelEs} → ${recording.mastery.to.labelEs}")
                    MaticaCaption(recording.mastery.reason)
                    MaticaCaption("Repaso: ${recording.review.reason}")
                }
            }
            val nextNumber = number + 1
            val hasNext = session.lessonsFor(skill).any { it.number == nextNumber }
            MaticaButton(
                label = if (hasNext) "Siguiente lección" else "Continuar mi aventura",
                onClick = {
                    if (hasNext) {
                        navigator.replace(MaticaRoute.LessonPlayer(skill, nextNumber))
                    } else {
                        navigator.resetTo(MaticaRoute.Home)
                    }
                },
                fullWidth = true,
            )
            MaticaButton(
                label = "Volver a la ruta",
                onClick = { navigator.resetTo(MaticaRoute.Home) },
                variant = MaticaButtonVariant.OUTLINE,
                fullWidth = true,
            )
        }
    }
}

@Composable
fun PracticeScreen(templateId: String, seed: Long, session: MaticaSession, navigator: MaticaNavigator) {
    val plan = remember(templateId, seed) { session.startPractice(templateId, seed) }
    MaticaScreen(
        title = "Práctica",
        subtitle = plan?.labelEs,
        session = session,
        onBack = { navigator.goBack() },
    ) {
        MaticaStateView(
            state = when {
                plan == null -> MaticaViewState.Error(
                    title = "No se pudo generar el ejercicio",
                    detail = session.message ?: "La plantilla devolvió un rechazo del motor.",
                )

                else -> MaticaViewState.success(plan)
            },
            onRetry = { navigator.replace(MaticaRoute.Practice(templateId, seed + MaticaSession.SEED_STRIDE)) },
        ) { exercise ->
            ExerciseBody(exercise, seed, session, navigator)
        }
    }
}

@Composable
private fun ExerciseBody(
    plan: ExerciseRenderPlan,
    seed: Long,
    session: MaticaSession,
    navigator: MaticaNavigator,
) {
    var picked by rememberSaveable { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }
    val selected = parseNumber(session.math, picked) ?: plan.solution

    MaticaCard {
        // bodyLarge y no titleMedium: `strategyEs` es una frase de dos líneas («Primero el producto
        // con su signo: menos por negativo da positivo…»), y en peso de título se leía como una orden
        // gritada por encima de la propia ecuación.
        Text(plan.strategyEs, style = MaterialTheme.typography.bodyLarge)
        EquationView(text = plan.equationText, variable = plan.variable)
    }

    // La figura va antes que los procedimientos: es el problema dibujado, y sus medidas salen de la
    // propia ecuación. Si la ecuación no se lee como rectángulo, `figure` es null y no hay sección.
    val figure = plan.figure?.takeIf { plan.hasGeometry }
    if (figure != null) {
        var sideChoice by rememberSaveable { mutableStateOf("") }
        val hypothesis = parseNumber(session.math, sideChoice) ?: plan.solution
        val candidates = figureCandidates(plan.numberLine)
        val reached = figure.totalAt(hypothesis)
        MaticaSection("Figura: ${figure.totalLabelEs} dibujado") {
            MaticaCard {
                RectangleFigureView(
                    figure = figure,
                    candidates = candidates,
                    hypothesis = hypothesis,
                    onHypothesisChange = { value -> sideChoice = value.toString() },
                )
                Text(
                    text = "Se pide ${figure.goalEs} · con lado $hypothesis tienes ${figure.totalLabelEs} $reached",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = MaticaSpacing.sm),
                )
                MaticaCaption(
                    if (reached == figure.total) {
                        "Coincide: ese lado es el que pide la ecuación."
                    } else {
                        "Todavía no: el perímetro y el área se suman con los lados exactos de la ecuación, " +
                            "así que el dibujo no presta ningún número."
                    },
                )
            }
        }
    }

    if (ExerciseSlot.NUMBER_LINE in plan.slots) {
        MaticaSection("Recta: dónde cae la solución") {
            MaticaCard {
                NumberLineView(
                    model = plan.numberLine,
                    selected = selected,
                    onValueChange = { value -> picked = nearestTick(value, plan.numberLine.ticks).toString() },
                )
                MaticaCaption("Arrastra o usa las flechas. El valor se engancha a la marca más cercana: la recta no inventa puntos.")
                // `NumberLineView` ya expone sus dos flechas con estas etiquetas; repetir aquí los botones
                // duplicaba el control y hacía que un lector de pantalla anunciara «Marca siguiente» dos veces.
                Text("Marcado: $selected", modifier = Modifier.fillMaxWidth().padding(horizontal = MaticaSpacing.sm))
            }
        }
    }

    if (plan.hasGraph) {
        val window = graphWindow(plan)
        val trackerLine = plan.lines.first()
        MaticaSection("Plano: las dos rectas se cortan en la solución") {
            CartesianGraphView(
                series = seriesOf(trackerLine, window.xFrom, window.xTo, window.yFrom, window.yTo),
                axisLabelX = plan.variable,
                axisLabelY = "y",
                solutionMarkers = listOf(plan.solution),
                xFrom = window.xFrom,
                xTo = window.xTo,
                yFrom = window.yFrom,
                yTo = window.yTo,
                trackerY = { value -> trackerLine.slope * value + trackerLine.intercept },
                // El punto del plano no tiene una conjetura propia: es la misma `selected` de la recta,
                // así que las flechas —únicos controles alcanzables con teclado— también mueven el punto.
                // Con estado separado el plano sólo se podía cambiar con el dedo.
                trackerX = selected,
                trackerCandidates = plan.numberLine.orderedTicks,
                onTrackerXChange = { value -> picked = value.toString() },
            )
            MaticaCaption(
                "Sólo se dibuja el lado izquierdo. La otra recta es ${trackerLine.slope}·${plan.variable} + " +
                    "${plan.lines.last().intercept}: si no son rectas, el plano se omite en lugar de fingirse. " +
                    "Arrastra el plano para mover el punto: se queda en la recta y en una marca exacta, y es la " +
                    "misma marca que mueve la recta numérica.",
            )
        }
    } else {
        // El plan decide no dibujar el plano, pero el silencio dejaba la sección cortada sin motivo: la
        // pantalla lo dice en lugar de hacer desaparecer el apartado.
        MaticaCaption(
            "El plano se omite: sin dos rectas reales no hay gráfica honesta que dibujar, y aquí la " +
                "incógnita ya queda despejada en la propia ecuación.",
        )
    }

    if (ExerciseSlot.QUOTIENT_SPLIT in plan.slots) {
        MaticaSection("Reparto del cociente") {
            MaticaCard {
                plan.quotientParts.forEach { part ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.md)) {
                        FractionView(value = part, asMixed = false, modifier = Modifier.weight(1f))
                        FractionStrip(value = part, modifier = Modifier.weight(2f))
                    }
                }
                MaticaCaption("Cada término del numerador entre el denominador. Sumarlas de nuevo regresa el numerador original.")
            }
        }
    }

    MaticaSection("Verificación") {
        plan.verification.forEach { line ->
            MaticaCard {
                EquationView(text = line, variable = plan.variable)
            }
        }
        MaticaCaption(
            if (plan.verified) "La sustitución quedó verificada por el motor." else "El motor no verificó esta sustitución: revísala antes de darla buena.",
        )
        MaticaCard {
            Text("Tu respuesta", style = MaterialTheme.typography.titleMedium)
            AnswerRow(plan = plan, session = session) { outcome, proposal ->
                picked = proposal.toString()
                if (outcome is VerificationOutcome.Satisfies) {
                    navigator.go(MaticaRoute.Rewards)
                }
            }
            MaticaButton(
                label = "Mostrar la solución",
                onClick = {
                    picked = (session.solutionOf(plan) ?: plan.solution).toString()
                    revealed = true
                },
                variant = MaticaButtonVariant.TEXT,
                fullWidth = true,
            )
        }
    }

    // El plan pide la solución como fracción, pero pintarla siempre la dejaba a la vista antes de que el
    // estudiante escribiera nada: sólo se muestra cuando él pide verla.
    if (revealed && ExerciseSlot.ANSWER_FRACTION in plan.slots) {
        MaticaSection("La solución como fracción") {
            MaticaCard {
                FractionView(value = plan.solution, asMixed = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm), modifier = Modifier.fillMaxWidth()) {
        MaticaButton(
            label = "Ejercicio anterior",
            onClick = { navigator.replace(MaticaRoute.Practice(templateIdOf(plan), seed - MaticaSession.SEED_STRIDE)) },
            modifier = Modifier.weight(1f),
            variant = MaticaButtonVariant.NEUTRAL,
        )
        MaticaButton(
            label = "Otro ejercicio",
            onClick = { navigator.replace(MaticaRoute.Practice(templateIdOf(plan), seed + MaticaSession.SEED_STRIDE)) },
            modifier = Modifier.weight(1f),
        )
    }
}

private fun templateIdOf(plan: ExerciseRenderPlan): String = plan.templateId
