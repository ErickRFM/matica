package mx.matica.math.analysis

import mx.matica.math.ast.Expr
import mx.matica.math.ast.containsProductWithSum
import mx.matica.math.ast.walk
import mx.matica.math.domain.DomainRestrictions
import mx.matica.math.domain.Restriction
import mx.matica.math.eval.EvalOutcome
import mx.matica.math.eval.Evaluator
import mx.matica.math.numeric.Rational
import mx.matica.math.parser.ExprParser
import mx.matica.math.parser.ParseOutcome
import mx.matica.math.poly.Monomial
import mx.matica.math.poly.Normalizer
import mx.matica.math.poly.PolyOutcome
import mx.matica.math.poly.Polynomial
import mx.matica.math.printer.ExprPrinter
import mx.matica.math.transform.RuleId

/**
 * Valida un paso del procedimiento, no solo la respuesta final.
 *
 * Idea central: dos ecuaciones del mismo problema son equivalentes cuando sus formas canónicas
 * `L - R` son proporcionales por un factor no nulo. Cuando el paso no es proporcional se intenta
 * atribuir a un error típico concreto; si eso tampoco es posible, el veredicto es `Indeterminate`
 * y no una acusación inventada.
 */
class StepAnalyzer {

    fun analyze(beforeText: String, afterText: String): StepVerdict {
        val before = ExprParser(beforeText).parse()
        val after = ExprParser(afterText).parse()
        if (before is ParseOutcome.Failure) return before.toSyntaxVerdict()
        if (after is ParseOutcome.Failure) return after.toSyntaxVerdict()

        val beforeExpression = (before as ParseOutcome.Success).expression
        val afterExpression = (after as ParseOutcome.Success).expression

        return when {
            beforeExpression is Expr.Equals && afterExpression is Expr.Equals ->
                analyzeEquation(beforeExpression, afterExpression, beforeText, afterText)

            beforeExpression is Expr.Equals || afterExpression is Expr.Equals -> StepVerdict.Indeterminate(
                "Una línea iguala dos lados y la siguiente no; escribe el '=' para poder compararlas.",
            )

            else -> analyzeExpressionStep(beforeExpression, afterExpression, beforeText)
        }
    }

    fun analyze(before: Expr, after: Expr): StepVerdict =
        analyze(ExprPrinter.render(before), ExprPrinter.render(after))

    private fun analyzeEquation(before: Expr.Equals, after: Expr.Equals, beforeText: String, afterText: String): StepVerdict {
        // Se comprueba primero: dividir entre cero bloquea la forma canónica y el paso quedaría
        // como `Indeterminate`, cuando en realidad es un error identificable.
        zeroDivisionVerdict(after, afterText)?.let { return it }

        val canonicalBefore = (Normalizer.ofEquation(before) as? PolyOutcome.Value)?.polynomial
            ?: return StepVerdict.Indeterminate(canonicalReason(before))
        val canonicalAfter = (Normalizer.ofEquation(after) as? PolyOutcome.Value)?.polynomial
            ?: return StepVerdict.Indeterminate(canonicalReason(after))

        val factor = canonicalBefore.proportionalityFactorTo(canonicalAfter)
        if (factor != null) {
            val lost = lostSolutions(before, after)
            if (lost.isNotEmpty()) return lostSolutionVerdict(lost)

            val rule = when {
                factor == Rational.ONE -> attributeStructuralRule(before, after)
                swapsSides(before, after) -> RuleId.EQUIVALENT_REWRITE
                else -> scalingRule(factor)
            }
            return StepVerdict.Valid(rule = rule, skills = rule.skills)
        }

        return diagnoseInvalid(before, after, canonicalBefore, canonicalAfter, beforeText, afterText)
    }

