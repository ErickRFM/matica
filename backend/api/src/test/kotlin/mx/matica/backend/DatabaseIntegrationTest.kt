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

                val gamification = JdbcGamificationRepository(database)

                val initialSummary = gamification.getSummary(student.id)
                assertEquals(0, initialSummary.streak)
                assertEquals(0, initialSummary.longestStreak)
                assertEquals(2, initialSummary.freezeCredits)
                assertEquals(0, initialSummary.totalXp)
                assertEquals(0, initialSummary.weeklyXp)
                assertEquals(1, initialSummary.level)
                assertNull(initialSummary.lastActiveDate)

                val att1 = UUID.randomUUID()
                attempts.insert(
                    AttemptRecord(
                        id = att1,
                        userId = student.id,
                        skillId = "integer-operations",
                        exerciseId = "ex-1",
                        before = "1+1",
                        after = "2",
                        verdict = "valid",
                        usedHint = false,
                        occurredAt = Instant.parse("2026-11-01T12:00:00Z"),
                    ),
                )
                val r1 = gamification.awardAttempt(
                    userId = student.id,
                    attemptId = att1,
                    verdict = "valid",
                    usedHint = false,
                    occurredAt = Instant.parse("2026-11-01T12:00:00Z"),
                    timezone = "UTC",
                )
                assertEquals(15, r1.xpAwarded)
                assertEquals(1, r1.streak)
                assertEquals(false, r1.freezeUsed)

                val att2 = UUID.randomUUID()
                attempts.insert(
                    AttemptRecord(
                        id = att2,
                        userId = student.id,
                        skillId = "integer-operations",
                        exerciseId = "ex-2",
                        before = "2+2",
                        after = "4",
                        verdict = "valid",
                        usedHint = true,
                        occurredAt = Instant.parse("2026-11-01T14:00:00Z"),
                    ),
                )
                val r2 = gamification.awardAttempt(
                    userId = student.id,
                    attemptId = att2,
                    verdict = "valid",
                    usedHint = true,
                    occurredAt = Instant.parse("2026-11-01T14:00:00Z"),
                    timezone = "UTC",
                )
                assertEquals(10, r2.xpAwarded)
                assertEquals(1, r2.streak)
                assertEquals(false, r2.freezeUsed)

                val att3 = UUID.randomUUID()
                attempts.insert(
                    AttemptRecord(
                        id = att3,
                        userId = student.id,
                        skillId = "integer-operations",
                        exerciseId = "ex-3",
                        before = "3+3",
                        after = "5",
                        verdict = "invalid",
                        usedHint = false,
                        occurredAt = Instant.parse("2026-11-02T10:00:00Z"),
                    ),
                )
                val r3 = gamification.awardAttempt(
                    userId = student.id,
                    attemptId = att3,
                    verdict = "invalid",
                    usedHint = false,
                    occurredAt = Instant.parse("2026-11-02T10:00:00Z"),
                    timezone = "UTC",
                )
                assertEquals(2, r3.xpAwarded)
                assertEquals(2, r3.streak)
                assertEquals(false, r3.freezeUsed)

                val att4 = UUID.randomUUID()
                attempts.insert(
                    AttemptRecord(
                        id = att4,
                        userId = student.id,
                        skillId = "integer-operations",
                        exerciseId = "ex-4",
                        before = "4+4",
                        after = "8",
                        verdict = "valid",
                        usedHint = false,
                        occurredAt = Instant.parse("2026-11-04T12:00:00Z"),
                    ),
                )
                val r4 = gamification.awardAttempt(
                    userId = student.id,
                    attemptId = att4,
                    verdict = "valid",
                    usedHint = false,
                    occurredAt = Instant.parse("2026-11-04T12:00:00Z"),
                    timezone = "UTC",
                )
                assertEquals(15, r4.xpAwarded)
                assertEquals(3, r4.streak)
                assertEquals(true, r4.freezeUsed)

                val summaryAfterFreeze = gamification.getSummary(student.id)
                assertEquals(3, summaryAfterFreeze.streak)
                assertEquals(3, summaryAfterFreeze.longestStreak)
                assertEquals(1, summaryAfterFreeze.freezeCredits)
                assertEquals(42, summaryAfterFreeze.totalXp)

                val att5 = UUID.randomUUID()
                attempts.insert(
                    AttemptRecord(
                        id = att5,
                        userId = student.id,
                        skillId = "integer-operations",
                        exerciseId = "ex-5",
                        before = "5+5",
                        after = "10",
                        verdict = "valid",
                        usedHint = false,
                        occurredAt = Instant.parse("2026-11-07T12:00:00Z"),
                    ),
                )
                val r5 = gamification.awardAttempt(
                    userId = student.id,
                    attemptId = att5,
                    verdict = "valid",
                    usedHint = false,
                    occurredAt = Instant.parse("2026-11-07T12:00:00Z"),
                    timezone = "UTC",
                )
                assertEquals(15, r5.xpAwarded)
                assertEquals(1, r5.streak)
                assertEquals(false, r5.freezeUsed)

                val summaryAfterReset = gamification.getSummary(student.id)
                assertEquals(1, summaryAfterReset.streak)
                assertEquals(3, summaryAfterReset.longestStreak)
                assertEquals(1, summaryAfterReset.freezeCredits)
                assertEquals(57, summaryAfterReset.totalXp)

                val failedClaim = gamification.claimFreeze(student.id)
                assertNotNull(failedClaim)
                assertEquals(false, failedClaim.success)
                assertEquals(1, failedClaim.freezeCredits)
                assertEquals(57, failedClaim.remainingXp)

                repeat(3) { i ->
                    val extraId = UUID.randomUUID()
                    attempts.insert(
                        AttemptRecord(
                            id = extraId,
                            userId = student.id,
                            skillId = "integer-operations",
                            exerciseId = "ex-extra-$i",
                            before = "x",
                            after = "y",
                            verdict = "valid",
                            usedHint = false,
                            occurredAt = Instant.parse("2026-11-07T13:0$i:00Z"),
                        ),
                    )
                    gamification.awardAttempt(
                        userId = student.id,
                        attemptId = extraId,
                        verdict = "valid",
                        usedHint = false,
                        occurredAt = Instant.parse("2026-11-07T13:0$i:00Z"),
                        timezone = "UTC",
                    )
                }

                val summaryBeforeClaim = gamification.getSummary(student.id)
                assertEquals(102, summaryBeforeClaim.totalXp)
                assertEquals(2, summaryBeforeClaim.level)

                val successClaim = gamification.claimFreeze(student.id)
                assertNotNull(successClaim)
                assertEquals(true, successClaim.success)
                assertEquals(2, successClaim.freezeCredits)
                assertEquals(2, successClaim.remainingXp)

                val summaryAfterClaim = gamification.getSummary(student.id)
                assertEquals(2, summaryAfterClaim.freezeCredits)
                assertEquals(2, summaryAfterClaim.totalXp)
                assertEquals(1, summaryAfterClaim.level)

                val ledgerCount = database.withConnection { conn ->
                    conn.prepareStatement("select count(*) from reward_ledger where user_id = ?").use { stmt ->
                        stmt.setObject(1, student.id)
                        stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                    }
                }
                assertEquals(9, ledgerCount)
            }
        }
    }
}
