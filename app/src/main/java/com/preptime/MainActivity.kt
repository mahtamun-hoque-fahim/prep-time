package com.preptime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.preptime.data.Settings
import com.preptime.data.store
import com.preptime.ui.HomeScreen
import com.preptime.ui.theme.PrepTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by store.settings.collectAsStateWithLifecycle(Settings())
            PrepTheme(settings) {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onOpen = { nav.navigate("timer/$it") },
                            onEdit = { nav.navigate(if (it == null) "edit" else "edit?id=$it") },
                        )
                    }
                }
            }
        }
    }
}
