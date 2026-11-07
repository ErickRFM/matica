package mx.matica.exercises

import mx.matica.math.ast.Expr
import mx.matica.math.numeric.Rational
import mx.matica.math.printer.ExprPrinter
import mx.matica.math.skills.SkillId

/** `ax + b = c`: la forma canónica del despeje de dos operaciones. */
class TwoStepLinearTemplate : ExerciseTemplate {
    override val id = "two-step-linear"
    override val labelEs = "Ecuación lineal de dos operaciones"
    override val skill = SkillId.BALANCE_PRINCIPLE
    override val variable = "x"
    override val ranges = listOf(
        ParameterRange("a", -9, 9),
        ParameterRange("b", -20, 20),
        ParameterRange("k", -12, 12),
    )

    override fun build(params: ParameterSet, seed: Long): GenerationOutcome {
        val reasons = validateRanges(ranges, params).toMutableList()
        val a = params.of("a") ?: return rejected(reasons)
        val b = params.of("b") ?: return rejected(reasons)
        val k = params.of("k") ?: return rejected(reasons)
        if (reasons.isNotEmpty()) return GenerationOutcome.Rejected(reasons)
        if (a == 0) return GenerationOutcome.Rejected(listOf("'a' = 0 anula la incógnita: no hay nada que despejar."))

        val right = ExerciseAst.const(a.toLong() * k + b)
        val equation = Expr.Equals(left = ExerciseAst.linearSum(a.toLong(), b.toLong(), variable), right = right)
        return ready(equation, k.toLong(), params, seed)
    }
}

/** `a(x + b) = c`: exige distribuir antes de poder despejar. */
class DistributiveLinearTemplate : ExerciseTemplate {
    override val id = "distributive-linear"
    override val labelEs = "Paréntesis que hay que distribuir"
    override val skill = SkillId.DISTRIBUTIVE_PROPERTY
    override val variable = "x"
    override val ranges = listOf(
        ParameterRange("a", 2, 9),
        ParameterRange("b", -15, 15),
        ParameterRange("k", -10, 10),
    )

    override fun build(params: ParameterSet, seed: Long): GenerationOutcome {
        val reasons = validateRanges(ranges, params).toMutableList()
        val a = params.of("a") ?: return rejected(reasons)
        val b = params.of("b") ?: return rejected(reasons)
        val k = params.of("k") ?: return rejected(reasons)
        if (reasons.isNotEmpty()) return GenerationOutcome.Rejected(reasons)
        if (b == 0) {
            return GenerationOutcome.Rejected(listOf("'b' = 0 deja un paréntesis vacío: no hay nada que distribuir."))
        }

        val leftSide = Expr.Product(
            listOf(
                Expr.Product.Factor(explicit = false, ExerciseAst.const(a.toLong())),
                Expr.Product.Factor(explicit = false, ExerciseAst.innerSum(b.toLong(), variable)),
            ),
        )
        val equation = Expr.Equals(left = leftSide, right = ExerciseAst.const(a.toLong() * (k + b)))
        return ready(equation, k.toLong(), params, seed)
    }
}

/** `(x + b) / d = a`: el denominador es constante y distinto de cero, así el ejercicio nunca nace proibido. */
class FractionLinearTemplate : ExerciseTemplate {
    override val id = "fraction-linear"
    override val labelEs = "Ecuación lineal con denominador"
    override val skill = SkillId.FRACTION_ARITHMETIC
    override val variable = "x"
    override val ranges = listOf(
        ParameterRange("b", -15, 15),
        // |d| ≥ 2: con d = 1 el ejercicio sería una resta disfrazada.
        ParameterRange("d", 2, 9),
        ParameterRange("a", -8, 8),
    )

    override fun build(params: ParameterSet, seed: Long): GenerationOutcome {
        val reasons = validateRanges(ranges, params).toMutableList()
        val b = params.of("b") ?: return rejected(reasons)
        val d = params.of("d") ?: return rejected(reasons)
        val a = params.of("a") ?: return rejected(reasons)
        if (reasons.isNotEmpty()) return GenerationOutcome.Rejected(reasons)

        val k = a * d - b
        val equation = Expr.Equals(
            left = Expr.Quotient(
                numerator = ExerciseAst.linearSum(1L, b.toLong(), variable),
                denominator = ExerciseAst.const(d.toLong()),
            ),
            right = ExerciseAst.const(a.toLong()),
        )
        return ready(equation, k.toLong(), params, seed)
    }
}

