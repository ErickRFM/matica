package mx.matica.presentation.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.learning.diagnosis.DiagnosisSession
import mx.matica.learning.mastery.MasteryLevel
import mx.matica.math.skills.SkillId
import mx.matica.presentation.components.MaticaButton
import mx.matica.presentation.components.MaticaButtonVariant
import mx.matica.presentation.components.MaticaChipPalette
import mx.matica.presentation.components.MaticaCountChip
import mx.matica.presentation.components.MaticaGlyph
import mx.matica.presentation.components.MaticaIcon
import mx.matica.presentation.components.MaticaSection
import mx.matica.presentation.math.RouteMapView
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute
import mx.matica.presentation.state.MaticaStateView
import mx.matica.presentation.state.MaticaViewState

@Composable
fun WelcomeScreen(session: MaticaSession, navigator: MaticaNavigator) {
    MaticaScreen(title = "Mática", session = session, showChips = false, subtitle = "Matemáticas con explicaciones") {
        MaticaCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.md)) {
                MaticaGlyph(icon = MaticaIcon.ROUTE, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(2.dp))
                Column(verticalArrangement = Arrangement.spacedBy(MaticaSpacing.xs)) {
                    Text("Primero se entiende, después se practica.", style = MaterialTheme.typography.titleMedium)
                    MaticaCaption(
                        "Cada paso que escribas lo revisa un motor que explica por qué vale o por qué no. " +
                            "El diagnóstico sólo sirve para ubicarte: no califica, no compara con nadie.",
                    )
                }
            }
        }
        MaticaButton(
            label = "Comenzar el diagnóstico",
            onClick = { navigator.go(MaticaRoute.Diagnosis) },
            leadingGlyph = MaticaIcon.ROUTE,
            fullWidth = true,
        )
        MaticaButton(
            label = "Entrar con una cuenta existente",
            onClick = { navigator.go(MaticaRoute.Login) },
            variant = MaticaButtonVariant.OUTLINE,
            fullWidth = true,
        )
        MaticaButton(
            label = "Explorar la ruta sin diagnóstico",
            onClick = { navigator.go(MaticaRoute.Home) },
            variant = MaticaButtonVariant.TEXT,
            fullWidth = true,
        )
        MaticaCaption(
            "Mática no pide datos de adultos ni muestra anuncios. El perfil vive en este dispositivo " +
                "hasta que exista el servicio de cuentas.",
        )
    }
}

@Composable
fun DiagnosisScreen(session: MaticaSession, navigator: MaticaNavigator) {
    val diagnosis = remember { session.currentDiagnosis() ?: session.startDiagnosis() }
    // `diagnosisAnswered` es estado observable: el diagnóstico vive en el motor de aprendizaje y Compose
    // no puede ver sus respuestas. Sin esta lectura la pantalla se quedaría en la primera pregunta.
    val answered = session.diagnosisAnswered
    val pending = diagnosis.pendingQuestions()

    MaticaScreen(
        title = "Diagnóstico inicial",
        subtitle = "Una pregunta por habilidad",
        session = session,
        onBack = { navigator.goBack() },
    ) {
        MaticaProgress(
            done = answered,
            total = diagnosis.totalItems,
            label = "$answered de ${diagnosis.totalItems} respondidas",
        )
        MaticaStateView(
            state = when {
                pending.isEmpty() -> MaticaViewState.Empty("Ya no hay más preguntas", "El corte por bandas terminó el diagnóstico.")
                else -> MaticaViewState.Success(pending.first())
            },
        ) { question ->
            QuestionCard(question) { index ->
                session.answerDiagnosis(question.item.id, index)
                if (session.currentDiagnosis()?.isFinished == true) navigator.replace(MaticaRoute.DiagnosisResult)
            }
        }
    }
}

/**
 * Las cuatro opciones se dibujan idénticas. Diferenciar la primera por su índice regalaría la clave:
 * el enunciado no dice cuál es la correcta y el botón tampoco debe decirlo.
 */
