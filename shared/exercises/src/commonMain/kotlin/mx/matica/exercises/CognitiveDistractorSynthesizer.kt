package mx.matica.exercises

import mx.matica.math.MathEngine
import mx.matica.math.analysis.StepError
import mx.matica.math.numeric.Rational
import mx.matica.math.poly.Monomial
import mx.matica.math.poly.Normalizer
import mx.matica.math.poly.PolyOutcome
import mx.matica.math.verify.VerificationOutcome

/**
 * Catálogo canónico de errores cognitivos y mal-reglas algebraicas
 * fundamentado en la teoría de "Mind Bugs" (VanLehn, 1990; Brown & Burton, 1978; Matz, 1982).
 */
enum class CognitiveBug(val id: String, val labelEs: String) {
    /** Omisión de invertir el signo al trasponer términos: ax + b = c -> ax = c + b */
    SIGN_FLIP_OMISSION(
        id = "sign-flip-omission",
        labelEs = "Omisión de cambio de signo al trasponer",
    ),

    /** Sustracción/adición del coeficiente multiplicador en lugar de dividir: ax = d -> x = d - a */
    ADDITIVE_DIVISOR(
        id = "additive-divisor",
        labelEs = "Divisor aditivo (restar coeficiente en vez de dividir)",
    ),

    /** Suma directa de denominadores en fracciones: a/b + c/d -> (a+c)/(b+d) */
    FRACTION_DENOMINATOR_ADDITION(
        id = "fraction-denominator-addition",
        labelEs = "Suma directa de denominadores",
    ),

    /** Violación de la jerarquía de operaciones: a + b * c -> (a+b) * c o distribución incompleta */
    HIERARCHY_PRECEDENCE_SLIP(
        id = "hierarchy-precedence-slip",
        labelEs = "Desliz en jerarquía de operaciones o precedencia",
    ),

    /** Fusión indebida de términos no semejantes: ax + b -> (a+b)x */
    LIKE_TERMS_COLLISION(
        id = "like-terms-collision",
        labelEs = "Colisión de términos no semejantes",
    ),

    /** Inversión del orden de los operandos en la división: ax = d -> x = a / d */
    INVERTED_DIVISION(
        id = "inverted-division",
        labelEs = "División invertida (divisor entre dividendo)",
    );

    /** Mapeo directo al error correspondiente del validador de pasos del motor matemático. */
    fun toStepError(): StepError? = when (this) {
        SIGN_FLIP_OMISSION -> StepError.SIGN_FLIP_OMISSION
        ADDITIVE_DIVISOR -> StepError.ADDITIVE_DIVISOR_ERROR
        FRACTION_DENOMINATOR_ADDITION -> StepError.FRACTION_DENOMINATOR_ADDITION
        HIERARCHY_PRECEDENCE_SLIP -> StepError.ORDER_OF_OPERATIONS
        LIKE_TERMS_COLLISION -> StepError.ARITHMETIC_ERROR
        INVERTED_DIVISION -> StepError.ARITHMETIC_ERROR
    }
}

/**
 * Distractor cognitivo determinista generado a partir de una concepción errónea específica.
 */
data class CognitiveDistractor(
    val text: String,
    val value: Rational?,
    val bug: CognitiveBug,
    val diagnosticFeedbackEs: String,
) {
    constructor(
        value: Rational,
        bug: CognitiveBug,
        diagnosticFeedbackEs: String,
    ) : this(
        text = value.toString(),
        value = value,
        bug = bug,
        diagnosticFeedbackEs = diagnosticFeedbackEs,
    )
}

/**
 * Sintetizador determinista de distractores diagnósticos (Bug Library).
 *
 * Transforma instancias de problemas matemáticos en opciones erróneas plausibles, garantizando que:
 * 1. Cada distractor represente fielmente un error conceptual modelado.
 * 2. Ningún distractor coincida numéricamente con la solución correcta.
 * 3. Todos los distractores sintetizados para un ejercicio sean mutuamente distintos.
 * 4. Toda la aritmética sea exacta mediante [Rational].
 */
object CognitiveDistractorSynthesizer {

    private val engine = MathEngine()

    // =========================================================================
    // 1. Ecuaciones lineales canónicas: ax + b = c
    // =========================================================================

