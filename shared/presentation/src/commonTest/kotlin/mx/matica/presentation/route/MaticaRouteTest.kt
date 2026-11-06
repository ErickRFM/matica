package mx.matica.presentation.route

import mx.matica.exercises.ExerciseTemplates
import mx.matica.math.skills.SkillId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaticaRouteTest {

    @Test
    fun elInventarioTieneDieciochoDestinos() {
        assertEquals(18, MaticaRoute.all.size)
        assertEquals(18, MaticaRoute.all.map { it.path }.distinct().size, "Dos rutas comparten dirección.")
    }

    @Test
    fun todaRutaSobreviveAlViajePorSuPath() {
        MaticaRoute.all.forEach { route ->
            assertEquals(route, MaticaRoute.parse(route.path), "La ruta ${route.javaClass.simpleName} no se recupera.")
        }
    }

    @Test
    fun unSkillDesconocidoNoSeConvierteEnRuta() {
        assertNull(MaticaRoute.parse("route/skill/NO_EXISTE"))
        assertNull(MaticaRoute.parse("lesson/NO_EXISTE/1"))
    }

    @Test
    fun losNumericosMalFormadosSeRechazan() {
        assertNull(MaticaRoute.parse("lesson/home/x"))
        assertNull(MaticaRoute.parse("lesson/home/1/attempt/abc"))
        assertNull(MaticaRoute.parse("practice/two-step-linear/semilla"))
    }

    @Test
    fun unaPlantillaInventadaNoAbreLaPractica() {
        assertNull(MaticaRoute.parse("practice/plantilla-magic/12"))
        assertNotNull(MaticaRoute.parse("practice/fraction-linear/12"))
    }

    /**
     * La ruta de práctica conoce exactamente las plantillas que existen. Escrito a mano, este conjunto
     * quedó obsoleto cuando el currículo añadió `integer-ops` y la plantilla dejó de ser alcanzable por
     * enlace; la aserción es el seguro contra ese olvido.
     */
    @Test
    fun lasPlantillasConocidasSonLasDelCatalogo() {
        val catalogIds = ExerciseTemplates.all.map { it.id }.toSet()
        assertEquals(catalogIds, MaticaRoute.KNOWN_TEMPLATES)
        catalogIds.forEach { id ->
            assertEquals(
                MaticaRoute.Practice(id, 12L),
                MaticaRoute.parse("practice/$id/12"),
                "El catálogo tiene '$id' pero la ruta la rechaza.",
            )
        }
    }

    @Test
    fun lasRutasExtrañasNoSonDestinos() {
        listOf("", "/", "no-se", "home/extra", "diagnosis/otra", "route/a/b/c").forEach {
            assertNull(MaticaRoute.parse(it), "'$it' no debería producir ruta.")
        }
    }

    @Test
    fun soloLaCapaDePistasEsSuperpuesta() {
        assertTrue(MaticaRoute.HintSheet(SkillId.BALANCE_PRINCIPLE, 1).isOverlay)
        assertFalse(MaticaRoute.LessonComplete(SkillId.BALANCE_PRINCIPLE, 1).isOverlay)
        assertFalse(MaticaRoute.Home.isOverlay)
    }

    @Test
    fun elPuntoDeEntradaDependeDeLoQueYaSeTiene() {
        assertEquals(MaticaRoute.Diagnosis, MaticaRoute.entryPoint(hasProfile = true, diagnosed = false))
        assertEquals(MaticaRoute.Login, MaticaRoute.entryPoint(hasProfile = false, diagnosed = true))
        assertEquals(MaticaRoute.Home, MaticaRoute.entryPoint(hasProfile = true, diagnosed = true))
    }

    @Test
    fun lasRutasDeLeccionSeAnidanUnasSobreOtras() {
        val skill = SkillId.FRACTION_ARITHMETIC
        val lesson = MaticaRoute.LessonPlayer(skill, 3)
        assertTrue(MaticaRoute.HintSheet(skill, 3).path.startsWith(lesson.path))
        assertTrue(MaticaRoute.LessonComplete(skill, 3).path.startsWith(lesson.path))
        assertTrue(MaticaRoute.AttemptReview(skill, 3, 2).path.startsWith(lesson.path))
    }
}

class MaticaNavigatorTest {

    @Test
    fun arrancaEnLaRutaInicial() {
        val navigator = MaticaNavigator(MaticaRoute.Welcome)
        assertEquals(MaticaRoute.Welcome, navigator.current)
        assertEquals(1, navigator.depth)
        assertFalse(navigator.canGoBack)
    }

    @Test
    fun irAlMismoDestinoNoDuplicaLaPila() {
        val navigator = MaticaNavigator(MaticaRoute.Home)
        navigator.go(MaticaRoute.Rewards)
        navigator.go(MaticaRoute.Rewards)
        assertEquals(2, navigator.depth)
    }

