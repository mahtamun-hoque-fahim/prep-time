package com.preptime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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
            var intro by rememberSaveable { mutableStateOf(true) }
            LaunchedEffect(Unit) { delay(1200); intro = false }
            if (intro) {
                Intro()
                return@setContent
            }
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

/** Continues the system launch screen (blue + logo at the same spot) and adds the tagline. */
@Composable
private fun Intro() {
    Box(Modifier.fillMaxSize().background(colorResource(R.color.ic_launcher_background)), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(240.dp))
        Text(
            "Track your debate time",
            Modifier.offset(y = 110.dp),
            color = Color.White,
            fontSize = 20.sp,
        )
    }
}