@Composable
private fun QuestionCard(question: mx.matica.learning.diagnosis.DiagnosisQuestion, onAnswer: (Int) -> Unit) {
    MaticaCard {
        Text(question.item.promptEs, style = MaterialTheme.typography.titleMedium)
        MaticaCaption("Escoge la opción que el motor pueda verificar. No hay tiempo límite.")
        question.options.forEachIndexed { index, option ->
            MaticaButton(
                label = option,
                onClick = { onAnswer(index) },
                // OUTLINE y no NEUTRAL: el relleno del neutro es BlueMist, y BlueMist es también
                // `surfaceVariant`, el color de la tarjeta. Dentro de una tarjeta el botón quedaba sin
                // borde ni fondo: cuatro respuestas que parecían texto suelto (captura del 2026-11-06).
                variant = MaticaButtonVariant.OUTLINE,
                fullWidth = true,
            )
        }
    }
}

@Composable
fun DiagnosisResultScreen(session: MaticaSession, navigator: MaticaNavigator) {
    // `remember`: terminar el diagnóstico registra evidencia; hacerlo en cada recomposición duplicaría
    // la misma evidencia cinco veces y subiría niveles que nadie ganó.
    val outcome = remember { session.finishDiagnosis() }

    MaticaScreen(
        title = "Dónde quedas",
        subtitle = outcome?.summaryEs ?: "Sin diagnóstico registrado",
        session = session,
        onBack = { navigator.goBack() },
    ) {
        MaticaStateView(
            state = outcome?.let { MaticaViewState.success(it) }
                ?: MaticaViewState.Empty("Aún no respondiste nada", "Responde el diagnóstico para ver tu ubicación."),
        ) { result ->
            MaticaCard {
                Text("Qué significa", style = MaterialTheme.typography.titleMedium)
                MaticaCaption(
                    "Un acierto en el diagnóstico no te hace competente: te hace «en desarrollo». La " +
                        "competencia llega con tres aciertos sin pista en días distintos, y eso sólo lo " +
                        "decide el motor de aprendizaje con tu práctica real.",
                )
                MaticaCaption(result.summaryEs)
            }
            result.placements.filter { it.asked }.forEach { placement ->
                LevelRow(
                    skill = placement.skill,
                    level = session.levelOf(placement.skill),
                    detail = placement.reasonEs,
                )
            }
            val skipped = result.placements.filterNot { it.asked }
            if (skipped.isNotEmpty()) {
                MaticaCaption("${skipped.size} habilidades no se preguntaron por el corte de bandas. Se abrirán con la ruta.")
            }
            MaticaButton(
                label = "Ver mi ruta",
                onClick = { navigator.resetTo(MaticaRoute.Home) },
                leadingGlyph = MaticaIcon.HOME,
                fullWidth = true,
            )
            MaticaButton(
                label = "Repetir el diagnóstico",
                onClick = {
                    session.startDiagnosis(seed = session.xp.toLong() + 1L)
                    navigator.replace(MaticaRoute.Diagnosis)
                },
                variant = MaticaButtonVariant.OUTLINE,
                fullWidth = true,
            )
        }
    }
}

