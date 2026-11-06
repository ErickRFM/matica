package mx.matica.design.theme

import androidx.compose.runtime.Composable

/**
 * Las barras del sistema tienen que seguir el tema que el estudiante eligió dentro de la app.
 *
 * `enableEdgeToEdge()` sin argumentos deja `SystemBarStyle.auto`, que obedece al ajuste del sistema:
 * en un teléfono en claro, poner Mática en «Oscuro» dejaba la hora y la batería pintadas para fondo
 * claro sobre `#0E1626`. La decisión del tema vive aquí, así que la aparición de las barras no puede
 * irse por su cuenta.
 */
@Composable
expect fun MaticaSystemBarAppearance(dark: Boolean)
