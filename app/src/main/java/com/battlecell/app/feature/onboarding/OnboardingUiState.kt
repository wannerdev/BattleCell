package com.battlecell.app.feature.onboarding

data class OnboardingUiState(
    val name: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)