    @Test
    fun laCapaDePistasRegresaAlReproductor() {
        val skill = SkillId.BALANCE_PRINCIPLE
        val player = MaticaRoute.LessonPlayer(skill, 1)
        val navigator = MaticaNavigator(MaticaRoute.Home)
        navigator.go(player)
        navigator.go(MaticaRoute.HintSheet(skill, 1))
        assertEquals(MaticaRoute.HintSheet(skill, 1), navigator.current)
        assertTrue(navigator.goBack())
        assertEquals(player, navigator.current)
    }

    @Test
    fun laCapaAbiertaNoMueveLaBaseQueDibujaElHost() {
        val skill = SkillId.BALANCE_PRINCIPLE
        val player = MaticaRoute.LessonPlayer(skill, 1)
        val navigator = MaticaNavigator(MaticaRoute.Home)
        navigator.go(player)
        assertEquals(player, navigator.base)
        assertEquals(2, navigator.baseDepth)

        navigator.go(MaticaRoute.HintSheet(skill, 1))
        assertEquals(MaticaRoute.HintSheet(skill, 1), navigator.current, "La hoja es la pantalla visible.")
        assertEquals(player, navigator.base, "El host debe seguir dibujando el reproductor debajo.")
        assertEquals(2, navigator.baseDepth, "La capa modal no cuenta para la dirección de la transición.")

        assertTrue(navigator.goBack())
        assertEquals(player, navigator.base)
    }

    @Test
    fun backDesdeLaRaizDevuelveFalse() {
        val navigator = MaticaNavigator(MaticaRoute.Home)
        assertFalse(navigator.goBack())
        assertEquals(MaticaRoute.Home, navigator.current)
    }

    @Test
    fun replaceNoCrecelaPila() {
        val navigator = MaticaNavigator(MaticaRoute.Diagnosis)
        navigator.replace(MaticaRoute.DiagnosisResult)
        assertEquals(1, navigator.depth)
        assertEquals(MaticaRoute.DiagnosisResult, navigator.current)
        assertFalse(navigator.canGoBack, "El diagnóstico ya no debería ser alcanzable por back.")
    }

    @Test
    fun popToDescartaLoQueQuedaEncima() {
        val navigator = MaticaNavigator(MaticaRoute.Home)
        navigator.go(MaticaRoute.RouteMap)
        navigator.go(MaticaRoute.Profile)
        assertTrue(navigator.popTo(MaticaRoute.RouteMap))
        assertEquals(MaticaRoute.RouteMap, navigator.current)
        assertEquals(2, navigator.depth)
        assertFalse(navigator.popTo(MaticaRoute.Rewards))
    }

    @Test
    fun laPilaSeSerializaYSeRecuperaExacta() {
        val navigator = MaticaNavigator(MaticaRoute.Home)
        navigator.go(MaticaRoute.RouteMap)
        navigator.go(MaticaRoute.SkillDetail(SkillId.SIGN_HANDLING))
        navigator.go(MaticaRoute.Practice("distributive-linear", 9_000L))
        val saved = navigator.asPaths()

        val restored = MaticaNavigator(MaticaRoute.Welcome)
        assertTrue(restored.restore(saved))
        assertEquals(navigator.current, restored.current)
        assertEquals(saved, restored.asPaths())
    }

    @Test
    fun unHistorialCorruptoSeLimpiaSinRomperLaApp() {
        val navigator = MaticaNavigator(MaticaRoute.Home)
        assertFalse(navigator.restore(listOf("/ruta/inventada", "home/extra")))
        assertNotNull(navigator.lastError)
        assertEquals(1, navigator.depth)
    }

    @Test
    fun unHistorialParcialmenteValidoConservaLoBueno() {
        val navigator = MaticaNavigator(MaticaRoute.Home)
        assertTrue(navigator.restore(listOf("malo", MaticaRoute.ReviewQueue.path, MaticaRoute.Profile.path)))
        assertEquals(listOf(MaticaRoute.ReviewQueue.path, MaticaRoute.Profile.path), navigator.asPaths())
        assertEquals(MaticaRoute.Profile, navigator.current)
    }

    @Test
    fun unEnlaceRotoSeReportaEnLugarDeNavegar() {
        val navigator = MaticaNavigator(MaticaRoute.Home)
        assertNull(navigator.acceptsLink("lesson/BOGUS/1"))
        assertNotNull(navigator.lastError)
        assertTrue(navigator.lastError!!.contains("lesson/BOGUS/1"))
    }

    @Test
    fun resetToDejaUnaPilaDeUnElemento() {
        val navigator = MaticaNavigator(MaticaRoute.Home)
        navigator.go(MaticaRoute.Settings)
        navigator.resetTo(MaticaRoute.Welcome)
        assertEquals(1, navigator.depth)
        assertEquals(MaticaRoute.Welcome, navigator.current)
    }
}
