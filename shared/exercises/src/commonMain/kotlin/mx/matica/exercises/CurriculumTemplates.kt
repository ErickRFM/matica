package mx.matica.exercises

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.math.abs
import mx.matica.math.ast.Expr
import mx.matica.math.numeric.Rational
import mx.matica.math.skills.SkillId

/**
 * `x = a + b(c - d)`: jerarquía de operaciones con paréntesis que deben evaluarse
 * antes del producto y la suma.
 */
class OrderOfOperationsTemplate : ExerciseTemplate {
    override val id = "order-of-operations"
    override val labelEs = "Jerarquía de operaciones: paréntesis y precedencia"
    override val skill = SkillId.ORDER_OF_OPERATIONS
    override val variable = "x"
    override val ranges = listOf(
        ParameterRange("a", 1, 20),
        ParameterRange("b", 2, 9),
        ParameterRange("c", 2, 9),
        ParameterRange("d", 1, 9),
    )

    override fun build(params: ParameterSet, seed: Long): GenerationOutcome {
        val reasons = validateRanges(ranges, params).toMutableList()
        val a = params.of("a") ?: return rejected(reasons)
        val b = params.of("b") ?: return rejected(reasons)
        val c = params.of("c") ?: return rejected(reasons)
        val d = params.of("d") ?: return rejected(reasons)
        if (reasons.isNotEmpty()) return GenerationOutcome.Rejected(reasons)
        if (c == d) {
            return GenerationOutcome.Rejected(listOf("'c' = 'd' ($c) anula el paréntesis: no hay operación que priorizar."))
        }

        val inner = Expr.Sum(
            listOf(
                ExerciseAst.term(c < 0, ExerciseAst.const(abs(c).toLong())),
                ExerciseAst.term(d > 0, ExerciseAst.const(abs(d).toLong())),
            ),
        )
        val product = Expr.Product(
            listOf(
                Expr.Product.Factor(explicit = false, operand = ExerciseAst.const(b.toLong())),
                Expr.Product.Factor(explicit = false, operand = inner),
            ),
        )
        val right = Expr.Sum(
            listOf(
                ExerciseAst.term(a < 0, ExerciseAst.const(abs(a).toLong())),
                ExerciseAst.term(false, product),
            ),
        )
        val equation = Expr.Equals(left = ExerciseAst.variable(variable), right = right)
        val solution = a.toLong() + b.toLong() * (c.toLong() - d.toLong())
        return ready(equation, solution, params, seed)
    }
}

/**
 * `ax + bx + c = d`: dos términos con incógnita que deben combinarse antes de aislar `x`.
 */
class CombiningLikeTermsTemplate : ExerciseTemplate {
    override val id = "combining-like-terms"
    override val labelEs = "Agrupar términos semejantes"
    override val skill = SkillId.COMBINING_LIKE_TERMS
    override val variable = "x"
    override val ranges = listOf(
        ParameterRange("a", 2, 9),
        ParameterRange("b", -9, 9),
        ParameterRange("c", -15, 15),
        ParameterRange("k", -10, 10),
    )

    override fun build(params: ParameterSet, seed: Long): GenerationOutcome {
        val reasons = validateRanges(ranges, params).toMutableList()
        val a = params.of("a") ?: return rejected(reasons)
        val b = params.of("b") ?: return rejected(reasons)
        val c = params.of("c") ?: return rejected(reasons)
        val k = params.of("k") ?: return rejected(reasons)
        if (reasons.isNotEmpty()) return GenerationOutcome.Rejected(reasons)
        if (b == 0) {
            return GenerationOutcome.Rejected(listOf("'b' = 0: se necesita un segundo término con '$variable' para agrupar."))
        }
        if (a + b == 0) {
            return GenerationOutcome.Rejected(listOf("'a' + 'b' = 0 cancela los términos semejantes de '$variable'."))
        }
        if (c == 0) {
            return GenerationOutcome.Rejected(listOf("'c' = 0 omite la constante del lado izquierdo."))
        }

        val terms = listOf(
            ExerciseAst.term(a < 0, ExerciseAst.coefficientTerm(abs(a).toLong(), variable)),
            ExerciseAst.term(b < 0, ExerciseAst.coefficientTerm(abs(b).toLong(), variable)),
            ExerciseAst.term(c < 0, ExerciseAst.const(abs(c).toLong())),
        )
        val left = Expr.Sum(terms)
        val rightValue = (a + b).toLong() * k.toLong() + c.toLong()
        val equation = Expr.Equals(left = left, right = ExerciseAst.const(rightValue))
        return ready(equation, k.toLong(), params, seed)
    }
}

/**
 * `-(a - x) = c`: el signo negativo exterior distribuye invirtiendo signos (`-a + x`),
 * previniendo el error habitual de omitir el cambio de signo del segundo término.
 */