    private fun analyzeExpressionStep(before: Expr, after: Expr, beforeText: String): StepVerdict {
        if (MisapplicationPatterns.isPurelyNumeric(before) && MisapplicationPatterns.isPurelyNumeric(after)) {
            val correct = Evaluator.evaluate(before) as? EvalOutcome.Value
                ?: return StepVerdict.Indeterminate("La expresión anterior no se puede evaluar de forma exacta.")
            val student = Evaluator.evaluate(after) as? EvalOutcome.Value
                ?: return StepVerdict.Indeterminate("El paso del estudiante no es un número evaluable.")

            if (student.value == correct.value) {
                return StepVerdict.Valid(RuleId.EVALUATE_EXPRESSION, RuleId.EVALUATE_EXPRESSION.skills)
            }

            val leftToRight = MisapplicationPatterns.leftToRightValue(beforeText)
            if (leftToRight == student.value) {
                return StepVerdict.Invalid(
                    error = StepError.ORDER_OF_OPERATIONS,
                    detail = "Calculaste '$beforeText' de izquierda a derecha y obtuviste ${student.value}.",
                    repairHint = "Primero potencias, luego multiplicaciones y divisiones, y al final sumas y restas.",
                )
            }

            return StepVerdict.Invalid(
                error = StepError.ARITHMETIC_ERROR,
                detail = "El planteamiento coincide, pero el valor correcto de '$beforeText' es ${correct.value}, no ${student.value}.",
                repairHint = "Rehaz la cuenta respetando la jerarquía de operaciones.",
            )
        }

        val canonicalBefore = (Normalizer.of(before) as? PolyOutcome.Value)?.polynomial
            ?: return StepVerdict.Indeterminate(canonicalReason(before))
        val canonicalAfter = (Normalizer.of(after) as? PolyOutcome.Value)?.polynomial
            ?: return StepVerdict.Indeterminate(canonicalReason(after))
        // Dos expresiones sueltas (sin '=') solo son el mismo objeto si sus formas canónicas
        // coinciden término a término. La proporcionalidad vale para ecuaciones —multiplicar los
        // dos lados conserva el conjunto solución—, pero `2x+3x` y `6x` son proporcionales y no
        // son la misma expresión: aceptarlo habría inventado una corrección.
        if (canonicalAfter == canonicalBefore) {
            val rule = if (before.containsProductWithSum()) RuleId.DISTRIBUTE else RuleId.EQUIVALENT_REWRITE
            return StepVerdict.Valid(rule = rule, skills = rule.skills)
        }

        val combiningSlip = combiningLikeTermsSlip(canonicalBefore, canonicalAfter)
        if (combiningSlip != null) {
            return StepVerdict.Invalid(
                error = StepError.ARITHMETIC_ERROR,
                detail = "Los términos son semejantes pero el coeficiente correcto es ${combiningSlip.second}, no ${combiningSlip.first}.",
                repairHint = "Al reunir términos semejantes se suman los coeficientes; la parte literal no cambia.",
            )
        }

        return StepVerdict.Indeterminate(
            "La expresión resultante no es equivalente a la anterior y no puedo atribuirlo a un error típico.",
        )
    }