    fun forTwoStepLinear(a: Long, b: Long, c: Long): List<CognitiveDistractor> =
        forTwoStepLinear(Rational.fromLong(a), Rational.fromLong(b), Rational.fromLong(c))

    fun forLinearEquation(a: Long, b: Long, c: Long): List<CognitiveDistractor> =
        forTwoStepLinear(a, b, c)

    fun forLinearEquation(a: Rational, b: Rational, c: Rational): List<CognitiveDistractor> =
        forTwoStepLinear(a, b, c)

    /**
     * ax + b = c  =>  Solución correcta: x = (c - b) / a
     */
    fun forTwoStepLinear(a: Rational, b: Rational, c: Rational): List<CognitiveDistractor> {
        require(!a.isZero) { "El coeficiente 'a' no puede ser cero en una ecuación lineal." }
        val solution = (c - b) / a
        val d = c - b

        val candidates = mutableListOf<CognitiveDistractor>()

        // 1. SIGN_FLIP_OMISSION: ax = c + b => x = (c + b) / a
        candidates += CognitiveDistractor(
            value = (c + b) / a,
            bug = CognitiveBug.SIGN_FLIP_OMISSION,
            diagnosticFeedbackEs = "Se omitió invertir el signo del término independiente al trasponer: para mover $b al otro miembro debió restarse ($c - $b), pero se sumó ($c + $b).",
        )

        // 2. ADDITIVE_DIVISOR: ax = d => x = d - a
        candidates += CognitiveDistractor(
            value = d - a,
            bug = CognitiveBug.ADDITIVE_DIVISOR,
            diagnosticFeedbackEs = "Se restó el coeficiente en lugar de dividir: la relación entre $a y la incógnita es multiplicativa ($a·x), por lo que se debe dividir entre $a.",
        )

        // 3. LIKE_TERMS_COLLISION: (a + b)x = c => x = c / (a + b)
        val sumCoeff = a + b
        if (!sumCoeff.isZero) {
            candidates += CognitiveDistractor(
                value = c / sumCoeff,
                bug = CognitiveBug.LIKE_TERMS_COLLISION,
                diagnosticFeedbackEs = "Se sumaron términos no semejantes: se combinó el término lineal con la constante ($a·x + $b) como si fueran (${sumCoeff})x.",
            )
        }

        // 4. INVERTED_DIVISION: ax = d => x = a / d
        if (!d.isZero) {
            candidates += CognitiveDistractor(
                value = a / d,
                bug = CognitiveBug.INVERTED_DIVISION,
                diagnosticFeedbackEs = "Se invirtió el orden de la división: se dividió el coeficiente entre el resultado ($a / $d) en lugar del resultado entre el coeficiente ($d / $a).",
            )
        }

        // 5. HIERARCHY_PRECEDENCE_SLIP: dividir entre 'a' sin dividir 'b' => x + b = c/a => x = c/a - b
        candidates += CognitiveDistractor(
            value = (c / a) - b,
            bug = CognitiveBug.HIERARCHY_PRECEDENCE_SLIP,
            diagnosticFeedbackEs = "Falla de jerarquía en el despeje: al dividir entre el coeficiente $a debió dividirse toda la ecuación o primero restar el término independiente.",
        )

        // 6. SIGN_FLIP_OMISSION secundario: cambio de signo global -solution
        if (!solution.isZero) {
            candidates += CognitiveDistractor(
                value = -solution,
                bug = CognitiveBug.SIGN_FLIP_OMISSION,
                diagnosticFeedbackEs = "Error de signos en la etapa final del despeje: se cambió indebidamente el signo del resultado.",
            )
        }

        return assembleDistractors(solution, candidates)
    }

    // =========================================================================
    // 2. Ecuaciones con propiedad distributiva: a(x + b) = c
    // =========================================================================

    fun forDistributiveLinear(a: Long, b: Long, c: Long): List<CognitiveDistractor> =
        forDistributiveLinear(Rational.fromLong(a), Rational.fromLong(b), Rational.fromLong(c))

    fun forDistributiveEquation(a: Long, b: Long, c: Long): List<CognitiveDistractor> =
        forDistributiveLinear(a, b, c)

    fun forDistributiveEquation(a: Rational, b: Rational, c: Rational): List<CognitiveDistractor> =
        forDistributiveLinear(a, b, c)