class SignHandlingTemplate : ExerciseTemplate {
    override val id = "sign-handling"
    override val labelEs = "Manejo de signos: distribución del negativo"
    override val skill = SkillId.SIGN_HANDLING
    override val variable = "x"
    override val ranges = listOf(
        ParameterRange("a", 2, 15),
        ParameterRange("k", -10, 10),
    )

    override fun build(params: ParameterSet, seed: Long): GenerationOutcome {
        val reasons = validateRanges(ranges, params).toMutableList()
        val a = params.of("a") ?: return rejected(reasons)
        val k = params.of("k") ?: return rejected(reasons)
        if (reasons.isNotEmpty()) return GenerationOutcome.Rejected(reasons)
        if (k == a) {
            return GenerationOutcome.Rejected(listOf("'k' = 'a' ($k) produce 0 en el lado derecho."))
        }

        val inner = Expr.Sum(
            listOf(
                ExerciseAst.term(false, ExerciseAst.const(a.toLong())),
                ExerciseAst.term(true, ExerciseAst.variable(variable)),
            ),
        )
        val left = Expr.Neg(inner)
        val equation = Expr.Equals(left = left, right = ExerciseAst.const((k - a).toLong()))
        return ready(equation, k.toLong(), params, seed)
    }
}

/**
 * `ax + b = cx + d`: términos con incógnita en ambos lados que exigen agrupar la incógnita
 * en un solo miembro para aislarla.
 */
class IsolatingVariableTemplate : ExerciseTemplate {
    override val id = "isolating-variable"
    override val labelEs = "Aislar la incógnita: términos con variable en ambos miembros"
    override val skill = SkillId.ISOLATING_VARIABLE
    override val variable = "x"
    override val ranges = listOf(
        ParameterRange("a", -9, 9),
        ParameterRange("b", -15, 15),
        ParameterRange("c", -9, 9),
        ParameterRange("k", -10, 10),
    )

    override fun build(params: ParameterSet, seed: Long): GenerationOutcome {
        val reasons = validateRanges(ranges, params).toMutableList()
        val a = params.of("a") ?: return rejected(reasons)
        val b = params.of("b") ?: return rejected(reasons)
        val c = params.of("c") ?: return rejected(reasons)
        val k = params.of("k") ?: return rejected(reasons)
        if (reasons.isNotEmpty()) return GenerationOutcome.Rejected(reasons)
        if (a == 0) return GenerationOutcome.Rejected(listOf("'a' = 0 anula la incógnita en el lado izquierdo."))
        if (c == 0) return GenerationOutcome.Rejected(listOf("'c' = 0 deja la incógnita en un solo miembro."))
        if (a == c) return GenerationOutcome.Rejected(listOf("'a' = 'c' ($a) cancela la incógnita al agrupar."))
        if (b == 0) return GenerationOutcome.Rejected(listOf("'b' = 0 omite la constante izquierda."))

        val left = ExerciseAst.linearSum(a.toLong(), b.toLong(), variable)
        val d = (a - c).toLong() * k.toLong() + b.toLong()
        val right = ExerciseAst.linearSum(c.toLong(), d, variable)
        val equation = Expr.Equals(left = left, right = right)
        return ready(equation, k.toLong(), params, seed)
    }
}

/**
 * `x = 0.25`: conversión de un número decimal exacto a su fracción irreducible correspondiente.
 */
class DecimalFractionConversionTemplate : ExerciseTemplate {
    override val id = "decimal-fraction-conversion"
    override val labelEs = "Conversión de decimal a fracción irreducible"
    override val skill = SkillId.DECIMAL_FRACTION_CONVERSION
    override val variable = "x"
    override val ranges = listOf(
        ParameterRange("p", 1, 19),
    )

    override fun build(params: ParameterSet, seed: Long): GenerationOutcome {
        val reasons = validateRanges(ranges, params).toMutableList()
        val p = params.of("p") ?: return rejected(reasons)
        if (reasons.isNotEmpty()) return GenerationOutcome.Rejected(reasons)

        val hundredths = p * 5
        val decimalStr = if (hundredths % 10 == 0) {
            "0.${hundredths / 10}"
        } else if (hundredths < 10) {
            "0.0$hundredths"
        } else {
            "0.$hundredths"
        }
        val solution = Rational.of(hundredths.toLong(), 100L)
        val equationText = "$variable = $decimalStr"
        return ready(equationText, solution, params, seed)
    }
}

/**
 * `x = P% * Total`: razonamiento proporcional con porcentajes calculando el valor exacto.
 */
