package mx.matica.design.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Escribe el flag de «fondo claro» en la ventana real. Se hace en un `SideEffect` y no durante la
 * composición porque tocar la ventana mientras se compone es justamente lo que Compose prohíbe; el
 * flag es propiedad del sistema, no del árbol.
 */
@Composable
actual fun MaticaSystemBarAppearance(dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val control = WindowCompat.getInsetsController(window, window.decorView)
        control.isAppearanceLightStatusBars = !dark
        control.isAppearanceLightNavigationBars = !dark
    }
}