@Composable
fun LoginScreen(session: MaticaSession, navigator: MaticaNavigator) {
    var email = remember { "" }
    MaticaScreen(title = "Entrar", session = session, onBack = { navigator.goBack() }, subtitle = "Cuentas y sincronización") {
        // El estado va suelto y la tarjeta al lado: el `content` de `MaticaStateView` sólo se invoca
        // en Success, así que mientras estaba anidado dentro de Offline el campo desactivado y su
        // leyenda no se componían nunca (lo cazó la instrumentación: «Correo de la escuela» no
        // aparecía en pantalla).
        MaticaStateView(
            state = MaticaViewState.Offline(
                title = "El servicio de cuentas todavía no existe",
                detail = "El backend está bloqueado: no hay servidor, así que no se puede crear cuenta, " +
                    "recuperar contraseña ni sincronizar progreso.",
            ),
            onRetry = null,
        ) { }
        MaticaCard {
            MaticaField(
                value = email,
                onValueChange = { email = it },
                label = "Correo de la escuela",
                placeholder = "nombre@escuela.mx",
                enabled = false,
                error = "Desactivado hasta que exista el servicio.",
            )
            MaticaCaption(
                "No se guarda ningún correo en la app y no se pide edad: el perfil local no necesita " +
                    "identificar a menores para funcionar.",
            )
        }
        MaticaButton(
            label = "Seguir con perfil local",
            onClick = { navigator.replace(MaticaRoute.Home) },
            leadingGlyph = MaticaIcon.HOME,
            fullWidth = true,
        )
    }
}

@Composable
fun HomeScreen(session: MaticaSession, navigator: MaticaNavigator) {
    val plan = session.studyPlan()
    MaticaScreen(
        title = "Hoy",
        subtitle = plan.reason,
        session = session,
        actions = {
            MaticaCountChip(value = session.gems, suffix = "gemas", glyph = MaticaIcon.GEM, pair = MaticaChipPalette.xp)
        },
    ) {
        MaticaButton(
            label = "Abrir mi ruta",
            onClick = { navigator.go(MaticaRoute.RouteMap) },
            leadingGlyph = MaticaIcon.ROUTE,
            fullWidth = true,
        )
        MaticaSection("Para hoy") {
            MaticaStateView(
                state = when {
                    plan.reviewsDue.isEmpty() && plan.nextSkills.isEmpty() ->
                        MaticaViewState.Empty("Nada nuevo que abrir", "Dominaste todo lo disponible o falta diagnóstico.")

                    else -> MaticaViewState.success(plan)
                },
            ) { study ->
                study.reviewsDue.forEach { due ->
                    MaticaCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.md)) {
                            MaticaGlyph(icon = MaticaIcon.REFRESH, tint = MaterialTheme.colorScheme.error)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(due.skill.labelEs, style = MaterialTheme.typography.titleMedium)
                                MaticaCaption("Repaso vencido hace ${due.daysOverdue} día(s). ${due.level.labelEs}.")
                            }
                            MaticaButton(label = "Repasar", onClick = { navigator.go(MaticaRoute.SkillDetail(due.skill)) })
                        }
                    }
                }
                study.nextSkills.forEach { recommendation ->
                    MaticaCard {
                        Column {
                            Text(recommendation.skill.labelEs, style = MaterialTheme.typography.titleMedium)
                            MaticaCaption(recommendation.reason)
                            if (recommendation.prerequisitesLeft.isNotEmpty()) {
                                MaticaCaption(
                                    "Faltan: " + recommendation.prerequisitesLeft.joinToString { it.labelEs },
                                )
                            }
                            MaticaButton(
                                label = "Abrir habilidad",
                                onClick = { navigator.go(MaticaRoute.SkillDetail(recommendation.skill)) },
                                // Mismo caso que las opciones del diagnóstico: dentro de la tarjeta el
                                // neutro se pinta con el color de la propia tarjeta y desaparece.
                                variant = MaticaButtonVariant.OUTLINE,
                                fullWidth = true,
                            )
                        }
                    }
                }
            }
        }
        MaticaSection("Accesos") {
            // Cuatro accesos en dos filas de dos. «Mi perfil» no tenía ninguna entrada en la app: la
            // pantalla existía y se probaba montándola por ruta, pero ningún botón llevaba a ella, así
            // que el reinicio del perfil local era inalcanzable. En tres por fila «Mi perfil» ya no
            // cabía (114 dp por celda contra ~118 de la etiqueta), de ahí el 2×2.
            listOf(
                MaticaRoute.ReviewQueue to "Repasos",
                MaticaRoute.Rewards to "Logros",
                MaticaRoute.Settings to "Ajustes",
                MaticaRoute.Profile to "Mi perfil",
            ).chunked(2).forEach { pareja ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    pareja.forEach { (destino, etiqueta) ->
                        MaticaButton(etiqueta, { navigator.go(destino) }, Modifier.weight(1f), MaticaButtonVariant.NEUTRAL)
                    }
                }
            }
        }
    }
}

