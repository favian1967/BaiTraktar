package com.example.baitraktar

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.baitraktar.ui.AppRoot
import com.example.baitraktar.ui.theme.BaiTraktarTheme

/** FragmentActivity, not ComponentActivity: BiometricPrompt hosts its dialog in a fragment. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            BaiTraktarTheme {
                AppRoot()
            }
        }
    }
}
