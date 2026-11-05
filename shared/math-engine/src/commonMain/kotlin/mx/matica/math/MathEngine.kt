package mx.matica.math

import mx.matica.math.analysis.StepAnalyzer
import mx.matica.math.analysis.StepVerdict
import mx.matica.math.ast.Expr
import mx.matica.math.domain.DomainRestrictions
import mx.matica.math.domain.Restriction
import mx.matica.math.eval.EvalOutcome
import mx.matica.math.eval.Evaluator
import mx.matica.math.hints.Hint
import mx.matica.math.hints.HintLevel
import mx.matica.math.hints.HintProvider
import mx.matica.math.numeric.Rational
import mx.matica.math.parser.ExprParser
import mx.matica.math.parser.ParseOutcome
import mx.matica.math.remote.RemoteSolutions
import mx.matica.math.remote.RemoteStep
import mx.matica.math.remote.SymbolicGateway
import mx.matica.math.printer.ExprPrinter
import mx.matica.math.solve.LinearSolver
import mx.matica.math.solve.SolveOutcome
import mx.matica.math.verify.SolutionVerifier
import mx.matica.math.verify.VerificationOutcome

/**
 * Fachada única que consume la app y el backend. Ninguna otra capa debe instanciar el parser,
 * el normalizador o el analizador por su cuenta: así es imposible que dos pantallas den
 * veredictos distintos para la misma entrada.
 */
class MathEngine(
    private val stepAnalyzer: StepAnalyzer = StepAnalyzer(),
    private val hintProvider: HintProvider = HintProvider(),
    private val remote: SymbolicGateway? = null,
) {

    fun parse(input: String): ParseOutcome = ExprParser(input).parse()

    fun parseOrNull(input: String): Expr? = (parse(input) as? ParseOutcome.Success)?.expression

    fun render(expression: Expr): String = ExprPrinter.render(expression)

    fun evaluate(input: String, environment: Map<String, Rational> = emptyMap()): EvalOutcome =
        when (val parsed = ExprParser(input).parse()) {
            is ParseOutcome.Success -> Evaluator.evaluate(parsed.expression, environment)
            is ParseOutcome.Failure -> EvalOutcome.Unsupported("No se pudo leer '$input': ${parsed.reason}.")
        }

    fun restrictions(input: String): Set<Restriction> {
        val expression = parseOrNull(input) ?: return setOf(Restriction.Unresolved("la entrada '$input' no es una expresión válida"))
        return DomainRestrictions.of(expression)
    }

    fun solve(input: String): SolveOutcome {
        val expression = parseOrNull(input)
            ?: return SolveOutcome.Blocked("No se pudo leer la ecuación.")
        return LinearSolver.solve(expression)
    }

    /** Verifica una propuesta de solución por sustitución en la ecuación original. */
    fun verifySolution(input: String, variable: String, value: Rational): VerificationOutcome =
        SolutionVerifier.verify(input, variable, value)

    /** Valida un paso del procedimiento entre dos líneas escritas por el estudiante. */
    fun checkStep(beforeText: String, afterText: String): StepVerdict = stepAnalyzer.analyze(beforeText, afterText)

    fun hints(equationText: String, upTo: HintLevel = HintLevel.NAME_RULE): List<Hint> =
        hintProvider.hintsFor(equationText, upTo)

    /** Valida un paso y, si y solo si lo offline quedó indeterminado, pide el análisis ampliado. */
    suspend fun checkStepExtended(beforeText: String, afterText: String): StepAssessment {
        val offline = checkStep(beforeText, afterText)
        val gateway = remote ?: return StepAssessment(offline, null)
        if (offline !is StepVerdict.Indeterminate) return StepAssessment(offline, null)

        val before = parseOrNull(beforeText) as? Expr.Equals ?: return StepAssessment(offline, null)
        val after = parseOrNull(afterText) as? Expr.Equals ?: return StepAssessment(offline, null)
        return StepAssessment(offline, gateway.classifyStep(before, after))
    }

    /** Resuelve offline y delega solo cuando el resolutor lineal se declara sin capacidad. */
    suspend fun solveExtended(input: String): SolutionAssessment {
        val offline = solve(input)
        val gateway = remote ?: return SolutionAssessment(offline, null)
        if (offline !is SolveOutcome.Blocked) return SolutionAssessment(offline, null)

        val equation = parseOrNull(input) as? Expr.Equals ?: return SolutionAssessment(offline, null)
        return SolutionAssessment(offline, gateway.solve(equation))
    }
}

/** Veredicto offline acompañable por el cálculo ampliado. `remote` nulo significa "no hizo falta o no hay servicio". */
data class StepAssessment(val offline: StepVerdict, val remote: RemoteStep?)

data class SolutionAssessment(val offline: SolveOutcome, val remote: RemoteSolutions?)
