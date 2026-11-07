package mx.matica.exercises

import mx.matica.math.MathEngine
import mx.matica.math.numeric.Rational
import mx.matica.math.verify.VerificationOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CognitiveDistractorSynthesizerTest {

    private val engine = MathEngine()
    private val generator = ExerciseGenerator()

    @Test
    fun testTwoStepLinearCanonicalEquation() {
        // 3x + 15 = 24  =>  x = 3
        val distractors = CognitiveDistractorSynthesizer.forTwoStepLinear(3L, 15L, 24L)

        assertEquals(3, distractors.size, "Deben generarse exactamente 3 distractores.")

        val solution = Rational.fromLong(3L)
        val seenValues = mutableSetOf<Rational>()
        val seenTexts = mutableSetOf<String>()

        distractors.forEach { d ->
            assertNotEquals(solution, d.value, "Ningún distractor debe coincidir con la solución real.")
            assertTrue(d.value !in seenValues, "Los distractores deben ser mutuamente distintos en valor: ${d.value}")
            assertTrue(d.text !in seenTexts, "Los distractores deben ser mutuamente distintos en texto: ${d.text}")
            assertTrue(d.diagnosticFeedbackEs.isNotBlank(), "Cada distractor debe tener explicación diagnóstica.")
            seenValues.add(d.value!!)
            seenTexts.add(d.text)
        }

        // Verificar bugs específicos esperados
        // 1. SIGN_FLIP_OMISSION: (24 + 15) / 3 = 13
        val signDistractor = distractors.firstOrNull { it.bug == CognitiveBug.SIGN_FLIP_OMISSION }
        assertTrue(signDistractor != null, "Debe contener distractor por omisión de cambio de signo.")
        assertEquals(Rational.fromLong(13L), signDistractor.value)

        // 2. ADDITIVE_DIVISOR: (24 - 15) - 3 = 6
        val additiveDistractor = distractors.firstOrNull { it.bug == CognitiveBug.ADDITIVE_DIVISOR }
        assertTrue(additiveDistractor != null, "Debe contener distractor por divisor aditivo.")
        assertEquals(Rational.fromLong(6L), additiveDistractor.value)

        // 3. LIKE_TERMS_COLLISION: 24 / (3 + 15) = 24/18 = 4/3
        val collisionDistractor = distractors.firstOrNull { it.bug == CognitiveBug.LIKE_TERMS_COLLISION }
        assertTrue(collisionDistractor != null, "Debe contener distractor por colisión de términos.")
        assertEquals(Rational.of(4L, 3L), collisionDistractor.value)
    }

    @Test
    fun testTwoStepLinearWithNegativeParameters() {
        // -2x + 6 = -4  =>  -2x = -10  =>  x = 5
        val distractors = CognitiveDistractorSynthesizer.forTwoStepLinear(-2L, 6L, -4L)

        assertEquals(3, distractors.size)
        val solution = Rational.fromLong(5L)
        distractors.forEach { d ->
            assertNotEquals(solution, d.value)
            assertTrue(d.diagnosticFeedbackEs.isNotBlank())
        }
        val uniqueValues = distractors.mapNotNull { it.value }.toSet()
        assertEquals(3, uniqueValues.size, "Todos los distractores deben ser mutuamente distintos.")
    }

    @Test
    fun testTwoStepLinearZeroConstantDegenerate() {
        // 2x + 0 = 8  =>  x = 4 (b = 0)
        val distractors = CognitiveDistractorSynthesizer.forTwoStepLinear(2L, 0L, 8L)

        assertEquals(3, distractors.size)
        val solution = Rational.fromLong(4L)
        distractors.forEach { d ->
            assertNotEquals(solution, d.value)
            assertTrue(d.diagnosticFeedbackEs.isNotBlank())
        }
        val uniqueValues = distractors.mapNotNull { it.value }.toSet()
        assertEquals(3, uniqueValues.size)
    }

    @Test
    fun testDistributiveLinearCanonicalEquation() {
        // 4(x + 3) = 28  =>  x = 4
        val distractors = CognitiveDistractorSynthesizer.forDistributiveLinear(4L, 3L, 28L)

        assertEquals(3, distractors.size)
        val solution = Rational.fromLong(4L)
        val seenValues = mutableSetOf<Rational>()

        distractors.forEach { d ->
            assertNotEquals(solution, d.value)
            assertTrue(d.value !in seenValues)
            assertTrue(d.diagnosticFeedbackEs.isNotBlank())
            seenValues.add(d.value!!)
        }

        // 1. HIERARCHY_PRECEDENCE_SLIP (distribución incompleta): (28 - 3) / 4 = 25/4
        val slipDistractor = distractors.firstOrNull { it.bug == CognitiveBug.HIERARCHY_PRECEDENCE_SLIP }
        assertTrue(slipDistractor != null, "Debe incluir distribución incompleta.")
        assertEquals(Rational.of(25L, 4L), slipDistractor.value)

        // 2. SIGN_FLIP_OMISSION: 28/4 + 3 = 10
        val signDistractor = distractors.firstOrNull { it.bug == CognitiveBug.SIGN_FLIP_OMISSION }
        assertTrue(signDistractor != null, "Debe incluir omisión de cambio de signo.")
        assertEquals(Rational.fromLong(10L), signDistractor.value)

        // 3. ADDITIVE_DIVISOR: 28 - 4 - 3 = 21
        val additiveDistractor = distractors.firstOrNull { it.bug == CognitiveBug.ADDITIVE_DIVISOR }
        assertTrue(additiveDistractor != null, "Debe incluir tratamiento aditivo.")
        assertEquals(Rational.fromLong(21L), additiveDistractor.value)
    }

    @Test
    fun testFractionLinearCanonicalEquation() {
        // (x + 3) / 4 = 5  =>  x = 5*4 - 3 = 17
        val distractors = CognitiveDistractorSynthesizer.forFractionLinear(3L, 4L, 5L)

        assertEquals(3, distractors.size)
        val solution = Rational.fromLong(17L)
        val seenValues = mutableSetOf<Rational>()

        distractors.forEach { d ->
            assertNotEquals(solution, d.value)
            assertTrue(d.value !in seenValues)
            assertTrue(d.diagnosticFeedbackEs.isNotBlank())
            seenValues.add(d.value!!)
        }

        // 1. SIGN_FLIP_OMISSION: 5*4 + 3 = 23
        val signDistractor = distractors.firstOrNull { it.bug == CognitiveBug.SIGN_FLIP_OMISSION }
        assertTrue(signDistractor != null)
        assertEquals(Rational.fromLong(23L), signDistractor.value)

        // 2. HIERARCHY_PRECEDENCE_SLIP: 4 * (5 - 3) = 8
        val slipDistractor = distractors.firstOrNull { it.bug == CognitiveBug.HIERARCHY_PRECEDENCE_SLIP }
        assertTrue(slipDistractor != null)
        assertEquals(Rational.fromLong(8L), slipDistractor.value)

        // 3. ADDITIVE_DIVISOR: 5 - 4 - 3 = -2
        val additiveDistractor = distractors.firstOrNull { it.bug == CognitiveBug.ADDITIVE_DIVISOR }
        assertTrue(additiveDistractor != null)
        assertEquals(Rational.fromLong(-2L), additiveDistractor.value)
    }

    @Test
    fun testIntegerOpsExpression() {
        // 3 - 2(-4) + 5  =>  3 + 8 + 5 = 16
        val distractors = CognitiveDistractorSynthesizer.forIntegerHierarchy(3L, 2L, 4L, 5L)

        assertEquals(3, distractors.size)
        val solution = Rational.fromLong(16L)
        val seenValues = mutableSetOf<Rational>()

        distractors.forEach { d ->
            assertNotEquals(solution, d.value)
            assertTrue(d.value !in seenValues)
            assertTrue(d.diagnosticFeedbackEs.isNotBlank())
            seenValues.add(d.value!!)
        }

        // 1. HIERARCHY_PRECEDENCE_SLIP: (3 - 2) * (-4) + 5 = 1 * (-4) + 5 = 1
        val slipDistractor = distractors.firstOrNull { it.bug == CognitiveBug.HIERARCHY_PRECEDENCE_SLIP }
        assertTrue(slipDistractor != null)
        assertEquals(Rational.fromLong(1L), slipDistractor.value)

        // 2. SIGN_FLIP_OMISSION: 3 - 8 + 5 = 0
        val signDistractor = distractors.firstOrNull { it.bug == CognitiveBug.SIGN_FLIP_OMISSION }
        assertTrue(signDistractor != null)
        assertEquals(Rational.ZERO, signDistractor.value)

        // 3. ADDITIVE_DIVISOR: 3 - 2 + 4 + 5 = 10
        val addDistractor = distractors.firstOrNull { it.bug == CognitiveBug.ADDITIVE_DIVISOR }
        assertTrue(addDistractor != null)
        assertEquals(Rational.fromLong(10L), addDistractor.value)
    }

    @Test
    fun testSimpleHierarchyExpression() {
        // 2 + 3 * 4  =>  14
        val distractors = CognitiveDistractorSynthesizer.forHierarchyExpression(2L, 3L, 4L)

        assertEquals(3, distractors.size)
        val solution = Rational.fromLong(14L)
        val seenValues = mutableSetOf<Rational>()

        distractors.forEach { d ->
            assertNotEquals(solution, d.value)
            assertTrue(d.value !in seenValues)
            assertTrue(d.diagnosticFeedbackEs.isNotBlank())
            seenValues.add(d.value!!)
        }

        // (2 + 3) * 4 = 20
        val slip = distractors.firstOrNull { it.bug == CognitiveBug.HIERARCHY_PRECEDENCE_SLIP }
        assertTrue(slip != null)
        assertEquals(Rational.fromLong(20L), slip.value)
    }

    @Test
    fun testFractionAdditionExpression() {
        // 1/2 + 1/3  =>  5/6
        val distractors = CognitiveDistractorSynthesizer.forFractionAddition(1L, 2L, 1L, 3L)

        assertEquals(3, distractors.size)
        val solution = Rational.of(5L, 6L)
        val seenValues = mutableSetOf<Rational>()

        distractors.forEach { d ->
            assertNotEquals(solution, d.value)
            assertTrue(d.value !in seenValues)
            assertTrue(d.diagnosticFeedbackEs.isNotBlank())
            seenValues.add(d.value!!)
        }

        // FRACTION_DENOMINATOR_ADDITION: (1+1)/(2+3) = 2/5
        val denAdd = distractors.firstOrNull { it.bug == CognitiveBug.FRACTION_DENOMINATOR_ADDITION }
        assertTrue(denAdd != null)
        assertEquals(Rational.of(2L, 5L), denAdd.value)
        assertTrue(denAdd.diagnosticFeedbackEs.contains("común denominador"))
    }

    @Test
    fun testExhaustiveSynthesisForAllRegisteredTemplates() {
        // Para cada plantilla del catálogo generamos varias instancias y verificamos invariantes
        for (template in ExerciseTemplates.all) {
            val batch = generator.generateBatch(template.id, seed = 42L, count = 10)
            assertTrue(batch.instances.isNotEmpty(), "La plantilla '${template.id}' debe generar instancias.")

            for (instance in batch.instances) {
                val distractors = CognitiveDistractorSynthesizer.forInstance(instance)

                assertEquals(
                    3,
                    distractors.size,
                    "Plantilla '${instance.templateId}' con semilla ${instance.seed} debe tener 3 distractores.",
                )

                val seenValues = mutableSetOf<Rational>()
                val seenTexts = mutableSetOf<String>()

                for (distractor in distractors) {
                    val value = distractor.value
                    if (value != null) {
                        assertNotEquals(
                            instance.solution,
                            value,
                            "Distractor '${distractor.text}' coincide con la solución ${instance.solution} en '${instance.equationText}'.",
                        )
                        assertTrue(
                            value !in seenValues,
                            "Distractor duplicado en valor: $value en '${instance.equationText}'.",
                        )
                        seenValues.add(value)

                        // Verificación con MathEngine: el distractor no debe satisfacer la ecuación original
                        if (instance.equationText.contains('=')) {
                            val verification = engine.verifySolution(instance.equationText, instance.variable, value)
                            assertTrue(
                                verification !is VerificationOutcome.Satisfies,
                                "El distractor $value satisfizo la ecuación original '${instance.equationText}'.",
                            )
                        }
                    }

                    assertTrue(
                        distractor.text !in seenTexts,
                        "Distractor duplicado en texto: ${distractor.text} en '${instance.equationText}'.",
                    )
                    seenTexts.add(distractor.text)

                    assertTrue(
                        distractor.diagnosticFeedbackEs.isNotBlank(),
                        "La retroalimentación diagnóstica no debe ser vacía para el bug ${distractor.bug}.",
                    )
                }
            }
        }
    }

    @Test
    fun testGeneralLinearStringEquation() {
        // 5x - 10 = 0  =>  x = 2
        val distractors = CognitiveDistractorSynthesizer.forGeneralLinear("5x - 10 = 0", Rational.fromLong(2L))

        assertEquals(3, distractors.size)
        val solution = Rational.fromLong(2L)
        distractors.forEach { d ->
            assertNotEquals(solution, d.value)
            assertTrue(d.diagnosticFeedbackEs.isNotBlank())
        }
        val uniqueValues = distractors.mapNotNull { it.value }.toSet()
        assertEquals(3, uniqueValues.size)
    }

    @Test
    fun testCognitiveBugMetadataAndMapping() {
        for (bug in CognitiveBug.entries) {
            assertTrue(bug.id.isNotBlank())
            assertTrue(bug.labelEs.isNotBlank())
        }

        assertEquals(
            mx.matica.math.analysis.StepError.SIGN_FLIP_OMISSION,
            CognitiveBug.SIGN_FLIP_OMISSION.toStepError(),
        )
        assertEquals(
            mx.matica.math.analysis.StepError.ADDITIVE_DIVISOR_ERROR,
            CognitiveBug.ADDITIVE_DIVISOR.toStepError(),
        )
        assertEquals(
            mx.matica.math.analysis.StepError.FRACTION_DENOMINATOR_ADDITION,
            CognitiveBug.FRACTION_DENOMINATOR_ADDITION.toStepError(),
        )
        assertEquals(
            mx.matica.math.analysis.StepError.ORDER_OF_OPERATIONS,
            CognitiveBug.HIERARCHY_PRECEDENCE_SLIP.toStepError(),
        )
    }
}
