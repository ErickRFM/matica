package mx.matica.learning.skills

import mx.matica.learning.text.pluralEs
import mx.matica.math.skills.SkillId

sealed interface GraphBuild {
    data class Ready(val graph: SkillGraph) : GraphBuild

    data class Rejected(val problems: List<GraphProblem>) : GraphBuild
}

sealed interface GraphProblem {
    data class SelfPrerequisite(val skill: SkillId) : GraphProblem

    data class Cycle(val path: List<SkillId>) : GraphProblem
}

data class LearningPath(val orderedSkills: List<SkillId>, val reason: String)

/**
 * Grafo de prerrequisitos. Una habilidad solo se desbloquea cuando sus prerrequisitos directos están
 * dominados, así que `order()` es también el orden en que se puede trabajar sin saltarse base.
 */
class SkillGraph private constructor(val edges: Map<SkillId, Set<SkillId>>) {

    /** Todos los nodos, incluidos los que solo aparecen como prerrequisito. */
    val skills: List<SkillId> = (edges.keys + edges.values.flatten()).toSet().sortedBySkillId()

    fun prerequisitesOf(skill: SkillId): Set<SkillId> = edges[skill].orEmpty()

    fun isRoot(skill: SkillId): Boolean = prerequisitesOf(skill).isEmpty()

    fun dependentsOf(skill: SkillId): Set<SkillId> =
        edges.filterValues { skill in it }.keys.sortedBySkillId().toSet()

    fun transitivePrerequisites(skill: SkillId): Set<SkillId> {
        val found = mutableSetOf<SkillId>()
        val pending = ArrayDeque(prerequisitesOf(skill))
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (found.add(current)) pending.addAll(prerequisitesOf(current))
        }
        return found
    }

    /** Orden topológico determinista: el mismo grafo siempre devuelve la misma lista. */
    fun order(): List<SkillId> {
        val pending = skills.associateWithTo(mutableMapOf()) { prerequisitesOf(it).toMutableSet() }
        val ordered = mutableListOf<SkillId>()
        while (pending.isNotEmpty()) {
            val readyNow = pending.filterValues { it.isEmpty() }.keys.sortedBySkillId()
            check(readyNow.isNotEmpty()) { "Grafo cíclico imposible de ordenar: $pending" }
            ordered += readyNow
            readyNow.forEach { skill ->
                pending.remove(skill)
                pending.values.forEach { it.remove(skill) }
            }
        }
        return ordered
    }

    /** Frontera real de trabajo: sin dominar y con todos sus prerrequisitos dominados. */
    fun availableSkills(mastered: Set<SkillId>): List<SkillId> = skills
        .filter { it !in mastered }
        .filter { skill -> prerequisitesOf(skill).all { prerequisite -> prerequisite in mastered } }

    /** Ruta de trabajo: lo que falta, ya en el orden topológico del grafo, y la meta al final. */
    fun pathTo(target: SkillId, mastered: Set<SkillId>): LearningPath {
        val sequence = order().withIndex().associate { (index, skill) -> skill to index }
        val missing = transitivePrerequisites(target).filterNot { it in mastered }
            .sortedBy { sequence.getValue(it) }
        return LearningPath(
            orderedSkills = missing + target,
            reason = if (missing.isEmpty()) {
                "${target.labelEs} está disponible: ya domina sus prerrequisitos."
            } else {
                val faltan = pluralEs(missing.size, "falta", "faltan")
                "Para llegar a ${target.labelEs} $faltan ${missing.size} " +
                    pluralEs(missing.size, "prerrequisito", "prerrequisitos") +
                    ": " + missing.joinToString { it.labelEs } + "."
            },
        )
    }

    class Builder {
        private val requirements = linkedMapOf<SkillId, MutableSet<SkillId>>()

        fun require(skill: SkillId, prerequisite: SkillId): Builder = apply {
            requirements.getOrPut(skill) { linkedSetOf() }.add(prerequisite)
            requirements.getOrPut(prerequisite) { linkedSetOf() }
        }

        fun build(): GraphBuild {
            val edges = requirements.mapValues { (_, prerequisites) -> prerequisites.toSet() }
            val selfRequirements = edges.filter { (skill, prerequisites) -> skill in prerequisites }
                .keys.sortedBySkillId()
                .map { GraphProblem.SelfPrerequisite(it) }
            if (selfRequirements.isNotEmpty()) return GraphBuild.Rejected(selfRequirements)
            val cycle = findCycle(edges) ?: return GraphBuild.Ready(SkillGraph(edges))
            return GraphBuild.Rejected(listOf(GraphProblem.Cycle(cycle)))
        }
    }

    companion object {
        fun builder(): Builder = Builder()

        private fun findCycle(edges: Map<SkillId, Set<SkillId>>): List<SkillId>? {
            val stack = mutableListOf<SkillId>()
            val onStack = mutableSetOf<SkillId>()
            val visited = mutableSetOf<SkillId>()

            fun cycleFrom(start: SkillId): List<SkillId> =
                stack.subList(stack.indexOf(start), stack.size).toList() + start

            fun visit(skill: SkillId): List<SkillId>? {
                if (skill in onStack) return cycleFrom(skill)
                if (skill in visited) return null
                visited += skill
                onStack += skill
                stack += skill
                prerequisitesOf(edges, skill).forEach { prerequisite ->
                    val found = visit(prerequisite)
                    if (found != null) return found
                }
                stack.removeAt(stack.lastIndex)
                onStack -= skill
                return null
            }

            edges.keys.sortedBySkillId().forEach { skill ->
                val found = visit(skill)
                if (found != null) return found
            }
            return null
        }

        private fun prerequisitesOf(edges: Map<SkillId, Set<SkillId>>, skill: SkillId): Set<SkillId> =
            edges[skill].orEmpty()
    }
}

private fun Iterable<SkillId>.sortedBySkillId(): List<SkillId> = sortedBy { it.ordinal }