@Composable
fun RouteMapScreen(session: MaticaSession, navigator: MaticaNavigator) {
    MaticaScreen(title = "Mi ruta", subtitle = "Desbloquea dominando, no avanzando", session = session, onBack = { navigator.goBack() }) {
        MaticaStateView(
            state = run {
                val nodes = session.nodes()
                if (nodes.isEmpty()) {
                    MaticaViewState.Empty("Sin habilidades en el grafo", "El currículo no cargó: revisa el módulo de aprendizaje.")
                } else {
                    MaticaViewState.success(nodes)
                }
            },
        ) { nodes ->
            RouteMapView(nodes = nodes, onNodeClick = { skill -> navigator.go(MaticaRoute.SkillDetail(skill)) })
            val suggestion = nodes.firstOrNull { it.suggested }
            // Sin sugerencia puede significar dos cosas distintas y el estudiante tiene derecho a
            // saber cuál: que ya dominó lo abierto, o que lo que sigue aún no tiene ejercicios.
            val faltaPlantilla = suggestion == null && nodes.any { it.unlocked && it.level != MasteryLevel.FLUENT }
            MaticaCaption(
                when {
                    suggestion != null -> "Sugerencia: ${suggestion.label}. Se abre cuando dominaste sus prerrequisitos."
                    faltaPlantilla -> "Lo que sigue todavía no tiene ejercicios en el catálogo: falta su plantilla."
                    else -> "No hay sugerencia: dominaste todo lo que el catálogo puede abrir."
                },
            )
            MaticaButton(
                label = "Practicar la sugerencia",
                onClick = {
                    val template = suggestion?.skill?.let { session.templateIdFor(it) }
                    if (template != null) navigator.go(MaticaRoute.Practice(template, MaticaSession.BASE_SEED))
                },
                enabled = suggestion != null && session.templateIdFor(suggestion.skill) != null,
                fullWidth = true,
            )
        }
    }
}

@Composable
fun SkillDetailScreen(skill: SkillId, session: MaticaSession, navigator: MaticaNavigator) {
    val path = session.learning.pathTo(session.profile, skill)
    val left = session.graph.prerequisitesOf(skill).filterNot { it in session.profile.mastered() }
    MaticaScreen(title = skill.labelEs, subtitle = session.levelOf(skill).labelEs, session = session, onBack = { navigator.goBack() }) {
        MaticaCard {
            Text("Por qué esta habilidad", style = MaterialTheme.typography.titleMedium)
            MaticaCaption(path.reason)
            MaticaCaption(
                if (left.isEmpty()) "Ya tienes sus prerrequisitos." else "Te faltan: " + left.joinToString { it.labelEs },
            )
        }
        MaticaCard {
            Text("Nivel y evidencia", style = MaterialTheme.typography.titleMedium)
            val progress = session.profile.progressOf(skill)
            MaticaCaption("${progress.level.labelEs} · ${progress.window.size} evidencia(s) en la ventana.")
            MaticaCaption(
                progress.window.joinToString(" · ") { "${it.quality.labelEs} el ${it.occurredOn}" }
                    .ifEmpty { "Todavía sin evidencias." },
            )
            MaticaCaption(
                progress.review.scheduledOn?.let { "Próximo repaso: $it." } ?: "Sin repaso programado todavía.",
            )
        }
        MaticaButton(
            label = "Ver lecciones",
            onClick = { navigator.go(MaticaRoute.LessonList(skill)) },
            enabled = session.templateIdFor(skill) != null,
            fullWidth = true,
        )
        MaticaButton(
            label = "Practicar suelto",
            onClick = {
                val template = session.templateIdFor(skill)
                if (template != null) navigator.go(MaticaRoute.Practice(template, MaticaSession.BASE_SEED))
            },
            variant = MaticaButtonVariant.OUTLINE,
            enabled = session.templateIdFor(skill) != null,
            fullWidth = true,
        )
        if (session.templateIdFor(skill) == null) {
            MaticaCaption(
                "Esta habilidad aún no tiene plantillas de ejercicio: el currículo las describe, pero el " +
                    "generador sólo construye las tres ecuaciones lineales del MVP.",
            )
        }
    }
}