    /**
     * a(x + b) = c  =>  Solución correcta: x = c/a - b = (c - a*b) / a
     */
    fun forDistributiveLinear(a: Rational, b: Rational, c: Rational): List<CognitiveDistractor> {
        require(!a.isZero) { "El factor 'a' no puede ser cero." }
        val solution = (c / a) - b
        val d = c - (a * b)

        val candidates = mutableListOf<CognitiveDistractor>()

        // 1. HIERARCHY_PRECEDENCE_SLIP: Distribución incompleta: ax + b = c => ax = c - b => x = (c - b) / a
        candidates += CognitiveDistractor(
            value = (c - b) / a,
            bug = CognitiveBug.HIERARCHY_PRECEDENCE_SLIP,
            diagnosticFeedbackEs = "Distribución incompleta: se multiplicó $a únicamente por 'x' pero se omitió multiplicarlo por $b ($a·x + $b en lugar de $a·x + ${a * b}).",
        )

        // 2. SIGN_FLIP_OMISSION: ax = c + ab => x = (c + ab) / a = c/a + b
        candidates += CognitiveDistractor(
            value = (c / a) + b,
            bug = CognitiveBug.SIGN_FLIP_OMISSION,
            diagnosticFeedbackEs = "Se omitió invertir el signo al trasponer: tras distribuir, debió restarse el producto del miembro derecho ($c - ${a * b}), pero se sumó.",
        )

        // 3. ADDITIVE_DIVISOR: Tratar el factor 'a' como término aditivo: x + b = c - a => x = c - a - b
        candidates += CognitiveDistractor(
            value = c - a - b,
            bug = CognitiveBug.ADDITIVE_DIVISOR,
            diagnosticFeedbackEs = "Se restó el factor multiplicador en lugar de dividir: la relación de $a con el paréntesis es una multiplicación, no una suma.",
        )

        // 4. LIKE_TERMS_COLLISION: Combinar x + b dentro del paréntesis como (1+b)x => a(1+b)x = c
        val insideSum = Rational.ONE + b
        val factorCollided = a * insideSum
        if (!factorCollided.isZero) {
            candidates += CognitiveDistractor(
                value = c / factorCollided,
                bug = CognitiveBug.LIKE_TERMS_COLLISION,
                diagnosticFeedbackEs = "Se sumaron términos no semejantes dentro del paréntesis: la incógnita 'x' y la constante $b no se pueden sumar en (${insideSum})x.",
            )
        }

        // 5. INVERTED_DIVISION: ax = d => x = a / d
        if (!d.isZero) {
            candidates += CognitiveDistractor(
                value = a / d,
                bug = CognitiveBug.INVERTED_DIVISION,
                diagnosticFeedbackEs = "Se invirtió el orden de la división al despejar: se dividió el coeficiente entre el resultado ($a / $d).",
            )
        }

        // 6. Fallback de signo
        if (!solution.isZero) {
            candidates += CognitiveDistractor(
                value = -solution,
                bug = CognitiveBug.SIGN_FLIP_OMISSION,
                diagnosticFeedbackEs = "Error de signo en la resolución de la ecuación distributiva.",
            )
        }

        return assembleDistractors(solution, candidates)
    }

    // =========================================================================
    // 3. Ecuaciones fraccionarias: (x + b) / d = a
    // =========================================================================

    fun forFractionLinear(b: Long, d: Long, a: Long): List<CognitiveDistractor> =
        forFractionLinear(Rational.fromLong(b), Rational.fromLong(d), Rational.fromLong(a))

    fun forFractionEquation(b: Long, d: Long, a: Long): List<CognitiveDistractor> =
        forFractionLinear(b, d, a)

    fun forFractionEquation(b: Rational, d: Rational, a: Rational): List<CognitiveDistractor> =
        forFractionLinear(b, d, a)

