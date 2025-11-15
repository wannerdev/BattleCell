package com.battlecell.app.feature.search

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.battlecell.app.R
import com.battlecell.app.domain.model.EncounterProfile
import com.battlecell.app.domain.model.EncounterSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SearchRoute(
    viewModel: SearchViewModel,
    onBattleRequested: (EncounterProfile) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pendingScan by remember { mutableStateOf(false) }

    fun requiredScanPermissions(): Set<String> {
        val required = mutableSetOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            required += Manifest.permission.BLUETOOTH_SCAN
            required += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required += Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            required += Manifest.permission.ACCESS_FINE_LOCATION
            required += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        return required
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val denied = permissions.filterValues { granted -> !granted }.keys
        if (denied.isEmpty()) {
            if (pendingScan) {
                viewModel.manualScan()
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.search_permission_denied)
                )
            }
        }
        pendingScan = false
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.search_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (uiState.isScanning) {
                        stringResource(id = R.string.search_status_scanning)
                    } else {
                        stringResource(id = R.string.search_status_idle)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            Button(
                onClick = {
                    val required = requiredScanPermissions()
                    val missing = required.filter { permission ->
                        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isEmpty()) {
                        viewModel.manualScan()
                    } else {
                        pendingScan = true
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                },
                enabled = !uiState.isScanning,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                      Icon(
                          imageVector = Icons.Default.BluetoothSearching,
                          contentDescription = null
                      )
                      Text(
                          text = stringResource(id = R.string.search_scan_button),
                          modifier = Modifier.padding(start = 12.dp)
                      )
                  }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (uiState.encounters.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(id = R.string.search_empty_state),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        items(uiState.encounters, key = { it.id }) { encounter ->
                            EncounterCard(
                                profile = encounter,
                                onBattle = { onBattleRequested(encounter) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EncounterCard(
    profile: EncounterProfile,
    onBattle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RowHeader(profile = profile)
            Text(
                text = stringResource(
                    id = R.string.search_power_label,
                    profile.adjustedPower
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(
                    id = R.string.search_last_seen_label,
                    formatTimestamp(profile.lastSeenEpoch)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onBattle,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(text = stringResource(id = R.string.search_battle_button))
            }
        }
    }
}

@Composable
private fun RowHeader(profile: EncounterProfile) {
      val icon = when (profile.source) {
          EncounterSource.WIFI, EncounterSource.BLUETOOTH, EncounterSource.PLAYER_CACHE -> Icons.Default.BluetoothSearching
          EncounterSource.NPC -> Icons.Default.BluetoothSearching
      }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = profile.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = if (profile.isPlayer) {
                stringResource(id = R.string.search_tag_player)
            } else {
                val title = profile.title.takeIf { it.isNotBlank() }
                    ?: stringResource(id = R.string.search_tag_npc)
                title
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTimestamp(epochMillis: Long): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}
