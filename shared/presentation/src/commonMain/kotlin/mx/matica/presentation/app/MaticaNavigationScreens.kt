package mx.matica.presentation.app

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.matica.design.brand.MaticaMark
import mx.matica.design.brand.MaticaMascot
import mx.matica.design.brand.MascotEmotion
import mx.matica.presentation.navigation.MaticaNavigationBar
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.tokens.MaticaLayout
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.learning.diagnosis.DiagnosisSession
import mx.matica.learning.mastery.MasteryLevel
import mx.matica.learning.text.pluralEs
import mx.matica.math.skills.SkillId
import mx.matica.presentation.components.MaticaButton
import mx.matica.presentation.components.MaticaButtonVariant
import mx.matica.presentation.components.MaticaChipPalette
import mx.matica.presentation.components.MaticaConfirmDialog
import mx.matica.presentation.components.MaticaCountChip
import mx.matica.presentation.components.MaticaGlyph
import mx.matica.presentation.components.MaticaIcon
import mx.matica.presentation.components.MaticaIconButton
import mx.matica.presentation.components.MaticaSection
import mx.matica.presentation.math.RouteMapView
import mx.matica.presentation.math.prettyMath
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute
import mx.matica.presentation.state.MaticaStateView
import mx.matica.presentation.state.MaticaViewState

