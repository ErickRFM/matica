package mx.matica.backend

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiTest {
    @Test
    fun topicsAreNamedWithoutSchoolGrades() = testApplication {
        application { maticaModule(databaseReady = { true }) }
        val response = client.get("/v1/topics")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Operaciones con enteros"))
        assertTrue(body.contains("\"version\":1"))
        listOf("primaria", "secundaria", "preparatoria", "bachillerato", "licenciatura", "universitario")
            .forEach { assertFalse(body.contains(it, ignoreCase = true), "Se filtró la etiqueta: $it") }
    }

    @Test
    fun healthIsNotDatabaseReadiness() = testApplication {
        application { maticaModule(databaseReady = { false }) }
        assertEquals(HttpStatusCode.OK, client.get("/healthz").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/readyz").status)
    }

    @Test
    fun readinessRequiresRealDatabase() = testApplication {
        application { maticaModule(databaseReady = { true }) }
        assertEquals(HttpStatusCode.OK, client.get("/readyz").status)
    }

    @Test
    fun missingDatabaseNeverMeansReady() {
        assertFalse(PostgresProbe.ready(null))
        assertFalse(PostgresProbe.ready(""))
    }

    @Test
    fun renderDatabaseUrlSupportsEncodedCredentials() {
        val details = PostgresProbe.details("postgresql://matica:p%40ss%2Bword@db.internal:5432/matica")
        assertEquals("jdbc:postgresql://db.internal:5432/matica", details.jdbcUrl)
        assertEquals("matica", details.properties.getProperty("user"))
        assertEquals("p@ss+word", details.properties.getProperty("password"))
    }
}
