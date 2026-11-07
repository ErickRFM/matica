package mx.matica.presentation.route

import mx.matica.exercises.ExerciseTemplates
import mx.matica.math.skills.SkillId

/**
 * Los 18 destinos de la app como tipos, no como cadenas sueltas. `path` es la única representación
 * que viaja por el sistema (salvado de estado, enlace profundo, pila de navegación), así que cada
 * ruta debe poder reconstruirse desde ella: [parse] es la inversa exacta de [path].
 *
 * Un `SkillId` desconocido o un segmento con texto que no es número devuelven `null` en lugar de
 * caer: recibir un enlace roto desde el historial guardado es un caso normal, no un error del motor.
 */
sealed class MaticaRoute(val path: String) {

    data object Welcome : MaticaRoute("welcome")

    data object Diagnosis : MaticaRoute("diagnosis")

    data object DiagnosisResult : MaticaRoute("diagnosis/result")

    data object Login : MaticaRoute("login")

    data object Home : MaticaRoute("home")

    data object RouteMap : MaticaRoute("route")

    data class SkillDetail(val skill: SkillId) : MaticaRoute("route/skill/${skill.name}")

    data class LessonList(val skill: SkillId) : MaticaRoute("skill/${skill.name}/lessons")

    data class LessonPlayer(val skill: SkillId, val lesson: Int) :
        MaticaRoute("lesson/${skill.name}/$lesson")

    /** Capa modal sobre el reproductor: la pila no se rompe, se superpone. */
    data class HintSheet(val skill: SkillId, val lesson: Int) :
        MaticaRoute("lesson/${skill.name}/$lesson/hints")

    data class LessonComplete(val skill: SkillId, val lesson: Int) :
        MaticaRoute("lesson/${skill.name}/$lesson/complete")

    data class AttemptReview(val skill: SkillId, val lesson: Int, val attempt: Int) :
        MaticaRoute("lesson/${skill.name}/$lesson/attempt/$attempt")

    data class Practice(val templateId: String, val seed: Long) :
        MaticaRoute("practice/$templateId/$seed")

    data class ErrorSpotting(val exerciseId: String) : MaticaRoute("error-spotting/$exerciseId")

    data class WordProblem(val problemId: String) : MaticaRoute("word-problem/$problemId")

    data object BalanceLab : MaticaRoute("balance-lab")

    data object ReviewQueue : MaticaRoute("review")

    data object Rewards : MaticaRoute("rewards")

    data object Profile : MaticaRoute("profile")

    data object Settings : MaticaRoute("settings")

    data object Classroom : MaticaRoute("classroom")

    /** Verdadero en las rutas que se muestran encima de otra sin reemplazarla. */
    val isOverlay: Boolean get() = this is HintSheet

    companion object {

        /**
         * Los 18 destinos declarados, en el orden del recorrido normal de la app. Prueba de inventario.
         * Es `lazy` porque los objetos anidados se construyen llamando al constructor de esta clase:
         * inicializar la lista durante el arranque de `MaticaRoute` dejaría la lista con nulos.
         */
        val all: List<MaticaRoute> by lazy {
            listOf(
                Welcome, Diagnosis, DiagnosisResult, Login, Home, RouteMap,
                SkillDetail(SkillId.BALANCE_PRINCIPLE),
                LessonList(SkillId.BALANCE_PRINCIPLE),
                LessonPlayer(SkillId.BALANCE_PRINCIPLE, 1),
                HintSheet(SkillId.BALANCE_PRINCIPLE, 1),
                LessonComplete(SkillId.BALANCE_PRINCIPLE, 1),
                AttemptReview(SkillId.BALANCE_PRINCIPLE, 1, 1),
                Practice("two-step-linear", 1L),
                ErrorSpotting("err-sign-flip-01"),
                WordProblem("wp-taxi-01"),
                BalanceLab,
                ReviewQueue, Rewards, Profile, Settings, Classroom,
            )
        }

        private val SKILL_NAMES: Set<String> = SkillId.entries.map { it.name }.toSet()

        /**
         * Plantillas conocidas; `parse` rechaza las que no existen en lugar de inventar un ejercicio.
         * Se deriva del catálogo de `:shared:exercises` y no de una lista a mano: la lista escrita a
         * mano quedó obsoleta el día que el currículo añadió `integer-ops`, y esa plantilla dejó de
         * poder abrirse por enlace ni desde «Practicar sin grupo».
         */
        val KNOWN_TEMPLATES: Set<String> = ExerciseTemplates.all.mapTo(LinkedHashSet()) { it.id }

        fun parse(path: String): MaticaRoute? {
            val parts = path.trim('/').split('/')
            if (parts.isEmpty() || parts.first() == "") return null
            fun skillAt(index: Int): SkillId? =
                parts.getOrNull(index)?.takeIf { it in SKILL_NAMES }?.let { SkillId.valueOf(it) }

            return when (parts.first()) {
                "welcome" -> take(parts, 1) { Welcome }
                "login" -> take(parts, 1) { Login }
                "home" -> take(parts, 1) { Home }
                "review" -> take(parts, 1) { ReviewQueue }
                "rewards" -> take(parts, 1) { Rewards }
                "profile" -> take(parts, 1) { Profile }
                "settings" -> take(parts, 1) { Settings }
                "classroom" -> take(parts, 1) { Classroom }

                "diagnosis" -> when (parts.size) {
                    1 -> Diagnosis
                    2 -> if (parts[1] == "result") DiagnosisResult else null
                    else -> null
                }

                "route" -> when {
                    parts.size == 1 -> RouteMap
                    parts.size == 3 && parts[1] == "skill" -> skillAt(2)?.let { SkillDetail(it) }
                    else -> null
                }

                "skill" -> if (parts.size == 3 && parts[2] == "lessons") {
                    skillAt(1)?.let { LessonList(it) }
                } else {
                    null
                }

                "practice" -> if (parts.size == 3) {
                    val template = parts[1]
                    val seed = parts[2].toLongOrNull()
                    if (template in KNOWN_TEMPLATES && seed != null) Practice(template, seed) else null
                } else {
                    null
                }

                "error-spotting" -> if (parts.size == 2) ErrorSpotting(parts[1]) else null
                "word-problem" -> if (parts.size == 2) WordProblem(parts[1]) else null
                "balance-lab" -> take(parts, 1) { BalanceLab }

                "lesson" -> skillAt(1)?.let { skill ->
                    val lesson = parts.getOrNull(2)?.toIntOrNull() ?: return@let null
                    when {
                        parts.size == 3 -> LessonPlayer(skill, lesson)
                        parts.size == 4 && parts[3] == "hints" -> HintSheet(skill, lesson)
                        parts.size == 4 && parts[3] == "complete" -> LessonComplete(skill, lesson)
                        parts.size == 5 && parts[3] == "attempt" ->
                            parts[4].toIntOrNull()?.let { AttemptReview(skill, lesson, it) }

                        else -> null
                    }
                }

                else -> null
            }
        }

        /** Ruta de arranque: el diagnóstico sólo manda la primera vez. */
        fun entryPoint(hasProfile: Boolean, diagnosed: Boolean): MaticaRoute = when {
            !diagnosed -> Diagnosis
            !hasProfile -> Login
            else -> Home
        }

        private inline fun take(parts: List<String>, expected: Int, build: () -> MaticaRoute): MaticaRoute? =
            if (parts.size == expected) build() else null
    }
}
