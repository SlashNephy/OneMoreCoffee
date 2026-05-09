package blue.starry.onemorecoffee

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import blue.starry.onemorecoffee.feature.list.StoreListScreen
import blue.starry.onemorecoffee.feature.map.MapScreen
import blue.starry.onemorecoffee.feature.settings.SettingsScreen
import blue.starry.onemorecoffee.feature.stats.StatsScreen

@Composable
fun App() {
    var currentRoute by remember { mutableStateOf(Route.Map) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Route.bottomTabs.forEach { route ->
                    NavigationBarItem(
                        selected = currentRoute == route,
                        onClick = {
                            currentRoute = route
                        },
                        label = {
                            Text(route.label)
                        },
                        icon = {},
                    )
                }
            }
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            when (currentRoute) {
                Route.Map -> MapScreen(modifier = Modifier.fillMaxSize())
                Route.List -> StoreListScreen(modifier = Modifier.fillMaxSize())
                Route.Stats -> StatsScreen(modifier = Modifier.fillMaxSize())
                Route.Settings -> SettingsScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