    /**
     * (x + b) / d = a  =>  Solución correcta: x = a*d - b
     */
    fun forFractionLinear(b: Rational, d: Rational, a: Rational): List<CognitiveDistractor> {
        require(!d.isZero) { "El denominador 'd' no puede ser cero." }
        val solution = (a * d) - b

        val candidates = mutableListOf<CognitiveDistractor>()

        // 1. SIGN_FLIP_OMISSION: x = ad + b
        candidates += CognitiveDistractor(
            value = (a * d) + b,
            bug = CognitiveBug.SIGN_FLIP_OMISSION,
            diagnosticFeedbackEs = "Se omitió invertir el signo al trasponer: para cancelar +$b se debió restar de ambos lados ($a·$d - $b), pero se sumó ($a·$d + $b).",
        )

        // 2. HIERARCHY_PRECEDENCE_SLIP: Restar b antes de multiplicar por d => x = d * (a - b)
        candidates += CognitiveDistractor(
            value = d * (a - b),
            bug = CognitiveBug.HIERARCHY_PRECEDENCE_SLIP,
            diagnosticFeedbackEs = "Falla de jerarquía de operaciones: la barra de fracción actúa como agrupación; primero debe multiplicarse por el denominador $d antes de restar $b.",
        )

        // 3. ADDITIVE_DIVISOR: Restar el denominador en vez de multiplicar: x + b = a - d => x = a - d - b
        candidates += CognitiveDistractor(
            value = a - d - b,
            bug = CognitiveBug.ADDITIVE_DIVISOR,
            diagnosticFeedbackEs = "Se restó el denominador en lugar de multiplicar: la operación inversa de dividir entre $d es multiplicar por $d.",
        )

        // 4. INVERTED_DIVISION: Dividir en vez de multiplicar: x + b = d / a => x = d/a - b
        if (!a.isZero) {
            candidates += CognitiveDistractor(
                value = (d / a) - b,
                bug = CognitiveBug.INVERTED_DIVISION,
                diagnosticFeedbackEs = "Se invirtió la operación: se dividió entre el resultado ($d / $a) en lugar de multiplicar por el divisor ($a · $d).",
            )
        }

        // 5. FRACTION_DENOMINATOR_ADDITION / Mezcla de denominador: x = a - (b + d)
        candidates += CognitiveDistractor(
            value = a - b - d,
            bug = CognitiveBug.FRACTION_DENOMINATOR_ADDITION,
            diagnosticFeedbackEs = "Error en la estructura de la fracción: se sumaron o mezclaron indebidamente el término del numerador y el denominador.",
        )

        // 6. Fallback de signo
        if (!solution.isZero) {
            candidates += CognitiveDistractor(
                value = -solution,
                bug = CognitiveBug.SIGN_FLIP_OMISSION,
                diagnosticFeedbackEs = "Error de signo en el despeje final de la ecuación fraccionaria.",
            )
        }

        return assembleDistractors(solution, candidates)
    }

    // =========================================================================
    // 4. Expresiones aritméticas de enteros y jerarquía
    // =========================================================================

    fun forIntegerOps(a: Long, b: Long, c: Long, d: Long = 0L): List<CognitiveDistractor> =
        forIntegerHierarchy(a, b, c, d)