class PercentageReasoningTemplate : ExerciseTemplate {
    override val id = "percentage-reasoning"
    override val labelEs = "Razonamiento con porcentajes"
    override val skill = SkillId.PERCENTAGE_REASONING
    override val variable = "x"
    override val ranges = listOf(
        ParameterRange("p", 1, 12),
        ParameterRange("m", 1, 15),
    )

    override fun build(params: ParameterSet, seed: Long): GenerationOutcome {
        val reasons = validateRanges(ranges, params).toMutableList()
        val p = params.of("p") ?: return rejected(reasons)
        val m = params.of("m") ?: return rejected(reasons)
        if (reasons.isNotEmpty()) return GenerationOutcome.Rejected(reasons)

        val rate = p * 5
        val gcd = BigInteger.fromInt(rate).gcd(BigInteger.fromInt(100)).intValue()
        val base = 100 / gcd
        val total = m * base
        val solution = (rate.toLong() * total.toLong()) / 100L

        val product = Expr.Product(
            listOf(
                Expr.Product.Factor(explicit = true, operand = Expr.Percent(ExerciseAst.const(rate.toLong()))),
                Expr.Product.Factor(explicit = true, operand = ExerciseAst.const(total.toLong())),
            ),
        )
        val equation = Expr.Equals(left = ExerciseAst.variable(variable), right = product)
        return ready(equation, solution, params, seed)
    }
}

/**
 * `x = b^m * b^n`: regla del producto de potencias de la misma base (`b^(m+n)`).
 */
class ExponentRulesTemplate : ExerciseTemplate {
    override val id = "exponent-rules"
    override val labelEs = "Reglas de potencias: producto de potencias de igual base"
    override val skill = SkillId.EXPONENT_RULES
    override val variable = "x"
    override val ranges = listOf(
        ParameterRange("b", 2, 4),
        ParameterRange("m", 1, 3),
        ParameterRange("n", 1, 3),
    )

    override fun build(params: ParameterSet, seed: Long): GenerationOutcome {
        val reasons = validateRanges(ranges, params).toMutableList()
        val b = params.of("b") ?: return rejected(reasons)
        val m = params.of("m") ?: return rejected(reasons)
        val n = params.of("n") ?: return rejected(reasons)
        if (reasons.isNotEmpty()) return GenerationOutcome.Rejected(reasons)
        if (b < 2) return GenerationOutcome.Rejected(listOf("La base 'b' = $b debe ser mayor o igual a 2."))
        if (m <= 0 || n <= 0) return GenerationOutcome.Rejected(listOf("Los exponentes deben ser positivos: m=$m, n=$n."))

        var solution = 1L
        repeat(m + n) { solution *= b.toLong() }

        val factor1 = Expr.Power(ExerciseAst.const(b.toLong()), ExerciseAst.const(m.toLong()))
        val factor2 = Expr.Power(ExerciseAst.const(b.toLong()), ExerciseAst.const(n.toLong()))
        val product = Expr.Product(
            listOf(
                Expr.Product.Factor(explicit = true, operand = factor1),
                Expr.Product.Factor(explicit = true, operand = factor2),
            ),
        )
        val equation = Expr.Equals(left = ExerciseAst.variable(variable), right = product)
        return ready(equation, solution, params, seed)
    }
}

/**
 * `a/(x - b) = c`: identificación del valor que anula el denominador y queda excluido del dominio.
 */
class DomainRestrictionsTemplate : ExerciseTemplate {
    override val id = "domain-restrictions"
    override val labelEs = "Restricciones del dominio: valor prohibido en denominador"
    override val skill = SkillId.DOMAIN_RESTRICTIONS
    override val variable = "x"
    override val ranges = listOf(
        ParameterRange("a", 1, 9),
        ParameterRange("b", -9, 9),
        ParameterRange("c", 1, 9),
    )

    override fun build(params: ParameterSet, seed: Long): GenerationOutcome {
        val reasons = validateRanges(ranges, params).toMutableList()
        val a = params.of("a") ?: return rejected(reasons)
        val b = params.of("b") ?: return rejected(reasons)
        val c = params.of("c") ?: return rejected(reasons)
        if (reasons.isNotEmpty()) return GenerationOutcome.Rejected(reasons)
        if (b == 0) return GenerationOutcome.Rejected(listOf("'b' = 0 deja denominador simple: se requiere binomio (x - b)."))
        if (a == 0) return GenerationOutcome.Rejected(listOf("'a' = 0 anula el numerador."))

        val denominator = ExerciseAst.linearSum(1L, (-b).toLong(), variable)
        val left = Expr.Quotient(numerator = ExerciseAst.const(a.toLong()), denominator = denominator)
        val equation = Expr.Equals(left = left, right = ExerciseAst.const(c.toLong()))
        return ready(equation, b.toLong(), params, seed)
    }
}

