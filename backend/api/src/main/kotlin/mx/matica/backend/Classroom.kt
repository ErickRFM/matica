package mx.matica.backend

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import mx.matica.math.skills.SkillId

internal data class AssignmentRecord(
    val id: UUID,
    val groupId: UUID,
    val skillId: String,
    val targetMastery: String,
    val dueDate: Instant?,
    val createdBy: UUID,
    val createdAt: Instant,
)

@Serializable
data class AssignmentDto(
    val id: String,
    val groupId: String,
    val skillId: String,
    val targetMastery: String,
    val dueDate: String?,
    val createdBy: String,
    val createdAt: String,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
)

@Serializable
data class CreateAssignmentRequest(
    val skillId: String,
    val targetMastery: String = "proficient",
    val dueDate: String? = null,
)

@Serializable
data class StudentSkillCellDto(
    val studentId: String? = null,
    val displayName: String? = null,
    val skillId: String,
    val mastery: String = "not_started",
    val attemptsCount: Int = 0,
    val successRate: Double = 0.0,
    val struggling: Boolean = false,
    val lastVerdict: String? = null,
)

@Serializable
data class StudentHeatmapRowDto(
    val studentId: String,
    val displayName: String,
    val cells: List<StudentSkillCellDto>,
)

@Serializable
data class ClassroomHeatmapDto(
    val groupId: String,
    val students: List<StudentHeatmapRowDto>,
    val skills: List<String>,
    val cells: List<StudentSkillCellDto> = emptyList(),
)

@Serializable
data class ErrorPatternDto(
    val skillId: String,
    val exerciseId: String,
    val errorCount: Int,
    val sampleAfter: String? = null,
)

@Serializable
data class ClassroomInsightsDto(
    val groupId: String,
    val commonErrors: List<ErrorPatternDto>,
    val strugglingSkills: List<String> = emptyList(),
    val totalAttempts: Int = 0,
    val overallAccuracy: Double = 0.0,
)

internal interface ClassroomRepository {
    fun createAssignment(
        groupId: UUID,
        skillId: String,
        targetMastery: String,
        dueDate: Instant?,
        teacherId: UUID,
    ): AssignmentRecord

    fun listAssignments(groupId: UUID): List<AssignmentDto>
    fun getHeatmap(groupId: UUID): ClassroomHeatmapDto
    fun getInsights(groupId: UUID): ClassroomInsightsDto
    fun recordAttempt(userId: UUID, skillId: String, verdict: String)
}

internal class JdbcClassroomRepository(private val database: Database) : ClassroomRepository {
    override fun createAssignment(
        groupId: UUID,
        skillId: String,
        targetMastery: String,
        dueDate: Instant?,
        teacherId: UUID,
    ): AssignmentRecord = database.withConnection { connection ->
        val id = UUID.randomUUID()
        val createdAt = Instant.now()
        connection.prepareStatement(
            """
            insert into assignments (id, group_id, skill_id, target_mastery, due_date, created_by, created_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, id)
            stmt.setObject(2, groupId)
            stmt.setString(3, skillId)
            stmt.setString(4, targetMastery)
            stmt.setTimestamp(5, dueDate?.let { Timestamp.from(it) })
            stmt.setObject(6, teacherId)
            stmt.setTimestamp(7, Timestamp.from(createdAt))
            stmt.executeUpdate()
        }

        connection.prepareStatement(
            """
            insert into assignment_progress (assignment_id, student_id, status)
            select ?, user_id, 'assigned'
            from group_members
            where group_id = ? and role = 'student'
            on conflict do nothing
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, id)
            stmt.setObject(2, groupId)
            stmt.executeUpdate()
        }

