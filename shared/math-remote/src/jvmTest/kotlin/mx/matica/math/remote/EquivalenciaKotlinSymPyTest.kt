package mx.matica.math.remote

import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import mx.matica.math.MathEngine
import mx.matica.math.analysis.StepVerdict
import mx.matica.math.ast.Expr
import mx.matica.math.numeric.Rational
import mx.matica.math.solve.SolveOutcome

/**
 * Contraste real entre el motor Kotlin offline y SymPy sobre las mismas líneas.
 *
 * Requiere el servicio levantado; sin URL la prueba se declara no ejecutada y no cuenta como verde:
 *   uvicorn app:app --port 8099
 *   ./gradlew :shared:math-remote:jvmTest -Pmatica.symbolic.baseUrl=http://127.0.0.1:8099
 *
 * Las expectaciones son lo que los dos motores realmente respondieron en la ejecución verificada,
 * incluido el desacuerdo honesto de las filas donde SymPy admite que no puede opinar.
 */
class EquivalenciaKotlinSymPyTest {

    // Offline puro: el gateway se consulta fila por fila, sin el filtro de la fachada, para poder comparar.
    private val engine = MathEngine()

    private data class Paso(
        val antes: String,
        val despues: String,
        val categoria: String,
        val kotlin: String,
        val sympy: String,
        val detalle: String = "",
    )

    private data class Ecuacion(
        val texto: String,
        val categoria: String,
        val kotlin: String,
        val sympy: String,
        val detalle: String = "",
    )

    private val pasos = listOf(
        Paso("2x + 3 = 7", "2x = 4", "lineal equivalente", "valido", "equivalente"),
        Paso("2(x+3) = 14", "2x + 6 = 14", "distributiva equivalente", "valido", "equivalente"),
        Paso("x/2 = 3", "x = 6", "despeje equivalente", "valido", "equivalente"),
        Paso("5 = x", "x = 5", "intercambio de lados", "valido", "equivalente"),
        Paso("3x + 2 = 3x + 5", "2 = 5", "contradicción correcta", "valido", "no_soportado"),
        Paso("x^2 = x", "x = 1", "solución perdida", "invalido", "no_equivalente", "perdidas=0"),
        Paso("x - 1 = 3", "x(x - 1) = 3x", "solución extraña", "invalido", "no_equivalente", "extrañas=0"),
        Paso("x^2 = 4", "x = 2", "cuadrática con raíz faltante", "indeterminado", "no_equivalente", "perdidas=-2"),
        Paso("(x^2-4)/(x-2) = x+2", "x^2-4 = (x+2)(x-2)", "restricción de dominio", "indeterminado", "no_equivalente", "extrañas=2"),
        Paso("2/x = 4", "2 = 4x", "racional con hueco", "indeterminado", "equivalente", "dominio=0"),
        Paso("2x + 3 = 7", "2x = 10", "lado derecho mal", "invalido", "no_equivalente"),
    )

    private val ecuaciones = listOf(
        Ecuacion("2x + 3 = 7", "lineal", "unica", "resuelto", "x=2"),
        Ecuacion("x^2 = 4", "cuadrática con raíz racional", "varias", "resuelto", "-2, 2"),
        Ecuacion("x^2 = 2", "cuadrática irracional", "bloqueado", "no_soportado"),
        Ecuacion("2/x = 4", "racional", "bloqueado", "resuelto", "x=1/2"),
        Ecuacion("3x + 2 = 3x + 5", "contradicción", "sin_solution", "no_soportado"),
        Ecuacion("2x = 2x", "identidad", "identidad", "no_soportado"),
        Ecuacion("(x^2-4)/(x-2) = x+2", "identidad con hueco", "bloqueado", "no_soportado"),
    )

