package mx.matica.backend

import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class SyncStateRequest(
    val lastSyncedAt: String? = null,
    val pendingAttempts: List<AttemptRequest> = emptyList(),
)

@Serializable
data class SyncStateResponse(
    val serverTime: String,
    val skillsMastery: Map<String, String>,
    val totalXp: Int,
    val currentStreak: Int,
    val freezeCredits: Int,
)

typealias LearnerStateDto = SyncStateResponse

internal class StateSyncService(
    private val attempts: AttemptRepository,
    private val gamification: GamificationRepository?,
    private val classroom: ClassroomRepository?,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun sync(userId: UUID, request: SyncStateRequest, timezone: String? = null): SyncStateResponse {
        for (item in request.pendingAttempts) {
            val id = item.id.toUuidOrNull() ?: continue
            val occurredAt = item.occurredAt.toInstantOrNull() ?: continue
            if (!Validation.validSkill(item.skillId)) continue
            if (!Validation.validVerdict(item.verdict)) continue

            val record = AttemptRecord(
                id = id,
                userId = userId,
                skillId = item.skillId,
                exerciseId = item.exerciseId,
                before = item.before,
                after = item.after,
                verdict = item.verdict,
                usedHint = item.usedHint,
                occurredAt = occurredAt,
            )

            val outcome = attempts.insert(record)
            if (outcome is AttemptInsert.Created) {
                gamification?.awardAttempt(
                    userId = userId,
                    attemptId = id,
                    verdict = record.verdict,
                    usedHint = record.usedHint,
                    occurredAt = record.occurredAt,
                    timezone = timezone,
                )
                classroom?.recordAttempt(userId, record.skillId, record.verdict)
            }
        }

        val allAttempts = attempts.list(userId, since = null, limit = 500)
        val skillsMastery = computeMastery(allAttempts)

        val summary = gamification?.getSummary(userId)

        return SyncStateResponse(
            serverTime = clock.instant().toString(),
            skillsMastery = skillsMastery,
            totalXp = summary?.totalXp ?: 0,
            currentStreak = summary?.streak ?: 0,
            freezeCredits = summary?.freezeCredits ?: 0,
        )
    }

    private fun computeMastery(attempts: List<AttemptRecord>): Map<String, String> {
        val grouped = attempts.groupBy { it.skillId }
        return grouped.mapValues { (_, skillAttempts) ->
            val total = skillAttempts.size
            val valid = skillAttempts.count { it.verdict == "valid" }
            val rate = if (total > 0) valid.toDouble() / total else 0.0
            when {
                valid >= 3 || rate >= 0.8 -> "proficient"
                total >= 2 && rate < 0.4 -> "struggling"
                total >= 1 -> "practiced"
                else -> "novice"
            }
        }
    }

    private fun String.toUuidOrNull(): UUID? = try {
        UUID.fromString(this)
    } catch (_: Exception) {
        null
    }

    private fun String.toInstantOrNull(): Instant? = try {
        OffsetDateTime.parse(this).toInstant()
    } catch (_: Exception) {
        try {
            Instant.parse(this)
        } catch (_: Exception) {
            null
        }
    }
}
