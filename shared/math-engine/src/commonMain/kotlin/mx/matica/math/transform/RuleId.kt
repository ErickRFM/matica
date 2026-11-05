package mx.matica.math.transform

import mx.matica.math.skills.SkillId

/**
 * Reglas que el motor reconoce en un paso. `labelEs` se usa tal cual en la retroalimentación,
 * así que está redactada para el estudiante, no para el depurador.
 */
enum class RuleId(val id: String, val labelEs: String, val skills: Set<SkillId>) {
    ADD_TO_BOTH_SIDES("add-to-both-sides", "Sumar la misma cantidad a los dos lados", setOf(SkillId.BALANCE_PRINCIPLE, SkillId.ISOLATING_VARIABLE)),
    SUBTRACT_FROM_BOTH_SIDES("subtract-from-both-sides", "Restar la misma cantidad a los dos lados", setOf(SkillId.BALANCE_PRINCIPLE, SkillId.ISOLATING_VARIABLE)),
    MULTIPLY_BOTH_SIDES_BY("multiply-both-sides-by", "Multiplicar los dos lados por el mismo número", setOf(SkillId.BALANCE_PRINCIPLE)),
    DIVIDE_BOTH_SIDES_BY("divide-both-sides-by", "Dividir los dos lados entre el mismo número", setOf(SkillId.BALANCE_PRINCIPLE, SkillId.ISOLATING_VARIABLE)),
    DISTRIBUTE("distribute", "Aplicar la propiedad distributiva", setOf(SkillId.DISTRIBUTIVE_PROPERTY, SkillId.ALGEBRAIC_EXPRESSIONS)),
    COMBINE_LIKE_TERMS("combine-like-terms", "Agrupar términos semejantes", setOf(SkillId.COMBINING_LIKE_TERMS)),
    SIMPLIFY_FRACTION("simplify-fraction", "Simplificar la fracción", setOf(SkillId.FRACTION_ARITHMETIC)),
    EVALUATE_EXPRESSION("evaluate-expression", "Calcular el valor numérico", setOf(SkillId.INTEGER_OPERATIONS, SkillId.ORDER_OF_OPERATIONS)),
    ISOLATE_VARIABLE("isolate-variable", "Aislar la incógnita", setOf(SkillId.ISOLATING_VARIABLE)),
    EQUIVALENT_REWRITE("equivalent-rewrite", "Reescribir de forma equivalente", setOf(SkillId.EQUIVALENCE)),
}