        AssignmentRecord(
            id = id,
            groupId = groupId,
            skillId = skillId,
            targetMastery = targetMastery,
            dueDate = dueDate,
            createdBy = teacherId,
            createdAt = createdAt,
        )
    }

    override fun listAssignments(groupId: UUID): List<AssignmentDto> = database.withConnection { connection ->
        connection.prepareStatement(
            """
            select a.id, a.group_id, a.skill_id, a.target_mastery, a.due_date, a.created_by, a.created_at,
                   count(ap.student_id) filter (where ap.status = 'completed') as completed_count,
                   count(ap.student_id) as total_count
            from assignments a
            left join assignment_progress ap on ap.assignment_id = a.id
            where a.group_id = ?
            group by a.id, a.group_id, a.skill_id, a.target_mastery, a.due_date, a.created_by, a.created_at
            order by a.created_at desc
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, groupId)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            AssignmentDto(
                                id = rs.getObject("id", UUID::class.java).toString(),
                                groupId = rs.getObject("group_id", UUID::class.java).toString(),
                                skillId = rs.getString("skill_id"),
                                targetMastery = rs.getString("target_mastery"),
                                dueDate = rs.getTimestamp("due_date")?.toInstant()?.toString(),
                                createdBy = rs.getObject("created_by", UUID::class.java).toString(),
                                createdAt = rs.getTimestamp("created_at").toInstant().toString(),
                                completedCount = rs.getInt("completed_count"),
                                totalCount = rs.getInt("total_count"),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun getHeatmap(groupId: UUID): ClassroomHeatmapDto = database.withConnection { connection ->
        data class StudentInfo(val id: UUID, val displayName: String)
        val students = mutableListOf<StudentInfo>()
        connection.prepareStatement(
            """
            select u.id, u.display_name, u.email
            from group_members gm
            join users u on u.id = gm.user_id
            where gm.group_id = ? and gm.role = 'student'
            order by gm.joined_at
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, groupId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val id = rs.getObject("id", UUID::class.java)
                    val rawName = rs.getString("display_name")
                    val email = rs.getString("email")
                    val name = rawName?.takeIf { it.isNotBlank() } ?: Validation.maskEmail(email)
                    students.add(StudentInfo(id, name))
                }
            }
        }

        data class AttemptRow(val userId: UUID, val skillId: String, val verdict: String, val occurredAt: Instant)
        val attempts = mutableListOf<AttemptRow>()
        connection.prepareStatement(
            """
            select a.user_id, a.skill_id, a.verdict, a.occurred_at
            from attempts a
            join group_members gm on gm.user_id = a.user_id
            where gm.group_id = ? and gm.role = 'student'
            order by a.occurred_at asc
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, groupId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    attempts.add(
                        AttemptRow(
                            userId = rs.getObject("user_id", UUID::class.java),
                            skillId = rs.getString("skill_id"),
                            verdict = rs.getString("verdict"),
                            occurredAt = rs.getTimestamp("occurred_at").toInstant(),
                        ),
                    )
                }
            }
        }

        val allSkills = SkillId.entries.map { it.id }
        val attemptsByStudentAndSkill = attempts.groupBy { it.userId to it.skillId }

        val allCells = mutableListOf<StudentSkillCellDto>()
        val studentRows = students.map { student ->
            val cells = allSkills.map { skillId ->
                val userSkillAttempts = attemptsByStudentAndSkill[student.id to skillId] ?: emptyList()
                val total = userSkillAttempts.size
                val valid = userSkillAttempts.count { it.verdict == "valid" }
                val rate = if (total > 0) valid.toDouble() / total else 0.0
                val lastVerdict = userSkillAttempts.lastOrNull()?.verdict
                val struggling = total >= 2 && rate < 0.5
                val mastery = when {
                    total == 0 -> "not_started"
                    struggling -> "struggling"
                    valid >= 3 || rate >= 0.8 -> "proficient"
                    else -> "practiced"
                }
                StudentSkillCellDto(
                    studentId = student.id.toString(),
                    displayName = student.displayName,
                    skillId = skillId,
                    mastery = mastery,
                    attemptsCount = total,
                    successRate = rate,
                    struggling = struggling,
                    lastVerdict = lastVerdict,
                )
            }
            allCells.addAll(cells)
            StudentHeatmapRowDto(
                studentId = student.id.toString(),
                displayName = student.displayName,
                cells = cells,
            )
        }

        ClassroomHeatmapDto(
            groupId = groupId.toString(),
            students = studentRows,
            skills = allSkills,
            cells = allCells,
        )
    }

    override fun getInsights(groupId: UUID): ClassroomInsightsDto = database.withConnection { connection ->
        data class AttemptData(val skillId: String, val exerciseId: String, val verdict: String, val afterText: String)
        val attempts = mutableListOf<AttemptData>()
        connection.prepareStatement(
            """
            select a.skill_id, a.exercise_id, a.verdict, a.after_text
            from attempts a
            join group_members gm on gm.user_id = a.user_id
            where gm.group_id = ? and gm.role = 'student'
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, groupId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    attempts.add(
                        AttemptData(
                            skillId = rs.getString("skill_id"),
                            exerciseId = rs.getString("exercise_id"),
                            verdict = rs.getString("verdict"),
                            afterText = rs.getString("after_text"),
                        ),
                    )
                }
            }
        }

        val totalAttempts = attempts.size
        val validAttempts = attempts.count { it.verdict == "valid" }
        val overallAccuracy = if (totalAttempts > 0) validAttempts.toDouble() / totalAttempts else 0.0

        val errors = attempts.filter { it.verdict in setOf("invalid", "syntax") }
        val commonErrors = errors.groupBy { it.skillId to it.exerciseId }
            .map { (key, group) ->
                ErrorPatternDto(
                    skillId = key.first,
                    exerciseId = key.second,
                    errorCount = group.size,
                    sampleAfter = group.lastOrNull()?.afterText,
                )
            }
            .sortedByDescending { it.errorCount }
            .take(10)

        val strugglingSkills = attempts.groupBy { it.skillId }
            .filter { (_, skillAttempts) ->
                skillAttempts.size >= 2 && (skillAttempts.count { it.verdict == "valid" }.toDouble() / skillAttempts.size) < 0.5
            }
            .keys.toList()

        ClassroomInsightsDto(
            groupId = groupId.toString(),
            commonErrors = commonErrors,
            strugglingSkills = strugglingSkills,
            totalAttempts = totalAttempts,
            overallAccuracy = overallAccuracy,
        )
    }

    override fun recordAttempt(userId: UUID, skillId: String, verdict: String) = database.withConnection { connection ->
        if (verdict == "valid") {
            connection.prepareStatement(
                """
                update assignment_progress ap
                set status = 'completed', completed_at = now()
                from assignments a
                where ap.assignment_id = a.id
                  and ap.student_id = ?
                  and a.skill_id = ?
                  and ap.status <> 'completed'
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setString(2, skillId)
                stmt.executeUpdate()
            }
        } else {
            connection.prepareStatement(
                """
                update assignment_progress ap
                set status = 'in_progress'
                from assignments a
                where ap.assignment_id = a.id
                  and ap.student_id = ?
                  and a.skill_id = ?
                  and ap.status = 'assigned'
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setString(2, skillId)
                stmt.executeUpdate()
            }
        }
        Unit
    }
}

