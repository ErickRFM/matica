package mx.matica.math.skills

/**
 * Habilidades que el motor puede marcar como evidenciadas por un paso. El grafo curricular del
 * Bloque 2 referencia estos identificadores, así que deben tratarse como contrato estable.
 */
enum class SkillId(val id: String, val labelEs: String) {
    INTEGER_OPERATIONS("integer-operations", "Operaciones con enteros"),
    ORDER_OF_OPERATIONS("order-of-operations", "Jerarquía de operaciones"),
    FRACTION_ARITHMETIC("fraction-arithmetic", "Aritmética de fracciones"),
    DECIMAL_FRACTION_CONVERSION("decimal-fraction-conversion", "Conversión decimal-fracción"),
    PERCENTAGE_REASONING("percentage-reasoning", "Razonamiento con porcentajes"),
    EXPONENT_RULES("exponent-rules", "Reglas de potencias"),
    ALGEBRAIC_EXPRESSIONS("algebraic-expressions", "Expresiones algebraicas"),
    DISTRIBUTIVE_PROPERTY("distributive-property", "Propiedad distributiva"),
    COMBINING_LIKE_TERMS("combining-like-terms", "Agrupar términos semejantes"),
    SIGN_HANDLING("sign-handling", "Manejo de signos"),
    BALANCE_PRINCIPLE("balance-principle", "Equilibrio de la ecuación"),
    ISOLATING_VARIABLE("isolating-variable", "Aislar la incógnita"),
    DOMAIN_RESTRICTIONS("domain-restrictions", "Restricciones del dominio"),
    SOLUTION_VERIFICATION("solution-verification", "Verificación por sustitución"),
    EQUIVALENCE("equivalence", "Equivalencia de expresiones"),
}
