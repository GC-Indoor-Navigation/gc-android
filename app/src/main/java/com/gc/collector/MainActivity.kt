package com.gc.collector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.gc.collector.ui.screen.MainScreen
import com.gc.collector.ui.theme.GcandroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GcandroidTheme {
                MainScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
