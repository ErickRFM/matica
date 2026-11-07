package mx.matica.presentation.exercise

import mx.matica.exercises.ExerciseInstance
import mx.matica.math.MathEngine
import mx.matica.math.ast.Expr
import mx.matica.math.eval.EvalOutcome
import mx.matica.math.eval.Evaluator
import mx.matica.math.numeric.Rational
import mx.matica.math.poly.Monomial
import mx.matica.math.poly.Normalizer
import mx.matica.math.poly.PolyOutcome
import mx.matica.math.printer.ExprPrinter
import mx.matica.math.verify.VerificationOutcome
import mx.matica.presentation.components.StepOption
import mx.matica.presentation.math.FractionViewModel
import mx.matica.presentation.math.NumberLineModel
import mx.matica.presentation.math.RectangleFigure
import mx.matica.presentation.math.fractionModel
import mx.matica.presentation.math.rectangleFigureOf

/** Qué componente matemático le corresponde a este tipo de ejercicio. */
enum class ExerciseSlot {
    EQUATION,
    ANSWER_FRACTION,
    QUOTIENT_SPLIT,
    NUMBER_LINE,
    GRAPH,
    GEOMETRY,
    VERIFICATION,
}

/** Una recta del plano: `pendiente · x + ordenada`. El cruce de las dos rectas es la solución. */
data class PlotLine(val labelEs: String, val slope: Rational, val intercept: Rational)

/**
 * Plan de render de un ejercicio: decide qué se dibuja y con qué valores exactos. Vive fuera de los
 * composables para que «tal plantilla se representa con tales componentes» sea una prueba y no una
 * decisión escondida en un `when` de UI.
 */
data class ExerciseRenderPlan(
    val templateId: String,
    val labelEs: String,
    val strategyEs: String,
    val equationText: String,
    val variable: String,
    val solution: Rational,
    val slots: List<ExerciseSlot>,
    val answer: FractionViewModel,
    val numberLine: NumberLineModel,
    val quotientParts: List<Rational>,
    val lines: List<PlotLine>,
    val verification: List<String>,
    val verified: Boolean,
    val figure: RectangleFigure?,
    val stepOptions: List<StepOption> = emptyList(),
) {
    /** Sin dos rectas reales no hay gráfica honesta que mostrar: la pantalla omite el plano. */
    val hasGraph: Boolean get() = ExerciseSlot.GRAPH in slots && lines.size == 2

    /** La figura existe sólo si la ecuación escrita se lee como rectángulo; si no, no se dibuja. */
    val hasGeometry: Boolean get() = ExerciseSlot.GEOMETRY in slots && figure != null
}

internal data class TemplateSpec(val labelEs: String, val strategyEs: String, val slots: List<ExerciseSlot>)

private val TWO_STEP = TemplateSpec(
    labelEs = "Ecuación lineal de dos operaciones",
    strategyEs = "Deshaz en orden inverso: primero la suma o resta, después el producto.",
    slots = listOf(
        ExerciseSlot.EQUATION,
        ExerciseSlot.ANSWER_FRACTION,
        ExerciseSlot.NUMBER_LINE,
        ExerciseSlot.GRAPH,
        ExerciseSlot.GEOMETRY,
        ExerciseSlot.VERIFICATION,
    ),
)

private val DISTRIBUTIVE = TemplateSpec(
    labelEs = "Paréntesis que hay que distribuir",
    strategyEs = "Reparte el factor sobre los dos términos del paréntesis antes de despejar.",
    slots = listOf(
        ExerciseSlot.EQUATION,
        ExerciseSlot.ANSWER_FRACTION,
        ExerciseSlot.NUMBER_LINE,
        ExerciseSlot.GRAPH,
        ExerciseSlot.GEOMETRY,
        ExerciseSlot.VERIFICATION,
    ),
)

private val FRACTION = TemplateSpec(
    labelEs = "Ecuación lineal con denominador",
    strategyEs = "Reparte el numerador entre el denominador, o multiplica los dos lados por él.",
    slots = listOf(
        ExerciseSlot.EQUATION,
        ExerciseSlot.QUOTIENT_SPLIT,
        ExerciseSlot.ANSWER_FRACTION,
        ExerciseSlot.NUMBER_LINE,
        ExerciseSlot.GRAPH,
        ExerciseSlot.VERIFICATION,
    ),
)