@Composable
fun ReviewQueueScreen(session: MaticaSession, navigator: MaticaNavigator) {
    val plan = session.studyPlan()
    MaticaScreen(title = "Repasos", subtitle = "Vencer no es olvidar: es revalidar", session = session, onBack = { navigator.goBack() }) {
        MaticaStateView(
            state = when {
                plan.reviewsDue.isEmpty() -> MaticaViewState.Empty(
                    "Nada vencido hoy",
                    "Los repasos aparecen cuando el motor de repaso programa una fecha. " +
                        "Practica y la fecha llega sola.",
                )

                else -> MaticaViewState.success(plan.reviewsDue)
            },
        ) { due ->
            due.forEach { item ->
                MaticaCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.md)) {
                        MaticaGlyph(icon = MaticaIcon.FLAME, tint = MaterialTheme.colorScheme.error)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.skill.labelEs, style = MaterialTheme.typography.titleMedium)
                            MaticaCaption("Programado el ${item.scheduledOn} · ${item.daysOverdue} día(s) de retraso.")
                        }
                        MaticaButton(label = "Abrir", onClick = { navigator.go(MaticaRoute.SkillDetail(item.skill)) })
                    }
                }
            }
        }
        val atRisk = session.profile.atRisk(session.today)
        if (atRisk.isNotEmpty()) {
            MaticaCaption("En riesgo: " + atRisk.joinToString { it.skill.labelEs })
        }
    }
}

@Composable
fun RewardsScreen(session: MaticaSession, navigator: MaticaNavigator) {
    // showChips = false: la propia pantalla abre con su fila de tres insignias (XP, racha, gemas).
    // Dejar además las dos de la cabecera duplicaba «0 XP» y «0 días» a 40 px de distancia, como
    // salió en la captura de Logros.
    MaticaScreen(
        title = "Logros",
        session = session,
        onBack = { navigator.goBack() },
        subtitle = "Se gana practicando",
        showChips = false,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm)) {
            MaticaCountChip(session.xp, "XP", MaticaIcon.XP, MaticaChipPalette.xp, Modifier.weight(1f))
            MaticaCountChip(session.streak, "días", MaticaIcon.FLAME, MaticaChipPalette.streak, Modifier.weight(1f))
            MaticaCountChip(session.gems, "gemas", MaticaIcon.GEM, MaticaChipPalette.xp, Modifier.weight(1f))
        }
        MaticaSection("Marcas") {
            listOf(
                60 to "Primer bloque de práctica",
                180 to "Tres habilidades en desarrollo",
                360 to "Constancia: un día más sin repaso vencido",
            ).forEach { (target, label) ->
                MaticaProgress(done = session.xp.coerceAtMost(target), total = target, label = "$label · $target XP")
            }
            MaticaCard {
                Text("Cómo se cuenta", style = MaterialTheme.typography.titleMedium)
                MaticaCaption(
                    "Cada acierto sin pista suma 12 XP, con pista 6 y el error no resta: la evidencia manda " +
                        "al nivel, no la racha. No hay compras, no hay anuncios y no hay ranking con otras " +
                        "personas: la comparación sólo existe contigo de ayer.",
                )
            }
            MaticaButton(
                label = "Volver a hoy",
                onClick = { navigator.popTo(MaticaRoute.Home) },
                variant = MaticaButtonVariant.TEXT,
                fullWidth = true,
            )
        }
    }
}

