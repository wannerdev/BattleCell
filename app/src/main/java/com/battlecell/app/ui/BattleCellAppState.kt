package com.battlecell.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.battlecell.app.navigation.BattleCellDestination

@Stable
class BattleCellAppState(
    val navController: NavHostController
) {
    val topLevelDestinations = listOf(
        BattleCellDestination.Home,
        BattleCellDestination.WarCouncil,
        BattleCellDestination.Training,
        BattleCellDestination.Search,
        BattleCellDestination.Profile
    )

    fun navigateTo(destination: BattleCellDestination) {
        when (destination) {
            is BattleCellDestination.TrainingGame,
            is BattleCellDestination.Battle -> {
                navController.navigate(destination.route)
            }

            else -> {
                navController.navigate(destination.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    fun navigateBack() {
        navController.popBackStack()
    }
}

@Composable
fun rememberBattleCellAppState(
    navController: NavHostController = rememberNavController()
): BattleCellAppState = remember(navController) {
    BattleCellAppState(navController)
}
