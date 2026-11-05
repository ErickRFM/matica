package mx.matica.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.matica.shared.mathengine.numeric.Rational

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold { innerPadding ->
                    SmokeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
@Preview
fun SmokeScreen(modifier: Modifier = Modifier) {
    val probe = Rational.of(1, 2) + Rational.of(1, 3)
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Mática", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "1/2 + 1/3 = $probe",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