internal class InMemoryClassroomRepository(
    private val groups: GroupRepository? = null,
    private val attempts: AttemptRepository? = null,
) : ClassroomRepository {
    private val assignments = ConcurrentHashMap<UUID, AssignmentRecord>()
    private val progress = ConcurrentHashMap<Pair<UUID, UUID>, String>() // (assignmentId, studentId) -> status

    override fun createAssignment(
        groupId: UUID,
        skillId: String,
        targetMastery: String,
        dueDate: Instant?,
        teacherId: UUID,
    ): AssignmentRecord {
        val id = UUID.randomUUID()
        val record = AssignmentRecord(
            id = id,
            groupId = groupId,
            skillId = skillId,
            targetMastery = targetMastery,
            dueDate = dueDate,
            createdBy = teacherId,
            createdAt = Instant.now(),
        )
        assignments[id] = record
        groups?.members(groupId)?.filter { it.role == "student" }?.forEach { member ->
            progress[id to member.userId] = "assigned"
        }
        return record
    }

    override fun listAssignments(groupId: UUID): List<AssignmentDto> =
        assignments.values.filter { it.groupId == groupId }.map { record ->
            val groupProgress = progress.filterKeys { it.first == record.id }
            val completed = groupProgress.values.count { it == "completed" }
            val total = groupProgress.size
            AssignmentDto(
                id = record.id.toString(),
                groupId = record.groupId.toString(),
                skillId = record.skillId,
                targetMastery = record.targetMastery,
                dueDate = record.dueDate?.toString(),
                createdBy = record.createdBy.toString(),
                createdAt = record.createdAt.toString(),
                completedCount = completed,
                totalCount = total,
            )
        }.sortedByDescending { it.createdAt }

    override fun getHeatmap(groupId: UUID): ClassroomHeatmapDto {
        val studentMembers = groups?.members(groupId)?.filter { it.role == "student" } ?: emptyList()
        val allSkills = SkillId.entries.map { it.id }
        val allCells = mutableListOf<StudentSkillCellDto>()

        val studentRows = studentMembers.map { student ->
            val userAttempts = attempts?.list(student.userId, null, 500) ?: emptyList()
            val cells = allSkills.map { skillId ->
                val skillAttempts = userAttempts.filter { it.skillId == skillId }
                val total = skillAttempts.size
                val valid = skillAttempts.count { it.verdict == "valid" }
                val rate = if (total > 0) valid.toDouble() / total else 0.0
                val struggling = total >= 2 && rate < 0.5
                val mastery = when {
                    total == 0 -> "not_started"
                    struggling -> "struggling"
                    valid >= 3 || rate >= 0.8 -> "proficient"
                    else -> "practiced"
                }
                StudentSkillCellDto(
                    studentId = student.userId.toString(),
                    displayName = student.displayName ?: Validation.maskEmail(student.email),
                    skillId = skillId,
                    mastery = mastery,
                    attemptsCount = total,
                    successRate = rate,
                    struggling = struggling,
                    lastVerdict = skillAttempts.lastOrNull()?.verdict,
                )
            }
            allCells.addAll(cells)
            StudentHeatmapRowDto(
                studentId = student.userId.toString(),
                displayName = student.displayName ?: Validation.maskEmail(student.email),
                cells = cells,
            )
        }

        return ClassroomHeatmapDto(
            groupId = groupId.toString(),
            students = studentRows,
            skills = allSkills,
            cells = allCells,
        )
    }

    override fun getInsights(groupId: UUID): ClassroomInsightsDto {
        val studentMembers = groups?.members(groupId)?.filter { it.role == "student" } ?: emptyList()
        val allAttempts = studentMembers.flatMap { attempts?.list(it.userId, null, 500) ?: emptyList() }
        val totalAttempts = allAttempts.size
        val validAttempts = allAttempts.count { it.verdict == "valid" }
        val overallAccuracy = if (totalAttempts > 0) validAttempts.toDouble() / totalAttempts else 0.0

        val errors = allAttempts.filter { it.verdict in setOf("invalid", "syntax") }
        val commonErrors = errors.groupBy { it.skillId to it.exerciseId }
            .map { (key, list) ->
                ErrorPatternDto(
                    skillId = key.first,
                    exerciseId = key.second,
                    errorCount = list.size,
                    sampleAfter = list.lastOrNull()?.after,
                )
            }
            .sortedByDescending { it.errorCount }
            .take(10)

        val strugglingSkills = allAttempts.groupBy { it.skillId }
            .filter { (_, list) ->
                list.size >= 2 && (list.count { it.verdict == "valid" }.toDouble() / list.size) < 0.5
            }
            .keys.toList()

        return ClassroomInsightsDto(
            groupId = groupId.toString(),
            commonErrors = commonErrors,
            strugglingSkills = strugglingSkills,
            totalAttempts = totalAttempts,
            overallAccuracy = overallAccuracy,
        )
    }

    override fun recordAttempt(userId: UUID, skillId: String, verdict: String) {
        for ((key, _) in progress) {
            val (assignId, studentId) = key
            if (studentId == userId) {
                val assign = assignments[assignId]
                if (assign != null && assign.skillId == skillId) {
                    if (verdict == "valid") {
                        progress[key] = "completed"
                    } else if (progress[key] == "assigned") {
                        progress[key] = "in_progress"
                    }
                }
            }
        }
    }
}