    /**
     * Expresión: a - b(-c) + d  =>  Solución correcta: a + b*c + d
     */
    fun forIntegerHierarchy(a: Long, b: Long, c: Long, d: Long = 0L): List<CognitiveDistractor> {
        val aR = Rational.fromLong(a)
        val bR = Rational.fromLong(b)
        val cR = Rational.fromLong(c)
        val dR = Rational.fromLong(d)

        val solution = aR + (bR * cR) + dR

        val candidates = mutableListOf<CognitiveDistractor>()

        // 1. HIERARCHY_PRECEDENCE_SLIP: Restar a - b antes de multiplicar por (-c): (a - b) * (-c) + d
        val precedenceVal = ((aR - bR) * (-cR)) + dR
        candidates += CognitiveDistractor(
            value = precedenceVal,
            bug = CognitiveBug.HIERARCHY_PRECEDENCE_SLIP,
            diagnosticFeedbackEs = "Se violó la jerarquía de operaciones: se restó ($a - $b) antes de multiplicar por (-$c).",
        )

        // 2. SIGN_FLIP_OMISSION: Error con -b(-c), tomarlo como resta de producto: a - b*c + d
        val signVal = aR - (bR * cR) + dR
        candidates += CognitiveDistractor(
            value = signVal,
            bug = CognitiveBug.SIGN_FLIP_OMISSION,
            diagnosticFeedbackEs = "Error en la ley de los signos: el producto de dos números negativos (-$b)·(-$c) resulta en una suma (+${b * c}), pero se restó.",
        )

        // 3. ADDITIVE_DIVISOR / Interpretación aditiva de paréntesis: a - b - (-c) + d = a - b + c + d
        val additiveVal = aR - bR + cR + dR
        candidates += CognitiveDistractor(
            value = additiveVal,
            bug = CognitiveBug.ADDITIVE_DIVISOR,
            diagnosticFeedbackEs = "Se interpretó el paréntesis como una adición/sustracción en lugar de una multiplicación (el número adyacente al paréntesis multiplica).",
        )

        // 4. HIERARCHY_PRECEDENCE_SLIP secundario: si d != 0, sumar (-c) + d antes de multiplicar: a - b(-c + d)
        if (d != 0L) {
            val rightPrecedence = aR - (bR * (-cR + dR))
            candidates += CognitiveDistractor(
                value = rightPrecedence,
                bug = CognitiveBug.HIERARCHY_PRECEDENCE_SLIP,
                diagnosticFeedbackEs = "Se violó la jerarquía de operaciones: se sumó (-$c) + $d antes de efectuar la multiplicación con $b.",
            )
        }

        // 5. LIKE_TERMS_COLLISION / Suma plana de componentes
        val flatSum = aR + bR + cR + dR
        candidates += CognitiveDistractor(
            value = flatSum,
            bug = CognitiveBug.LIKE_TERMS_COLLISION,
            diagnosticFeedbackEs = "Se sumaron directamente los valores absolutos de los números ignorando la multiplicación y los signos de la operación.",
        )

        return assembleDistractors(solution, candidates)
    }

    /**
     * Jerarquía simple: a + b * c  =>  Solución correcta: a + b*c
     */
    fun forHierarchyExpression(a: Long, b: Long, c: Long): List<CognitiveDistractor> {
        val aR = Rational.fromLong(a)
        val bR = Rational.fromLong(b)
        val cR = Rational.fromLong(c)
        val solution = aR + (bR * cR)

        val candidates = mutableListOf<CognitiveDistractor>()

        // 1. HIERARCHY_PRECEDENCE_SLIP: (a + b) * c
        candidates += CognitiveDistractor(
            value = (aR + bR) * cR,
            bug = CognitiveBug.HIERARCHY_PRECEDENCE_SLIP,
            diagnosticFeedbackEs = "Se violó la jerarquía de operaciones: se realizó la suma ($a + $b) antes de la multiplicación por $c.",
        )

        // 2. SIGN_FLIP_OMISSION: a - b * c
        candidates += CognitiveDistractor(
            value = aR - (bR * cR),
            bug = CognitiveBug.SIGN_FLIP_OMISSION,
            diagnosticFeedbackEs = "Error de signo: se restó el producto en lugar de sumarlo.",
        )

        // 3. ADDITIVE_DIVISOR / Tratamiento aditivo: a + b + c
        candidates += CognitiveDistractor(
            value = aR + bR + cR,
            bug = CognitiveBug.ADDITIVE_DIVISOR,
            diagnosticFeedbackEs = "Se interpretó la multiplicación como una adición ($b + $c en vez de $b · $c).",
        )

        // 4. INVERTED_DIVISION: a + b / c
        if (c != 0L) {
            candidates += CognitiveDistractor(
                value = aR + (bR / cR),
                bug = CognitiveBug.INVERTED_DIVISION,
                diagnosticFeedbackEs = "Se dividió en lugar de multiplicar ($b / $c en vez de $b · $c).",
            )
        }

        return assembleDistractors(solution, candidates)
    }

    /**
     * Suma de fracciones: n1/d1 + n2/d2  =>  Solución correcta: (n1*d2 + n2*d1)/(d1*d2)
     */
    fun forFractionAddition(n1: Long, d1: Long, n2: Long, d2: Long): List<CognitiveDistractor> {
        require(d1 != 0L && d2 != 0L) { "Los denominadores no pueden ser cero." }
        return forFractionAddition(Rational.of(n1, d1), Rational.of(n2, d2), n1, d1, n2, d2)
    }

