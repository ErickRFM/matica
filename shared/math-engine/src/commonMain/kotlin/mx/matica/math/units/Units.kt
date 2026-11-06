package mx.matica.math.units

import mx.matica.math.numeric.Rational

/**
 * Familias de magnitudes que la tabla conoce. Dos unidades sólo se suman si pertenecen a la misma.
 */
enum class Dimension(val labelEs: String) {
    LENGTH("longitud"),
    MASS("masa"),
    CAPACITY("capacidad"),
    TIME("tiempo"),
}

/**
 * Unidad del catálogo interno. `factorToBase` es cuántas unidades base vale una de éstas, exacto
 * como fracción: `Rational.of(1, 1000)` para el milímetro, sin tocar `Double`.
 */
data class MeasurementUnit(
    val symbol: String,
    val nameEs: String,
    val dimension: Dimension,
    val baseSymbol: String,
    val factorToBase: Rational,
)

/**
 * Catálogo cerrado de unidades. Es cerrado a propósito: una tabla abierta permitiría escribir
 * `3 qlm` y el motor tendría que inventar qué significa.
 *
 * Faltan familias a sabiendas. No está el día (`d`) ni el año (`a`) porque colisionan con incógnitas
 * frecuentes, y no hay unidades con desplazamiento como los grados Celsius: sumar 20 °C con 30 °C no
 * da 50 veces el cero térmico y una fracción `factorToBase` no modela ese desplazamiento.
 */
object Units {
    val all: List<MeasurementUnit> = buildList {
        // Longitud, con el metro como base.
        add(unit("km", "kilómetro", Dimension.LENGTH, 1_000, 1))
        add(unit("hm", "hectómetro", Dimension.LENGTH, 100, 1))
        add(unit("dam", "decámetro", Dimension.LENGTH, 10, 1))
        add(unit("m", "metro", Dimension.LENGTH, 1, 1))
        add(unit("dm", "decímetro", Dimension.LENGTH, 1, 10))
        add(unit("cm", "centímetro", Dimension.LENGTH, 1, 100))
        add(unit("mm", "milímetro", Dimension.LENGTH, 1, 1_000))
        // Masa: la base del SI es el kilogramo, así que el gramo vale una milésima.
        add(unit("t", "tonelada", Dimension.MASS, 1_000, 1))
        add(unit("kg", "kilogramo", Dimension.MASS, 1, 1))
        add(unit("hg", "hectogramo", Dimension.MASS, 1, 10))
        add(unit("dag", "decagramo", Dimension.MASS, 1, 100))
        add(unit("g", "gramo", Dimension.MASS, 1, 1_000))
        add(unit("mg", "miligramo", Dimension.MASS, 1, 1_000_000))
        // Capacidad, en litros.
        add(unit("kL", "kilolitro", Dimension.CAPACITY, 1_000, 1))
        add(unit("hL", "hectolitro", Dimension.CAPACITY, 100, 1))
        add(unit("daL", "decalitro", Dimension.CAPACITY, 10, 1))
        add(unit("L", "litro", Dimension.CAPACITY, 1, 1))
        add(unit("dL", "decilitro", Dimension.CAPACITY, 1, 10))
        add(unit("cL", "centilitro", Dimension.CAPACITY, 1, 100))
        add(unit("mL", "mililitro", Dimension.CAPACITY, 1, 1_000))
        // Tiempo, en segundos.
        add(unit("h", "hora", Dimension.TIME, 3_600, 1))
        add(unit("min", "minuto", Dimension.TIME, 60, 1))
        add(unit("s", "segundo", Dimension.TIME, 1, 1))
        add(unit("ms", "milisegundo", Dimension.TIME, 1, 1_000))
    }

    private val index: Map<String, MeasurementUnit> = all.associateBy { it.symbol }

    /** Distingue por tamaño y acentos: `Km` no está en la tabla y por eso no se lee como kilómetro. */
    fun bySymbol(symbol: String): MeasurementUnit? = index[symbol]

    /**
     * Conversión exacta entre dos unidades de la misma familia. Rechaza mezclar familias en lugar
     * de devolver un número que no significa nada.
     */
    fun convert(value: Rational, from: MeasurementUnit, to: MeasurementUnit): Rational {
        require(from.dimension == to.dimension) {
            "${from.symbol} y ${to.symbol} no son de la misma magnitud."
        }
        return value * from.factorToBase / to.factorToBase
    }

    /**
     * La unidad más fina del grupo, es decir la que tiene el factor menor. Elegirla para escribir
     * un resultado evita decimales: `3 km + 200 m` se muestra como `3200 m` y no como `3.2 km`.
     */
    fun finest(units: Collection<MeasurementUnit>): MeasurementUnit? = units.minByOrNull { it.factorToBase }

    private fun unit(symbol: String, name: String, dimension: Dimension, numerator: Long, denominator: Long): MeasurementUnit =
        MeasurementUnit(symbol, name, dimension, baseSymbolOf(dimension), Rational.of(numerator, denominator))

    private fun baseSymbolOf(dimension: Dimension): String = when (dimension) {
        Dimension.LENGTH -> "m"
        Dimension.MASS -> "kg"
        Dimension.CAPACITY -> "L"
        Dimension.TIME -> "s"
    }
}
