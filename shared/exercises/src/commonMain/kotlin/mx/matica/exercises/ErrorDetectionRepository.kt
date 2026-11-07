package mx.matica.exercises

import mx.matica.math.analysis.StepError
import mx.matica.math.skills.SkillId

/**
 * Catálogo de ejercicios metacognitivos "Detecta el Error".
 * Diseñados para activar el análisis crítico del estudiante sobre desarrollos matemáticos
 * ajenos, despersonalizando el error y consolidando los principios de equivalencia y balance.
 */
object ErrorDetectionRepository {

    val all: List<ErrorDetectionExercise> = listOf(
        ErrorDetectionExercise(
            id = "err-sign-flip-01",
            title = "Despejando con sumas y restas",
            studentName = "Mateo",
            initialProblem = "3x + 15 = 24",
            steps = listOf(
                WorkedStep(
                    stepNumber = 1,
                    expression = "3x = 24 + 15",
                    explanation = "Pasa el 15 al lado derecho",
                    isErroneous = true,
                    canonicalError = StepError.SIGN_FLIP_OMISSION,
                ),
                WorkedStep(
                    stepNumber = 2,
                    expression = "3x = 39",
                    explanation = "Suma 24 + 15",
                    isErroneous = false,
                ),
                WorkedStep(
                    stepNumber = 3,
                    expression = "x = 13",
                    explanation = "Divide ambos lados entre 3",
                    isErroneous = false,
                ),
            ),
            erroneousStepNumber = 1,
            targetSkill = SkillId.SIGN_HANDLING,
            reasonOptions = listOf(
                ErrorReasonOption(
                    id = "sign-omission",
                    text = "Omitió invertir el signo: al restar 15 en ambos lados debe quedar 24 - 15.",
                    isCorrect = true,
                    explanation = "El principio de balance exige aplicar la operación inversa. Si sumaba 15, debe restar 15.",
                ),
                ErrorReasonOption(
                    id = "wrong-division-first",
                    text = "Debía dividir entre 3 antes de mover el 15.",
                    isCorrect = false,
                    explanation = "Aunque es posible dividir todo entre 3 primero, aislar el término con variable restando 15 es la ruta estándar.",
                ),
                ErrorReasonOption(
                    id = "bad-sum",
                    text = "La suma de 24 + 15 está mal calculada.",
                    isCorrect = false,
                    explanation = "24 + 15 es efectivamente 39; el error ocurrió antes al plantear la suma en lugar de la resta.",
                ),
            ),
            wrapUpPedagogy = "¡Excelente ojo crítico! Al trasponer un término al otro miembro de la ecuación, en realidad estamos sumando o restando en ambos lados para mantener la balanza en equilibrio.",
        ),

        ErrorDetectionExercise(
            id = "err-additive-divisor-01",
            title = "El coeficiente multiplicador",
            studentName = "Sofía",
            initialProblem = "5x = 35",
            steps = listOf(
                WorkedStep(
                    stepNumber = 1,
                    expression = "x = 35 - 5",
                    explanation = "Despeja la x restando el 5",
                    isErroneous = true,
                    canonicalError = StepError.ADDITIVE_DIVISOR_ERROR,
                ),
                WorkedStep(
                    stepNumber = 2,
                    expression = "x = 30",
                    explanation = "Calcula la resta 35 - 5",
                    isErroneous = false,
                ),
            ),
            erroneousStepNumber = 1,
            targetSkill = SkillId.ISOLATING_VARIABLE,
            reasonOptions = listOf(
                ErrorReasonOption(
                    id = "additive-divisor",
                    text = "Restó el 5 en vez de dividir: el 5 está multiplicando a x.",
                    isCorrect = true,
                    explanation = "5x significa 5 multiplicado por x. La operación inversa de la multiplicación es la división.",
                ),
                ErrorReasonOption(
                    id = "arithmetic-subtraction",
                    text = "La resta 35 - 5 no es 30.",
                    isCorrect = false,
                    explanation = "La resta aritmética 35 - 5 sí es 30, pero la operación correcta era dividir entre 5.",
                ),
                ErrorReasonOption(
                    id = "variable-position",
                    text = "La x debió quedar al lado derecho.",
                    isCorrect = false,
                    explanation = "La variable puede aislarse en cualquier lado de la igualdad.",
                ),
            ),
            wrapUpPedagogy = "¡Muy bien! Un coeficiente pegado a la variable multiplica. Para neutralizarlo debemos dividir ambos platillos de la balanza entre ese mismo coeficiente (35 / 5 = 7).",
        ),

        ErrorDetectionExercise(
            id = "err-distributive-01",
            title = "Paréntesis y factores",
            studentName = "Lucas",
            initialProblem = "4(x + 3) = 28",
            steps = listOf(
                WorkedStep(
                    stepNumber = 1,
                    expression = "4x + 3 = 28",
                    explanation = "Quita el paréntesis multiplicando el 4",
                    isErroneous = true,
                    canonicalError = StepError.DISTRIBUTION_ERROR,
                ),
                WorkedStep(
                    stepNumber = 2,
                    expression = "4x = 25",
                    explanation = "Resta 3 en ambos lados",
                    isErroneous = false,
                ),
                WorkedStep(
                    stepNumber = 3,
                    expression = "x = 25/4",
                    explanation = "Divide entre 4",
                    isErroneous = false,
                ),
            ),
            erroneousStepNumber = 1,
            targetSkill = SkillId.DISTRIBUTIVE_PROPERTY,
            reasonOptions = listOf(
                ErrorReasonOption(
                    id = "incomplete-distribution",
                    text = "Distribuyó el 4 solo en la x y olvidó multiplicarlo por el 3 (debía ser 4x + 12).",
                    isCorrect = true,
                    explanation = "La propiedad distributiva a(b + c) = ab + ac exige multiplicar el factor por TODOS los términos dentro del paréntesis.",
                ),
                ErrorReasonOption(
                    id = "wrong-subtraction",
                    text = "28 - 3 no es 25.",
                    isCorrect = false,
                    explanation = "28 - 3 = 25 es aritméticamente correcto; la falla estuvo en la distribución previa.",
                ),
                ErrorReasonOption(
                    id = "cant-distribute-addition",
                    text = "No se puede distribuir cuando hay una suma.",
                    isCorrect = false,
                    explanation = "La distribución aplica precisamente sobre sumas y restas.",
                ),
            ),
            wrapUpPedagogy = "¡Brillante! El factor fuera del paréntesis multiplica a cada paquete o término interior: 4 grupos de (x + 3) son 4x y también 4×3 = 12.",
        ),

        ErrorDetectionExercise(
            id = "err-fraction-denominator-01",
            title = "Suma de fracciones",
            studentName = "Camila",
            initialProblem = "1/2 + 1/3 = x",
            steps = listOf(
                WorkedStep(
                    stepNumber = 1,
                    expression = "2/5 = x",
                    explanation = "Suma los numeradores (1+1) y los denominadores (2+3)",
                    isErroneous = true,
                    canonicalError = StepError.FRACTION_DENOMINATOR_ADDITION,
                ),
            ),
            erroneousStepNumber = 1,
            targetSkill = SkillId.FRACTION_ARITHMETIC,
            reasonOptions = listOf(
                ErrorReasonOption(
                    id = "denominators-direct-add",
                    text = "Sumó los denominadores directamente en vez de buscar un común denominador (sextos).",
                    isCorrect = true,
                    explanation = "Los denominadores indican el tamaño de los pedazos (medios y tercios). No se pueden sumar pedazos de distinto tamaño sin convertirlos primero a un tamaño común: 3/6 + 2/6 = 5/6.",
                ),
                ErrorReasonOption(
                    id = "numerators-error",
                    text = "1 + 1 no es 2.",
                    isCorrect = false,
                    explanation = "El numerador sumado da 2, pero sumar denominadores es conceptualmente inválido.",
                ),
                ErrorReasonOption(
                    id = "variable-side",
                    text = "La x debe estar a la izquierda para poder sumar.",
                    isCorrect = false,
                    explanation = "El orden de los miembros de una igualdad es simétrico.",
                ),
            ),
            wrapUpPedagogy = "¡Identificación impecable! 1/2 pastel más 1/3 pastel es casi un pastel entero (5/6 ≈ 83%), mientras que 2/5 es menos de la mitad (40%). Sumar denominadores viola el sentido de proporción.",
        ),

        ErrorDetectionExercise(
            id = "err-control-ok-01",
            title = "Resolución paso a paso",
            studentName = "Valeria",
            initialProblem = "6x - 8 = 22",
            steps = listOf(
                WorkedStep(
                    stepNumber = 1,
                    expression = "6x = 22 + 8",
                    explanation = "Suma 8 en ambos lados para cancelar el -8",
                    isErroneous = false,
                ),
                WorkedStep(
                    stepNumber = 2,
                    expression = "6x = 30",
                    explanation = "Calcula la suma 22 + 8 = 30",
                    isErroneous = false,
                ),
                WorkedStep(
                    stepNumber = 3,
                    expression = "x = 5",
                    explanation = "Divide ambos lados entre 6 (30 / 6 = 5)",
                    isErroneous = false,
                ),
            ),
            erroneousStepNumber = null, // ¡Control: no hay errores!
            targetSkill = SkillId.BALANCE_PRINCIPLE,
            reasonOptions = listOf(
                ErrorReasonOption(
                    id = "all-valid",
                    text = "Todos los pasos son completamente correctos y válidos.",
                    isCorrect = true,
                    explanation = "Cada paso respeta el principio de balance y la jerarquía aritmética.",
                ),
                ErrorReasonOption(
                    id = "fake-sign",
                    text = "El paso 1 debió restar 8 en vez de sumar 8.",
                    isCorrect = false,
                    explanation = "Como la ecuación tenía -8, la operación inversa correcta es +8.",
                ),
                ErrorReasonOption(
                    id = "fake-division",
                    text = "El paso 3 debió restar 6 en vez de dividir.",
                    isCorrect = false,
                    explanation = "6x multiplica la variable, por lo que dividir entre 6 es correcto.",
                ),
            ),
            wrapUpPedagogy = "¡Extraordinario juicio crítico! No siempre hay un error escondido: saber reconocer un desarrollo impecable demuestra un dominio conceptual sólido e independiente.",
        ),
    )

    fun byId(id: String): ErrorDetectionExercise? = all.firstOrNull { it.id == id }

    fun forSkill(skill: SkillId): List<ErrorDetectionExercise> = all.filter { it.targetSkill == skill }
}
