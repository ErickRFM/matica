package mx.matica.learning.skills

import mx.matica.math.skills.SkillId
import mx.matica.math.skills.SkillId.ALGEBRAIC_EXPRESSIONS
import mx.matica.math.skills.SkillId.BALANCE_PRINCIPLE
import mx.matica.math.skills.SkillId.COMBINING_LIKE_TERMS
import mx.matica.math.skills.SkillId.DECIMAL_FRACTION_CONVERSION
import mx.matica.math.skills.SkillId.DISTRIBUTIVE_PROPERTY
import mx.matica.math.skills.SkillId.DOMAIN_RESTRICTIONS
import mx.matica.math.skills.SkillId.EQUIVALENCE
import mx.matica.math.skills.SkillId.EXPONENT_RULES
import mx.matica.math.skills.SkillId.FRACTION_ARITHMETIC
import mx.matica.math.skills.SkillId.INTEGER_OPERATIONS
import mx.matica.math.skills.SkillId.ISOLATING_VARIABLE
import mx.matica.math.skills.SkillId.ORDER_OF_OPERATIONS
import mx.matica.math.skills.SkillId.PERCENTAGE_REASONING
import mx.matica.math.skills.SkillId.SIGN_HANDLING
import mx.matica.math.skills.SkillId.SOLUTION_VERIFICATION

/**
 * Currículo base del producto. Cada arista es una decisión revisable: se justifica en la misma
 * línea para que un cambio quede a la vista en la diferencia, no enterrado en un JSON.
 */
object DefaultCurriculum {

    val graph: SkillGraph = buildGraph()

    fun buildGraph(): SkillGraph {
        val builder = SkillGraph.builder()
            // Aritmética de fracciones necesita operar con enteros antes que cualquier otro concepto.
            .require(FRACTION_ARITHMETIC, INTEGER_OPERATIONS)
            .require(ORDER_OF_OPERATIONS, INTEGER_OPERATIONS)
            // Convertir decimal ↔ fracción solo tiene sentido sabiendo qué representa la fracción.
            .require(DECIMAL_FRACTION_CONVERSION, FRACTION_ARITHMETIC)
            .require(PERCENTAGE_REASONING, DECIMAL_FRACTION_CONVERSION)
            // Las potencias usan la jerarquía y la multiplicación repetida de enteros.
            .require(EXPONENT_RULES, INTEGER_OPERATIONS)
            .require(EXPONENT_RULES, ORDER_OF_OPERATIONS)
            // El álgebra empieza al tratar una expresión como objeto, no solo como cuenta.
            .require(ALGEBRAIC_EXPRESSIONS, ORDER_OF_OPERATIONS)
            .require(DISTRIBUTIVE_PROPERTY, ALGEBRAIC_EXPRESSIONS)
            .require(DISTRIBUTIVE_PROPERTY, INTEGER_OPERATIONS)
            .require(COMBINING_LIKE_TERMS, ALGEBRAIC_EXPRESSIONS)
            .require(SIGN_HANDLING, ALGEBRAIC_EXPRESSIONS)
            .require(SIGN_HANDLING, INTEGER_OPERATIONS)
            // Equilibrio: la operación debe aplicarse a los dos lados; exige operar con expresiones.
            .require(BALANCE_PRINCIPLE, DISTRIBUTIVE_PROPERTY)
            .require(BALANCE_PRINCIPLE, COMBINING_LIKE_TERMS)
            .require(BALANCE_PRINCIPLE, SIGN_HANDLING)
            .require(ISOLATING_VARIABLE, BALANCE_PRINCIPLE)
            // Una restricción de dominio solo se entiende habiendo despejado y sabido dividir.
            .require(DOMAIN_RESTRICTIONS, ISOLATING_VARIABLE)
            .require(DOMAIN_RESTRICTIONS, FRACTION_ARITHMETIC)
            .require(SOLUTION_VERIFICATION, ISOLATING_VARIABLE)
            .require(EQUIVALENCE, BALANCE_PRINCIPLE)
            .require(EQUIVALENCE, COMBINING_LIKE_TERMS)
            .require(EQUIVALENCE, DISTRIBUTIVE_PROPERTY)
        return (builder.build() as GraphBuild.Ready).graph
    }

    /** El currículo debe cubrir cada habilidad que el motor puede emitir. */
    val uncoveredSkills: Set<SkillId> = SkillId.entries.toSet() - graph.skills.toSet()
}
