package mx.matica.backend

import java.sql.ResultSet
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

internal data class User(
    val id: UUID,
    val email: String,
    val displayName: String?,
    val passwordHash: String,
)

internal data class AttemptRecord(
    val id: UUID,
    val userId: UUID,
    val skillId: String,
    val exerciseId: String,
    val before: String,
    val after: String,
    val verdict: String,
    val usedHint: Boolean,
    val occurredAt: Instant,
)

internal sealed interface AttemptInsert {
    data object Created : AttemptInsert
    data object Duplicate : AttemptInsert
    data object OtherOwner : AttemptInsert
}

internal data class GroupRecord(
    val id: UUID,
    val name: String,
    val inviteCode: String,
    val ownerId: UUID,
    val memberCount: Int,
)

internal data class GroupMembership(
    val group: GroupRecord,
    val role: String,
)

internal data class GroupMemberRecord(
    val userId: UUID,
    val email: String,
    val displayName: String?,
    val role: String,
    val joinedAt: Instant,
)

internal interface UserRepository {
    fun findByEmail(normalizedEmail: String): User?
    fun findById(id: UUID): User?
    fun create(email: String, normalizedEmail: String, displayName: String?, passwordHash: String): User?
}

internal interface AttemptRepository {
    fun insert(record: AttemptRecord): AttemptInsert
    fun list(userId: UUID, since: Instant?, limit: Int): List<AttemptRecord>
}

internal interface GroupRepository {
    /** `null` si el código de invitación ya está ocupado; el llamador reintenta. */
    fun create(ownerId: UUID, name: String, inviteCode: String): GroupRecord?
    fun findByCode(code: String): GroupRecord?
    fun findById(id: UUID): GroupRecord?
    fun membership(groupId: UUID, userId: UUID): GroupMembership?
    fun addMember(groupId: UUID, userId: UUID): Boolean
    fun listFor(userId: UUID): List<GroupMembership>
    fun members(groupId: UUID): List<GroupMemberRecord>
}

internal interface DeviceTokenRepository {
    fun upsert(userId: UUID, token: String, platform: String)
    fun tokensFor(userId: UUID): List<String>
}

internal class JdbcUserRepository(private val database: Database) : UserRepository {
    override fun findByEmail(normalizedEmail: String): User? = database.withConnection { connection ->
        connection.prepareStatement(
            "select id, email, password_hash, display_name from users where email_normalized = ?",
        ).use { statement ->
            statement.setString(1, normalizedEmail)
            statement.executeQuery().use { result -> if (result.next()) result.toUser() else null }
        }
    }

    override fun findById(id: UUID): User? = database.withConnection { connection ->
        connection.prepareStatement(
            "select id, email, password_hash, display_name from users where id = ?",
        ).use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { result -> if (result.next()) result.toUser() else null }
        }
    }

    override fun create(
        email: String,
        normalizedEmail: String,
        displayName: String?,
        passwordHash: String,
    ): User? = try {
        database.withConnection { connection ->
            val user = User(UUID.randomUUID(), email, displayName, passwordHash)
            connection.prepareStatement(
                "insert into users (id, email, email_normalized, password_hash, display_name) values (?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setObject(1, user.id)
                statement.setString(2, email)
                statement.setString(3, normalizedEmail)
                statement.setString(4, passwordHash)
                statement.setString(5, displayName)
                statement.executeUpdate()
            }
            user
        }
    } catch (error: SQLException) {
        if (error.sqlState == UNIQUE_VIOLATION) null else throw error
    }

    private fun ResultSet.toUser() = User(
        id = getObject("id", UUID::class.java),
        email = getString("email"),
        displayName = getString("display_name"),
        passwordHash = getString("password_hash"),
    )
}

