package mx.matica.exercises

import mx.matica.math.numeric.Rational
import mx.matica.math.skills.SkillId

/**
 * Opción de selección múltiple para una estación de problema verbal.
 */
data class WordProblemChoice(
    val id: String,
    val text: String,
    val isCorrect: Boolean,
    val explanation: String,
)

/**
 * Estación 1: Identificación de la incógnita en el contexto del problema.
 */
data class UnknownIdentificationStage(
    val prompt: String,
    val choices: List<WordProblemChoice>,
)

/**
 * Estación 2: Extracción y distinción de datos fijos vs variables.
 */
data class DataExtractionStage(
    val prompt: String,
    val choices: List<WordProblemChoice>,
)

/**
 * Estación 3: Construcción y ensamble del modelo algebraico (la ecuación formal).
 */
data class EquationModelingStage(
    val prompt: String,
    val equationText: String,
    val choices: List<WordProblemChoice>,
)

/**
 * Estación 4: Interpretación contextual de la solución matemática calculada.
 */
data class ContextualInterpretationStage(
    val prompt: String,
    val numericSolution: Rational,
    val choices: List<WordProblemChoice>,
)

/**
 * Problema verbal modelado según el principio de andamiaje cognitivo (Kintsch & Greeno).
 * Descompone la complejidad lingüística en 4 estaciones progresivas antes de requerir
 * la resolución simbólica, cerrando la brecha entre el lenguaje natural y el álgebra.
 */
data class ScaffoldingWordProblem(
    val id: String,
    val title: String,
    val storyContext: String,
    val skill: SkillId,
    val stage1: UnknownIdentificationStage,
    val stage2: DataExtractionStage,
    val stage3: EquationModelingStage,
    val stage4: ContextualInterpretationStage,
    val wrapUpStory: String,
)