private val RECTANGLE_PERIMETER = TemplateSpec(
    labelEs = "Rectángulo: el lado que falta en el perímetro",
    strategyEs = "El perímetro suma los cuatro lados: dos veces el largo y dos veces el ancho. Despeja el lado que falta.",
    slots = listOf(
        ExerciseSlot.EQUATION,
        ExerciseSlot.ANSWER_FRACTION,
        ExerciseSlot.NUMBER_LINE,
        ExerciseSlot.GRAPH,
        ExerciseSlot.GEOMETRY,
        ExerciseSlot.VERIFICATION,
    ),
)

private val INTEGER_OPS = TemplateSpec(
    labelEs = "Enteros: producto de signos",
    strategyEs = "Primero el producto con su signo: menos por negativo da positivo. Después se suma en orden.",
    // Sin plano: aquí la incógnita ya está aislada, y dibujar «y = x» contra una constante diría
    // que hay dos rectas discutiendo cuando sólo hay una cuenta que hacer.
    slots = listOf(
        ExerciseSlot.EQUATION,
        ExerciseSlot.NUMBER_LINE,
        ExerciseSlot.VERIFICATION,
    ),
)

/** Las plantillas del catálogo de render. `byId` coincide con `ExerciseTemplates` y la prueba lo ata. */
object ExerciseRenderCatalog {
    private val specs: Map<String, TemplateSpec> = mapOf(
        "integer-ops" to INTEGER_OPS,
        "two-step-linear" to TWO_STEP,
        "distributive-linear" to DISTRIBUTIVE,
        "fraction-linear" to FRACTION,
        "rectangle-perimeter" to RECTANGLE_PERIMETER,
    )

    val templateIds: List<String> get() = specs.keys.toList()

    internal fun specOf(templateId: String): TemplateSpec? = specs[templateId]
}

/**
 * Construye el plan desde el ejercicio ya generado. Devuelve `null` si la plantilla no está
 * registrada o si el texto no se vuelve a leer: sin una ecuación legible no hay nada que dibujar,
 * y dibujar un esquema inventado sería peor que no dibujar nada.
 */
fun renderPlan(instance: ExerciseInstance, engine: MathEngine = MathEngine()): ExerciseRenderPlan? {
    val spec = ExerciseRenderCatalog.specOf(instance.templateId) ?: return null
    val equation = engine.parseOrNull(instance.equationText) as? Expr.Equals ?: return null

    return ExerciseRenderPlan(
        templateId = instance.templateId,
        labelEs = spec.labelEs,
        strategyEs = spec.strategyEs,
        equationText = instance.equationText,
        variable = instance.variable,
        solution = instance.solution,
        slots = spec.slots,
        answer = fractionModel(instance.solution, asMixed = true),
        numberLine = numberLineAround(instance.solution),
        quotientParts = quotientSplit(equation, instance.variable),
        lines = plotLines(equation, instance.variable),
        verification = verificationLines(equation, instance.variable, instance.solution),
        verified = engine.verifySolution(instance.equationText, instance.variable, instance.solution)
            is VerificationOutcome.Satisfies,
        figure = rectangleFigureOf(equation, instance.variable, instance.solution),
        stepOptions = buildStepOptions(equation, instance.variable, instance.templateId),
    )
}

/** Recta con la solución centrada y marcas enteras alrededor: se ve dónde cae el valor. */
internal fun numberLineAround(solution: Rational): NumberLineModel {
    val span = Rational.fromLong(4L)
    val from = solution - span
    val ticks = (0L..8L).map { offset -> from + Rational.fromLong(offset) }
    return NumberLineModel(from = from, to = solution + span, ticks = ticks, markers = listOf(solution))
}

/**
 * `(x + 6)/3` se descompone en `x/3 + 6/3`: cada término del numerador entre el denominador.
 * Devuelve vacío cuando el lado izquierdo no es un cociente de una suma entre una constante,
 * porque entonces la descomposición que se mostraría no es la del ejercicio real.
 */
internal fun quotientSplit(equation: Expr.Equals, variable: String): List<Rational> {
    val quotient = equation.left as? Expr.Quotient ?: return emptyList()
    val denominator = (quotient.denominator as? Expr.Const)?.value ?: return emptyList()
    if (denominator.isZero) return emptyList()
    val terms = (quotient.numerator as? Expr.Sum)?.terms ?: return emptyList()
    return terms.mapNotNull { term ->
        val coefficient = when (val operand = term.operand) {
            is Expr.Const -> operand.value
            is Expr.Var -> if (operand.name == variable) Rational.ONE else return@mapNotNull null
            else -> return@mapNotNull null
        }
        if (term.operator == Expr.Sum.Term.Operator.MINUS) -coefficient / denominator else coefficient / denominator
    }
}

