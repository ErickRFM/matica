package mx.matica.learning.diagnosis

import mx.matica.math.skills.SkillId

/**
 * Banco de 15 ítems: uno por habilidad del currículo, en cuatro bandas. Ninguna alternativa fue
 * escrita «porque sí»: `DiagnosisInstrumentValidator` demuestra con el propio motor que cada ítem
 * tiene exactamente una respuesta equivalente, así que una errata en el banco se vuelve un fallo de
 * prueba en lugar de una pregunta sin clave.
 */
object DiagnosisInstrument {

    private val basicBand: List<DiagnosisItem> = listOf(
        DiagnosisItem(
            id = "diag-integer-operations",
            skill = SkillId.INTEGER_OPERATIONS,
            kind = DiagnosisKind.EXPRESSION,
            band = 1,
            promptEs = "¿Qué número es igual a 7 − 12 + 3?",
            reference = "7 - 12 + 3",
            options = listOf("-2", "2", "-8", "8"),
        ),
        DiagnosisItem(
            id = "diag-order-of-operations",
            skill = SkillId.ORDER_OF_OPERATIONS,
            kind = DiagnosisKind.EXPRESSION,
            band = 1,
            promptEs = "¿Qué número es igual a 3 + 4 · 2?",
            reference = "3 + 4 * 2",
            options = listOf("11", "14", "8", "6"),
        ),
        DiagnosisItem(
            id = "diag-sign-handling",
            skill = SkillId.SIGN_HANDLING,
            kind = DiagnosisKind.EXPRESSION,
            band = 1,
            promptEs = "¿Qué número es igual a (−4) · (−3)?",
            reference = "(-4) * (-3)",
            options = listOf("12", "-12", "7", "-7"),
        ),
    )

    private val rationalBand: List<DiagnosisItem> = listOf(
        DiagnosisItem(
            id = "diag-fraction-arithmetic",
            skill = SkillId.FRACTION_ARITHMETIC,
            kind = DiagnosisKind.EXPRESSION,
            band = 2,
            promptEs = "¿Qué número es igual a 3/4 + 1/4?",
            reference = "3/4 + 1/4",
            options = listOf("1", "3/2", "1/2", "5/4"),
        ),
        DiagnosisItem(
            id = "diag-decimal-fraction",
            skill = SkillId.DECIMAL_FRACTION_CONVERSION,
            kind = DiagnosisKind.EXPRESSION,
            band = 2,
            promptEs = "¿Qué fracción es igual a 0.25?",
            reference = "0.25",
            options = listOf("1/4", "2/5", "1/5", "3/4"),
        ),
        DiagnosisItem(
            id = "diag-percentage",
            skill = SkillId.PERCENTAGE_REASONING,
            kind = DiagnosisKind.EXPRESSION,
            band = 2,
            promptEs = "¿Qué número es igual al 20% de 50?",
            reference = "50 * 20%",
            options = listOf("10", "100", "5", "1000"),
        ),
    )

    private val algebraBand: List<DiagnosisItem> = listOf(
        DiagnosisItem(
            id = "diag-algebraic-expressions",
            skill = SkillId.ALGEBRAIC_EXPRESSIONS,
            kind = DiagnosisKind.EXPRESSION,
            band = 3,
            promptEs = "¿Cuál expresión es igual a 2x + 3x?",
            reference = "2x + 3x",
            options = listOf("5x", "5x^2", "6x", "5"),
        ),
        DiagnosisItem(
            id = "diag-distributive",
            skill = SkillId.DISTRIBUTIVE_PROPERTY,
            kind = DiagnosisKind.EXPRESSION,
            band = 3,
            promptEs = "¿Cuál expresión es igual a 3(x + 4)?",
            reference = "3(x + 4)",
            options = listOf("3x + 12", "3x + 4", "3x + 7", "x + 12"),
        ),
        DiagnosisItem(
            id = "diag-like-terms",
            skill = SkillId.COMBINING_LIKE_TERMS,
            kind = DiagnosisKind.EXPRESSION,
            band = 3,
            promptEs = "¿Cuál expresión es igual a 5x + 2 − 2x + 6?",
            reference = "5x + 2 - 2x + 6",
            options = listOf("3x + 8", "7x + 8", "3x + 4", "3x - 8"),
        ),
    )

    private val equationBand: List<DiagnosisItem> = listOf(
        DiagnosisItem(
            id = "diag-balance",
            skill = SkillId.BALANCE_PRINCIPLE,
            kind = DiagnosisKind.EQUATION,
            band = 4,
            promptEs = "¿Qué ecuación tiene la misma solución que x + 5 = 12?",
            reference = "x + 5 = 12",
            options = listOf("x = 7", "x = 17", "x = 5", "x = 12"),
        ),
        DiagnosisItem(
            id = "diag-isolating",
            skill = SkillId.ISOLATING_VARIABLE,
            kind = DiagnosisKind.EQUATION,
            band = 4,
            promptEs = "¿Qué ecuación tiene la misma solución que 3x = 12?",
            reference = "3x = 12",
            options = listOf("x = 4", "x = 36", "x = 15", "x = 9"),
        ),
        DiagnosisItem(
            id = "diag-equivalence",
            skill = SkillId.EQUIVALENCE,
            kind = DiagnosisKind.EXPRESSION,
            band = 4,
            promptEs = "¿Cuál expresión es igual a (x + 2)²?",
            reference = "(x + 2)^2",
            options = listOf("x^2 + 4x + 4", "x^2 + 4", "x^2 + 2x + 4", "x^2 + 4x + 2"),
        ),
        DiagnosisItem(
            id = "diag-verification",
            skill = SkillId.SOLUTION_VERIFICATION,
            kind = DiagnosisKind.EQUATION,
            band = 4,
            promptEs = "¿Qué ecuación tiene la misma solución que 2(x − 3) = 4?",
            reference = "2(x - 3) = 4",
            options = listOf("x = 5", "x = 1", "x = 7", "x = 4"),
        ),
        DiagnosisItem(
            id = "diag-exponents",
            skill = SkillId.EXPONENT_RULES,
            kind = DiagnosisKind.EXPRESSION,
            band = 4,
            promptEs = "¿Cuál expresión es igual a x³ · x²?",
            reference = "x^3 * x^2",
            // `x^9` quedó fuera: el normalizador no expande potencias sobre 8, así que el motor no
            // podría afirmar nada de esa alternativa y el ítem perdería su clave demostrable.
            options = listOf("x^5", "x^6", "x^2", "2x^5"),
        ),
        DiagnosisItem(
            id = "diag-domain",
            skill = SkillId.DOMAIN_RESTRICTIONS,
            kind = DiagnosisKind.UNDEFINED_AT,
            band = 4,
            promptEs = "¿Qué valor de x hace indefinida a 7/(x − 5)?",
            reference = "7/(x - 5)",
            options = listOf("5", "0", "7", "-5"),
        ),
    )

    /** Bandas 1 a 4, un ítem por habilidad. */
    val items: List<DiagnosisItem> = basicBand + rationalBand + algebraBand + equationBand

    val skillsCovered: Set<SkillId> = items.map { it.skill }.toSet()

    fun byId(id: String): DiagnosisItem? = items.firstOrNull { it.id == id }
}