internal class JdbcAttemptRepository(private val database: Database) : AttemptRepository {
    override fun insert(record: AttemptRecord): AttemptInsert = database.withConnection { connection ->
        val inserted = connection.prepareStatement(
            """
            insert into attempts (id, user_id, skill_id, exercise_id, before_text, after_text, verdict, used_hint, occurred_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (id) do nothing
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, record.id)
            statement.setObject(2, record.userId)
            statement.setString(3, record.skillId)
            statement.setString(4, record.exerciseId)
            statement.setString(5, record.before)
            statement.setString(6, record.after)
            statement.setString(7, record.verdict)
            statement.setBoolean(8, record.usedHint)
            statement.setTimestamp(9, java.sql.Timestamp.from(record.occurredAt))
            statement.executeUpdate()
        }
        if (inserted == 1) {
            AttemptInsert.Created
        } else {
            val owner = connection.prepareStatement("select user_id from attempts where id = ?").use { statement ->
                statement.setObject(1, record.id)
                statement.executeQuery().use { result -> if (result.next()) result.getObject(1, UUID::class.java) else null }
            }
            if (owner == record.userId) AttemptInsert.Duplicate else AttemptInsert.OtherOwner
        }
    }

    override fun list(userId: UUID, since: Instant?, limit: Int): List<AttemptRecord> =
        database.withConnection { connection ->
            val sql = buildString {
                append("select id, user_id, skill_id, exercise_id, before_text, after_text, verdict, used_hint, occurred_at ")
                append("from attempts where user_id = ?")
                if (since != null) append(" and occurred_at > ?")
                append(" order by occurred_at desc limit ?")
            }
            connection.prepareStatement(sql).use { statement ->
                var index = 1
                statement.setObject(index++, userId)
                if (since != null) statement.setTimestamp(index++, java.sql.Timestamp.from(since))
                statement.setInt(index, limit)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(
                                AttemptRecord(
                                    id = result.getObject("id", UUID::class.java),
                                    userId = result.getObject("user_id", UUID::class.java),
                                    skillId = result.getString("skill_id"),
                                    exerciseId = result.getString("exercise_id"),
                                    before = result.getString("before_text"),
                                    after = result.getString("after_text"),
                                    verdict = result.getString("verdict"),
                                    usedHint = result.getBoolean("used_hint"),
                                    occurredAt = result.getTimestamp("occurred_at").toInstant(),
                                ),
                            )
                        }
                    }.asReversed()
                }
            }
        }
}

internal class JdbcGroupRepository(private val database: Database) : GroupRepository {
    override fun create(ownerId: UUID, name: String, inviteCode: String): GroupRecord? =
        database.withConnection { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val group = GroupRecord(
                    id = UUID.randomUUID(),
                    name = name,
                    inviteCode = inviteCode,
                    ownerId = ownerId,
                    memberCount = 1,
                )
                val inserted = connection.prepareStatement(
                    "insert into study_groups (id, name, owner_id, invite_code) values (?, ?, ?, ?) on conflict (invite_code) do nothing",
                ).use { statement ->
                    statement.setObject(1, group.id)
                    statement.setString(2, group.name)
                    statement.setObject(3, group.ownerId)
                    statement.setString(4, group.inviteCode)
                    statement.executeUpdate()
                }
                if (inserted == 0) {
                    connection.rollback()
                    null
                } else {
                    connection.prepareStatement(
                        "insert into group_members (group_id, user_id, role) values (?, ?, 'owner')",
                    ).use { statement ->
                        statement.setObject(1, group.id)
                        statement.setObject(2, ownerId)
                        statement.executeUpdate()
                    }
                    connection.commit()
                    group
                }
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }

    override fun findByCode(code: String): GroupRecord? =
        findOne("select id, name, invite_code, owner_id from study_groups where invite_code = ?", code)

    override fun findById(id: UUID): GroupRecord? = database.withConnection { connection ->
        connection.prepareStatement(
            """
            select g.id, g.name, g.invite_code, g.owner_id,
                   (select count(*) from group_members m where m.group_id = g.id) as member_count
            from study_groups g where g.id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { result -> if (result.next()) result.toGroup() else null }
        }
    }

