package mx.matica.exercises

import mx.matica.math.numeric.Rational
import mx.matica.math.skills.SkillId

/**
 * Catálogo de problemas verbales con andamiaje de 4 estaciones cognitivas.
 */
object WordProblemRepository {

    val all: List<ScaffoldingWordProblem> = listOf(
        ScaffoldingWordProblem(
            id = "wp-taxi-01",
            title = "La tarifa del taxi",
            storyContext = "Un taxi cobra un banderazo fijo de \$15 pesos al subir, más \$8 pesos por cada kilómetro recorrido. Al llegar a su destino, el taxímetro marca un total de \$95 pesos.",
            skill = SkillId.BALANCE_PRINCIPLE,
            stage1 = UnknownIdentificationStage(
                prompt = "¿Qué representa exactamente la incógnita 'x' en este problema?",
                choices = listOf(
                    WordProblemChoice(
                        id = "c1",
                        text = "El número de kilómetros recorridos durante el viaje.",
                        isCorrect = true,
                        explanation = "Es la cantidad desconocida que varía y determina el costo variable.",
                    ),
                    WordProblemChoice(
                        id = "c2",
                        text = "El costo total del viaje (\$95 pesos).",
                        isCorrect = false,
                        explanation = "El costo total es un dato conocido (\$95), no la incógnita.",
                    ),
                    WordProblemChoice(
                        id = "c3",
                        text = "El precio del banderazo inicial (\$15 pesos).",
                        isCorrect = false,
                        explanation = "El banderazo es un valor fijo conocido (\$15).",
                    ),
                ),
            ),
            stage2 = DataExtractionStage(
                prompt = "¿Cómo se clasifican los costos que componen la tarifa?",
                choices = listOf(
                    WordProblemChoice(
                        id = "c1",
                        text = "Fijo: \$15 pesos (banderazo); Variable: \$8 pesos por cada km (8x).",
                        isCorrect = true,
                        explanation = "El banderazo no cambia; el costo por distancia se multiplica por cada kilómetro (8x).",
                    ),
                    WordProblemChoice(
                        id = "c2",
                        text = "Fijo: \$8 pesos; Variable: \$15 pesos por km (15x).",
                        isCorrect = false,
                        explanation = "Los valores están invertidos: el cobro fijo es de 15, no de 8.",
                    ),
                    WordProblemChoice(
                        id = "c3",
                        text = "Todos los costos son fijos y suman 15 + 8 = 23 pesos.",
                        isCorrect = false,
                        explanation = "El costo de 8 pesos depende de cuántos kilómetros se recorran, no es fijo.",
                    ),
                ),
            ),
            stage3 = EquationModelingStage(
                prompt = "¿Cuál es la ecuación lineal que modela la situación matemática?",
                equationText = "8x + 15 = 95",
                choices = listOf(
                    WordProblemChoice(
                        id = "c1",
                        text = "8x + 15 = 95",
                        isCorrect = true,
                        explanation = "8 por cada kilómetro (8x) más los 15 de arranque suman el total de 95.",
                    ),
                    WordProblemChoice(
                        id = "c2",
                        text = "15x + 8 = 95",
                        isCorrect = false,
                        explanation = "El 15 no multiplica a los kilómetros; es una cuota única fija.",
                    ),
                    WordProblemChoice(
                        id = "c3",
                        text = "8x - 15 = 95",
                        isCorrect = false,
                        explanation = "El banderazo se suma a la cuenta, no se descuenta ni resta.",
                    ),
                ),
            ),
            stage4 = ContextualInterpretationStage(
                prompt = "Al resolver 8x + 15 = 95 obtenemos x = 10. ¿Qué significa este valor en la vida real?",
                numericSolution = Rational.of(10, 1),
                choices = listOf(
                    WordProblemChoice(
                        id = "c1",
                        text = "El viaje en taxi recorrió una distancia de 10 kilómetros.",
                        isCorrect = true,
                        explanation = "Como 'x' representa los kilómetros, x = 10 indica exactamente 10 km.",
                    ),
                    WordProblemChoice(
                        id = "c2",
                        text = "El pasajero pagó \$10 pesos en total.",
                        isCorrect = false,
                        explanation = "El total pagado fue de \$95 pesos, no \$10.",
                    ),
                    WordProblemChoice(
                        id = "c3",
                        text = "El viaje duró 10 minutos.",
                        isCorrect = false,
                        explanation = "La variable mide distancia en kilómetros, no tiempo en minutos.",
                    ),
                ),
            ),
            wrapUpStory = "¡Ciclo de modelado completado! Has traducido una situación cotidiana en una ecuación lineal, la resolviste y le diste sentido real al resultado.",
        ),

        ScaffoldingWordProblem(
            id = "wp-papeleria-01",
            title = "Compra en la papelería",
            storyContext = "Ana compró 4 cuadernos del mismo precio y una mochila escolar de \$120 pesos. En la caja registradora pagó un total de \$360 pesos.",
            skill = SkillId.ISOLATING_VARIABLE,
            stage1 = UnknownIdentificationStage(
                prompt = "¿Cuál es la incógnita 'x' que necesitamos encontrar?",
                choices = listOf(
                    WordProblemChoice(
                        id = "c1",
                        text = "El precio individual de un cuaderno.",
                        isCorrect = true,
                        explanation = "Es el valor desconocido que queremos calcular.",
                    ),
                    WordProblemChoice(
                        id = "c2",
                        text = "La cantidad de cuadernos comprados (4).",
                        isCorrect = false,
                        explanation = "Sabemos con certeza que compró 4 cuadernos, no es una incógnita.",
                    ),
                    WordProblemChoice(
                        id = "c3",
                        text = "El precio de la mochila (\$120).",
                        isCorrect = false,
                        explanation = "El precio de la mochila ya está dado en el enunciado.",
                    ),
                ),
            ),
            stage2 = DataExtractionStage(
                prompt = "¿Cómo se expresa algebraicamente el costo de los 4 cuadernos más la mochila?",
                choices = listOf(
                    WordProblemChoice(
                        id = "c1",
                        text = "4x representa el costo de los cuadernos, y 120 es el costo fijo de la mochila.",
                        isCorrect = true,
                        explanation = "4 cuadernos por su precio unitario x es 4x; la mochila suma 120.",
                    ),
                    WordProblemChoice(
                        id = "c2",
                        text = "4 + x representa el costo de los cuadernos.",
                        isCorrect = false,
                        explanation = "El costo de varios artículos iguales se multiplica (4x), no se suma (4+x).",
                    ),
                    WordProblemChoice(
                        id = "c3",
                        text = "120x representa los cuadernos y 4 la mochila.",
                        isCorrect = false,
                        explanation = "Los números están intercambiados.",
                    ),
                ),
            ),
            stage3 = EquationModelingStage(
                prompt = "¿Cuál es la ecuación para encontrar el precio de cada cuaderno?",
                equationText = "4x + 120 = 360",
                choices = listOf(
                    WordProblemChoice(
                        id = "c1",
                        text = "4x + 120 = 360",
                        isCorrect = true,
                        explanation = "El costo de 4 cuadernos (4x) más la mochila (120) iguala el pago total (360).",
                    ),
                    WordProblemChoice(
                        id = "c2",
                        text = "4x - 120 = 360",
                        isCorrect = false,
                        explanation = "La mochila agrega costo, no reduce el total.",
                    ),
                    WordProblemChoice(
                        id = "c3",
                        text = "4x = 360 + 120",
                        isCorrect = false,
                        explanation = "El total de 360 ya incluye los 120 de la mochila.",
                    ),
                ),
            ),
            stage4 = ContextualInterpretationStage(
                prompt = "Resolviendo la ecuación: 4x = 240, luego x = 60. ¿Qué significa x = 60?",
                numericSolution = Rational.of(60, 1),
                choices = listOf(
                    WordProblemChoice(
                        id = "c1",
                        text = "Cada cuaderno costó \$60 pesos.",
                        isCorrect = true,
                        explanation = "Comprobación: 4 × 60 = 240, más 120 de la mochila = \$360 pesos.",
                    ),
                    WordProblemChoice(
                        id = "c2",
                        text = "Ana compró 60 cuadernos en total.",
                        isCorrect = false,
                        explanation = "Ana compró solo 4 cuadernos.",
                    ),
                    WordProblemChoice(
                        id = "c3",
                        text = "La mochila costó \$60 pesos.",
                        isCorrect = false,
                        explanation = "La mochila costó \$120 pesos según el problema.",
                    ),
                ),
            ),
            wrapUpStory = "¡Magia del álgebra! Descomponer el problema en costo unitario y constante permitió saber el precio exacto de cada cuaderno sin tener que adivinar.",
        ),

        ScaffoldingWordProblem(
            id = "wp-cine-01",
            title = "Boletos de cine con descuento grupal",
            storyContext = "Tres amigos fueron juntos al cine. El precio normal de un boleto es 'x' pesos, pero por una promoción grupal a cada boleto se le descontaron \$15 pesos. En total por los 3 boletos pagaron \$195 pesos: 3(x - 15) = 195.",
            skill = SkillId.DISTRIBUTIVE_PROPERTY,
            stage1 = UnknownIdentificationStage(
                prompt = "¿Qué representa la variable 'x' en este contexto grupal?",
                choices = listOf(
                    WordProblemChoice(
                        id = "c1",
                        text = "El precio normal y original de un boleto sin descuento.",
                        isCorrect = true,
                        explanation = "Es el valor base antes de restarle los \$15 pesos de cupón.",
                    ),
                    WordProblemChoice(
                        id = "c2",
                        text = "El número de amigos en el grupo (3).",
                        isCorrect = false,
                        explanation = "La cantidad de amigos ya se conoce y es 3.",
                    ),
                    WordProblemChoice(
                        id = "c3",
                        text = "El descuento total de los tres amigos.",
                        isCorrect = false,
                        explanation = "El descuento individual es \$15, no la incógnita x.",
                    ),
                ),
            ),
            stage2 = DataExtractionStage(
                prompt = "¿Por qué la expresión tiene un paréntesis multiplicado por 3: 3(x - 15)?",
                choices = listOf(
                    WordProblemChoice(
                        id = "c1",
                        text = "Porque son 3 amigos y a cada uno se le aplica el descuento individual (x - 15).",
                        isCorrect = true,
                        explanation = "El paréntesis agrupa el precio con descuento de un boleto; el 3 lo repite para cada amigo.",
                    ),
                    WordProblemChoice(
                        id = "c2",
                        text = "Porque el descuento de 15 se divide entre los 3 amigos.",
                        isCorrect = false,
                        explanation = "El descuento de \$15 es para cada boleto, no a repartir.",
                    ),
                    WordProblemChoice(
                        id = "c3",
                        text = "Porque compraron 3 palomitas y 15 refrescos.",
                        isCorrect = false,
                        explanation = "No tiene relación con los datos del problema.",
                    ),
                ),
            ),
            stage3 = EquationModelingStage(
                prompt = "Al aplicar la propiedad distributiva a 3(x - 15) = 195, ¿cómo queda la ecuación desarrollada?",
                equationText = "3x - 45 = 195",
                choices = listOf(
                    WordProblemChoice(
                        id = "c1",
                        text = "3x - 45 = 195",
                        isCorrect = true,
                        explanation = "El 3 multiplica tanto a x como al -15: 3×x = 3x y 3×(-15) = -45.",
                    ),
                    WordProblemChoice(
                        id = "c2",
                        text = "3x - 15 = 195",
                        isCorrect = false,
                        explanation = "¡Error canónico de distribución! Se olvidó multiplicar el 3 por el 15.",
                    ),
                    WordProblemChoice(
                        id = "c3",
                        text = "3x + 45 = 195",
                        isCorrect = false,
                        explanation = "Como era un descuento (-15), al multiplicar por 3 queda -45, no +45.",
                    ),
                ),
            ),
            stage4 = ContextualInterpretationStage(
                prompt = "Resolviendo: 3x = 240, por lo que x = 80. ¿Qué significa x = 80?",
                numericSolution = Rational.of(80, 1),
                choices = listOf(
                    WordProblemChoice(
                        id = "c1",
                        text = "El precio original de cada boleto sin descuento era de \$80 pesos.",
                        isCorrect = true,
                        explanation = "Con descuento pagaron 80 - 15 = \$65 por boleto; 65 × 3 = \$195 pesos.",
                    ),
                    WordProblemChoice(
                        id = "c2",
                        text = "Cada boleto con descuento costó \$80 pesos.",
                        isCorrect = false,
                        explanation = "\$80 era el precio regular; con descuento pagaron \$65.",
                    ),
                    WordProblemChoice(
                        id = "c3",
                        text = "Los amigos ahorraron \$80 pesos en total.",
                        isCorrect = false,
                        explanation = "El ahorro total fue de 3 × 15 = \$45 pesos.",
                    ),
                ),
            ),
            wrapUpStory = "¡Dominio total de la propiedad distributiva en problemas reales! Los paréntesis representan grupos que se repiten con sus propios ajustes internos.",
        ),
    )

    fun byId(id: String): ScaffoldingWordProblem? = all.firstOrNull { it.id == id }

    fun forSkill(skill: SkillId): List<ScaffoldingWordProblem> = all.filter { it.skill == skill }
}
