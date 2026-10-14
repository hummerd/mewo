package com.mewo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mewo.ui.AboutScreen
import com.mewo.ui.MewoTheme

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MewoTheme {
                AboutScreen(onBack = { finish() })
            }
        }
    }
}