/**
 * `a(x + b) + c = d`: comprobación por sustitución de la solución que satisface la ecuación.
 */
class SolutionVerificationTemplate : ExerciseTemplate {
    override val id = "solution-verification"
    override val labelEs = "Verificación por sustitución: ecuación con paréntesis y término exterior"
    override val skill = SkillId.SOLUTION_VERIFICATION
    override val variable = "x"
    override val ranges = listOf(
        ParameterRange("a", 2, 6),
        ParameterRange("b", -10, 10),
        ParameterRange("c", -15, 15),
        ParameterRange("k", -8, 8),
    )

    override fun build(params: ParameterSet, seed: Long): GenerationOutcome {
        val reasons = validateRanges(ranges, params).toMutableList()
        val a = params.of("a") ?: return rejected(reasons)
        val b = params.of("b") ?: return rejected(reasons)
        val c = params.of("c") ?: return rejected(reasons)
        val k = params.of("k") ?: return rejected(reasons)
        if (reasons.isNotEmpty()) return GenerationOutcome.Rejected(reasons)
        if (b == 0) return GenerationOutcome.Rejected(listOf("'b' = 0 deja un paréntesis vacío: no hay nada que distribuir."))
        if (c == 0) return GenerationOutcome.Rejected(listOf("'c' = 0 omite el término exterior."))

        val product = Expr.Product(
            listOf(
                Expr.Product.Factor(explicit = false, operand = ExerciseAst.const(a.toLong())),
                Expr.Product.Factor(explicit = false, operand = ExerciseAst.innerSum(b.toLong(), variable)),
            ),
        )
        val left = Expr.Sum(
            listOf(
                Expr.Sum.Term(operator = Expr.Sum.Term.Operator.PLUS, operand = product),
                ExerciseAst.term(c < 0, ExerciseAst.const(abs(c).toLong())),
            ),
        )
        val d = a.toLong() * (k.toLong() + b.toLong()) + c.toLong()
        val equation = Expr.Equals(left = left, right = ExerciseAst.const(d))
        return ready(equation, k.toLong(), params, seed)
    }
}

/**
 * `a(x + b) + c(x + d) = e`: equivalencia algebraica requiriendo distribuir y combinar
 * términos semejantes antes de aislar la incógnita.
 */
class EquivalenceTemplate : ExerciseTemplate {
    override val id = "equivalence"
    override val labelEs = "Equivalencia de expresiones: suma de términos distribuidos"
    override val skill = SkillId.EQUIVALENCE
    override val variable = "x"
    override val ranges = listOf(
        ParameterRange("a", 2, 5),
        ParameterRange("b", -8, 8),
        ParameterRange("c", 2, 5),
        ParameterRange("d", -8, 8),
        ParameterRange("k", -6, 6),
    )

    override fun build(params: ParameterSet, seed: Long): GenerationOutcome {
        val reasons = validateRanges(ranges, params).toMutableList()
        val a = params.of("a") ?: return rejected(reasons)
        val b = params.of("b") ?: return rejected(reasons)
        val c = params.of("c") ?: return rejected(reasons)
        val d = params.of("d") ?: return rejected(reasons)
        val k = params.of("k") ?: return rejected(reasons)
        if (reasons.isNotEmpty()) return GenerationOutcome.Rejected(reasons)
        if (b == 0 || d == 0) {
            return GenerationOutcome.Rejected(listOf("Los paréntesis requieren sumandos no nulos: b=$b, d=$d."))
        }
        if (a + c == 0) {
            return GenerationOutcome.Rejected(listOf("'a' + 'c' = 0 elimina la incógnita al combinar."))
        }

        val p1 = Expr.Product(
            listOf(
                Expr.Product.Factor(explicit = false, operand = ExerciseAst.const(a.toLong())),
                Expr.Product.Factor(explicit = false, operand = ExerciseAst.innerSum(b.toLong(), variable)),
            ),
        )
        val p2 = Expr.Product(
            listOf(
                Expr.Product.Factor(explicit = false, operand = ExerciseAst.const(c.toLong())),
                Expr.Product.Factor(explicit = false, operand = ExerciseAst.innerSum(d.toLong(), variable)),
            ),
        )
        val left = Expr.Sum(
            listOf(
                Expr.Sum.Term(operator = Expr.Sum.Term.Operator.PLUS, operand = p1),
                Expr.Sum.Term(operator = Expr.Sum.Term.Operator.PLUS, operand = p2),
            ),
        )
        val e = a.toLong() * (k.toLong() + b.toLong()) + c.toLong() * (k.toLong() + d.toLong())
        val equation = Expr.Equals(left = left, right = ExerciseAst.const(e))
        return ready(equation, k.toLong(), params, seed)
    }
}
