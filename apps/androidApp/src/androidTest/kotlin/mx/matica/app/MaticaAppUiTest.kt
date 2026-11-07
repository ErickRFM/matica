package mx.matica.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import mx.matica.design.tokens.MaticaPalette
import mx.matica.learning.text.pluralEs
import mx.matica.math.skills.SkillId
import mx.matica.presentation.app.MaticaApp
import mx.matica.presentation.app.MaticaSession
import mx.matica.presentation.app.verdictMessage
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Estas pruebas corren en el dispositivo y tocan la pantalla de verdad: verifican que el host, las
 * rutas y el estado observable están cableados, no que una función pura devuelve lo esperado.
 *
 * Lo matemático se comprueba en `commonTest`; aquí sólo se mira lo que el usuario ve después de pulsar.
 */
@RunWith(AndroidJUnit4::class)
class MaticaAppUiTest {

    @get:Rule
    val rule = createComposeRule()

    private fun session() = MaticaSession(today = LocalDate(2026, 11, 5))

    @Test
    fun elDiagnostioCuentaEnPantallaCadaRespuesta() {
        val session = session()
        rule.setContent { MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Diagnosis)) }
        rule.waitForIdle()
        rule.onNodeWithText("0 de 15 respondidas").assertIsDisplayed()

        val question = requireNotNull(session.currentDiagnosis()).pendingQuestions().first()
        rule.onNodeWithText(question.options[question.correctIndex]).performClick()
        rule.waitForIdle()

        rule.onNodeWithText("1 de 15 respondidas").assertIsDisplayed()
        assertEquals(1, session.diagnosisAnswered)
    }

    @Test
    fun laHojaDePistasDejaElReproductorDebajoYRegresa() {
        val session = session()
        val skill = SkillId.BALANCE_PRINCIPLE
        val navigator = MaticaNavigator(MaticaRoute.Home)
        navigator.go(MaticaRoute.LessonPlayer(skill, 1))
        rule.setContent { MaticaApp(session = session, navigator = navigator) }
        rule.waitForIdle()
        rule.onNodeWithText("Comprobar paso").assertIsDisplayed()

        rule.onNodeWithText("Pista").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Mostrar una pista más").assertIsDisplayed()
        assertEquals(MaticaRoute.HintSheet(skill, 1), navigator.current)
        assertEquals(MaticaRoute.LessonPlayer(skill, 1), navigator.base)

        // Con la hoja abierta hay dos botones «Regresa» en el árbol: el del reproductor de abajo y el
        // de la hoja. Se pulsa el de la hoja, que es el que el estudiante ve. El título es hermano
        // directo del botón: un `Column` sin semántica propia no aparece como nodo intermedio.
        rule.onNode(hasContentDescription("Regresa") and hasAnySibling(hasText("Pistas"))).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Comprobar paso").assertIsDisplayed()
        assertEquals(MaticaRoute.LessonPlayer(skill, 1), navigator.current)
    }

    @Test
    fun laHojaDePistasPintaUnaSuperficieOpacaSobreElReproductor() {
        val session = session()
        val navigator = MaticaNavigator(MaticaRoute.LessonPlayer(SkillId.BALANCE_PRINCIPLE, 1))
        rule.setContent { MaticaApp(session = session, navigator = navigator) }
        rule.waitForIdle()

        // El píxel se calibra con la hoja cerrada, no con un color escrito a mano: lo que tiene que
        // cambiar es lo que hay en el sitio donde estaba el botón primario del reproductor.
        val boton = rule.onNodeWithText("Comprobar paso").fetchSemanticsNode().boundsInWindow
        val raiz = rule.onRoot().fetchSemanticsNode().boundsInWindow
        val x = ((boton.left + boton.right) / 2f - raiz.left).roundToInt()
        val y = ((boton.top + boton.bottom) / 2f - raiz.top).roundToInt()
        val debajo = pixelDeLaRaiz(x, y)

        rule.onNodeWithText("Pista").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Mostrar una pista más").assertIsDisplayed()

        val encima = pixelDeLaRaiz(x, y)
        assertTrue(
            !mismoColor(debajo, encima),
            "En ($x,$y), donde estaba «Comprobar paso», se leía #${Integer.toHexString(debajo)} con la hoja " +
                "cerrada y sigue #${Integer.toHexString(encima)} con ella abierta: la capa modal se pinta " +
                "transparente y el reproductor se filtra a través de las pistas.",
        )

        val tituloDeLaHoja = rule.onNodeWithText("Pistas").fetchSemanticsNode().boundsInWindow
        assertTrue(
            tituloDeLaHoja.top >= raiz.top + raiz.height * FRACCION_SUPERIOR_DE_SCIM,
            "El título «Pistas» arranca en y=${tituloDeLaHoja.top} dentro de una zona de ${raiz.height} px: " +
                "cae en la misma franja que el título de la pantalla de abajo y los dos textos se pintan uno " +
                "sobre otro, que es lo que salió en la captura del emulador.",
        )
    }

    private fun pixelDeLaRaiz(x: Int, y: Int): Int {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        return bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1))
    }

    @Test
    fun lasOpcionesDelDiagnosticoSeRecortanSobreLaTarjeta() {
        val session = session()
        rule.setContent { MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Diagnosis)) }
        rule.waitForIdle()
        val pregunta = requireNotNull(session.currentDiagnosis()).pendingQuestions().first()

        // El relleno del botón neutro es BlueMist y la tarjeta se pinta con surfaceVariant, que en
        // claro también es BlueMist: dentro de una tarjeta el botón no tenía fondo ni borde y las
        // cuatro opciones parecían texto suelto. Se mide el píxel, no la intención: a la izquierda del
        // botón sigue habiendo tarjeta, así que si ningún punto del borde izquierdo difiere de ese
        // punto exterior, la opción no se recorta contra nada.
        val opcion = rule.onNode(hasText(pregunta.options[0]) and hasClickAction()).fetchSemanticsNode().boundsInWindow
        val raiz = rule.onRoot().fetchSemanticsNode().boundsInWindow
        val centroY = ((opcion.top + opcion.bottom) / 2f - raiz.top).roundToInt()
        val fuera = pixelDeLaRaiz((opcion.left - 8f - raiz.left).roundToInt(), centroY)
        val seRecorta = (0..12).any { desplazamiento ->
            pixelDeLaRaiz((opcion.left - raiz.left).roundToInt() + desplazamiento, centroY) != fuera
        }
        assertTrue(
            seRecorta,
            "La opción «${pregunta.options[0]}» empieza en x=${opcion.left} y los 12 px siguientes valen lo " +
                "mismo que la tarjeta de al lado (#${Integer.toHexString(fuera)}): el botón no se dibuja " +
                "recortado sobre la tarjeta y el estudiante ve cuatro frases, no cuatro respuestas.",
        )
    }

    private fun mismoColor(uno: Int, otro: Int): Boolean =
        intArrayOf(16, 8, 0).all { turno -> abs(((uno shr turno) and 0xFF) - ((otro shr turno) and 0xFF)) <= 2 }

    @Test
    fun desdeHoySePuedeAbrirElPerfilLocal() {
        val session = session()
        val navigator = MaticaNavigator(MaticaRoute.Home)
        rule.setContent { MaticaApp(session = session, navigator = navigator) }
        rule.waitForIdle()

        // «Mi perfil» se probaba montando la ruta a mano, y así el test verde convivía con un destino
        // al que ningún botón llevaba. Aquí se recorre el camino del estudiante, no el del navegador.
        // Hay que desplazarse primero: «Accesos» cae bajo el pliegue y la sonda midió el nodo en
        // Rect(0,0,0,0), así que el toque se emitía en el vacío y la ruta seguía en Home.
        rule.onNodeWithText("Mi perfil").performScrollTo().performClick()
        rule.waitForIdle()
        // El perfil creció con la fila de insignias: el botón ya no cabe en el primer cuadro, así que
        // la prueba exige que se pueda alcanzar, no que nazca visible.
        rule.onNodeWithText("Reiniciar el perfil local").performScrollTo().assertIsDisplayed()
        assertEquals(MaticaRoute.Profile, navigator.current)
    }

    @Test
    fun ajustesNoClasificanAQuienPracticaPorGradoEscolar() {
        rule.setContent { MaticaApp(session = session(), navigator = MaticaNavigator(MaticaRoute.Settings)) }
        rule.waitForIdle()
        // Una ruta de matemáticas no debe mostrar primaria/secundaria ni preguntar el grado.
        listOf("Grado", "1.º de secundaria", "2.º de secundaria", "3.º de secundaria").forEach { etiqueta ->
            rule.onAllNodes(hasText(etiqueta)).assertCountEquals(0)
        }
    }

    @Test
    fun logrosNoRepiteLasInsigniasDeLaCabecera() {
        val session = session()
        rule.setContent { MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Rewards)) }
        rule.waitForIdle()

        // Los chips exponen su valor en `contentDescription` y no en `Text` (el número que se pinta
        // es el interpolado de la animación); buscar texto exacto contaría cero y la prueba pasaría
        // por el motivo equivocado.
        val etiquetaRacha = "${session.streak} ${pluralEs(session.streak, "día", "días")}"
        listOf("${session.xp} XP", etiquetaRacha).forEach { etiqueta ->
            val veces = rule.onAllNodes(hasContentDescription(etiqueta)).fetchSemanticsNodes().size
            assertTrue(
                veces == 1,
                "«$etiqueta» aparece $veces veces en Logros: la pantalla pinta su propia fila de insignias " +
                    "encima de la de la cabecera y el estudiante ve el mismo contador dos veces a 40 px.",
            )
        }
    }

    /**
     * La insignia de la racha se escribió siempre en plural: con un día de racha la pantalla de Hoy
     * mostraba «1 días», y el estudiante leía un fallo del motor donde sólo había una etiqueta.
     */
    @Test
    fun laRachaConcuerdaConElNumeroQueMuestra() {
        val session = session()
        rule.setContent { MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Home)) }
        rule.waitForIdle()

        // Cero días: la racha todavía no tiene evidencia y el sustantivo va en plural. El chip lo
        // publica en `contentDescription`, no como texto: el número pintado es el de la animación.
        rule.onNode(hasContentDescription("0 días")).assertIsDisplayed()
        rule.onAllNodes(hasContentDescription("0 día")).assertCountEquals(0)

        // Un día de verdad: verificar la solución registra evidencia fechada hoy y la racha sube a 1.
        // No se escribe `session.streak = 1` porque el setter es privado a propósito: la racha sólo
        // crece con práctica, así que la prueba tiene que practicar.
        val plan = requireNotNull(session.startPractice("two-step-linear", 1L))
        session.checkAnswer(plan, requireNotNull(session.solutionOf(plan)))
        rule.waitForIdle()

        rule.onNode(hasContentDescription("1 día")).assertIsDisplayed()
        rule.onAllNodes(hasContentDescription("1 días")).assertCountEquals(0)
    }

    @Test
    fun elReproductorMuestraElVeredictoQueDioElMotor() {
        val session = session()
        val navigator = MaticaNavigator(MaticaRoute.LessonPlayer(SkillId.BALANCE_PRINCIPLE, 1))
        rule.setContent { MaticaApp(session = session, navigator = navigator) }
        rule.waitForIdle()

        rule.onNode(hasSetTextAction() and hasAnySibling(hasText("Siguiente línea")))
            .performTextInput("x = 4")
        rule.onNodeWithText("Comprobar paso").performClick()
        rule.waitForIdle()

        val verdict = requireNotNull(session.lastVerdict)
        // El texto en pantalla es el del motor: la UI no decide si el paso sirve.
        rule.onNodeWithText(verdictMessage(verdict).first).assertIsDisplayed()
        assertEquals(1, session.attempts.size)
    }

    @Test
    fun laRespuestaVerificadaEnPantallaCierraLaLeccion() {
        val skill = SkillId.BALANCE_PRINCIPLE
        val session = session()
        val plan = session.lessonsFor(skill).first { it.number == 1 }.plan
        val solution = requireNotNull(session.solutionOf(plan)).toString()
        rule.setContent { MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.LessonPlayer(skill, 1))) }
        rule.waitForIdle()

        rule.onNode(hasSetTextAction() and hasAnySibling(hasText("¿Cuánto vale ${plan.variable}?")))
            .performTextInput(solution)
        rule.onNodeWithText("Verificar por sustitución").performClick()
        rule.waitForIdle()

        // El cierre de lección lo titula la cabecera; «¡Lección completada!» se pinta dos veces
        // (subtítulo y cuerpo), así que se ancla al título, que aparece una sola vez.
        rule.onNodeWithText("¡EXCELENTE!").assertIsDisplayed()
        assertTrue(session.xp > 0, "Cerrar la lección sin XP dejaría la recompensa desconectada de la evidencia.")
    }

    @Test
    fun losAjustesCambianElTemaQueSePinta() {
        val session = session()
        rule.setContent { MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Settings)) }
        rule.waitForIdle()

        rule.onNodeWithText("Claro").performClick()
        rule.waitForIdle()
        assertCercaDe(
            MaticaPalette.Mist.toArgb(),
            cornerPixel(),
            "Con tema claro el fondo pintado no es MaticaPalette.Mist",
        )

        rule.onNodeWithText("Oscuro").performClick()
        rule.waitForIdle()
        assertCercaDe(
            MaticaPalette.Night.toArgb(),
            cornerPixel(),
            "Con tema oscuro el fondo pintado no es MaticaPalette.Night",
        )
    }

    /**
     * Sin servidor, ni el login ni el aula dibujan un formulario muerto: dicen en voz alta por qué no
     * pueden trabajar y ofrecen la salida que sí existe. Los dos campos del login desaparecían antes
     * dentro del `content` de `MaticaStateView` (sólo se compone en Success); ahora no se dibujan a
     * propósito y la prueba lo fija para que nadie los devuelva desactivados sin darse cuenta.
     */
    @Test
    fun losServiciosBloqueadosDicenQueFaltaUnaCuenta() {
        val session = session()
        var route by mutableStateOf<MaticaRoute>(MaticaRoute.Login)
        rule.setContent { MaticaApp(session = session, navigator = MaticaNavigator(route)) }
        rule.waitForIdle()

        rule.onNodeWithText("Cuentas no disponibles en esta versión").assertIsDisplayed()
        rule.onNodeWithText("Correo electrónico").assertDoesNotExist()
        rule.onNodeWithText("Practicar sin cuenta").performScrollTo().assertIsDisplayed()

        // El aula sí tiene red en esta prueba: el aviso tiene que ser que falta la cuenta, no la red.
        rule.runOnIdle {
            session.setConnection(true)
            route = MaticaRoute.Classroom
        }
        rule.waitForIdle()
        rule.onNodeWithText("El aula necesita una cuenta").assertIsDisplayed()
        rule.onNodeWithText("Código de invitación").assertDoesNotExist()
        rule.onNodeWithText("Entrar a mi cuenta").performScrollTo().assertIsDisplayed()
    }

    /**
     * El login no puede parecer un formulario roto: cuando la compilación no trae dirección de
     * servidor, la pantalla lo declara y deja seguir sin cuenta. El encabezado es semántico porque es
     * el título real de la pantalla, no un texto grande cualquiera.
     */
    @Test
    fun elLoginDeclaraQueEstaCompilacionNoTieneCuentas() {
        rule.setContent { MaticaApp(session = session(), navigator = MaticaNavigator(MaticaRoute.Login)) }
        rule.waitForIdle()

        rule.onNode(
            hasText("Entra a tu cuenta") and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        ).assertIsDisplayed()
        rule.onNodeWithText("Cuentas no disponibles en esta versión").assertIsDisplayed()
        rule.onNodeWithText("La práctica local funciona completa", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Contraseña").assertDoesNotExist()
        rule.onNodeWithText("Practicar sin cuenta").performScrollTo().assertIsDisplayed()
    }

    private fun cornerPixel(): Int =
        rule.onRoot().captureToImage().asAndroidBitmap().getPixel(4, 4)

    private fun assertCercaDe(expected: Int, actual: Int, message: String) {
        if (!mismoColor(expected, actual)) {
            throw AssertionError(
                "$message: esperado #${Integer.toHexString(expected)} real #${Integer.toHexString(actual)}",
            )
        }
    }
}

/** La hoja modal ocupa el 78 % de abajo, así que su cabecera no puede nacer por encima del 22 %. */
private const val FRACCION_SUPERIOR_DE_SCIM = 0.22f
