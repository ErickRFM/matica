package mx.matica.backend

import java.sql.Date
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.Serializable

internal data class UserStreakRecord(
    val userId: UUID,
    val currentStreak: Int,
    val longestStreak: Int,
    val lastActiveDate: LocalDate?,
    val freezeCredits: Int,
)

internal data class UserExperienceRecord(
    val userId: UUID,
    val totalXp: Int,
    val weeklyXp: Int,
    val level: Int,
)

@Serializable
data class GamificationReward(
    val xpAwarded: Int,
    val streak: Int,
    val freezeUsed: Boolean,
)

@Serializable
data class GamificationSummaryDto(
    val streak: Int,
    val longestStreak: Int,
    val freezeCredits: Int,
    val totalXp: Int,
    val weeklyXp: Int,
    val level: Int,
    val lastActiveDate: String?,
)

@Serializable
data class ClaimFreezeResponse(
    val success: Boolean,
    val freezeCredits: Int,
    val remainingXp: Int,
)

internal interface GamificationRepository {
    fun getSummary(userId: UUID): GamificationSummaryDto
    fun awardAttempt(
        userId: UUID,
        attemptId: UUID,
        verdict: String,
        usedHint: Boolean,
        occurredAt: Instant,
        timezone: String?,
    ): GamificationReward
    fun claimFreeze(userId: UUID): ClaimFreezeResponse?
}

internal object GamificationRules {
    const val FREEZE_COST_XP = 100
    const val INITIAL_FREEZE_CREDITS = 2

    fun calculateXp(verdict: String, usedHint: Boolean): Int = when {
        verdict == "valid" && !usedHint -> 15
        verdict == "valid" && usedHint -> 10
        verdict in setOf("invalid", "syntax") -> 2
        else -> 0
    }

    fun xpReason(verdict: String, usedHint: Boolean): String = when {
        verdict == "valid" && !usedHint -> "attempt_completed"
        verdict == "valid" && usedHint -> "attempt_with_hint"
        verdict in setOf("invalid", "syntax") -> "attempt_effort"
        else -> "attempt_other"
    }

    fun calculateLevel(totalXp: Int): Int = 1 + (totalXp / 100)

    data class StreakUpdate(
        val streak: Int,
        val longestStreak: Int,
        val freezeCredits: Int,
        val freezeUsed: Boolean,
        val lastActiveDate: LocalDate,
    )

    fun calculateStreak(
        currentStreak: Int,
        longestStreak: Int,
        lastActiveDate: LocalDate?,
        freezeCredits: Int,
        today: LocalDate,
    ): StreakUpdate {
        val yesterday = today.minusDays(1)
        val twoDaysAgo = today.minusDays(2)

        var newStreak: Int
        var newLongest: Int
        var newFreeze = freezeCredits
        var freezeUsed = false

        when {
            lastActiveDate == null -> {
                newStreak = 1
                newLongest = maxOf(longestStreak, 1)
            }
            lastActiveDate == today || lastActiveDate.isAfter(today) -> {
                newStreak = if (currentStreak == 0) 1 else currentStreak
                newLongest = maxOf(longestStreak, newStreak)
            }
            lastActiveDate == yesterday -> {
                newStreak = currentStreak + 1
                newLongest = maxOf(longestStreak, newStreak)
            }
            lastActiveDate == twoDaysAgo && freezeCredits > 0 -> {
                newStreak = currentStreak + 1
                newLongest = maxOf(longestStreak, newStreak)
                newFreeze = freezeCredits - 1
                freezeUsed = true
            }
            else -> {
                newStreak = 1
                newLongest = maxOf(longestStreak, 1)
            }
        }

        val finalActiveDate = if (lastActiveDate != null && lastActiveDate.isAfter(today)) lastActiveDate else today

        return StreakUpdate(
            streak = newStreak,
            longestStreak = newLongest,
            freezeCredits = newFreeze,
            freezeUsed = freezeUsed,
            lastActiveDate = finalActiveDate,
        )
    }

