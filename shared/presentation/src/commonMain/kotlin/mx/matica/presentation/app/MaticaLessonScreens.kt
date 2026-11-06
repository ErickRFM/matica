package mx.matica.presentation.app

import androidx.compose.foundation.layout.Arrangement
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
import mx.matica.presentation.components.MaticaButton
import mx.matica.presentation.components.MaticaButtonVariant
import mx.matica.presentation.components.MaticaGlyph
import mx.matica.presentation.components.MaticaIcon
import mx.matica.presentation.components.MaticaIconButton
import mx.matica.presentation.components.MaticaSectionHeader
import mx.matica.presentation.exercise.ExerciseRenderPlan
import mx.matica.presentation.exercise.ExerciseSlot
import mx.matica.presentation.math.CartesianGraphView
import mx.matica.presentation.math.EquationView
import mx.matica.presentation.math.FractionStrip
import mx.matica.presentation.math.FractionView
import mx.matica.presentation.math.NumberLineView
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
                        Column(modifier = Modifier.weight(1f)) {
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
                ),
                onRetry = { navigator.goBack() },
            ) { }
        }
        return
    }

    val plan = lesson.plan
    var workspace by rememberSaveable { mutableStateOf(plan.equationText) }
    var draft by rememberSaveable { mutableStateOf("") }
    var feedback by remember { mutableStateOf<Pair<String, String>?>(null) }

    MaticaScreen(
        title = lesson.title,
        subtitle = plan.labelEs,
        session = session,
        onBack = { navigator.goBack() },
    ) {
        MaticaCard {
            MaticaCaption("Lo que vas resolviendo")
            EquationView(text = workspace, variable = plan.variable)
            MaticaCaption("Escribe la siguiente línea. El motor decide si es una consecuencia válida, no un botón de «siguiente».")
            MaticaField(
                value = draft,
                onValueChange = { draft = it },
                label = "Siguiente línea",
                placeholder = plan.equationText,
                error = feedback?.takeIf { it.first.contains("incorrecto") || it.first.contains("entiende") }?.second,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm)) {
                MaticaButton(
                    label = "Comprobar paso",
                    onClick = {
                        if (draft.isBlank()) {
                            feedback = "Falta la línea" to "Escribe la expresión a la que llegaste."
                        } else {
                            val verdict = session.submitStep(plan, workspace, draft)
                            val (title, detail) = verdictMessage(verdict)
                            feedback = title to detail
                            if (verdict is mx.matica.math.analysis.StepVerdict.Valid) workspace = draft
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
                if (outcome is VerificationOutcome.Satisfies) navigator.go(MaticaRoute.LessonComplete(skill, lessonNumber))
            }
        }

        if (session.attempts.isNotEmpty()) {
            MaticaSectionHeader("Intentos de esta sesión")
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
                onClick = {
                    workspace = plan.equationText
                    draft = ""
                    feedback = null
                },
                variant = MaticaButtonVariant.TEXT,
                fullWidth = true,
            )
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
    val recordings = session.masteryChanges.takeLast(3)
    MaticaScreen(
        title = "Lección terminada",
        subtitle = skill.labelEs,
        session = session,
        showChips = false,
        onBack = { navigator.goBack() },
    ) {
        MaticaStateView(state = MaticaViewState.success(lessonNumber)) { number ->
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
            MaticaButton(
                label = "Siguiente lección",
                onClick = { navigator.replace(MaticaRoute.LessonPlayer(skill, nextNumber)) },
                enabled = session.lessonsFor(skill).any { it.number == nextNumber },
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
        Text(plan.strategyEs, style = MaterialTheme.typography.titleMedium)
        EquationView(text = plan.equationText, variable = plan.variable)
    }

    if (ExerciseSlot.NUMBER_LINE in plan.slots) {
        MaticaSectionHeader("Recta: dónde cae la solución")
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

    if (plan.hasGraph) {
        MaticaSectionHeader("Plano: las dos rectas se cortan en la solución")
        val window = graphWindow(plan)
        CartesianGraphView(
            series = seriesOf(plan.lines.first(), window.xFrom, window.xTo, window.yFrom, window.yTo),
            axisLabelX = plan.variable,
            axisLabelY = "y",
            solutionMarkers = listOf(plan.solution),
            xFrom = window.xFrom,
            xTo = window.xTo,
        )
        MaticaCaption(
            "Sólo se dibuja el lado izquierdo. La otra recta es ${plan.lines.last().slope}·${plan.variable} + " +
                "${plan.lines.last().intercept}: si no son rectas, el plano se omite en lugar de fingirse.",
        )
    }

    if (ExerciseSlot.QUOTIENT_SPLIT in plan.slots) {
        MaticaSectionHeader("Reparto del cociente")
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

    MaticaSectionHeader("Verificación")
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

    // El plan pide la solución como fracción, pero pintarla siempre la dejaba a la vista antes de que el
    // estudiante escribiera nada: sólo se muestra cuando él pide verla.
    if (revealed && ExerciseSlot.ANSWER_FRACTION in plan.slots) {
        MaticaSectionHeader("La solución como fracción")
        MaticaCard {
            FractionView(value = plan.solution, asMixed = true, modifier = Modifier.fillMaxWidth())
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm), modifier = Modifier.fillMaxWidth().padding(bottom = MaticaSpacing.md)) {
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