@Composable
fun WelcomeScreen(session: MaticaSession, navigator: MaticaNavigator) {
    MaticaScreen(title = "Mática", session = session, showChips = false, subtitle = "Aprende matemáticas paso a paso") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = MaticaSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaticaSpacing.md),
        ) {
            MaticaMascot(
                emotion = MascotEmotion.HAPPY,
                modifier = Modifier.size(140.dp),
            )
            Text(
                text = "¡Las matemáticas pueden ser divertidas!",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Aprende paso a paso, resuelve retos a tu propio ritmo y domina cada concepto con explicaciones claras.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            "Mática no muestra anuncios ni solicita datos personales innecesarios. " +
                "Sin cuenta, el avance vive sólo en este teléfono; con cuenta, los intentos se envían " +
                "al servidor cuando hay red.",
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
        // `md` y no los 8 dp de la tarjeta: cuatro botones de 48 dp tocándose con 8 dp leídos en la
        // captura del teléfono parecían una lista densa, no cuatro alternativas para elegir.
        Column(verticalArrangement = Arrangement.spacedBy(MaticaSpacing.md)) {
            question.options.forEachIndexed { index, option ->
                MaticaButton(
                    // El instrumento escribe `x^5` porque es lo que el motor parsea; en pantalla se
                    // lee x⁵, igual que en el resto de la app.
                    label = prettyMath(option),
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
                MaticaCaption(
                    "${skipped.size} ${pluralEs(skipped.size, "habilidad", "habilidades")} no se preguntaron " +
                        "por el corte de bandas. Se abrirán con la ruta.",
                )
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

/**
 * Entrada con cuenta real. El servidor emite tokens (Argon2id + JWT), así que el formulario hace lo
 * que promete: entrar o crear la cuenta. También deja claro qué queda en el teléfono y qué viaja.
 */
@Composable
fun LoginScreen(session: MaticaSession, navigator: MaticaNavigator) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val motion = LocalMotionStyle.current
    // La marca entra dibujándose una sola vez por visita. Con movimiento reducido nace completa: no
    // hay versión «quieta» que esconda el nombre de la app.
    val heroDraw = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(motion) {
        // El `snapTo` de la rama reducida no es un detalle: la escala del sistema se puede cambiar con
        // la app abierta y `platformMotionScale` ahora publica ese cambio al instante. Sin él, la marca
        // se quedaba a medias de trazar para siempre justo cuando el teléfono pidió calma.
        if (motion.reduced) {
            heroDraw.snapTo(1f)
        } else {
            heroDraw.animateTo(1f, tween(motion.ms(MaticaMotion.durations.xLong), easing = FastOutSlowInEasing))
        }
    }
    val identity = session.accountIdentity

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaticaSpacing.md, vertical = MaticaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaticaIconButton(icon = MaticaIcon.BACK, contentDescription = "Regresa", onClick = { navigator.goBack() })
            Spacer(modifier = Modifier.weight(1f))
            LoginStatusPill(signedIn = identity != null)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterHorizontally)
                .widthIn(max = MaticaLayout.maxContentWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaticaSpacing.lg, vertical = MaticaSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaticaSpacing.lg),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
            ) {
                MaticaMark(modifier = Modifier.size(72.dp), drawn = heroDraw.value)
                Text(
                    text = if (identity == null) "Entra a tu cuenta" else "Tu cuenta",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = identity?.email ?: "Tu ruta, tus repasos y tus logros, en un solo perfil.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (identity != null) {
                MaticaCard {
                    Text(identity.displayName ?: identity.email, style = MaterialTheme.typography.titleMedium)
                    MaticaCaption(
                        "Intentos pendientes de enviar: ${session.pendingSync}. El servidor reconoce cada " +
                            "intento por su identificador, así que reintentar no duplica evidencia.",
                    )
                    MaticaButton(
                        label = "Sincronizar ahora",
                        onClick = {
                            session.requestSync()
                            session.refreshSyncState()
                        },
                        variant = MaticaButtonVariant.OUTLINE,
                        fullWidth = true,
                    )
                }
                MaticaButton(
                    label = "Continuar",
                    onClick = { navigator.replace(MaticaRoute.Home) },
                    leadingGlyph = MaticaIcon.HOME,
                    fullWidth = true,
                )
                MaticaButton(
                    label = "Cerrar sesión",
                    onClick = { session.signOut() },
                    variant = MaticaButtonVariant.OUTLINE,
                    fullWidth = true,
                )
                MaticaCaption(
                    "Al cerrar sesión, los intentos ya guardados permanecen en este teléfono y se envían " +
                        "cuando vuelvas a entrar con la misma cuenta.",
                )
            } else if (!session.accountAvailable) {
                MaticaStateView(
                    state = MaticaViewState.Error(
                        title = "Cuentas no disponibles en esta versión",
                        detail = "Esta copia de Mática se compiló sin la dirección del servidor, así que no puede " +
                            "crear cuentas ni sincronizar. La práctica local funciona completa.",
                    ),
                    onRetry = null,
                ) { }
                MaticaButton(
                    label = "Practicar sin cuenta",
                    onClick = { navigator.replace(MaticaRoute.Home) },
                    leadingGlyph = MaticaIcon.HOME,
                    fullWidth = true,
                )
            } else {
                MaticaCard {
                    Text(if (creating) "Crear cuenta" else "Cuenta de Mática", style = MaterialTheme.typography.titleMedium)
                    MaticaCaption(
                        if (creating) {
                            "Un correo y una contraseña de al menos 8 caracteres. El nombre es opcional y " +
                                "es lo que verá tu grupo."
                        } else {
                            "Entra con el correo y la contraseña de tu cuenta."
                        },
                    )
                    MaticaField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Correo electrónico",
                        placeholder = "tu@correo.com",
                        keyboard = KeyboardType.Email,
                        enabled = !session.authBusy,
                    )
                    if (creating) {
                        MaticaField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = "Nombre en el aula (opcional)",
                            placeholder = "Cómo te verá el grupo",
                            enabled = !session.authBusy,
                        )
                    }
                    MaticaField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Contraseña",
                        placeholder = if (creating) "Al menos 8 caracteres" else "Tu contraseña",
                        keyboard = KeyboardType.Password,
                        enabled = !session.authBusy,
                    )
                    session.authError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    MaticaButton(
                        label = when {
                            session.authBusy -> "Conectando…"
                            creating -> "Crear cuenta"
                            else -> "Entrar"
                        },
                        onClick = {
                            scope.launch {
                                val ok = if (creating) {
                                    session.createAccount(email.trim(), password, displayName.trim())
                                } else {
                                    session.signIn(email.trim(), password)
                                }
                                if (ok) navigator.replace(MaticaRoute.Home)
                            }
                        },
                        enabled = !session.authBusy,
                        fullWidth = true,
                    )
                    MaticaButton(
                        label = if (creating) "Ya tengo cuenta" else "Crear una cuenta nueva",
                        onClick = {
                            creating = !creating
                            session.clearAuthError()
                        },
                        enabled = !session.authBusy,
                        variant = MaticaButtonVariant.OUTLINE,
                        fullWidth = true,
                    )
                }

                MaticaButton(
                    label = "Practicar sin cuenta",
                    onClick = { navigator.replace(MaticaRoute.Home) },
                    leadingGlyph = MaticaIcon.HOME,
                    variant = MaticaButtonVariant.NEUTRAL,
                    fullWidth = true,
                )
                MaticaCaption("Sin cuenta puedes practicar; el progreso vive sólo en este teléfono.")

                MaticaSection("Con tu cuenta") {
                    MaticaCard {
                        AccountFeature(
                            icon = MaticaIcon.REFRESH,
                            title = "Sincroniza entre dispositivos",
                            detail = "Los intentos se guardan primero en el teléfono y se envían al servidor cuando hay red.",
                        )
                        AccountFeature(
                            icon = MaticaIcon.LOCK,
                            title = "Recupera tu avance",
                            detail = "La cuenta devuelve tu historial si pierdes el dispositivo.",
                        )
                        AccountFeature(
                            icon = MaticaIcon.FORWARD,
                            title = "Aula del docente",
                            detail = "Un código de grupo, sin datos personales de más.",
                        )
                    }
                }
                MaticaCaption(
                    "La contraseña se envía al servidor de Mática y se guarda con Argon2id. La app no la " +
                        "conserva: sólo guarda el token de la sesión.",
                )
            }
        }
    }
}

