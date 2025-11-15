package com.battlecell.app.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.battlecell.app.feature.home.HomeViewModel
import com.battlecell.app.feature.onboarding.OnboardingViewModel
import com.battlecell.app.feature.profile.ProfileViewModel
import com.battlecell.app.feature.search.SearchViewModel
import com.battlecell.app.feature.train.TrainingViewModel
import com.battlecell.app.feature.war.WarCouncilViewModel

class BattleCellViewModelFactory(
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when {
            modelClass.isAssignableFrom(OnboardingViewModel::class.java) ->
                OnboardingViewModel(appContainer.playerRepository)

            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(appContainer.playerRepository)

            modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
                ProfileViewModel(appContainer.playerRepository)

            modelClass.isAssignableFrom(WarCouncilViewModel::class.java) ->
                WarCouncilViewModel(appContainer.playerRepository)

            modelClass.isAssignableFrom(TrainingViewModel::class.java) ->
                TrainingViewModel(
                    playerRepository = appContainer.playerRepository,
                    getTrainingGamesUseCase = appContainer.getTrainingGamesUseCase
                )

            modelClass.isAssignableFrom(SearchViewModel::class.java) ->
                SearchViewModel(
                    encounterRepository = appContainer.encounterRepository,
                    nearbyDiscoveryManager = appContainer.nearbyDiscoveryManager,
                    encounterGenerator = appContainer.encounterGenerator,
                    playerRepository = appContainer.playerRepository
                )

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }
}
