package mx.matica.backend

import kotlinx.serialization.Serializable
import mx.matica.math.skills.SkillId

@Serializable
data class ServiceStatus(val status: String, val service: String = "matica-api")

@Serializable
data class TopicItem(val id: String, val title: String)

@Serializable
data class TopicCatalog(val version: Int, val topics: List<TopicItem>)

@Serializable
data class ErrorResponse(val code: String, val message: String)

@Serializable
data class UserDto(val id: String, val email: String, val displayName: String?)

@Serializable
data class RegisterRequest(val email: String, val password: String, val displayName: String? = null)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(
    val token: String,
    val expiresAt: String,
    val user: UserDto,
    val role: String = "student",
    val isAnonymous: Boolean = false,
)

@Serializable
data class StudentJoinRequest(
    val code: String? = null,
    val inviteCode: String? = null,
    val displayName: String? = null,
    val nickname: String? = null,
)

@Serializable
data class AttemptRequest(
    val id: String,
    val skillId: String,
    val exerciseId: String,
    val before: String,
    val after: String,
    val verdict: String,
    val usedHint: Boolean = false,
    val occurredAt: String,
)

@Serializable
data class AttemptDto(
    val id: String,
    val skillId: String,
    val exerciseId: String,
    val before: String,
    val after: String,
    val verdict: String,
    val usedHint: Boolean,
    val occurredAt: String,
)

@Serializable
data class AttemptAccepted(val id: String, val status: String)

@Serializable
data class AttemptsResponse(val attempts: List<AttemptDto>, val serverTime: String)

@Serializable
data class CreateGroupRequest(val name: String)

@Serializable
data class JoinGroupRequest(val code: String)

@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val inviteCode: String?,
    val role: String,
    val memberCount: Int,
)

@Serializable
data class GroupMemberDto(val displayName: String?, val role: String, val joinedAt: String)

@Serializable
data class GroupDetailDto(val group: GroupDto, val members: List<GroupMemberDto>)

@Serializable
data class GroupsResponse(val groups: List<GroupDto>)

@Serializable
data class DeviceRequest(val token: String, val platform: String = "android")

internal object Validation {
    const val MAX_EMAIL = 254
    const val MIN_PASSWORD = 8
    const val MAX_PASSWORD = 128
    const val MAX_DISPLAY_NAME = 60
    const val MAX_GROUP_NAME = 60
    const val MAX_EXERCISE_ID = 80
    const val MAX_LINE = 500
    const val MAX_DEVICE_TOKEN = 4096

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** Devuelve el correo normalizado (minúsculas, sin espacios) o `null` si no es aceptable. */
    fun normalizeEmail(raw: String): String? {
        val value = raw.trim().lowercase()
        if (value.isEmpty() || value.length > MAX_EMAIL) return null
        if (!EMAIL.matches(value)) return null
        return value
    }

    fun passwordProblem(password: String): String? = when {
        password.length < MIN_PASSWORD -> "La contraseña necesita al menos $MIN_PASSWORD caracteres."
        password.length > MAX_PASSWORD -> "La contraseña no puede pasar de $MAX_PASSWORD caracteres."
        else -> null
    }

    fun displayName(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return value.take(MAX_DISPLAY_NAME)
    }

    fun validSkill(skillId: String): Boolean = SkillId.entries.any { it.id == skillId }

    fun validVerdict(verdict: String): Boolean = verdict in VERDICTS

    /** El aula comparte alias, no correos: el correo sólo lo ve su dueño. */
    fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        if (at <= 0) return "estudiante"
        val local = email.substring(0, at)
        val domain = email.substring(at + 1)
        val visible = local.take(1)
        return "$visible***@$domain"
    }

    private val VERDICTS = setOf("valid", "invalid", "indeterminate", "syntax")
}
