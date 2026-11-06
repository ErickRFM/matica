package mx.matica.presentation.app

import kotlin.test.Test
import kotlin.test.assertTrue
import mx.matica.math.skills.SkillId

class TopicNamingPolicyTest {
    @Test
    fun losTemasNoClasificanPorEtapaEscolar() {
        val forbidden = listOf("primaria", "secundaria", "preparatoria", "bachillerato", "licenciatura", "universitario")
        SkillId.entries.forEach { topic ->
            forbidden.forEach { label ->
                assertTrue(!topic.labelEs.contains(label, ignoreCase = true), "No mostrar nivel escolar en ${topic.id}: $label")
            }
        }
    }
}