    fun forFractionAddition(f1: Rational, f2: Rational): List<CognitiveDistractor> {
        val n1 = f1.numerator.longValue()
        val d1 = f1.denominator.longValue()
        val n2 = f2.numerator.longValue()
        val d2 = f2.denominator.longValue()
        return forFractionAddition(f1, f2, n1, d1, n2, d2)
    }

    private fun forFractionAddition(
        f1: Rational,
        f2: Rational,
        n1: Long,
        d1: Long,
        n2: Long,
        d2: Long,
    ): List<CognitiveDistractor> {
        val solution = f1 + f2
        val candidates = mutableListOf<CognitiveDistractor>()

        // 1. FRACTION_DENOMINATOR_ADDITION: (n1 + n2) / (d1 + d2)
        val denSum = d1 + d2
        if (denSum != 0L) {
            candidates += CognitiveDistractor(
                value = Rational.of(n1 + n2, denSum),
                bug = CognitiveBug.FRACTION_DENOMINATOR_ADDITION,
                diagnosticFeedbackEs = "Se sumaron numeradores y denominadores directamente (${n1 + n2}/${denSum}): no se pueden sumar denominadores porque indican el tamaño de las partes (se requiere común denominador).",
            )
        }

        // 2. INVERTED_DIVISION: Invertir la fracción resultante de la suma directa
        val numSum = n1 + n2
        if (numSum != 0L && denSum != 0L) {
            candidates += CognitiveDistractor(
                value = Rational.of(denSum, numSum),
                bug = CognitiveBug.INVERTED_DIVISION,
                diagnosticFeedbackEs = "Se invirtió la fracción resultante (colocando el denominador en el numerador).",
            )
        }

        // 3. HIERARCHY_PRECEDENCE_SLIP / Denominador multiplicado sin expandir numeradores: (n1 + n2) / (d1 * d2)
        val denProd = d1 * d2
        if (denProd != 0L) {
            candidates += CognitiveDistractor(
                value = Rational.of(n1 + n2, denProd),
                bug = CognitiveBug.HIERARCHY_PRECEDENCE_SLIP,
                diagnosticFeedbackEs = "Se multiplicaron los denominadores pero se olvidó multiplicar los numeradores por sus factores cruzados correspondientes.",
            )
        }

        // 4. SIGN_FLIP_OMISSION: Restar en vez de sumar: f1 - f2
        candidates += CognitiveDistractor(
            value = f1 - f2,
            bug = CognitiveBug.SIGN_FLIP_OMISSION,
            diagnosticFeedbackEs = "Se restaron las fracciones en lugar de sumarlas.",
        )

        return assembleDistractors(solution, candidates)
    }

    // =========================================================================
    // 5. Instancias de ejercicios generales (ExerciseInstance)
    // =========================================================================

    fun synthesize(instance: ExerciseInstance): List<CognitiveDistractor> = forInstance(instance)

    /**
     * Sintetiza exactamente 3 distractores deterministas para cualquier [ExerciseInstance].
     * Verifica que los distractores no satisfagan la ecuación con [MathEngine].
     */
    fun forInstance(instance: ExerciseInstance): List<CognitiveDistractor> {
        val distractors = when (instance.templateId) {
            "two-step-linear" -> {
                val a = instance.parameters.of("a")?.toLong() ?: 1L
                val b = instance.parameters.of("b")?.toLong() ?: 0L
                val k = instance.parameters.of("k")?.toLong() ?: instance.solution.toLongOrNull() ?: 0L
                val c = (a * k) + b
                forTwoStepLinear(a, b, c)
            }
            "distributive-linear" -> {
                val a = instance.parameters.of("a")?.toLong() ?: 1L
                val b = instance.parameters.of("b")?.toLong() ?: 0L
                val k = instance.parameters.of("k")?.toLong() ?: instance.solution.toLongOrNull() ?: 0L
                val c = a * (k + b)
                forDistributiveLinear(a, b, c)
            }
            "rectangle-perimeter" -> {
                val b = instance.parameters.of("b")?.toLong() ?: 0L
                val k = instance.parameters.of("k")?.toLong() ?: instance.solution.toLongOrNull() ?: 0L
                val a = 2L
                val c = a * (k + b)
                forDistributiveLinear(a, b, c)
            }
            "fraction-linear" -> {
                val b = instance.parameters.of("b")?.toLong() ?: 0L
                val d = instance.parameters.of("d")?.toLong() ?: 1L
                val a = instance.parameters.of("a")?.toLong() ?: 0L
                forFractionLinear(b, d, a)
            }
            "integer-ops" -> {
                val a = instance.parameters.of("a")?.toLong() ?: 0L
                val b = instance.parameters.of("b")?.toLong() ?: 1L
                val c = instance.parameters.of("c")?.toLong() ?: 1L
                val d = instance.parameters.of("d")?.toLong() ?: 0L
                forIntegerHierarchy(a, b, c, d)
            }
            "domain-restrictions" -> {
                val b = instance.parameters.of("b")?.toLong() ?: instance.solution.toLongOrNull() ?: 1L
                forDomainRestrictions(Rational.fromLong(b), instance.equationText, instance.variable)
            }
            else -> {
                forGeneralLinear(instance.equationText, instance.solution, instance.variable)
            }
        }

        // Verificación con MathEngine: asegurar que ningún distractor satisfaga la ecuación
        return distractors.map { distractor ->
            if (distractor.value != null && instance.equationText.contains('=')) {
                val verification = engine.verifySolution(instance.equationText, instance.variable, distractor.value)
                check(verification !is VerificationOutcome.Satisfies) {
                    "Distractor incoherente: ${distractor.value} satisfizo la ecuación original '${instance.equationText}'."
                }
            }
            distractor
        }
    }