/**
 * Las dos rectas del plano. Un lado que el normalizador no puede escribir como polinomio, o que
 * tiene grado distinto de 1, deja la lista incompleta: la gráfica se omite en lugar de inventarse.
 */
internal fun plotLines(equation: Expr.Equals, variable: String): List<PlotLine> {
    val left = lineOf(equation.left, variable, "lado izquierdo") ?: return emptyList()
    val right = lineOf(equation.right, variable, "lado derecho") ?: return emptyList()
    return listOf(left, right)
}

private fun lineOf(side: Expr, variable: String, label: String): PlotLine? {
    val polynomial = (Normalizer.of(side) as? PolyOutcome.Value)?.polynomial ?: return null
    if (polynomial.degree > 1) return null
    val slope = polynomial.coefficientOf(Monomial.of(variable))
    return PlotLine(labelEs = label, slope = slope, intercept = polynomial.constantTerm)
}

/**
 * Comprobación por sustitución escrita con el texto que el motor ya evaluó: primero la sustitución
 * sin calcular, luego los dos lados reducidos. Nunca se afirma «correcto» sin el veredicto.
 */
internal fun verificationLines(equals: Expr.Equals, variable: String, value: Rational): List<String> {
    val leftFilled = Evaluator.substitute(equals.left, variable, value)
    val rightFilled = Evaluator.substitute(equals.right, variable, value)
    val leftValue = (Evaluator.evaluate(leftFilled) as? EvalOutcome.Value)?.value
    val rightValue = (Evaluator.evaluate(rightFilled) as? EvalOutcome.Value)?.value
    val substituted = "${ExprPrinter.render(leftFilled)} = ${ExprPrinter.render(rightFilled)}"
    return if (leftValue == null || rightValue == null) {
        listOf(substituted, "Al sustituir, algún lado quedó indefinido.")
    } else {
        listOf(
            substituted,
            "${render(leftValue)} = ${render(rightValue)}",
            if (leftValue == rightValue) {
                "Sustituir $variable = ${render(value)} deja ambos lados en ${render(leftValue)}."
            } else {
                "Sustituir $variable = ${render(value)} no satisface: los lados difieren."
            },
        )
    }
}

private fun render(value: Rational): String = ExprPrinter.render(Expr.Const(value))

private fun exprToLong(expr: Expr): Long? = when (expr) {
    is Expr.Const -> expr.value.toLongOrNull()
    is Expr.Neg -> exprToLong(expr.operand)?.let { -it }
    else -> null
}

/** Genera las opciones de primer paso para el andamiaje del ejercicio (Figma Screen 4). */
fun buildStepOptions(plan: ExerciseRenderPlan, engine: MathEngine = MathEngine()): List<StepOption> {
    if (plan.stepOptions.isNotEmpty()) return plan.stepOptions
    val parsed = engine.parseOrNull(plan.equationText) as? Expr.Equals ?: return fallbackStepOptions()
    return buildStepOptions(parsed, plan.variable, plan.templateId)
}

fun buildStepOptions(equation: Expr.Equals, variable: String, templateId: String = ""): List<StepOption> {
    return distributiveStepOptions(equation, variable)
        ?: twoStepStepOptions(equation, variable)
        ?: fractionStepOptions(equation, variable)
        ?: fallbackStepOptions()
}

