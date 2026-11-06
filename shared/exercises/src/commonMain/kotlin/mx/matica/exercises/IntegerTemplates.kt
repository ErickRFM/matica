package mx.matica.exercises

import kotlin.math.abs
import mx.matica.math.ast.Expr
import mx.matica.math.skills.SkillId

/**
 * `a - b(-c) (+ d)`: el signo del producto decide el resultado antes de cualquier despeje, que es
 * exactamente la habilidad raíz del currículo. Sin esta plantilla el grafo arranca en
 * «Operaciones con enteros» y el catálogo no tiene ejercicios ahí, así que un estudiante nuevo
 * llegaba a la ruta y no podía abrir nada (lo mostró la caminata por el emulador).
 */
class IntegerOpsTemplate : ExerciseTemplate {
    override val id = "integer-ops"
    override val labelEs = "Enteros: producto de signos"
    override val skill = SkillId.INTEGER_OPERATIONS
    override val variable = "x"
    override val ranges = listOf(
        ParameterRange("a", -12, 12),
        // |b| y |c| ≥ 2: con 1 el producto sería un simple cambio de signo disfrazado.
        ParameterRange("b", 2, 9),
        ParameterRange("c", 2, 9),
        ParameterRange("d", -15, 15),
    )

    override fun build(params: ParameterSet, seed: Long): GenerationOutcome {
        val reasons = validateRanges(ranges, params).toMutableList()
        val a = params.of("a") ?: return rejected(reasons)
        val b = params.of("b") ?: return rejected(reasons)
        val c = params.of("c") ?: return rejected(reasons)
        val d = params.of("d") ?: return rejected(reasons)
        if (reasons.isNotEmpty()) return GenerationOutcome.Rejected(reasons)
        if (a == 0) return GenerationOutcome.Rejected(listOf("'a' = 0 abre la suma con un término vacío."))

        val product = Expr.Product(
            listOf(
                Expr.Product.Factor(explicit = false, ExerciseAst.const(b.toLong())),
                Expr.Product.Factor(explicit = false, ExerciseAst.negativeParen(c.toLong())),
            ),
        )
        val terms = mutableListOf(
            ExerciseAst.term(a < 0, ExerciseAst.const(abs(a).toLong())),
            Expr.Sum.Term(operator = Expr.Sum.Term.Operator.MINUS, operand = product),
        )
        if (d != 0) terms += ExerciseAst.term(d < 0, ExerciseAst.const(abs(d).toLong()))

        val equation = Expr.Equals(left = ExerciseAst.variable(variable), right = Expr.Sum(terms))
        return ready(equation, a.toLong() + b.toLong() * c.toLong() + d.toLong(), params, seed)
    }
}
