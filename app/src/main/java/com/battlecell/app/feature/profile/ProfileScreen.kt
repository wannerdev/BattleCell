package com.battlecell.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.battlecell.app.R
import com.battlecell.app.domain.model.AttributeType

@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(id = R.string.profile_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            uiState.character?.let { character ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = character.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(
                                id = R.string.profile_level_and_xp,
                                character.level,
                                character.statusPoints,
                                character.experience,
                                character.skillPoints
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(
                                id = R.string.profile_combat_rating,
                                character.combatRating
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                AttributeAllocationCard(
                    title = stringResource(id = R.string.profile_power_label),
                    value = character.attributes.power,
                    trainingPoints = character.variantSkillPoints(AttributeType.POWER),
                    generalPoints = character.skillPoints,
                    onAllocate = { amount -> viewModel.allocatePoints(AttributeType.POWER, amount) }
                )
                AttributeAllocationCard(
                    title = stringResource(id = R.string.profile_agility_label),
                    value = character.attributes.agility,
                    trainingPoints = character.variantSkillPoints(AttributeType.AGILITY),
                    generalPoints = character.skillPoints,
                    onAllocate = { amount -> viewModel.allocatePoints(AttributeType.AGILITY, amount) }
                )
                AttributeAllocationCard(
                    title = stringResource(id = R.string.profile_endurance_label),
                    value = character.attributes.endurance,
                    trainingPoints = character.variantSkillPoints(AttributeType.ENDURANCE),
                    generalPoints = character.skillPoints,
                    onAllocate = { amount -> viewModel.allocatePoints(AttributeType.ENDURANCE, amount) }
                )
                AttributeAllocationCard(
                    title = stringResource(id = R.string.profile_focus_label),
                    value = character.attributes.focus,
                    trainingPoints = character.variantSkillPoints(AttributeType.FOCUS),
                    generalPoints = character.skillPoints,
                    onAllocate = { amount -> viewModel.allocatePoints(AttributeType.FOCUS, amount) }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(id = R.string.profile_footer_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            } ?: run {
                Text(
                    text = stringResource(id = R.string.profile_empty_state),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun AttributeAllocationCard(
    title: String,
    value: Int,
    trainingPoints: Int,
    generalPoints: Int,
    onAllocate: (Int) -> Unit
) {
    val canAllocate = trainingPoints + generalPoints > 0
    val totalAvailable = trainingPoints + generalPoints
    var menuExpanded by remember { mutableStateOf(false) }
    val baseOptions = listOf(1, 3, 5, 10).filter { it in 1 until totalAvailable }
    val menuOptions = (baseOptions + totalAvailable).distinct()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(id = R.string.profile_attribute_value, value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        id = R.string.profile_training_points_label,
                        trainingPoints,
                        generalPoints
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                Button(
                    onClick = { if (canAllocate) menuExpanded = true },
                    enabled = canAllocate,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(id = R.string.profile_allocate_button),
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (menuOptions.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(text = "No sigils available") },
                            enabled = false,
                            onClick = {}
                        )
                    } else {
                        menuOptions.forEach { amount ->
                            val label = if (amount == totalAvailable) {
                                "Allocate all ($totalAvailable)"
                            } else {
                                "Allocate $amount"
                            }
                            DropdownMenuItem(
                                text = { Text(text = label) },
                                enabled = amount in 1..totalAvailable,
                                onClick = {
                                    menuExpanded = false
                                    onAllocate(amount)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
