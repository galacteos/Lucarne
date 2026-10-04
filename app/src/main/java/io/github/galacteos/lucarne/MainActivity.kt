package io.github.galacteos.lucarne

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.galacteos.lucarne.ui.GuideScreen
import io.github.galacteos.lucarne.ui.GuideTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GuideTvTheme {
                GuideScreen()
            }
        }
    }
}
