package io.github.omegadestroyer.soundpeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import io.github.omegadestroyer.soundpeter.protocol.Command

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Placeholder() }
    }
}

/** Stands in until the screens from docs/DESIGN.md are built. */
@Preview
@Composable
private fun Placeholder() {
    Box(Modifier.fillMaxSize().background(Color(0xFF101214)), contentAlignment = Alignment.Center) {
        BasicText(
            "SoundPeter\n${Command.entries.size} commands known",
            style = TextStyle(color = Color(0xFFE8E6E1)),
        )
    }
}