internal fun distributiveStepOptions(equation: Expr.Equals, variable: String): List<StepOption>? {
    val (exprWithParen, otherSide) = when {
        equation.left is Expr.Product -> equation.left as Expr.Product to equation.right
        equation.right is Expr.Product -> equation.right as Expr.Product to equation.left
        else -> return null
    }

    val constFactor = exprWithParen.factors.firstNotNullOfOrNull { exprToLong(it.operand) } ?: return null
    val sumFactor = exprWithParen.factors.firstOrNull { it.operand is Expr.Sum }?.operand as? Expr.Sum ?: return null
    val c = exprToLong(otherSide) ?: return null

    var b = 0L
    for (term in sumFactor.terms) {
        val v = exprToLong(term.operand) ?: continue
        b = if (term.operator == Expr.Sum.Term.Operator.MINUS) -v else v
    }

    val a = constFactor
    val bAbs = kotlin.math.abs(b)
    val bAction = if (b >= 0) "Sumar $bAbs a ambos lados" else "Restar $bAbs a ambos lados"
    val bSign = if (b >= 0) "+ $b" else "- ${-b}"
    val rightResult = if (a != 0L && c % a == 0L) (c / a).toString() else "$c/$a"
    val resulting = "$variable $bSign = $rightResult"

    return listOf(
        StepOption(
            letter = "A",
            text = "Dividir ambos lados entre $a",
            resultingExpression = resulting,
            isCorrect = true,
        ),
        StepOption(
            letter = "B",
            text = bAction,
            resultingExpression = null,
            isCorrect = false,
        ),
        StepOption(
            letter = "C",
            text = "Multiplicar ambos lados por $a",
            resultingExpression = null,
            isCorrect = false,
        ),
    )
}

internal fun twoStepStepOptions(equation: Expr.Equals, variable: String): List<StepOption>? {
    val sum = (equation.left as? Expr.Sum) ?: return null
    var a = 0L
    var b = 0L
    for (term in sum.terms) {
        val constVal = exprToLong(term.operand)
        if (constVal != null) {
            b = if (term.operator == Expr.Sum.Term.Operator.MINUS) -constVal else constVal
            continue
        }
        when (val op = term.operand) {
            is Expr.Var -> {
                if (op.name == variable) {
                    a = if (term.operator == Expr.Sum.Term.Operator.MINUS) -1L else 1L
                }
            }
            is Expr.Product -> {
                val coeff = op.factors.firstNotNullOfOrNull { exprToLong(it.operand) } ?: 1L
                a = if (term.operator == Expr.Sum.Term.Operator.MINUS) -coeff else coeff
            }
            else -> {}
        }
    }
    if (a == 0L || b == 0L) return null
    val c = exprToLong(equation.right) ?: return null

    val bAbs = kotlin.math.abs(b)
    val correctAction = if (b > 0) "Restar $bAbs a ambos lados" else "Sumar $bAbs a ambos lados"
    val wrongAction = if (b > 0) "Sumar $bAbs a ambos lados" else "Restar $bAbs a ambos lados"
    val aStr = if (a == 1L) variable else if (a == -1L) "-$variable" else "$a$variable"
    val resulting = "$aStr = ${c - b}"

    return listOf(
        StepOption(letter = "A", text = correctAction, resultingExpression = resulting, isCorrect = true),
        StepOption(letter = "B", text = "Dividir ambos lados entre ${kotlin.math.abs(a)}", resultingExpression = null, isCorrect = false),
        StepOption(letter = "C", text = wrongAction, resultingExpression = null, isCorrect = false),
    )
}

internal fun fractionStepOptions(equation: Expr.Equals, variable: String): List<StepOption>? {
    val quotient = (equation.left as? Expr.Quotient) ?: return null
    val d = exprToLong(quotient.denominator) ?: return null
    val c = exprToLong(equation.right) ?: return null
    val sum = quotient.numerator as? Expr.Sum ?: return null
    var b = 0L
    for (term in sum.terms) {
        val v = exprToLong(term.operand) ?: continue
        b = if (term.operator == Expr.Sum.Term.Operator.MINUS) -v else v
    }
    val bSign = if (b >= 0) "+ $b" else "- ${-b}"
    val resulting = "$variable $bSign = ${c * d}"
    val bAbs = kotlin.math.abs(b)

    return listOf(
        StepOption(letter = "A", text = "Multiplicar ambos lados por $d", resultingExpression = resulting, isCorrect = true),
        StepOption(letter = "B", text = "Restar $bAbs a ambos lados", resultingExpression = null, isCorrect = false),
        StepOption(letter = "C", text = "Dividir ambos lados entre $d", resultingExpression = null, isCorrect = false),
    )
}

internal fun fallbackStepOptions(): List<StepOption> = listOf(
    StepOption(letter = "A", text = "Aislar términos con la incógnita", resultingExpression = null, isCorrect = true),
    StepOption(letter = "B", text = "Sumar término a un solo lado", resultingExpression = null, isCorrect = false),
    StepOption(letter = "C", text = "Multiplicar ambos lados por 0", resultingExpression = null, isCorrect = false),
)