@Composable
private fun LoginStatusPill(signedIn: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .background(scheme.secondaryContainer, MaticaShapes.pill)
            .padding(horizontal = MaticaSpacing.md, vertical = MaticaSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaticaGlyph(
            icon = if (signedIn) MaticaIcon.CHECK else MaticaIcon.LOCK,
            tint = scheme.onSecondaryContainer,
            modifier = Modifier.size(16.dp),
            strokeWidthDp = 2.2f,
        )
        Text(
            text = if (signedIn) "Sesión activa" else "Cuenta Mática",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun AccountFeature(icon: MaticaIcon, title: String, detail: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.md),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(MaticaShapes.pill)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            MaticaGlyph(
                icon = icon,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
                strokeWidthDp = 2.2f,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaticaSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            MaticaCaption(detail)
        }
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
            MaticaCountChip(
                value = session.gems,
                unit = "gema",
                pluralUnit = "gemas",
                glyph = MaticaIcon.GEM,
                pair = MaticaChipPalette.xp,
            )
        },
        bottomBar = { MaticaNavigationBar(currentRoute = MaticaRoute.Home, navigator = navigator) },
    ) {
        // Hero Card «SIGUE TU AVENTURA» (Figma 1:34)
        val nextSkill = plan.nextSkills.firstOrNull()?.skill
        MaticaCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = MaticaSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SIGUE TU AVENTURA",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "x²",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    text = nextSkill?.labelEs ?: "Álgebra básica",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Mundo 2 · Enfoque diario de aprendizaje",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MaticaButton(
                    label = if (nextSkill != null) "Continuar lección" else "Abrir mi ruta",
                    onClick = {
                        if (nextSkill != null) {
                            navigator.go(MaticaRoute.SkillDetail(nextSkill))
                        } else {
                            navigator.go(MaticaRoute.RouteMap)
                        }
                    },
                    leadingGlyph = MaticaIcon.ROUTE,
                    fullWidth = true,
                )
            }
        }

        // Misiones de hoy (Figma 1:45)
        MaticaSection("Tus misiones de hoy") {
            MaticaCard {
                Column(verticalArrangement = Arrangement.spacedBy(MaticaSpacing.sm)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
                    ) {
                        MaticaGlyph(
                            icon = if (session.streak > 0) MaticaIcon.CHECK else MaticaIcon.LESSON,
                            tint = if (session.streak > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Completa una lección hoy",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
                    ) {
                        MaticaGlyph(
                            icon = if (session.xp > 0) MaticaIcon.CHECK else MaticaIcon.XP,
                            tint = if (session.xp > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Acumula experiencia diaria",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }
        }

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
                                MaticaCaption(
                                    "Repaso vencido hace ${due.daysOverdue} " +
                                        pluralEs(due.daysOverdue.toInt(), "día", "días") + ". ${due.level.labelEs}.",
                                )
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
    MaticaScreen(title = "Mi ruta", subtitle = "Desbloquea dominando, no avanzando", session = session, onBack = { navigator.goBack() }, bottomBar = { MaticaNavigationBar(currentRoute = MaticaRoute.RouteMap, navigator = navigator) }) {
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
            // Cabecera de Mundo (Figma 1:74)
            val dominadas = nodes.count { it.level == MasteryLevel.PROFICIENT || it.level == MasteryLevel.FLUENT }
            val total = nodes.size
            MaticaCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = MaticaSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(MaticaSpacing.xs),
                ) {
                    Text(
                        text = "MUNDO 02",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = "El reino del álgebra",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$dominadas de $total habilidades dominadas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${if (total > 0) (dominadas * 100 / total) else 0}%",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

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
    MaticaScreen(title = "Repasos", subtitle = "Vencer no es olvidar: es revalidar", session = session, onBack = { navigator.goBack() }, bottomBar = { MaticaNavigationBar(currentRoute = MaticaRoute.ReviewQueue, navigator = navigator) }) {
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
                            MaticaCaption(
                                "Programado el ${item.scheduledOn} · ${item.daysOverdue} " +
                                    pluralEs(item.daysOverdue.toInt(), "día", "días") + " de retraso.",
                            )
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
            MaticaCountChip(
                session.streak,
                "día",
                MaticaIcon.FLAME,
                MaticaChipPalette.streak,
                Modifier.weight(1f),
                pluralUnit = "días",
            )
            MaticaCountChip(
                session.gems,
                "gema",
                MaticaIcon.GEM,
                MaticaChipPalette.xp,
                Modifier.weight(1f),
                pluralUnit = "gemas",
            )
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
    var confirmarReinicio by remember { mutableStateOf(false) }
    val conCuenta = session.accountIdentity != null
    MaticaScreen(
        title = "Mi perfil",
        session = session,
        onBack = { navigator.goBack() },
        subtitle = if (conCuenta) "Progreso local y cuenta Mática" else "Perfil local, sin servidor",
        bottomBar = { MaticaNavigationBar(currentRoute = MaticaRoute.Profile, navigator = navigator) },
        overlay = {
            if (confirmarReinicio) {
                MaticaConfirmDialog(
                    title = "¿Reiniciar el perfil local?",
                    detail = "Se borran la evidencia, el nivel de cada habilidad, la racha y las gemas de este " +
                        "teléfono." + if (conCuenta) {
                        " Tu cuenta y los intentos ya sincronizados con el servidor no se borran desde aquí."
                    } else {
                        " No hay copia en ningún servidor: lo que se va aquí se va para siempre."
                    },
                    confirmLabel = "Sí, reiniciar",
                    dismissLabel = "Conservar",
                    onConfirm = {
                        confirmarReinicio = false
                        session.resetSession()
                        navigator.resetTo(MaticaRoute.Welcome)
                    },
                    onDismissRequest = { confirmarReinicio = false },
                )
            }
        },
    ) {
        // Mis estadísticas (Figma 1:177)
        MaticaCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = MaticaSpacing.xs),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${session.streak}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                    Text("Días de racha", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val lecciones = session.profile.progress.values.sumOf { it.window.size }
                    Text("$lecciones", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.secondary)
                    Text("Lecciones", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${session.gems}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.tertiary)
                    Text("Gemas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Mis insignias (Figma 1:184)
        MaticaSection("Mis insignias") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
            ) {
                listOf(
                    Triple("★", "Primer paso", session.xp > 0),
                    Triple("♛", "Mente ágil", session.streak >= 3),
                    Triple("⚡", "Sin rendirse", session.gems >= 5),
                ).forEach { (icono, nombre, ganada) ->
                    MaticaCard(
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = icono,
                                style = MaterialTheme.typography.titleLarge,
                                color = if (ganada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            )
                            Text(
                                text = nombre,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                textAlign = TextAlign.Center,
                                color = if (ganada) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

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
            onClick = { confirmarReinicio = true },
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
    val coroutineScope = rememberCoroutineScope()
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
        MaticaSection("Cuenta") {
            val identity = session.accountIdentity
            if (identity == null) {
                MaticaCaption("Sin cuenta: el progreso vive sólo en este teléfono.")
                if (session.accountAvailable) {
                    MaticaButton(
                        label = "Entrar o crear cuenta",
                        onClick = { navigator.go(MaticaRoute.Login) },
                        leadingGlyph = MaticaIcon.LOCK,
                        fullWidth = true,
                    )
                } else {
                    MaticaCaption("Esta compilación no incluye el servidor de cuentas.")
                }
            } else {
                MaticaCaption(identity.email)
                MaticaCaption(
                    if (session.pendingSync == 0) {
                        "Sin intentos pendientes de sincronizar."
                    } else if (session.pendingSync == 1) {
                        "1 intento esperando sincronizar."
                    } else {
                        "${session.pendingSync} intentos esperando sincronizar."
                    },
                )
                MaticaButton(
                    label = "Sincronizar ahora",
                    onClick = {
                        session.requestSync()
                        session.refreshSyncState()
                    },
                    variant = MaticaButtonVariant.OUTLINE,
                    fullWidth = true,
                )
                MaticaButton(
                    label = "Cerrar sesión",
                    onClick = {
                        session.signOut()
                        navigator.go(MaticaRoute.Login)
                    },
                    variant = MaticaButtonVariant.NEUTRAL,
                    fullWidth = true,
                )
            }
        }
        MaticaSection("Conexión") {
            MaticaButton(
                label = when {
                    session.checkingConnection -> "Comprobando…"
                    session.offline -> "Comprobar conexión"
                    else -> "Volver a comprobar"
                },
                onClick = { coroutineScope.launch { session.refreshConnection() } },
                enabled = !session.checkingConnection,
                variant = MaticaButtonVariant.OUTLINE,
                fullWidth = true,
            )
            // El esqueleto de carga sólo se muestra mientras la sonda está en vuelo; no hay espera que
            // fingir en el resto de la app, porque todo lo demás se calcula en el propio teléfono.
            if (session.checkingConnection) {
                MaticaStateView(state = MaticaViewState.Loading) { }
            }
            MaticaCaption(
                "Estado real del servidor. El motor matemático y la práctica local funcionan también sin conexión.",
            )
            session.message?.let { MaticaCaption(it) }
        }
    }
}

@Composable
fun ClassroomScreen(session: MaticaSession, navigator: MaticaNavigator) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val identity = session.accountIdentity
    LaunchedEffect(identity?.id) { if (identity != null) session.loadClassrooms() }

    MaticaScreen(title = "Aula", session = session, onBack = { navigator.goBack() }, subtitle = "Grupo del docente") {
        if (identity == null) {
            // Dos copy distintas y no una: «no hay red en este teléfono» y «no hay sesión» se
            // arreglan de manera diferente. El caso Offline sigue siendo el único lugar donde la
            // app construye `MaticaViewState.Offline` de verdad.
            MaticaStateView(
                state = if (session.offline) {
                    MaticaViewState.Offline(
                        title = "Sin conexión con el aula",
                        detail = "Unirse a un grupo necesita el backend y este teléfono no lo alcanza " +
                            "ahora mismo. Nada se pierde al esperar: el trabajo local sigue guardado.",
                    )
                } else {
                    MaticaViewState.Error(
                        title = "El aula necesita una cuenta",
                        detail = "Unirse a un grupo exige identificar al estudiante. Entra a tu cuenta; " +
                            "la práctica local sigue disponible sin ella.",
                    )
                },
                onRetry = null,
            ) { }
            MaticaButton(
                label = "Entrar a mi cuenta",
                onClick = { navigator.go(MaticaRoute.Login) },
                leadingGlyph = MaticaIcon.LOCK,
                fullWidth = true,
            )
            return@MaticaScreen
        }

        if (session.classroomBusy) {
            MaticaStateView(state = MaticaViewState.Loading) { }
        }

        MaticaCard {
            Text("Unirme con un código", style = MaterialTheme.typography.titleSmall)
            MaticaField(
                value = code,
                onValueChange = { code = it.uppercase().take(6) },
                label = "Código de invitación",
                placeholder = "6 caracteres",
                hint = "Te lo da tu docente. Se lee sin distinguir mayúsculas.",
                enabled = !session.classroomBusy,
            )
            MaticaButton(
                label = if (session.classroomBusy) "Comprobando…" else "Unirme",
                onClick = { scope.launch { session.joinClassroom(code) } },
                enabled = !session.classroomBusy && code.length == 6,
                fullWidth = true,
            )
        }

        MaticaCard {
            Text("Crear un grupo", style = MaterialTheme.typography.titleSmall)
            MaticaField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = "Nombre del grupo",
                placeholder = "3.º B — turno matutino",
                enabled = !session.classroomBusy,
            )
            MaticaButton(
                label = "Crear grupo",
                onClick = { scope.launch { session.createClassroom(name) } },
                enabled = !session.classroomBusy && name.length >= 3,
                variant = MaticaButtonVariant.OUTLINE,
                fullWidth = true,
            )
        }

        session.classroomMessage?.let { MaticaCaption(it) }

        MaticaSection(title = "Mis grupos") {
            if (session.classroomGroups.isEmpty()) {
                MaticaCaption("Todavía no perteneces a ningún grupo.")
            } else {
                session.classroomGroups.forEach { group ->
                    MaticaCard {
                        Text(group.name, style = MaterialTheme.typography.titleSmall)
                        MaticaCaption(
                            buildString {
                                append(if (group.role == "owner") "Docente" else "Estudiante")
                                append(" · ")
                                append(if (group.memberCount == 1) "1 integrante" else "${group.memberCount} integrantes")
                                if (group.inviteCode != null) append(" · Código: ${group.inviteCode}")
                            },
                        )
                    }
                }
            }
        }

        MaticaButton(
            label = "Practicar sin grupo",
            onClick = {
                val template = MaticaRoute.KNOWN_TEMPLATES.first()
                navigator.go(MaticaRoute.Practice(template, MaticaSession.BASE_SEED))
            },
            variant = MaticaButtonVariant.OUTLINE,
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
