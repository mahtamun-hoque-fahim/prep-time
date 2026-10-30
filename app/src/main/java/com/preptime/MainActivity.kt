package com.preptime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import com.preptime.ui.EditScreen
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.dropUnlessResumed
import com.preptime.audio.Bell
import com.preptime.data.Settings
import com.preptime.data.store
import com.preptime.ui.HomeScreen
import com.preptime.ui.TimerScreen
import com.preptime.ui.theme.PrepTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) Bell.restoreSavedVolume(this)
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
                    composable(
                        "edit?id={id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true }),
                    ) { EditScreen(it.arguments?.getString("id"), onDone = dropUnlessResumed { nav.popBackStack() }) }
                    composable("timer/{id}") { TimerScreen(onBack = dropUnlessResumed { nav.popBackStack() }) }
                }
            }
        }
    }
}