    override fun membership(groupId: UUID, userId: UUID): GroupMembership? = database.withConnection { connection ->
        connection.prepareStatement(
            """
            select g.id, g.name, g.invite_code, g.owner_id, gm.role,
                   (select count(*) from group_members m where m.group_id = g.id) as member_count
            from study_groups g join group_members gm on gm.group_id = g.id
            where g.id = ? and gm.user_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, groupId)
            statement.setObject(2, userId)
            statement.executeQuery().use { result ->
                if (result.next()) GroupMembership(result.toGroup(), result.getString("role")) else null
            }
        }
    }

    override fun addMember(groupId: UUID, userId: UUID): Boolean = database.withConnection { connection ->
        connection.prepareStatement(
            "insert into group_members (group_id, user_id, role) values (?, ?, 'student') on conflict do nothing",
        ).use { statement ->
            statement.setObject(1, groupId)
            statement.setObject(2, userId)
            statement.executeUpdate() == 1
        }
    }

    override fun listFor(userId: UUID): List<GroupMembership> = database.withConnection { connection ->
        connection.prepareStatement(
            """
            select g.id, g.name, g.invite_code, g.owner_id, gm.role,
                   (select count(*) from group_members m where m.group_id = g.id) as member_count
            from study_groups g join group_members gm on gm.group_id = g.id
            where gm.user_id = ?
            order by g.created_at desc
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { result ->
                buildList { while (result.next()) add(GroupMembership(result.toGroup(), result.getString("role"))) }
            }
        }
    }

    override fun members(groupId: UUID): List<GroupMemberRecord> = database.withConnection { connection ->
        connection.prepareStatement(
            """
            select u.id, u.email, u.display_name, gm.role, gm.joined_at
            from group_members gm join users u on u.id = gm.user_id
            where gm.group_id = ?
            order by gm.joined_at
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, groupId)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            GroupMemberRecord(
                                userId = result.getObject("id", UUID::class.java),
                                email = result.getString("email"),
                                displayName = result.getString("display_name"),
                                role = result.getString("role"),
                                joinedAt = result.getTimestamp("joined_at").toInstant(),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun findOne(sql: String, value: String): GroupRecord? = database.withConnection { connection ->
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, value)
            statement.executeQuery().use { result ->
                if (result.next()) {
                    GroupRecord(
                        id = result.getObject("id", UUID::class.java),
                        name = result.getString("name"),
                        inviteCode = result.getString("invite_code"),
                        ownerId = result.getObject("owner_id", UUID::class.java),
                        memberCount = 0,
                    )
                } else {
                    null
                }
            }
        }
    }

    private fun ResultSet.toGroup() = GroupRecord(
        id = getObject("id", UUID::class.java),
        name = getString("name"),
        inviteCode = getString("invite_code"),
        ownerId = getObject("owner_id", UUID::class.java),
        memberCount = getInt("member_count"),
    )
}

internal class JdbcDeviceTokenRepository(private val database: Database) : DeviceTokenRepository {
    override fun upsert(userId: UUID, token: String, platform: String) = database.withConnection { connection ->
        connection.prepareStatement(
            """
            insert into device_tokens (user_id, token, platform, updated_at) values (?, ?, ?, now())
            on conflict (user_id, token) do update set platform = excluded.platform, updated_at = now()
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, userId)
            statement.setString(2, token)
            statement.setString(3, platform)
            statement.executeUpdate()
        }
        Unit
    }

    override fun tokensFor(userId: UUID): List<String> = database.withConnection { connection ->
        connection.prepareStatement("select token from device_tokens where user_id = ?").use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { result ->
                buildList { while (result.next()) add(result.getString("token")) }
            }
        }
    }
}

private const val UNIQUE_VIOLATION = "23505"
