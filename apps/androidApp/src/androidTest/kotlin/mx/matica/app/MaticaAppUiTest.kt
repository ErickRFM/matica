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
        rule.onNodeWithText("Mi perfil").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Reiniciar el perfil local").assertIsDisplayed()
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

        // `onAllNodes` con el texto exacto: las marcas del apartado de abajo también llevan «XP»
        // («Primer bloque de práctica · 60 XP»), así que buscar la subcadena contaría tres y la
        // duplicación de la cabecera quedaría tapada por el propio listado.
        listOf("${session.xp} XP", "${session.streak} días").forEach { etiqueta ->
            val veces = rule.onAllNodes(hasText(etiqueta)).fetchSemanticsNodes().size
            assertTrue(
                veces == 1,
                "«$etiqueta» aparece $veces veces en Logros: la pantalla pinta su propia fila de insignias " +
                    "encima de la de la cabecera y el estudiante ve el mismo contador dos veces a 40 px.",
            )
        }
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

        rule.onNodeWithText("Lección terminada").assertIsDisplayed()
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
     * El formulario de un servicio bloqueado tiene que verse aunque el estado diga que no hay datos:
     * anidado dentro del `content` de `MaticaStateView` sólo se compone en Success, y las dos pantallas
     * bloqueadas quedaban con el campo invisible (así lo cazó la instrumentación al navegar hasta
     * «Correo electrónico»).
     */
    @Test
    fun losServiciosBloqueadosMuestranSuFormularioDesactivado() {
        var route by mutableStateOf<MaticaRoute>(MaticaRoute.Login)
        rule.setContent { MaticaApp(session = session(), navigator = MaticaNavigator(route)) }
        rule.waitForIdle()

        listOf(
            Triple(MaticaRoute.Login, "Correo electrónico", "Desactivado hasta que exista el servicio."),
            Triple(MaticaRoute.Classroom, "Código de invitación", "Desactivado: no hay servidor que lo valide."),
        ).forEach { (destino, etiqueta, aviso) ->
            rule.runOnIdle { route = destino }
            rule.waitForIdle()
            rule.onNodeWithText(etiqueta).assertIsDisplayed()
            rule.onNodeWithText(aviso).assertIsDisplayed()
        }
    }

    /**
     * El login no puede parecer un formulario roto: la pantalla tiene que decir en voz alta que las
     * cuentas aún no existen y que los dos campos están desactivados a propósito. El encabezado es
     * semántico porque es el título real de la pantalla, no un texto grande cualquiera.
     */
    @Test
    fun elLoginExplicaQueLasCuentasEstanEnPreparacion() {
        rule.setContent { MaticaApp(session = session(), navigator = MaticaNavigator(MaticaRoute.Login)) }
        rule.waitForIdle()

        rule.onNode(
            hasText("Entra a tu cuenta") and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        ).assertIsDisplayed()
        rule.onNodeWithText("Cuentas en preparación").assertIsDisplayed()
        rule.onNodeWithText("Correo electrónico").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Contraseña").performScrollTo().assertIsDisplayed()
        // Las etiquetas son nodos de texto sueltos; el estado desactivado vive en los dos campos.
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)).assertCountEquals(2)
        rule.onNodeWithText("Desactivado hasta que exista el servicio.").assertIsDisplayed()
        rule.onNodeWithText("Seguir con perfil local").performScrollTo().assertIsDisplayed()
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
