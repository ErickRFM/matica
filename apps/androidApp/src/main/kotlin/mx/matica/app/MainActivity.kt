package mx.matica.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import mx.matica.presentation.app.MaticaApp
import mx.matica.presentation.app.MaticaSession
import mx.matica.presentation.app.rememberMaticaNavigator

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val session = remember { MaticaSession() }
            val navigator = rememberMaticaNavigator()
            // El botón físico deshace la pila propia; sólo al llegar a la raíz deja salir de la app.
            BackHandler(enabled = navigator.canGoBack) { navigator.goBack() }
            MaticaApp(session = session, navigator = navigator)
        }
    }
}
