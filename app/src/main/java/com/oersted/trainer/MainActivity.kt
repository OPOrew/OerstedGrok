package com.oersted.trainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.oersted.trainer.ui.AppRoot
import com.oersted.trainer.ui.common.classic737Scheme

private const val PREFS_NAME = "classic737"
// Same key as the web app's localStorage "classic737-theme".
private const val THEME_KEY = "classic737-theme"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        setContent {
            var light by remember { mutableStateOf(prefs.getString(THEME_KEY, "dark") == "light") }
            MaterialTheme(colorScheme = classic737Scheme(light)) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(
                        lightMode = light,
                        onToggleTheme = {
                            light = !light
                            prefs.edit().putString(THEME_KEY, if (light) "light" else "dark").apply()
                        },
                    )
                }
            }
        }
    }
}
