package mx.matica.design.brand

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaticaMascotTest {

    @Test
    fun emocionesDisponiblesContienenEstadosClave() {
        val emociones = MascotEmotion.values().toList()
        assertTrue(emociones.contains(MascotEmotion.IDLE))
        assertTrue(emociones.contains(MascotEmotion.HAPPY))
        assertTrue(emociones.contains(MascotEmotion.THINKING))
        assertTrue(emociones.contains(MascotEmotion.CELEBRATING))
        assertEquals(4, emociones.size)
    }
}