    private fun diagnoseInvalid(
        before: Expr.Equals,
        after: Expr.Equals,
        canonicalBefore: Polynomial,
        canonicalAfter: Polynomial,
        beforeText: String,
        afterText: String,
    ): StepVerdict {
        val sidesBefore = listOf(before.left, before.right)
        val sidesAfter = listOf(after.left, after.right)
        for (index in sidesBefore.indices) {
            val polyAfter = (Normalizer.of(sidesAfter[index]) as? PolyOutcome.Value)?.polynomial ?: continue
            val side = if (index == 0) "lado izquierdo" else "lado derecho"

            if (MisapplicationPatterns.distributionDeltas(sidesBefore[index]).any { it == polyAfter }) {
                return StepVerdict.Invalid(
                    error = StepError.DISTRIBUTION_ERROR,
                    detail = "En el $side multiplicaste el factor solo por el primer término.",
                    repairHint = "El número que multiplica el paréntesis afecta a todos los términos: 2(x+3) es 2x+6, no 2x+3.",
                )
            }

            if (MisapplicationPatterns.signDeltas(sidesBefore[index]).any { it == polyAfter }) {
                return StepVerdict.Invalid(
                    error = StepError.SIGN_ERROR,
                    detail = "Al quitar el signo negativo del $side cambiaste solo el primer término.",
                    repairHint = "El signo negativo afecta a todos los términos dentro del paréntesis.",
                )
            }
        }

        val lost = lostSolutions(before, after)
        if (lost.isNotEmpty()) return lostSolutionVerdict(lost)

        monomialFactor(canonicalBefore, canonicalAfter)?.let { (monomial, _) ->
            val candidate = monomialZeros(monomial).firstOrNull { satisfies(before, it) }
            if (candidate != null) {
                return StepVerdict.Invalid(
                    error = StepError.LOST_SOLUTION,
                    detail = "Cancelaste el factor '${monomial.key}' y con eso descartas ${candidate.first} = ${candidate.second}, que sí satisface '$beforeText'.",
                    repairHint = "Si el factor que cancelas puede valer cero, estudia ese caso por separado antes de dividir.",
                )
            }
            return StepVerdict.Indeterminate(
                "El paso divide entre '${monomial.key}' y el resultado no es equivalente; necesito el ejercicio completo para juzgarlo.",
            )
        }

        monomialFactor(canonicalAfter, canonicalBefore)?.let { (monomial, _) ->
            val intruder = monomialZeros(monomial).firstOrNull()
            if (intruder != null) {
                return StepVerdict.Invalid(
                    error = StepError.ADDED_SOLUTION,
                    detail = "Multiplicaste por '${monomial.key}', que se anula en ${intruder.first} = ${intruder.second}: esa solución no existía en '$beforeText'.",
                    repairHint = "Al multiplicar por una expresión con variable, verifica cada candidato en la ecuación original.",
                )
            }
        }

        // Los heurísticos de delta por lado asumen transformaciones lineales. En `x^2 = 4 → x = 2`
        // acusarían de operación desbalanceada un paso que en realidad es correcto: el motor offline
        // no demuestra extracción de raíces, así que lo admite en lugar de inventar una culpa.
        if (canonicalBefore.degree > 1 || canonicalAfter.degree > 1) {
            return StepVerdict.Indeterminate(
                "La línea involucra potencias de grado ${maxOf(canonicalBefore.degree, canonicalAfter.degree)} y la validación offline cubre transformaciones lineales; pide el análisis ampliado para juzgarla.",
            )
        }

        val leftDelta = sideDelta(before.left, after.left)
        val rightDelta = sideDelta(before.right, after.right)
        if (leftDelta != null && rightDelta != null && leftDelta != rightDelta) {
            val description = when {
                leftDelta.isZero -> "dejaste el lado izquierdo igual y modificaste solo el derecho"
                rightDelta.isZero -> "modificaste solo el lado izquierdo y dejaste el derecho intacto"
                else -> "aplicaste operaciones distintas a cada lado (izquierda ${leftDelta}, derecha ${rightDelta})"
            }
            return StepVerdict.Invalid(
                error = if (leftDelta.isZero || rightDelta.isZero) StepError.ONE_SIDED_OPERATION else StepError.NOT_BALANCED_OPERATION,
                detail = "En '$afterText' $description.",
                repairHint = "La ecuación se mantiene equilibrada solo si la misma operación se aplica a los dos lados.",
            )
        }

        if (sameVariablePart(canonicalBefore, canonicalAfter)) {
            return StepVerdict.Invalid(
                error = StepError.ARITHMETIC_ERROR,
                detail = "Los términos con variable coinciden; el término independiente debería ser ${expectedConstantAfter(canonicalBefore, canonicalAfter)}, no ${canonicalAfter.constantTerm}.",
                repairHint = "Vuelve a calcular solo los números, sin mover los términos con variable.",
            )
        }

        if (partialSignFlip(canonicalBefore, canonicalAfter)) {
            return StepVerdict.Invalid(
                error = StepError.SIGN_ERROR,
                detail = "Las magnitudes coinciden pero algunos términos cambiaron de signo sin razón.",
                repairHint = "Al pasar un término al otro lado del igual se invierte su signo una sola vez.",
            )
        }

        return StepVerdict.Indeterminate(
            "El paso no produce una ecuación equivalente y no puedo atribuirlo a un error típico concreto; reescribe la línea y la vuelvo a analizar.",
        )
    }

    /**
     * Atribución estructural para un paso ya validado como equivalente. Si no hay señal
     * suficiente se devuelve `EQUIVALENT_REWRITE`: verdadero, sin fingir una regla no demostrada.
     */
    private fun attributeStructuralRule(before: Expr.Equals, after: Expr.Equals): RuleId {
        val beforeHasProductWithSum = listOf(before.left, before.right).any { it.containsProductWithSum() }
        val afterHasProductWithSum = listOf(after.left, after.right).any { it.containsProductWithSum() }
        if (beforeHasProductWithSum && !afterHasProductWithSum) return RuleId.DISTRIBUTE

        termMovedAcrossEquals(before, after)?.let { return it }

        if (countSideTerms(before) > countSideTerms(after)) return RuleId.COMBINE_LIKE_TERMS

        if (rightSideBecameConstant(before, after)) return RuleId.ISOLATE_VARIABLE

        return RuleId.EQUIVALENT_REWRITE
    }

    private fun scalingRule(factor: Rational): RuleId =
        if (factor.abs < Rational.ONE) RuleId.DIVIDE_BOTH_SIDES_BY else RuleId.MULTIPLY_BOTH_SIDES_BY