    fun forDomainRestrictions(
        forbidden: Rational,
        equationText: String? = null,
        variable: String = "x",
    ): List<CognitiveDistractor> {
        val candidates = mutableListOf<CognitiveDistractor>()

        if (!forbidden.isZero) {
            candidates += CognitiveDistractor(
                value = -forbidden,
                bug = CognitiveBug.SIGN_FLIP_OMISSION,
                diagnosticFeedbackEs = "Se omitió cambiar el signo al igualar el denominador a cero: x - b = 0 implica x = b, no x = -b.",
            )
            candidates += CognitiveDistractor(
                value = Rational.ZERO,
                bug = CognitiveBug.LIKE_TERMS_COLLISION,
                diagnosticFeedbackEs = "Se asumió que el valor prohibido siempre es cero, en lugar de igualar el binomio del denominador a cero.",
            )
        }
        candidates += CognitiveDistractor(
            value = forbidden + Rational.fromLong(3L),
            bug = CognitiveBug.ADDITIVE_DIVISOR,
            diagnosticFeedbackEs = "Error al despejar la restricción que anula el denominador.",
        )
        if (!forbidden.isZero) {
            candidates += CognitiveDistractor(
                value = -forbidden + Rational.fromLong(2L),
                bug = CognitiveBug.INVERTED_DIVISION,
                diagnosticFeedbackEs = "Error en el cálculo del valor excluido del dominio.",
            )
        }

        val nonSatisfying = candidates.filter { c ->
            if (c.value != null && equationText != null && equationText.contains('=')) {
                engine.verifySolution(equationText, variable, c.value) !is VerificationOutcome.Satisfies
            } else true
        }

        val baseDistractors = assembleDistractors(forbidden, nonSatisfying)
        return if (equationText != null && equationText.contains('=')) {
            baseDistractors.map { d ->
                if (d.value != null && engine.verifySolution(equationText, variable, d.value) is VerificationOutcome.Satisfies) {
                    var offset = 5L
                    var candidate = forbidden + Rational.fromLong(offset)
                    while (candidate == forbidden || engine.verifySolution(equationText, variable, candidate) is VerificationOutcome.Satisfies) {
                        offset++
                        candidate = forbidden + Rational.fromLong(offset)
                    }
                    CognitiveDistractor(candidate, d.bug, d.diagnosticFeedbackEs)
                } else d
            }
        } else baseDistractors
    }

    /**
     * Despeje e inferencia para ecuaciones lineales arbitrarias expresadas como texto.
     */
    fun forGeneralLinear(
        equationText: String,
        solution: Rational,
        variable: String = "x",
    ): List<CognitiveDistractor> {
        val parsed = engine.parseOrNull(equationText)
        val polynomial = (Normalizer.ofEquation(parsed ?: return fallbackGeneral(solution)) as? PolyOutcome.Value)?.polynomial
            ?: return fallbackGeneral(solution)

        val slope = polynomial.coefficientOf(Monomial.of(variable, 1))
        val constant = polynomial.constantTerm

        if (slope.isZero) return fallbackGeneral(solution)

        // ax + b = 0 => a = slope, b = constant, c = 0
        return forTwoStepLinear(slope, constant, Rational.ZERO)
    }

