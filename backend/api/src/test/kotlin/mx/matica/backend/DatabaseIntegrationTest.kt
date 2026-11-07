package mx.matica.backend

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La única prueba que ejecuta el SQL de verdad. Sin PostgreSQL local, el resto de la suite valida
 * lógica y rutas con repositorios falsos; aquí Flyway aplica la migración y los repositorios JDBC
 * hablan con un Postgres efímero, así que un error de sintaxis o de tipo no pasa inadvertido.
 */
class DatabaseIntegrationTest {

    @Test
    fun migrationsAndJdbcRepositoriesWorkAgainstRealPostgres() {
        EmbeddedPostgres.builder().start().use { postgres ->
            val url = "postgresql://postgres:postgres@localhost:${postgres.port}/postgres"
            Database.open(url, maxPoolSize = 2).use { database ->
                database.migrate()

                val users = JdbcUserRepository(database)
                val attempts = JdbcAttemptRepository(database)
                val groups = JdbcGroupRepository(database)
                val devices = JdbcDeviceTokenRepository(database)

                val owner = users.create("docente@escuela.mx", "docente@escuela.mx", "Docente", "hash")
                assertNotNull(owner)
                assertNotNull(users.findByEmail("docente@escuela.mx"))
                assertNull(users.create("docente@escuela.mx", "docente@escuela.mx", null, "otro-hash"))

                val student = users.create("estudiante@escuela.mx", "estudiante@escuela.mx", "Estudiante", "hash")
                assertNotNull(student)

                val attempt = AttemptRecord(
                    id = UUID.randomUUID(),
                    userId = owner.id,
                    skillId = "integer-operations",
                    exerciseId = "integer-add-1",
                    before = "2+2",
                    after = "4",
                    verdict = "valid",
                    usedHint = false,
                    occurredAt = Instant.parse("2026-11-06T11:55:00Z"),
                )
                assertEquals(AttemptInsert.Created, attempts.insert(attempt))
                assertEquals(AttemptInsert.Duplicate, attempts.insert(attempt))
                assertEquals(1, attempts.list(owner.id, null, 200).size)
                assertEquals(0, attempts.list(student.id, null, 200).size)

                val group = groups.create(owner.id, "Álgebra 2A", "ABC234")
                assertNotNull(group)
                assertNull(groups.create(student.id, "Otro", "ABC234"))
                assertTrue(groups.addMember(group.id, student.id))
                assertEquals("student", groups.membership(group.id, student.id)?.role)
                assertEquals(2, groups.members(group.id).size)
                assertEquals(1, groups.listFor(student.id).size)
                assertEquals(2, groups.findById(group.id)?.memberCount)

                devices.upsert(owner.id, "token-uno", "android")
                devices.upsert(owner.id, "token-uno", "android")
                assertEquals(listOf("token-uno"), devices.tokensFor(owner.id))
            }
        }
    }
}
