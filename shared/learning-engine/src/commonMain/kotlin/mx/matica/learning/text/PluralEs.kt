package mx.matica.learning.text

/**
 * Concordancia de número en español para los textos que arma el motor. El repo escribía
 * `"$n día(s)"`, `"$n habilidad(es)"`: un atajo de programador que en la pantalla se lee
 * «1 días» y «1 habilidad(es)», y que la auditoría del 2026-11-06 encontró en nueve sitios
 * distintos de las cuatro pantallas fotografiadas en el teléfono.
 *
 * Sin regla de plural: el español convierte `habilidad` en `habilidades` y `día` en `días` con
 * dos terminaciones distintas, y ningún generador de sufijos acierta con los acentos. Cada
 * llamador escribe sus dos formas, que son palabras que ya conocemos.
 */
fun pluralEs(cantidad: Int, singular: String, plural: String = singular): String =
    if (cantidad == 1) singular else plural