    private fun fallbackGeneral(solution: Rational): List<CognitiveDistractor> =
        assembleDistractors(solution, emptyList())

    // =========================================================================
    // Ensamblado e invariantes de unicidad y distinción
    // =========================================================================

    private fun assembleDistractors(
        solution: Rational,
        candidates: List<CognitiveDistractor?>,
    ): List<CognitiveDistractor> {
        val result = mutableListOf<CognitiveDistractor>()
        val seenValues = mutableSetOf<Rational>()
        val seenTexts = mutableSetOf<String>()

        for (candidate in candidates) {
            if (candidate == null) continue
            val v = candidate.value
            if (v != null) {
                if (v == solution) continue
                if (v in seenValues) continue
                seenValues.add(v)
            }
            if (candidate.text in seenTexts) continue
            seenTexts.add(candidate.text)
            result.add(candidate)
            if (result.size == 3) return result
        }

        // Garantía absoluta de 3 distractores mediante fallbacks canónicos deterministas
        var offset = 1L
        val fallbackSequence = listOf(
            CognitiveBug.SIGN_FLIP_OMISSION,
            CognitiveBug.ADDITIVE_DIVISOR,
            CognitiveBug.INVERTED_DIVISION,
            CognitiveBug.HIERARCHY_PRECEDENCE_SLIP,
            CognitiveBug.LIKE_TERMS_COLLISION,
        )
        var fallbackIdx = 0

        while (result.size < 3) {
            val bug = fallbackSequence[fallbackIdx % fallbackSequence.size]
            fallbackIdx++

            val fallbackVal = when (bug) {
                CognitiveBug.SIGN_FLIP_OMISSION -> {
                    if (!solution.isZero && -solution !in seenValues) -solution
                    else solution + Rational.fromLong(offset++)
                }
                CognitiveBug.ADDITIVE_DIVISOR -> {
                    val candidate = solution - Rational.fromLong(offset++)
                    if (candidate !in seenValues && candidate != solution) candidate
                    else solution + Rational.fromLong(offset++)
                }
                CognitiveBug.INVERTED_DIVISION -> {
                    if (!solution.isZero && solution.reciprocal() !in seenValues && solution.reciprocal() != solution) {
                        solution.reciprocal()
                    } else {
                        solution + Rational.fromLong(offset++)
                    }
                }
                CognitiveBug.HIERARCHY_PRECEDENCE_SLIP -> solution + Rational.fromLong(offset++)
                CognitiveBug.LIKE_TERMS_COLLISION -> solution - Rational.fromLong(offset++)
                else -> solution + Rational.fromLong(offset++)
            }

            if (fallbackVal == solution || fallbackVal in seenValues) {
                offset++
                continue
            }

            val feedback = when (bug) {
                CognitiveBug.SIGN_FLIP_OMISSION -> "Error en el manejo de signos al trasponer o despejar."
                CognitiveBug.ADDITIVE_DIVISOR -> "Operación inversa incorrecta: se aplicó adición/sustracción en lugar de división/multiplicación."
                CognitiveBug.INVERTED_DIVISION -> "Inversión errónea en los operandos de la división."
                CognitiveBug.HIERARCHY_PRECEDENCE_SLIP -> "Falla en la jerarquía o precedencia de las operaciones."
                CognitiveBug.LIKE_TERMS_COLLISION -> "Combinación errónea de términos o componentes algebraicos."
                CognitiveBug.FRACTION_DENOMINATOR_ADDITION -> "Error de suma directa en los denominadores de las fracciones."
            }

            val distractor = CognitiveDistractor(
                text = fallbackVal.toString(),
                value = fallbackVal,
                bug = bug,
                diagnosticFeedbackEs = feedback,
            )

            seenValues.add(fallbackVal)
            seenTexts.add(distractor.text)
            result.add(distractor)
        }

        return result
    }
}