@Composable
fun ProfileScreen(session: MaticaSession, navigator: MaticaNavigator) {
    val skills = session.graph.order().sortedBy { it.labelEs }
    MaticaScreen(title = "Mi perfil", session = session, onBack = { navigator.goBack() }, subtitle = "Perfil local, sin servidor") {
        MaticaStateView(
            state = when {
                session.profile.progress.isEmpty() -> MaticaViewState.Empty(
                    "Perfil vacío",
                    "Todavía no hay evidencia. El diagnóstico o la primera práctica llenan esta hoja.",
                )

                else -> MaticaViewState.success(skills)
            },
        ) { list ->
            list.forEach { skill ->
                LevelRow(
                    skill = skill,
                    level = session.levelOf(skill),
                    detail = session.profile.progressOf(skill).window
                        .lastOrNull()
                        ?.let { "Última evidencia: ${it.quality.labelEs} el ${it.occurredOn} (${it.origin})" }
                        ?: "Sin evidencias registradas.",
                )
            }
            MaticaCaption("Nivel «Competente» = 3 aciertos sin pista en la ventana. «Fluido» añade repaso a una semana.")
        }
        MaticaButton(
            label = "Reiniciar el perfil local",
            onClick = {
                session.resetSession()
                navigator.resetTo(MaticaRoute.Welcome)
            },
            variant = MaticaButtonVariant.OUTLINE,
            fullWidth = true,
        )
    }
}

@Composable
private fun LevelRow(skill: SkillId, level: MasteryLevel, detail: String) {
    MaticaCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.md)) {
            MaticaGlyph(
                icon = when (level) {
                    MasteryLevel.NOT_INTRODUCED -> MaticaIcon.LOCK
                    MasteryLevel.DEVELOPING -> MaticaIcon.FORWARD
                    MasteryLevel.PROFICIENT -> MaticaIcon.CHECK
                    MasteryLevel.FLUENT -> MaticaIcon.GEM
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(skill.labelEs, style = MaterialTheme.typography.titleMedium)
                MaticaCaption("${level.labelEs} · $detail")
            }
        }
    }
}