    /** `5 = x` a partir de `x = 5`: los dos lados aparecen literalmente intercambiados. */
    private fun swapsSides(before: Expr.Equals, after: Expr.Equals): Boolean {
        val beforeLeft = (Normalizer.of(before.left) as? PolyOutcome.Value)?.polynomial ?: return false
        val beforeRight = (Normalizer.of(before.right) as? PolyOutcome.Value)?.polynomial ?: return false
        val afterLeft = (Normalizer.of(after.left) as? PolyOutcome.Value)?.polynomial ?: return false
        val afterRight = (Normalizer.of(after.right) as? PolyOutcome.Value)?.polynomial ?: return false
        return beforeLeft == afterRight && beforeRight == afterLeft
    }

    /** Mismos términos semejantes y coeficiente mal calculado: `(estudiante, correcto)`. */
    private fun combiningLikeTermsSlip(before: Polynomial, after: Polynomial): Pair<Rational, Rational>? {
        if (before.isConstant || after.isConstant) return null
        if (before.degree != after.degree) return null
        val beforeVariables = before.nonConstantTerms().sortedBy { it.first }
        val afterVariables = after.nonConstantTerms().sortedBy { it.first }
        if (beforeVariables.size != afterVariables.size || beforeVariables.isEmpty()) return null
        if (beforeVariables.map { it.first } != afterVariables.map { it.first }) return null
        if (before.constantTerm != after.constantTerm) return null
        val mismatched = beforeVariables.zip(afterVariables).firstOrNull { (origin, student) -> origin.second != student.second }
            ?: return null
        return mismatched.second.second to mismatched.first.second
    }

    private fun lostSolutionVerdict(lost: List<String>): StepVerdict = StepVerdict.Invalid(
        error = StepError.LOST_SOLUTION,
        detail = "El paso impone una restricción nueva y descarta ${lost.joinToString(", ")}, que sí satisface la línea anterior.",
        repairHint = "Antes de dividir entre una expresión con variable, anota qué valores la anulan y considéralos como candidatos.",
    )

    private fun lostSolutions(before: Expr.Equals, after: Expr.Equals): List<String> {
        val beforeKeys = DomainRestrictions.keys(DomainRestrictions.of(before))
        val added = DomainRestrictions.of(after).filter { DomainRestrictions.keys(setOf(it)).first() !in beforeKeys }
        return added.filterIsInstance<Restriction.NotEqualTo>()
            .filter { satisfies(before, it.variable to it.excluded) }
            .map { "${it.variable} = ${it.excluded}" }
    }

    private fun satisfies(equation: Expr.Equals, candidate: Pair<String, Rational>): Boolean {
        val substituted = Evaluator.substitute(equation, candidate.first, candidate.second) as? Expr.Equals ?: return false
        return Evaluator.equalityHolds(substituted, emptyMap()) == true
    }

    private fun zeroDivisionIn(expression: Expr): Expr? = expression.walk()
        .filterIsInstance<Expr.Quotient>()
        .firstOrNull { quotient ->
            (Evaluator.evaluate(quotient.denominator) as? EvalOutcome.Value)?.value?.isZero == true
        }
        ?.denominator

    private fun zeroDivisionVerdict(after: Expr.Equals, afterText: String): StepVerdict? {
        val denominator = zeroDivisionIn(after) ?: return null
        return StepVerdict.Invalid(
            error = StepError.DIVISION_BY_ZERO,
            detail = "En '$afterText' divides entre '${ExprPrinter.render(denominator)}', que vale 0.",
            repairHint = "Ninguna expresión se divide entre cero; revisa el término que cancelaste.",
        )
    }

    private fun countSideTerms(equation: Expr.Equals): Int = termsOf(equation.left) + termsOf(equation.right)

    private fun termsOf(side: Expr): Int = if (side is Expr.Sum) side.terms.size else 1

    private fun rightSideBecameConstant(before: Expr.Equals, after: Expr.Equals): Boolean {
        val afterRight = (Normalizer.of(after.right) as? PolyOutcome.Value)?.polynomial ?: return false
        val beforeRight = (Normalizer.of(before.right) as? PolyOutcome.Value)?.polynomial ?: return false
        return afterRight.isConstant && !beforeRight.isConstant
    }

    private fun termMovedAcrossEquals(before: Expr.Equals, after: Expr.Equals): RuleId? {
        val beforeLeft = (Normalizer.of(before.left) as? PolyOutcome.Value)?.polynomial ?: return null
        val afterLeft = (Normalizer.of(after.left) as? PolyOutcome.Value)?.polynomial ?: return null
        val beforeRight = (Normalizer.of(before.right) as? PolyOutcome.Value)?.polynomial ?: return null
        val afterRight = (Normalizer.of(after.right) as? PolyOutcome.Value)?.polynomial ?: return null
        val removed = beforeLeft - afterLeft
        if (removed.isZero) return null
        val added = afterRight - beforeRight
        // El mismo cambio aplicado a los dos lados: lo que desaparece de la izquierda aparece invertido a la derecha.
        if (added != -removed) return null
        val term = removed.sortedTerms.singleOrNull() ?: return null
        // Quitar un término positivo se describe como "restar"; quitar uno negativo es "sumar" su magnitud.
        return if (term.second.signum > 0) RuleId.SUBTRACT_FROM_BOTH_SIDES else RuleId.ADD_TO_BOTH_SIDES
    }

