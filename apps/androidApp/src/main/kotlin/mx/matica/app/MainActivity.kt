package mx.matica.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import mx.matica.presentation.app.MaticaLaunch
import mx.matica.presentation.app.MaticaSession
import mx.matica.presentation.app.rememberMaticaNavigator

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Con `targetSdk = 36` Android 15 ya impone edge-to-edge; llamarlo aquí hace explícito el estado
        // y deja los insets de la ventana en manos del host (`MaticaApp`), que es donde se aplican.
        enableEdgeToEdge()
        // El splash de marca sólo tiene sentido en un arranque frío: en una rotación la app ya estaba
        // viva y volver a mostrar un segundo de marca sería un peaje, no una bienvenida.
        val coldStart = savedInstanceState == null
        setContent {
            val session = remember { MaticaSession() }
            val navigator = rememberMaticaNavigator()
            // El botón físico deshace la pila propia; sólo al llegar a la raíz deja salir de la app.
            BackHandler(enabled = navigator.canGoBack) { navigator.goBack() }
            val teclado = LocalSoftwareKeyboardController.current
            // Registrado después que el de la pila, así que es el primero que consulta el dispatcher:
            // mientras el teclado asoma, el atrás sólo lo guarda y la pantalla debajo no se mueve.
            BackHandler(enabled = WindowInsets.isImeVisible) { teclado?.hide() }
            MaticaLaunch(session = session, navigator = navigator, startWithSplash = coldStart)
        }
    }
}
