package com.battlecell.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.battlecell.app.core.AppContainer
import com.battlecell.app.core.BattleCellViewModelFactory
import com.battlecell.app.feature.battle.BattlePlaceholder
import com.battlecell.app.feature.home.HomeRoute
import com.battlecell.app.feature.onboarding.OnboardingRoute
import com.battlecell.app.feature.profile.ProfileRoute
import com.battlecell.app.feature.search.SearchRoute
import com.battlecell.app.feature.train.TrainingGameRoute
import com.battlecell.app.feature.train.TrainingRoute
import com.battlecell.app.feature.train.TrainingGameViewModel
import com.battlecell.app.navigation.BattleCellDestination

@Composable
fun BattleCellApp(
    appContainer: AppContainer
) {
    val appState = rememberBattleCellAppState()
    val viewModelFactory = remember(appContainer) { BattleCellViewModelFactory(appContainer) }

    val playerState by appContainer.playerRepository.playerStream.collectAsStateWithLifecycle(initialValue = null)
    val onboardingCompleted by appContainer.playerRepository.onboardingCompleted.collectAsStateWithLifecycle(initialValue = false)

    val navBackStackEntry by appState.navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(playerState, onboardingCompleted, currentRoute) {
        if (playerState != null && onboardingCompleted &&
            currentRoute == BattleCellDestination.Onboarding.route
        ) {
            appState.navController.navigate(BattleCellDestination.Home.route) {
                popUpTo(BattleCellDestination.Onboarding.route) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute != BattleCellDestination.Onboarding.route) {
                BattleCellBottomBar(
                    appState = appState,
                    currentRoute = currentRoute ?: BattleCellDestination.Home.route
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = appState.navController,
            startDestination = BattleCellDestination.Onboarding.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BattleCellDestination.Onboarding.route) {
                val viewModel = viewModel(
                    factory = viewModelFactory,
                    modelClass = com.battlecell.app.feature.onboarding.OnboardingViewModel::class.java
                )
                OnboardingRoute(
                    viewModel = viewModel,
                    onFinished = { appState.navigateTo(BattleCellDestination.Home) }
                )
            }
            composable(BattleCellDestination.Home.route) {
                val viewModel = viewModel(
                    factory = viewModelFactory,
                    modelClass = com.battlecell.app.feature.home.HomeViewModel::class.java
                )
                HomeRoute(
                    viewModel = viewModel,
                    onNavigateToTraining = { appState.navigateTo(BattleCellDestination.Training) },
                    onNavigateToSearch = { appState.navigateTo(BattleCellDestination.Search) },
                    onNavigateToProfile = { appState.navigateTo(BattleCellDestination.Profile) }
                )
            }
            composable(BattleCellDestination.Training.route) {
                val viewModel = viewModel(
                    factory = viewModelFactory,
                    modelClass = com.battlecell.app.feature.train.TrainingViewModel::class.java
                )
                TrainingRoute(
                    viewModel = viewModel,
                    onGameSelected = { definition ->
                        appState.navController.navigate(
                            BattleCellDestination.TrainingGame.createRoute(definition.id)
                        )
                    }
                )
            }
            composable(BattleCellDestination.Search.route) {
                val viewModel = viewModel(
                    factory = viewModelFactory,
                    modelClass = com.battlecell.app.feature.search.SearchViewModel::class.java
                )
                SearchRoute(
                    viewModel = viewModel,
                    onBattleRequested = { encounter ->
                        appState.navController.navigate(
                            BattleCellDestination.Battle.createRoute(encounter.id)
                        )
                    }
                )
            }
            composable(BattleCellDestination.Profile.route) {
                val viewModel = viewModel(
                    factory = viewModelFactory,
                    modelClass = com.battlecell.app.feature.profile.ProfileViewModel::class.java
                )
                ProfileRoute(viewModel = viewModel)
            }
            composable(
                route = BattleCellDestination.TrainingGame.route,
                arguments = listOf(
                    navArgument(BattleCellDestination.TrainingGame.ARG_GAME_ID) {
                        type = NavType.StringType
                        nullable = true
                    }
                )
            ) { backStackEntry ->
                val gameId = backStackEntry.arguments?.getString(BattleCellDestination.TrainingGame.ARG_GAME_ID) ?: ""
                val viewModel = viewModel<TrainingGameViewModel>(
                    factory = TrainingGameViewModel.provideFactory(
                        playerRepository = appContainer.playerRepository,
                        getTrainingGamesUseCase = appContainer.getTrainingGamesUseCase,
                        gameId = gameId
                    )
                )
                TrainingGameRoute(
                    viewModel = viewModel,
                    onExit = { appState.navigateBack() }
                )
            }
            composable(
                route = BattleCellDestination.Battle.route,
                arguments = listOf(
                    navArgument(BattleCellDestination.Battle.ARG_OPPONENT_ID) {
                        type = NavType.StringType
                        nullable = true
                    }
                )
            ) { backStackEntry ->
                val opponentId = backStackEntry.arguments?.getString(BattleCellDestination.Battle.ARG_OPPONENT_ID)
                BattlePlaceholder(opponentId = opponentId)
            }
        }
    }
}

@Composable
private fun BattleCellBottomBar(
    appState: BattleCellAppState,
    currentRoute: String
) {
    NavigationBar {
        appState.topLevelDestinations.forEach { destination ->
            val label = when (destination) {
                BattleCellDestination.Home -> "Hall"
                BattleCellDestination.Training -> "Trials"
                BattleCellDestination.Search -> "Horn"
                BattleCellDestination.Profile -> "Chronicle"
                else -> destination.route
            }
            val icon = when (destination) {
                BattleCellDestination.Home -> Icons.Default.Home
                BattleCellDestination.Training -> Icons.Default.SportsMartialArts
                BattleCellDestination.Search -> Icons.Default.BluetoothSearching
                BattleCellDestination.Profile -> Icons.Default.Person
                else -> Icons.Default.Home
            }
            NavigationBarItem(
                selected = currentRoute.startsWith(destination.route),
                onClick = { appState.navigateTo(destination) },
                icon = { Icon(imageVector = icon, contentDescription = label) },
                label = { Text(text = label) },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
