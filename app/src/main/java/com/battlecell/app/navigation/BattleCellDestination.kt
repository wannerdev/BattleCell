package com.battlecell.app.navigation

sealed class BattleCellDestination(val route: String) {
    data object Onboarding : BattleCellDestination("onboarding")
    data object Home : BattleCellDestination("home")
    data object Training : BattleCellDestination("training")
    data object TrainingGame : BattleCellDestination("training/{gameId}") {
        const val ARG_GAME_ID = "gameId"
        fun createRoute(gameId: String) = "training/$gameId"
    }
    data object Search : BattleCellDestination("search")
    data object Profile : BattleCellDestination("profile")
    data object Battle : BattleCellDestination("battle/{opponentId}") {
        const val ARG_OPPONENT_ID = "opponentId"
        fun createRoute(opponentId: String) = "battle/$opponentId"
    }
}
