package mx.matica.backend

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import kotlinx.serialization.Serializable
import mx.matica.math.skills.SkillId

@Serializable
data class SkillTopicDto(
    val id: String,
    val title: String,
    val prerequisites: List<String> = emptyList(),
)

@Serializable
data class CurriculumBundleDto(
    val version: Int,
    val generatedAt: String,
    val skills: List<SkillTopicDto>,
    val templatesCount: Int,
    val checksum: String,
)

internal class CurriculumOtaService(
    private val clock: Clock = Clock.systemUTC(),
) {
    private val bundleSkills: List<SkillTopicDto> = listOf(
        SkillTopicDto(SkillId.INTEGER_OPERATIONS.id, SkillId.INTEGER_OPERATIONS.labelEs, emptyList()),
        SkillTopicDto(SkillId.ORDER_OF_OPERATIONS.id, SkillId.ORDER_OF_OPERATIONS.labelEs, listOf(SkillId.INTEGER_OPERATIONS.id)),
        SkillTopicDto(SkillId.FRACTION_ARITHMETIC.id, SkillId.FRACTION_ARITHMETIC.labelEs, listOf(SkillId.INTEGER_OPERATIONS.id)),
        SkillTopicDto(SkillId.DECIMAL_FRACTION_CONVERSION.id, SkillId.DECIMAL_FRACTION_CONVERSION.labelEs, listOf(SkillId.FRACTION_ARITHMETIC.id)),
        SkillTopicDto(SkillId.PERCENTAGE_REASONING.id, SkillId.PERCENTAGE_REASONING.labelEs, listOf(SkillId.DECIMAL_FRACTION_CONVERSION.id)),
        SkillTopicDto(SkillId.EXPONENT_RULES.id, SkillId.EXPONENT_RULES.labelEs, listOf(SkillId.INTEGER_OPERATIONS.id, SkillId.ORDER_OF_OPERATIONS.id)),
        SkillTopicDto(SkillId.ALGEBRAIC_EXPRESSIONS.id, SkillId.ALGEBRAIC_EXPRESSIONS.labelEs, listOf(SkillId.ORDER_OF_OPERATIONS.id)),
        SkillTopicDto(SkillId.DISTRIBUTIVE_PROPERTY.id, SkillId.DISTRIBUTIVE_PROPERTY.labelEs, listOf(SkillId.ALGEBRAIC_EXPRESSIONS.id, SkillId.INTEGER_OPERATIONS.id)),
        SkillTopicDto(SkillId.COMBINING_LIKE_TERMS.id, SkillId.COMBINING_LIKE_TERMS.labelEs, listOf(SkillId.ALGEBRAIC_EXPRESSIONS.id)),
        SkillTopicDto(SkillId.SIGN_HANDLING.id, SkillId.SIGN_HANDLING.labelEs, listOf(SkillId.ALGEBRAIC_EXPRESSIONS.id, SkillId.INTEGER_OPERATIONS.id)),
        SkillTopicDto(SkillId.BALANCE_PRINCIPLE.id, SkillId.BALANCE_PRINCIPLE.labelEs, listOf(SkillId.DISTRIBUTIVE_PROPERTY.id, SkillId.COMBINING_LIKE_TERMS.id, SkillId.SIGN_HANDLING.id)),
        SkillTopicDto(SkillId.ISOLATING_VARIABLE.id, SkillId.ISOLATING_VARIABLE.labelEs, listOf(SkillId.BALANCE_PRINCIPLE.id)),
        SkillTopicDto(SkillId.DOMAIN_RESTRICTIONS.id, SkillId.DOMAIN_RESTRICTIONS.labelEs, listOf(SkillId.ISOLATING_VARIABLE.id, SkillId.FRACTION_ARITHMETIC.id)),
        SkillTopicDto(SkillId.SOLUTION_VERIFICATION.id, SkillId.SOLUTION_VERIFICATION.labelEs, listOf(SkillId.ISOLATING_VARIABLE.id)),
        SkillTopicDto(SkillId.EQUIVALENCE.id, SkillId.EQUIVALENCE.labelEs, listOf(SkillId.BALANCE_PRINCIPLE.id, SkillId.COMBINING_LIKE_TERMS.id, SkillId.DISTRIBUTIVE_PROPERTY.id)),
    )

    private val templatesCount = 15

    private val bundleChecksum: String by lazy {
        val raw = "v$CURRENT_VERSION:${bundleSkills.joinToString { it.id }}:$templatesCount"
        MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun getBundle(): CurriculumBundleDto = CurriculumBundleDto(
        version = CURRENT_VERSION,
        generatedAt = clock.instant().toString(),
        skills = bundleSkills,
        templatesCount = templatesCount,
        checksum = bundleChecksum,
    )

    fun isUpToDate(clientVersion: Int?): Boolean = clientVersion == CURRENT_VERSION

    companion object {
        const val CURRENT_VERSION = 2
    }
}