    fun parseZone(timezone: String?): ZoneId = try {
        if (!timezone.isNullOrBlank()) ZoneId.of(timezone.trim()) else ZoneOffset.UTC
    } catch (_: Exception) {
        ZoneOffset.UTC
    }
}

internal class JdbcGamificationRepository(private val database: Database) : GamificationRepository {

    override fun getSummary(userId: UUID): GamificationSummaryDto = database.withConnection { connection ->
        var streak = 0
        var longestStreak = 0
        var freezeCredits = GamificationRules.INITIAL_FREEZE_CREDITS
        var lastActiveDate: String? = null

        connection.prepareStatement(
            "select current_streak, longest_streak, freeze_credits, last_active_date from user_streaks where user_id = ?",
        ).use { stmt ->
            stmt.setObject(1, userId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    streak = rs.getInt("current_streak")
                    longestStreak = rs.getInt("longest_streak")
                    freezeCredits = rs.getInt("freeze_credits")
                    lastActiveDate = rs.getDate("last_active_date")?.toLocalDate()?.toString()
                }
            }
        }

        var totalXp = 0
        var weeklyXp = 0
        var level = 1

        connection.prepareStatement(
            "select total_xp, weekly_xp, level from user_experience where user_id = ?",
        ).use { stmt ->
            stmt.setObject(1, userId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    totalXp = rs.getInt("total_xp")
                    weeklyXp = rs.getInt("weekly_xp")
                    level = rs.getInt("level")
                }
            }
        }

        GamificationSummaryDto(
            streak = streak,
            longestStreak = longestStreak,
            freezeCredits = freezeCredits,
            totalXp = totalXp,
            weeklyXp = weeklyXp,
            level = level,
            lastActiveDate = lastActiveDate,
        )
    }

    override fun awardAttempt(
        userId: UUID,
        attemptId: UUID,
        verdict: String,
        usedHint: Boolean,
        occurredAt: Instant,
        timezone: String?,
    ): GamificationReward = database.withConnection { connection ->
        val oldAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            val zoneId = GamificationRules.parseZone(timezone)
            val today = occurredAt.atZone(zoneId).toLocalDate()

            var currentStreak = 0
            var longestStreak = 0
            var lastActiveDate: LocalDate? = null
            var freezeCredits = GamificationRules.INITIAL_FREEZE_CREDITS

            connection.prepareStatement(
                "select current_streak, longest_streak, last_active_date, freeze_credits from user_streaks where user_id = ?",
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        currentStreak = rs.getInt("current_streak")
                        longestStreak = rs.getInt("longest_streak")
                        lastActiveDate = rs.getDate("last_active_date")?.toLocalDate()
                        freezeCredits = rs.getInt("freeze_credits")
                    }
                }
            }

            val streakUpdate = GamificationRules.calculateStreak(
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                lastActiveDate = lastActiveDate,
                freezeCredits = freezeCredits,
                today = today,
            )

            connection.prepareStatement(
                """
                insert into user_streaks (user_id, current_streak, longest_streak, last_active_date, freeze_credits, updated_at)
                values (?, ?, ?, ?, ?, now())
                on conflict (user_id) do update set
                    current_streak = excluded.current_streak,
                    longest_streak = excluded.longest_streak,
                    last_active_date = excluded.last_active_date,
                    freeze_credits = excluded.freeze_credits,
                    updated_at = now()
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setInt(2, streakUpdate.streak)
                stmt.setInt(3, streakUpdate.longestStreak)
                stmt.setDate(4, Date.valueOf(streakUpdate.lastActiveDate))
                stmt.setInt(5, streakUpdate.freezeCredits)
                stmt.executeUpdate()
            }

            var totalXp = 0
            var weeklyXp = 0

            connection.prepareStatement(
                "select total_xp, weekly_xp from user_experience where user_id = ?",
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        totalXp = rs.getInt("total_xp")
                        weeklyXp = rs.getInt("weekly_xp")
                    }
                }
            }

            val xpAwarded = GamificationRules.calculateXp(verdict, usedHint)
            val newTotalXp = totalXp + xpAwarded
            val newWeeklyXp = weeklyXp + xpAwarded
            val newLevel = GamificationRules.calculateLevel(newTotalXp)

            connection.prepareStatement(
                """
                insert into user_experience (user_id, total_xp, weekly_xp, level, updated_at)
                values (?, ?, ?, ?, now())
                on conflict (user_id) do update set
                    total_xp = excluded.total_xp,
                    weekly_xp = excluded.weekly_xp,
                    level = excluded.level,
                    updated_at = now()
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setInt(2, newTotalXp)
                stmt.setInt(3, newWeeklyXp)
                stmt.setInt(4, newLevel)
                stmt.executeUpdate()
            }

            val ledgerId = UUID.randomUUID()
            val reason = GamificationRules.xpReason(verdict, usedHint)

            connection.prepareStatement(
                """
                insert into reward_ledger (id, user_id, attempt_id, xp_awarded, reason, awarded_at)
                values (?, ?, ?, ?, ?, now())
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, ledgerId)
                stmt.setObject(2, userId)
                stmt.setObject(3, attemptId)
                stmt.setInt(4, xpAwarded)
                stmt.setString(5, reason)
                stmt.executeUpdate()
            }

            connection.commit()

            GamificationReward(
                xpAwarded = xpAwarded,
                streak = streakUpdate.streak,
                freezeUsed = streakUpdate.freezeUsed,
            )
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = oldAutoCommit
        }
    }

    override fun claimFreeze(userId: UUID): ClaimFreezeResponse? = database.withConnection { connection ->
        val oldAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            val exists = connection.prepareStatement("select 1 from users where id = ?").use { stmt ->
                stmt.setObject(1, userId)
                stmt.executeQuery().use { it.next() }
            }
            if (!exists) {
                connection.rollback()
                return@withConnection null
            }

            var totalXp = 0
            connection.prepareStatement("select total_xp from user_experience where user_id = ?").use { stmt ->
                stmt.setObject(1, userId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        totalXp = rs.getInt("total_xp")
                    }
                }
            }

            var freezeCredits = GamificationRules.INITIAL_FREEZE_CREDITS
            connection.prepareStatement("select freeze_credits from user_streaks where user_id = ?").use { stmt ->
                stmt.setObject(1, userId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        freezeCredits = rs.getInt("freeze_credits")
                    }
                }
            }

            if (totalXp < GamificationRules.FREEZE_COST_XP) {
                connection.rollback()
                return@withConnection ClaimFreezeResponse(
                    success = false,
                    freezeCredits = freezeCredits,
                    remainingXp = totalXp,
                )
            }

            val remainingXp = totalXp - GamificationRules.FREEZE_COST_XP
            val newLevel = GamificationRules.calculateLevel(remainingXp)
            val newFreezeCredits = freezeCredits + 1

            connection.prepareStatement(
                """
                insert into user_experience (user_id, total_xp, weekly_xp, level, updated_at)
                values (?, ?, 0, ?, now())
                on conflict (user_id) do update set
                    total_xp = excluded.total_xp,
                    level = excluded.level,
                    updated_at = now()
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setInt(2, remainingXp)
                stmt.setInt(3, newLevel)
                stmt.executeUpdate()
            }

            connection.prepareStatement(
                """
                insert into user_streaks (user_id, current_streak, longest_streak, last_active_date, freeze_credits, updated_at)
                values (?, 0, 0, null, ?, now())
                on conflict (user_id) do update set
                    freeze_credits = excluded.freeze_credits,
                    updated_at = now()
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setInt(2, newFreezeCredits)
                stmt.executeUpdate()
            }

            connection.prepareStatement(
                """
                insert into reward_ledger (id, user_id, attempt_id, xp_awarded, reason, awarded_at)
                values (?, ?, null, ?, 'claim_freeze', now())
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.setObject(2, userId)
                stmt.setInt(3, -GamificationRules.FREEZE_COST_XP)
                stmt.executeUpdate()
            }

            connection.commit()

            ClaimFreezeResponse(
                success = true,
                freezeCredits = newFreezeCredits,
                remainingXp = remainingXp,
            )
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = oldAutoCommit
        }
    }
}
