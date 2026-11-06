package mx.matica.design.theme

import androidx.compose.runtime.Composable

/** Sin ventana ni barras del sistema que ajustar: el destino JVM no tiene dónde escribir el flag. */
@Composable
actual fun MaticaSystemBarAppearance(dark: Boolean) = Unit
