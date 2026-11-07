package mx.matica.app

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import mx.matica.app.db.MaticaDatabase
import org.junit.jupiter.api.Test

/** La cola de intentos, ejecutada sobre SQLite real en memoria: no hay aserciones sobre mocks. */
class PendingAttemptsTest {

    private fun database(): MaticaDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MaticaDatabase.Schema.create(driver)
        return MaticaDatabase(driver)
    }

    private fun MaticaDatabase.insert(id: String, user: String, created: Int) {
        pendingAttemptsQueries.insertPending(
            id = id,
            user_id = user,
            skill_id = "integer-operations",
            exercise_id = "ex-1",
            before_text = "2+2",
            after_text = "4",
            verdict = "valid",
            used_hint = 0L,
            occurred_at = "2026-11-06T12:00:00Z",
            created_at = created.toLong(),
        )
    }

    @Test
    fun queueIsPerUserAndKeepsArrivalOrder() {
        val db = database()
        db.insert("a", "ana", 1)
        db.insert("b", "ana", 2)
        db.insert("c", "beto", 3)

        val queue = db.pendingAttemptsQueries.selectPendingForUser("ana", 25).executeAsList()

        assertEquals(listOf("a", "b"), queue.map { it.id })
        assertEquals(2L, db.pendingAttemptsQueries.countPendingForUser("ana").executeAsOne())
        assertEquals(1L, db.pendingAttemptsQueries.countPendingForUser("beto").executeAsOne())
    }

    @Test
    fun insertIsIdempotentByAttemptId() {
        val db = database()
        db.insert("a", "ana", 1)
        db.insert("a", "ana", 2)

        assertEquals(1L, db.pendingAttemptsQueries.countPendingForUser("ana").executeAsOne())
    }

    @Test
    fun deletingSentAttemptLeavesTheRest() {
        val db = database()
        db.insert("a", "ana", 1)
        db.insert("b", "ana", 2)

        db.pendingAttemptsQueries.deletePending("a")

        assertEquals(listOf("b"), db.pendingAttemptsQueries.selectPendingForUser("ana", 25).executeAsList().map { it.id })
    }

    @Test
    fun pruneDropsOnlyOldRows() {
        val db = database()
        db.insert("viejo", "ana", 1)
        db.insert("nuevo", "ana", 10)

        db.pendingAttemptsQueries.pruneOlderThan(5)

        val remaining = db.pendingAttemptsQueries.selectPendingForUser("ana", 25).executeAsList().map { it.id }
        assertEquals(listOf("nuevo"), remaining)
    }

    @Test
    fun limitCapsTheBatch() {
        val db = database()
        repeat(30) { index -> db.insert("a-$index", "ana", index) }

        val batch = db.pendingAttemptsQueries.selectPendingForUser("ana", 25).executeAsList()

        assertEquals(25, batch.size)
        assertTrue(batch.first().id == "a-0")
    }
}
