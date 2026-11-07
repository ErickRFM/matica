package mx.matica.presentation.app

import mx.matica.exercises.WordProblemRepository
import mx.matica.presentation.route.MaticaRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WordProblemScreenTest {

    @Test
    fun rutaWordProblemSeParseaYReconstruye() {
        val problem = WordProblemRepository.all.first()
        val route = MaticaRoute.WordProblem(problem.id)
        assertEquals("word-problem/${problem.id}", route.path)

        val parsed = MaticaRoute.parse(route.path)
        assertNotNull(parsed)
        assertTrue(parsed is MaticaRoute.WordProblem)
        assertEquals(problem.id, parsed.problemId)
    }

    @Test
    fun rutaWordProblemEstaEnInventario() {
        assertEquals(21, MaticaRoute.all.size)
        assertNotNull(MaticaRoute.all.firstOrNull { it is MaticaRoute.WordProblem })
    }
}