    private fun sideDelta(before: Expr, after: Expr): Polynomial? {
        val origin = (Normalizer.of(before) as? PolyOutcome.Value)?.polynomial ?: return null
        val target = (Normalizer.of(after) as? PolyOutcome.Value)?.polynomial ?: return null
        return target - origin
    }

    /** Busca el monomio `m` con `bigger == smaller * m`; si `m` tiene variables, el paso dividió entre `m`. */
    private fun monomialFactor(bigger: Polynomial, smaller: Polynomial): Pair<Monomial, Rational>? {
        if (bigger.isZero || smaller.isZero) return null
        val anchor = smaller.sortedTerms.firstOrNull() ?: return null
        for ((bigMonomial, bigCoefficient) in bigger.sortedTerms) {
            if (!bigMonomial.contains(anchor.first)) continue
            val exponents = subtractExponents(bigMonomial, anchor.first)
            val candidate = Monomial(exponents)
            if (candidate.variables.isEmpty()) continue
            val coefficient = bigCoefficient / anchor.second
            if (reconstructMatches(smaller, candidate, coefficient, bigger)) return candidate to coefficient
        }
        return null
    }

    private fun reconstructMatches(
        smaller: Polynomial,
        factor: Monomial,
        coefficient: Rational,
        bigger: Polynomial,
    ): Boolean {
        if (smaller.sortedTerms.size != bigger.sortedTerms.size) return false
        return smaller.sortedTerms.all { (monomial, value) ->
            bigger.coefficientOf(monomial * factor) == value * coefficient
        }
    }

    private fun subtractExponents(from: Monomial, by: Monomial): Map<String, Int> {
        val result = from.exponents.toMutableMap()
        by.exponents.forEach { (variable, exponent) -> result[variable] = (result[variable] ?: 0) - exponent }
        return result.filterValues { it != 0 }
    }

    private fun monomialZeros(monomial: Monomial): List<Pair<String, Rational>> = monomial.variables.map { it to Rational.ZERO }

    private fun sameVariablePart(before: Polynomial, after: Polynomial): Boolean {
        if (before.isConstant) return false
        val beforeVariables = before.nonConstantTerms()
        val afterVariables = after.nonConstantTerms()
        return beforeVariables.map { it.first } == afterVariables.map { it.first } &&
            beforeVariables.map { it.second } == afterVariables.map { it.second }
    }

    private fun expectedConstantAfter(before: Polynomial, after: Polynomial): Rational {
        val reference = before.nonConstantTerms().firstOrNull() ?: return before.constantTerm
        val counterpart = after.nonConstantTerms().firstOrNull() ?: return before.constantTerm
        if (reference.second.isZero) return before.constantTerm
        val factor = counterpart.second / reference.second
        return before.constantTerm * factor
    }

    private fun partialSignFlip(before: Polynomial, after: Polynomial): Boolean {
        if (before.variables != after.variables) return false
        val monomials = before.nonConstantTerms().map { it.first } + after.nonConstantTerms().map { it.first }
        val interesting = monomials.toSet()
        var flipped = 0
        var kept = 0
        interesting.forEach { monomial ->
            val origin = before.coefficientOf(monomial)
            val target = after.coefficientOf(monomial)
            when {
                target == origin -> kept++
                target == -origin -> flipped++
                else -> return false
            }
        }
        val constantOrigin = before.constantTerm
        val constantTarget = after.constantTerm
        if (constantOrigin != constantTarget) {
            if (constantTarget == -constantOrigin) flipped++ else return false
        } else {
            kept++
        }
        return flipped > 0 && kept > 0
    }

    private fun canonicalReason(expression: Expr): String {
        val outcome = if (expression is Expr.Equals) Normalizer.ofEquation(expression) else Normalizer.of(expression)
        return (outcome as? PolyOutcome.Blocked)?.reason
            ?: "La expresión todavía no se puede escribir en forma canónica."
    }

    private fun ParseOutcome.Failure.toSyntaxVerdict(): StepVerdict.SyntaxProblem =
        StepVerdict.SyntaxProblem(position, reason, suggestion)
}
