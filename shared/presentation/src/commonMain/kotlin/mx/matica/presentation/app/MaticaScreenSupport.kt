package mx.matica.presentation.app

import mx.matica.math.MathEngine
import mx.matica.math.analysis.StepError
import mx.matica.math.analysis.StepVerdict
import mx.matica.math.ast.Expr
import mx.matica.math.numeric.Rational
import mx.matica.math.verify.VerificationOutcome
import mx.matica.presentation.exercise.ExerciseRenderPlan
import mx.matica.presentation.exercise.PlotLine
import mx.matica.presentation.math.PlotSeries
import mx.matica.presentation.math.plotSeries
import mx.matica.presentation.math.samplePlot

/**
 * Lectura de un número escrito por el estudiante. Se apoya en el motor y no en `toDouble`: «3/4» debe
 * llegar exacto, y «3,x» o «hola» devuelven `null` en lugar de un número aproximado que la pantalla
 * aceptaría como respuesta.
 */
fun parseNumber(math: MathEngine, text: String): Rational? {
    val expression = math.parseOrNull(text.trim()) ?: return null
    return when (expression) {
        is Expr.Const -> expression.value
        else -> null
    }
}

/**
 * Recta del plano en el vecindario de la solución. El rango vertical es el de las dos rectas juntas,
 * redondeado a enteros: un eje con marcas arbitrarias enseñaría a leer mal una gráfica.
 */
fun seriesOf(
    line: PlotLine,
    xFrom: Rational,
    xTo: Rational,
    yFrom: Rational,
    yTo: Rational,
    samples: Int = 41,
): PlotSeries {
    val points = samplePlot(
        f = { x -> line.slope * x + line.intercept },
        xFrom = xFrom,
        xTo = xTo,
        samples = samples,
    )
    return plotSeries(points, xFrom, xTo, yFrom, yTo)
}

/** Ventana del plano: la solución centrada y las dos rectas evaluadas en ella. */
data class GraphWindow(val xFrom: Rational, val xTo: Rational, val yFrom: Rational, val yTo: Rational)

fun graphWindow(plan: ExerciseRenderPlan): GraphWindow {
    val span = Rational.fromLong(4L)
    val xFrom = plan.solution - span
    val xTo = plan.solution + span
    val ys = plan.lines.flatMap { line -> listOf(line.slope * xFrom + line.intercept, line.slope * xTo + line.intercept) }
    val lowest = ys.minOrNull() ?: Rational.ZERO
    val highest = ys.maxOrNull() ?: Rational.ONE
    val pad = ((highest - lowest) / Rational.of(8, 1)).let { if (it.isZero) Rational.ONE else it }
    return GraphWindow(xFrom = xFrom, xTo = xTo, yFrom = lowest - pad, yTo = highest + pad)
}

/** Qué dice la pantalla después de un paso. El texto siempre viene del veredicto, nunca de la UI. */
fun verdictMessage(verdict: StepVerdict): Pair<String, String> = when (verdict) {
    is StepVerdict.Valid -> "Paso válido" to "Regla aplicada: ${verdict.rule.labelEs}. ${verdict.notes.joinToString(" ")}"
    is StepVerdict.Invalid -> "Paso incorrecto" to "${verdict.error.labelEs}. ${verdict.detail} ${verdict.repairHint}"
    is StepVerdict.Indeterminate -> "No se puede decidir" to verdict.reason
    is StepVerdict.SyntaxProblem -> "No se entiende la escritura" to "${verdict.reason} en la posición ${verdict.position}. ${verdict.suggestion}"
}

fun errorSkillsOf(verdict: StepVerdict): Set<StepError> = when (verdict) {
    is StepVerdict.Invalid -> setOf(verdict.error)
    else -> emptySet()
}

fun answerMessage(outcome: VerificationOutcome, proposal: Rational): Pair<String, String> = when (outcome) {
    is VerificationOutcome.Satisfies ->
        "$proposal sí resuelve la ecuación" to "Sustituido, los dos lados quedan iguales."

    is VerificationOutcome.DoesNotSatisfy ->
        "$proposal no resuelve la ecuación" to "El lado izquierdo da ${outcome.left} y el derecho ${outcome.right}."

    is VerificationOutcome.ExcludedFromDomain ->
        "$proposal está prohibida" to outcome.reason

    is VerificationOutcome.Blocked -> "Todavía no se puede comprobar" to outcome.reason
}
