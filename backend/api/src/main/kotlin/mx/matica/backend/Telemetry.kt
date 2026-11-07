package mx.matica.backend

import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
data class TelemetryEventDto(
    val id: String? = null,
    val eventType: String,
    val skillId: String? = null,
    val exerciseId: String? = null,
    val dwellMs: Long? = null,
    val metadata: Map<String, String> = emptyMap(),
    val recordedAt: String? = null,
)

@Serializable
data class TelemetryBatchRequest(
    val events: List<TelemetryEventDto> = emptyList(),
)

@Serializable
data class TelemetryResponse(
    val accepted: Int,
    val serverTime: String,
)

internal interface TelemetryRepository {
    fun recordBatch(userId: UUID?, events: List<TelemetryEventDto>): Int
    fun listForUser(userId: UUID, limit: Int = 100): List<TelemetryEventDto>
}

internal class JdbcTelemetryRepository(private val database: Database) : TelemetryRepository {
    override fun recordBatch(userId: UUID?, events: List<TelemetryEventDto>): Int = database.withConnection { connection ->
        if (events.isEmpty()) return@withConnection 0
        val sql = """
            insert into telemetry_events (id, user_id, event_type, skill_id, exercise_id, dwell_ms, metadata, recorded_at)
            values (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            for (event in events) {
                val id = event.id?.let { try { UUID.fromString(it) } catch (_: Exception) { null } } ?: UUID.randomUUID()
                val recordedAt = event.recordedAt?.let { parseTime(it) } ?: Instant.now()
                val metaJson = Json.encodeToString(MapSerializer(String.serializer(), String.serializer()), event.metadata)

                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                stmt.setString(3, event.eventType)
                stmt.setString(4, event.skillId)
                stmt.setString(5, event.exerciseId)
                if (event.dwellMs != null) stmt.setLong(6, event.dwellMs) else stmt.setNull(6, java.sql.Types.BIGINT)
                stmt.setString(7, metaJson)
                stmt.setTimestamp(8, Timestamp.from(recordedAt))
                stmt.addBatch()
            }
            val counts = stmt.executeBatch()
            counts.sumOf { if (it >= 0) 1 else 0 }
        }
    }

    override fun listForUser(userId: UUID, limit: Int): List<TelemetryEventDto> = database.withConnection { connection ->
        connection.prepareStatement(
            "select id, event_type, skill_id, exercise_id, dwell_ms, recorded_at from telemetry_events where user_id = ? order by recorded_at desc limit ?",
        ).use { stmt ->
            stmt.setObject(1, userId)
            stmt.setInt(2, limit)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            TelemetryEventDto(
                                id = rs.getObject("id", UUID::class.java).toString(),
                                eventType = rs.getString("event_type"),
                                skillId = rs.getString("skill_id"),
                                exerciseId = rs.getString("exercise_id"),
                                dwellMs = rs.getLong("dwell_ms").takeIf { !rs.wasNull() },
                                recordedAt = rs.getTimestamp("recorded_at")?.toInstant()?.toString(),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun parseTime(raw: String): Instant? = try {
        OffsetDateTime.parse(raw).toInstant()
    } catch (_: Exception) {
        try {
            Instant.parse(raw)
        } catch (_: Exception) {
            null
        }
    }
}

internal class InMemoryTelemetryRepository : TelemetryRepository {
    private val events = ConcurrentLinkedQueue<Pair<UUID?, TelemetryEventDto>>()

    override fun recordBatch(userId: UUID?, events: List<TelemetryEventDto>): Int {
        for (event in events) {
            this.events.add(userId to event)
        }
        return events.size
    }

    override fun listForUser(userId: UUID, limit: Int): List<TelemetryEventDto> =
        events.filter { it.first == userId }.map { it.second }.take(limit)
}