/**
 * `2(x + b) = P`: el perímetro de un rectángulo de lados `x` y `b`. Es la única plantilla geométrica
 * del catálogo porque es la única lectura que no añade información falsa: los cuatro lados y el
 * total salen de la propia ecuación, y el dibujo no tiene que inventarse ninguna medida.
 */
class PerimeterRectangleTemplate : ExerciseTemplate {
    override val id = "rectangle-perimeter"
    override val labelEs = "Rectángulo: el lado que falta en el perímetro"
    override val skill = SkillId.ALGEBRAIC_EXPRESSIONS
    override val variable = "x"
    override val ranges = listOf(
        // Lados positivos por construcción: un lado negativo no se puede rotular en la figura.
        ParameterRange("b", 1, 12),
        ParameterRange("k", 1, 15),
    )

    override fun build(params: ParameterSet, seed: Long): GenerationOutcome {
        val reasons = validateRanges(ranges, params).toMutableList()
        val b = params.of("b") ?: return rejected(reasons)
        val k = params.of("k") ?: return rejected(reasons)
        if (reasons.isNotEmpty()) return GenerationOutcome.Rejected(reasons)
        if (b <= 0) return GenerationOutcome.Rejected(listOf("'b' = $b no es un lado: la figura no rotula longitudes negativas."))
        if (k <= 0) return GenerationOutcome.Rejected(listOf("'k' = $k dejaría un lado de cero o menos."))

        val leftSide = Expr.Product(
            listOf(
                Expr.Product.Factor(explicit = false, ExerciseAst.const(2L)),
                Expr.Product.Factor(explicit = false, ExerciseAst.innerSum(b.toLong(), variable)),
            ),
        )
        val equation = Expr.Equals(left = leftSide, right = ExerciseAst.const(2L * (k + b)))
        return ready(equation, k.toLong(), params, seed)
    }
}

/** Currículo completo de plantillas: 15 habilidades del grafo curricular. */
object ExerciseTemplates {
    val all: List<ExerciseTemplate> = listOf(
        IntegerOpsTemplate(),
        OrderOfOperationsTemplate(),
        FractionLinearTemplate(),
        DecimalFractionConversionTemplate(),
        PercentageReasoningTemplate(),
        ExponentRulesTemplate(),
        PerimeterRectangleTemplate(),
        DistributiveLinearTemplate(),
        CombiningLikeTermsTemplate(),
        SignHandlingTemplate(),
        TwoStepLinearTemplate(),
        IsolatingVariableTemplate(),
        DomainRestrictionsTemplate(),
        SolutionVerificationTemplate(),
        EquivalenceTemplate(),
    )

    fun byId(id: String): ExerciseTemplate? = all.firstOrNull { it.id == id }
}

internal fun ExerciseTemplate.ready(equation: Expr.Equals, solution: Long, params: ParameterSet, seed: Long): GenerationOutcome =
    GenerationOutcome.Ready(
        ExerciseInstance(
            templateId = id,
            variable = variable,
            equationText = ExprPrinter.render(equation),
            solution = Rational.fromLong(solution),
            parameters = params,
            seed = seed,
        ),
    )

internal fun ExerciseTemplate.ready(equation: Expr.Equals, solution: Rational, params: ParameterSet, seed: Long): GenerationOutcome =
    GenerationOutcome.Ready(
        ExerciseInstance(
            templateId = id,
            variable = variable,
            equationText = ExprPrinter.render(equation),
            solution = solution,
            parameters = params,
            seed = seed,
        ),
    )

internal fun ExerciseTemplate.ready(equationText: String, solution: Rational, params: ParameterSet, seed: Long): GenerationOutcome =
    GenerationOutcome.Ready(
        ExerciseInstance(
            templateId = id,
            variable = variable,
            equationText = equationText,
            solution = solution,
            parameters = params,
            seed = seed,
        ),
    )

internal fun rejected(reasons: List<String>): GenerationOutcome = GenerationOutcome.Rejected(reasons)