    @Test
    fun `los dos motores coinciden donde ambos opinan`() {
        val baseUrl = System.getProperty("matica.symbolic.baseUrl")
        if (baseUrl.isNullOrBlank()) {
            writeReport("NO EJECUTADA: falta -Dmatica.symbolic.baseUrl con el servicio de SymPy levantado.\n")
            return
        }

        val client = symbolicHttpClient(OkHttp, baseUrl)
        val gateway = KtorSymbolicGateway(client)
        val informe = StringBuilder("CONTRASTE Kotlin vs SymPy  ($baseUrl)\n\nPASOS\n")
        val desacuerdos = mutableListOf<String>()

        try {
            for (paso in pasos) {
                val offline = engine.checkStep(paso.antes, paso.despues)
                val remoto = runBlocking { gateway.classifyStep(equality(paso.antes), equality(paso.despues)) }
                val kotlinLabel = offline.label()
                val sympyLabel = remoto.label()
                informe.appendLine("  [${paso.categoria}] '${paso.antes}' -> '${paso.despues}'")
                informe.appendLine("      kotlin: $kotlinLabel")
                informe.appendLine("      sympy : $sympyLabel")

                assertTrue(kotlinLabel.startsWith(paso.kotlin), "'${paso.antes}' -> '${paso.despues}': Kotlin dijo $kotlinLabel")
                assertTrue(sympyLabel.startsWith(paso.sympy), "'${paso.antes}' -> '${paso.despues}': SymPy dijo $sympyLabel")
                if (paso.detalle.isNotEmpty()) assertTrue(paso.detalle in sympyLabel, "faltó '${paso.detalle}' en $sympyLabel")

                if (remoto is RemoteStep.TransportFailure) desacuerdos.add("transporte: ${remoto.reason}")
                if (offline is StepVerdict.Valid && remoto is RemoteStep.NotEquivalent) {
                    desacuerdos.add("Kotlin validó y SymPy rechazó: '${paso.antes}' -> '${paso.despues}'")
                }
                if (offline is StepVerdict.Invalid && remoto is RemoteStep.Equivalent) {
                    desacuerdos.add("Kotlin acusó error y SymPy declaró equivalente: '${paso.despues}'")
                }
                if (remoto is RemoteStep.NotEquivalent && remoto.lost.isNotEmpty() && offline is StepVerdict.Valid) {
                    desacuerdos.add("SymPy encontró raíz perdida y Kotlin validó: '${paso.despues}'")
                }
            }

            informe.appendLine("\nECUACIONES")
            for (ecuacion in ecuaciones) {
                val offline = engine.solve(ecuacion.texto)
                val remoto = runBlocking { gateway.solve(equality(ecuacion.texto)) }
                val kotlinLabel = offline.label()
                val sympyLabel = remoto.label()
                informe.appendLine("  [${ecuacion.categoria}] '${ecuacion.texto}'")
                informe.appendLine("      kotlin: $kotlinLabel")
                informe.appendLine("      sympy : $sympyLabel")

                assertTrue(kotlinLabel.startsWith(ecuacion.kotlin), "'${ecuacion.texto}': Kotlin dijo $kotlinLabel")
                assertTrue(sympyLabel.startsWith(ecuacion.sympy), "'${ecuacion.texto}': SymPy dijo $sympyLabel")
                if (ecuacion.detalle.isNotEmpty()) assertTrue(ecuacion.detalle in sympyLabel, "faltó '${ecuacion.detalle}' en $sympyLabel")

                if (remoto is RemoteSolutions.TransportFailure) desacuerdos.add("transporte: ${remoto.reason}")
                // Cuando los dos resuelven, las raíces tienen que coincidir exactamente.
                if (offline is SolveOutcome.Unique && remoto is RemoteSolutions.Resolved) {
                    assertEquals(listOf(offline.value), remoto.solutions, "desacuerdo en '${ecuacion.texto}'")
                }
                if (offline is SolveOutcome.Multiple && remoto is RemoteSolutions.Resolved) {
                    assertEquals(offline.values.sorted(), remoto.solutions.sorted(), "desacuerdo en '${ecuacion.texto}'")
                }
            }

            assertTrue(desacuerdos.isEmpty(), desacuerdos.joinToString("\n"))
        } finally {
            // El informe se escribe aunque falle una comparación: la evidencia vale más que el verde.
            writeReport(informe.toString())
            client.close()
        }
    }

    private fun writeReport(text: String) {
        File("build/reports").mkdirs()
        File("build/reports/equivalencia-kotlin-sympy.txt").writeText(text)
    }

    private fun equality(text: String): Expr.Equals =
        (engine.parseOrNull(text) as? Expr.Equals) ?: error("'$text' no es una igualdad legible por el motor Kotlin")

    private fun StepVerdict.label(): String = when (this) {
        is StepVerdict.Valid -> "valido (${rule.name})"
        is StepVerdict.Invalid -> "invalido (${error.name})"
        is StepVerdict.Indeterminate -> "indeterminado"
        is StepVerdict.SyntaxProblem -> "sintaxis"
    }

    private fun RemoteStep?.label(): String = when (this) {
        null -> "no consultado"
        is RemoteStep.Equivalent -> "equivalente (dominio=${domainExcluded.render()}, $method)"
        is RemoteStep.NotEquivalent -> "no_equivalente (perdidas=${lost.render()}, extrañas=${extraneous.render()}, dominio=${domainExcluded.render()}, $method)"
        is RemoteStep.NotSupported -> "no_soportado: $reason"
        is RemoteStep.TransportFailure -> "transporte: $reason"
    }

    private fun SolveOutcome.label(): String = when (this) {
        is SolveOutcome.Unique -> "unica x=$value"
        is SolveOutcome.Multiple -> "varias x=${values.render()}"
        SolveOutcome.Identity -> "identidad"
        SolveOutcome.NoSolution -> "sin_solution"
        is SolveOutcome.RejectedByDomain -> "rechazada_por_dominio x=$value"
        is SolveOutcome.Blocked -> "bloqueado"
    }

    private fun RemoteSolutions?.label(): String = when (this) {
        null -> "no consultado"
        is RemoteSolutions.Resolved -> "resuelto $variable=${solutions.render()} rechazadas=${rejected.map { it.value }.render()} dominio=${excludedValues.render()}"
        is RemoteSolutions.NotSupported -> "no_soportado: $reason"
        is RemoteSolutions.TransportFailure -> "transporte: $reason"
    }

    private fun List<Rational>.render(): String = if (isEmpty()) "[]" else joinToString(", ")
}
