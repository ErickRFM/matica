package mx.matica.presentation.app

import mx.matica.math.MathEngine
import mx.matica.math.analysis.StepError
import mx.matica.math.analysis.StepVerdict
import mx.matica.math.ast.Expr
import mx.matica.math.ast.isConstantExpression
import mx.matica.math.eval.EvalOutcome
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
 *
 * El AST conserva la forma tal como se escribió (`-9` es `Neg(Const(9))`, no `Const(-9)`), así que la
 * única manera de reconocer un número es evaluar la expresión: mirar `Expr.Const` devolvía `null`
 * para los negativos y las fracciones que la propia pantalla promete aceptar.
 */
fun parseNumber(math: MathEngine, text: String): Rational? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val expression = math.parseOrNull(trimmed) ?: return null
    if (expression is Expr.Equals || expression is Expr.Quantity) return null
    if (!expression.isConstantExpression()) return null
    return (math.evaluate(trimmed) as? EvalOutcome.Value)?.value
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

/**
 * Ventana del plano: en x, exactamente el intervalo que ya muestra la recta del ejercicio, para que
 * el mismo valor caiga en el mismo sitio horizontal de las dos superficies. Cada una tenía su propio
 * `±4` escrito a mano y cambiar uno bastaba para que recta y plano enseñaran el mismo punto en
 * escalas distintas. En y manda el recorrido de las rectas trazadas.
 */
data class GraphWindow(val xFrom: Rational, val xTo: Rational, val yFrom: Rational, val yTo: Rational)

fun graphWindow(plan: ExerciseRenderPlan): GraphWindow {
    val xFrom = plan.numberLine.from
    val xTo = plan.numberLine.to
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

/**
 * Motivo corto del rechazo, para el campo donde se escribió la línea. La tarjeta de veredicto ya
 * muestra el detalle completo; repetir los dos en la misma pantalla deja la misma frase pegada una
 * debajo de la otra (así lo midió la sonda del 2026-11-06 en el reproductor). `null` cuando no hay
 * rechazo que señalar junto al campo.
 */
fun verdictReason(verdict: StepVerdict): String? = when (verdict) {
    is StepVerdict.Invalid -> verdict.error.labelEs
    is StepVerdict.SyntaxProblem -> verdict.reason
    else -> null
}

fun errorSkillsOf(verdict: StepVerdict): Set<StepError> = when (verdict) {
    is StepVerdict.Invalid -> setOf(verdict.error)
    else -> emptySet()
}

/**
 * Pregunta socrática orientadora cuando el paso es inválido y tiene una guía pedagógica disponible.
 */
fun socraticPromptOf(verdict: StepVerdict): String? = (verdict as? StepVerdict.Invalid)?.socraticPrompt

fun answerMessage(outcome: VerificationOutcome, proposal: Rational): Pair<String, String> = when (outcome) {
    is VerificationOutcome.Satisfies ->
        "$proposal sí resuelve la ecuación" to "Sustituido, los dos lados quedan iguales."

    is VerificationOutcome.DoesNotSatisfy ->
        "$proposal no resuelve la ecuación" to "El lado izquierdo da ${outcome.left} y el derecho ${outcome.right}."

    is VerificationOutcome.ExcludedFromDomain ->
        "$proposal está prohibida" to outcome.reason

    is VerificationOutcome.Blocked -> "Todavía no se puede comprobar" to outcome.reason
}
