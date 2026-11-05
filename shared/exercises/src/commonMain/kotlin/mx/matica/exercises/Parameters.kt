package mx.matica.exercises

/** Dominio declarado de un parámetro: intervalo cerrado de enteros. */
data class ParameterRange(val name: String, val fromInclusive: Int, val toInclusive: Int) {
    init {
        require(name.isNotBlank()) { "Un parámetro necesita nombre." }
        require(fromInclusive <= toInclusive) { "El rango de '$name' está invertido: $fromInclusive..$toInclusive." }
    }

    fun contains(value: Int): Boolean = value in fromInclusive..toInclusive

    fun labelEs(): String = "$name ∈ [$fromInclusive, $toInclusive]"
}

data class ParameterSet(val values: Map<String, Int>) {
    fun of(name: String): Int? = values[name]
}

/**
 * Parámetros ausentes o fuera del rango declarado. Se devuelven como motivos legibles porque un
 * ejercicio mal parametrizado debe poder explicarse, no fallar con una excepción opaca.
 */
internal fun validateRanges(ranges: List<ParameterRange>, params: ParameterSet): List<String> {
    val reasons = mutableListOf<String>()
    ranges.forEach { range ->
        val value = params.of(range.name)
        when {
            value == null -> reasons += "Falta el parámetro '${range.name}' (${range.labelEs()})."
            !range.contains(value) -> reasons += "'${range.name}' = $value está fuera de ${range.labelEs()}."
        }
    }
    return reasons
}