@Composable
fun SettingsScreen(session: MaticaSession, navigator: MaticaNavigator) {
    val settings = session.settings
    MaticaScreen(title = "Ajustes", session = session, onBack = { navigator.goBack() }, subtitle = "Accesibilidad primero") {
        MaticaSection("Tema") {
            Row(horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm)) {
                ThemeMode.entries.forEach { mode ->
                    MaticaButton(
                        label = mode.labelEs(),
                        onClick = { session.updateSettings { it.copy(themeMode = mode) } },
                        modifier = Modifier.weight(1f),
                        variant = if (settings.themeMode == mode) MaticaButtonVariant.PRIMARY else MaticaButtonVariant.NEUTRAL,
                    )
                }
            }
        }
        MaticaSection("Movimiento") {
            MaticaButton(
                label = if (settings.reduceTransitions) "Transiciones reducidas: activas" else "Transiciones reducidas: apagadas",
                onClick = { session.updateSettings { it.copy(reduceTransitions = !it.reduceTransitions) } },
                variant = MaticaButtonVariant.NEUTRAL,
                fullWidth = true,
            )
            // Cuatro etiquetas no caben en una línea siquiera con el ancho repartido: cada pastilla
            // necesita ~103 dp (etiqueta + 24 de relleno por lado) y el teléfono de 392 dp deja ~90,
            // así que «Rápida» se escribía una letra por renglón. En dos filas de dos cada botón
            // recibe el doble de sitio y la etiqueta cabe entera.
            listOf(0f to 0.5f, 1f to 1.5f).forEach { (primera, segunda) ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf(primera, segunda).forEach { scale ->
                        MaticaButton(
                            label = scale.label(),
                            onClick = { session.updateSettings { it.copy(motionScale = scale) } },
                            modifier = Modifier.weight(1f),
                            variant = if (settings.motionScale == scale) MaticaButtonVariant.PRIMARY else MaticaButtonVariant.NEUTRAL,
                        )
                    }
                }
            }
            MaticaCaption("«Ninguna» equivale a reducir el movimiento del sistema: las animaciones se apagan, el contenido no.")
        }
        MaticaSection("Práctica") {
            MaticaButton(
                label = if (settings.showHintsByDefault) "Pistas abiertas por defecto" else "Pistas ocultas por defecto",
                onClick = { session.updateSettings { it.copy(showHintsByDefault = !it.showHintsByDefault) } },
                variant = MaticaButtonVariant.NEUTRAL,
                fullWidth = true,
            )
        }
        MaticaSection("Grado") {
            // NEUTRAL y no TEXT: sin superficie las tres opciones se leían como etiquetas y sólo
            // la elegada parecía un botón. No van en una fila de tres: «2.º de secundaria» necesita
            // ~150 dp de etiqueta y el teléfono deja ~116 dp por celda, así que se partiría.
            listOf("1.º de secundaria", "2.º de secundaria", "3.º de secundaria").forEach { grade ->
                MaticaButton(
                    label = grade,
                    onClick = { session.updateSettings { it.copy(grade = grade) } },
                    variant = if (settings.grade == grade) MaticaButtonVariant.PRIMARY else MaticaButtonVariant.NEUTRAL,
                    fullWidth = true,
                )
            }
        }
        MaticaSection("Conexión") {
            MaticaButton(
                label = if (session.offline) "Simular conexión" else "Simular sin conexión",
                onClick = { session.setConnection(session.offline) },
                variant = MaticaButtonVariant.OUTLINE,
                fullWidth = true,
            )
            MaticaCaption(
                "La conexión sólo afecta a lo que depende del backend (cuentas y aulas). El motor y el " +
                    "registro de evidencia siguen funcionando sin ella.",
            )
            session.message?.let { MaticaCaption(it) }
        }
    }
}

@Composable
fun ClassroomScreen(session: MaticaSession, navigator: MaticaNavigator) {
    var code = remember { "" }
    MaticaScreen(title = "Aula", session = session, onBack = { navigator.goBack() }, subtitle = "Grupo del docente") {
        MaticaStateView(
            state = MaticaViewState.Error(
                title = "Sin servicio de aula",
                detail = "Unirse a un grupo necesita el backend: no está construido ni desplegado, así que " +
                    "ningún código de invitación puede validarse. La app no finge unirse a nada.",
            ),
            onRetry = null,
        ) { }
        MaticaCard {
            MaticaField(
                value = code,
                onValueChange = { code = it },
                label = "Código de invitación",
                placeholder = "6 caracteres",
                enabled = false,
                error = "Desactivado: no hay servidor que lo valide.",
            )
            MaticaCaption(
                "Cuando exista el servicio, el aula traerá: lista del grupo, asignaciones y vista " +
                    "docente sin datos personales del estudiante más allá del alias del grupo.",
            )
        }
        MaticaButton(
            label = "Practicar sin grupo",
            onClick = {
                val template = MaticaRoute.KNOWN_TEMPLATES.first()
                navigator.go(MaticaRoute.Practice(template, MaticaSession.BASE_SEED))
            },
            fullWidth = true,
        )
    }
}

private fun ThemeMode.labelEs(): String = when (this) {
    ThemeMode.SYSTEM -> "Sistema"
    ThemeMode.LIGHT -> "Claro"
    ThemeMode.DARK -> "Oscuro"
}

private fun Float.label(): String = when (this) {
    0f -> "Ninguna"
    0.5f -> "Lenta"
    1f -> "Normal"
    else -> "Rápida"
}
