package mx.matica.math.units

import mx.matica.math.numeric.Rational
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La tabla de unidades es contrato: si un factor se escribe mal, todas las conversiones salen mal y
 * ninguna prueba de ecuaciones lo detecta. Por eso se comprueban los factores uno por uno, con
 * fracciones exactas y no con decimales.
 */
class UnitsTableTest {

    @Test
    fun elFactorDeCadaUnidadEsExacto() {
        assertEquals(Rational.of(1_000, 1), Units.bySymbol("km")?.factorToBase)
        assertEquals(Rational.of(1, 1), Units.bySymbol("m")?.factorToBase)
        assertEquals(Rational.of(1, 100), Units.bySymbol("cm")?.factorToBase)
        assertEquals(Rational.of(1, 1_000), Units.bySymbol("mm")?.factorToBase)
        // La base de la masa es el kilogramo, no el gramo: confundirlos multiplicaría por mil todo.
        assertEquals(Rational.of(1, 1), Units.bySymbol("kg")?.factorToBase)
        assertEquals(Rational.of(1, 1_000), Units.bySymbol("g")?.factorToBase)
        assertEquals(Rational.of(1, 1_000_000), Units.bySymbol("mg")?.factorToBase)
        assertEquals(Rational.of(3_600, 1), Units.bySymbol("h")?.factorToBase)
        assertEquals(Rational.of(60, 1), Units.bySymbol("min")?.factorToBase)
    }

    @Test
    fun lasCuatroFamiliasTienenSuBase() {
        Dimension.entries.forEach { familia ->
            val encontrada = Units.all.firstOrNull { it.dimension == familia && it.factorToBase.isOne }
            assertNotNull(encontrada, "La familia $familia no tiene una unidad base con factor 1.")
            val base = encontrada!!
            assertEquals(base.symbol, base.baseSymbol, "La base de $familia debe nombrarse a sí misma.")
        }
    }

    @Test
    fun elSimboloSeReconoceSoloEscritoComoEnLaTabla() {
        assertNotNull(Units.bySymbol("km"))
        assertNull(Units.bySymbol("Km"), "'Km' no es el símbolo: el motor no adivina mayúsculas.")
        assertNull(Units.bySymbol("kms"), "Un plural inventado no entra en el catálogo.")
        assertNull(Units.bySymbol("d"), "El día se dejó fuera porque choca con una incógnita habitual.")
        assertNull(Units.bySymbol("C"), "Sin unidades de temperatura: los grados no son multiplicativos.")
    }

    @Test
    fun convertirEntreFamiliasDistintasFallaEnLugarDeInventar() {
        val error = assertFailsWith<IllegalArgumentException> {
            Units.convert(Rational.ONE, Units.bySymbol("km")!!, Units.bySymbol("kg")!!)
        }
        assertTrue("misma magnitud" in (error.message ?: ""), "El motivo debe decir que no son comparables: ${error.message}")
    }

    @Test
    fun laConversionConservaElValorExacto() {
        val metros = Units.convert(Rational.of(3, 2), Units.bySymbol("km")!!, Units.bySymbol("m")!!)
        assertEquals(Rational.fromLong(1_500), metros, "3/2 km son 1500 m, sin redondeo intermedio.")
        assertEquals(
            Rational.of(3, 2),
            Units.convert(metros, Units.bySymbol("m")!!, Units.bySymbol("km")!!),
            "Convertir y volver a convertir regresa al mismo número.",
        )
    }

    @Test
    fun laUnidadMasFinaEsLaQueTieneElFactorMenor() {
        val lista = listOf(Units.bySymbol("km")!!, Units.bySymbol("cm")!!, Units.bySymbol("m")!!)
        assertEquals("cm", Units.finest(lista)?.symbol, "La más fina evita decimales al escribir el resultado.")
        assertNull(Units.finest(emptyList()))
    }

    @Test
    fun elCatalogoEstaLibreDeSimbolosRepetidos() {
        val simbolos = Units.all.map { it.symbol }
        assertEquals(simbolos.size, simbolos.toSet().size, "Un símbolo repetido haría ambigua la lectura.")
        assertTrue(simbolos.size >= 20, "Se perdieron familias de la tabla: $simbolos")
    }
}
