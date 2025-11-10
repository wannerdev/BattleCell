package com.battlecell.app.feature.train

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.battlecell.app.domain.model.TrainingGameDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun TrainingGameRoute(
    viewModel: TrainingGameViewModel,
    onExit: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TrainingGameEvent.Error -> snackbarHostState.showSnackbar(event.message)
                TrainingGameEvent.Defeat -> snackbarHostState.showSnackbar("The nanobot slipped past your defenses.")
                is TrainingGameEvent.Victory -> snackbarHostState.showSnackbar(
                    "Victory! +${event.result.attributeGain} ${event.result.attributeType.name.lowercase()} - +${event.result.experienceGain} XP"
                )
            }
        }
    }

    BackHandler(onBack = onExit)

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            when {
                uiState.definition == null -> TrainingGameError(onExit)
                else -> TrainingGameContent(
                    state = uiState,
                    onSubmitResult = viewModel::recordOutcome,
                    onExit = onExit,
                    onResetResult = viewModel::consumeResult
                )
            }
        }
    }
}

private enum class GamePhase { Idle, Playing, Victory, Defeat }

@Composable
private fun TrainingGameContent(
    state: TrainingGameUiState,
    onSubmitResult: (elapsedMillis: Long, didWin: Boolean) -> Unit,
    onExit: () -> Unit,
    onResetResult: () -> Unit
) {
    val definition = state.definition ?: return
    val behavior = definition.behavior
    val density = LocalDensity.current

    var gamePhase by remember(definition.id) { mutableStateOf(GamePhase.Idle) }
    var runId by remember(definition.id) { mutableIntStateOf(0) }
    val progress = remember(definition.id) { Animatable(0f) }
    var bugVisible by remember { mutableStateOf(true) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var startTimestamp by remember { mutableLongStateOf(0L) }

    val coroutineScope = rememberCoroutineScope()

    val bugRadiusPx = with(density) { behavior.bugRadiusDp.dp.toPx() }

    var startPosition by remember { mutableStateOf(Offset.Zero) }
    var arenaSize by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(gamePhase, runId) {
        if (gamePhase == GamePhase.Playing) {
            startTimestamp = SystemClock.elapsedRealtime()
            progress.snapTo(0f)
            try {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = behavior.totalDurationMillis,
                        easing = LinearEasing
                    )
                )
                if (gamePhase == GamePhase.Playing) {
                    val elapsed = SystemClock.elapsedRealtime() - startTimestamp
                    onSubmitResult(elapsed, false)
                    elapsedMillis = elapsed
                    gamePhase = GamePhase.Defeat
                }
            } catch (c: CancellationException) {
                // animation cancelled due to restart
            }
        } else {
            progress.snapTo(0f)
        }
    }

    LaunchedEffect(gamePhase, runId) {
        elapsedMillis = 0L
        if (gamePhase == GamePhase.Playing) {
            val start = SystemClock.elapsedRealtime()
            while (isActive && gamePhase == GamePhase.Playing) {
                elapsedMillis = SystemClock.elapsedRealtime() - start
                delay(16)
            }
        }
    }

    LaunchedEffect(gamePhase, runId, behavior.flickerEnabled) {
        bugVisible = true
        if (gamePhase == GamePhase.Playing && behavior.flickerEnabled) {
            while (isActive && gamePhase == GamePhase.Playing) {
                bugVisible = true
                delay(behavior.visibleWindowMillis)
                if (gamePhase != GamePhase.Playing) break
                bugVisible = false
                delay(behavior.invisibleWindowMillis)
            }
            bugVisible = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Header(definition = definition, state = state, elapsedMillis = elapsedMillis)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                .pointerInput(gamePhase, runId, bugVisible) {
                    detectBugTap(
                        enabled = gamePhase == GamePhase.Playing && bugVisible,
                        bugRadiusPx = bugRadiusPx,
                        bugPositionProvider = {
                            val widthPx = with(density) { constraints.maxWidth.toPx() }
                            val heightPx = with(density) { constraints.maxHeight.toPx() }
                            arenaSize = Offset(widthPx, heightPx)
                            val center = Offset(widthPx / 2f, heightPx / 2f)
                            val start = startPosition
                            lerp(start, center, progress.value)
                        }
                    ) {
                        coroutineScope.launch {
                            progress.stop()
                        }
                        val elapsed = SystemClock.elapsedRealtime() - startTimestamp
                        elapsedMillis = elapsed
                        onSubmitResult(elapsed, true)
                        gamePhase = GamePhase.Victory
                    }
                }
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            if (arenaSize == Offset.Zero) {
                arenaSize = Offset(widthPx, heightPx)
            }
            if (startPosition == Offset.Zero || gamePhase == GamePhase.Idle) {
                startPosition = randomStartPosition(widthPx, heightPx, bugRadiusPx, runId)
            }
            val center = Offset(widthPx / 2f, heightPx / 2f)
            val bugPosition = lerp(startPosition, center, progress.value)
            TrainingArena(
                bugVisible = bugVisible && gamePhase == GamePhase.Playing,
                bugPosition = bugPosition,
                bugRadiusPx = bugRadiusPx,
                center = center,
                arenaSize = Offset(widthPx, heightPx),
                progress = progress.value
            )
        }

        ControlPanel(
            phase = gamePhase,
            definition = definition,
            elapsedMillis = elapsedMillis,
            lastResult = state.lastResult,
            onStart = {
                bugVisible = true
                onResetResult()
                gamePhase = GamePhase.Playing
                runId++
            },
            onRetry = {
                bugVisible = true
                onResetResult()
                gamePhase = GamePhase.Idle
                runId++
            },
            onExit = onExit
        )
    }
}

@Composable
private fun Header(
    definition: TrainingGameDefinition,
    state: TrainingGameUiState,
    elapsedMillis: Long
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = definition.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = definition.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Badge(text = definition.difficulty.name.lowercase().replaceFirstChar { it.titlecase() })
            Badge(text = "Reward ${definition.displayReward}")
            Badge(text = "Target ${definition.attributeReward.name.lowercase()}")
            Badge(text = "Timer ${(elapsedMillis / 1000.0).formatSeconds()}")
        }
        state.character?.let {
            Text(
                text = "Current power score ${it.combatRating}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.primary,
        tonalElevation = 0.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TrainingArena(
    bugVisible: Boolean,
    bugPosition: Offset,
    bugRadiusPx: Float,
    center: Offset,
    arenaSize: Offset,
    progress: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawBackgroundGrid(arenaSize)
        drawTarget(center, bugRadiusPx)
        if (bugVisible) {
            drawBug(bugPosition, bugRadiusPx)
        } else {
            drawGhostTrail(start = bugPosition, center = center, progress = progress, bugRadiusPx = bugRadiusPx)
        }
    }
}

private fun DrawScope.drawBackgroundGrid(arenaSize: Offset) {
    val step = size.minDimension / 8f
    val gridColor = Color.White.copy(alpha = 0.08f)
    for (x in 0..8) {
        val xPos = step * x
        drawLine(
            color = gridColor,
            start = Offset(xPos, 0f),
            end = Offset(xPos, size.height),
            strokeWidth = 2f
        )
    }
    for (y in 0..8) {
        val yPos = step * y
        drawLine(
            color = gridColor,
            start = Offset(0f, yPos),
            end = Offset(size.width, yPos),
            strokeWidth = 2f
        )
    }
}

private fun DrawScope.drawTarget(center: Offset, bugRadiusPx: Float) {
    val outerRadius = bugRadiusPx * 2.5f
    drawCircle(
        color = Color(0xFF242424),
        radius = outerRadius,
        center = center
    )
    drawCircle(
        color = Color(0xFF383838),
        radius = outerRadius * 0.7f,
        center = center,
        style = Stroke(width = bugRadiusPx / 2f)
    )
    drawCircle(
        color = Color(0xFF6200EE),
        radius = bugRadiusPx / 2f,
        center = center
    )
}

private fun DrawScope.drawBug(position: Offset, bugRadiusPx: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF03DAC5), Color(0xFF018786)),
            center = position,
            radius = bugRadiusPx * 1.4f
        ),
        radius = bugRadiusPx,
        center = position
    )
    drawCircle(
        color = Color.White,
        radius = bugRadiusPx / 4f,
        center = position + Offset(bugRadiusPx / 3f, -bugRadiusPx / 3f)
    )
}

private fun DrawScope.drawGhostTrail(
    start: Offset,
    center: Offset,
    progress: Float,
    bugRadiusPx: Float
) {
    val pathColor = Color(0xFF03DAC5).copy(alpha = 0.2f)
    drawLine(
        color = pathColor,
        start = start,
        end = center,
        strokeWidth = bugRadiusPx / 2f,
        cap = StrokeCap.Round
    )
    drawCircle(
        color = pathColor,
        radius = bugRadiusPx,
        center = lerp(start, center, progress)
    )
}

@Composable
private fun ControlPanel(
    phase: GamePhase,
    definition: TrainingGameDefinition,
    elapsedMillis: Long,
    lastResult: TrainingGameResult?,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (phase) {
            GamePhase.Idle -> {
                Text(
                    text = "Tap the nanobot before it reaches the reactor core. Ready?",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Flag, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Start training")
                }
            }

            GamePhase.Playing -> {
                Text(
                    text = "Elapsed: ${(elapsedMillis / 1000.0).formatSeconds()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Tip: anticipate its path. Difficulty ${definition.difficulty.name.lowercase().replaceFirstChar { it.titlecase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            GamePhase.Victory -> {
                VictoryPanel(lastResult = lastResult, onRetry = onRetry, onExit = onExit)
            }

            GamePhase.Defeat -> {
                DefeatPanel(onRetry = onRetry, onExit = onExit)
            }
        }
    }
}

@Composable
private fun VictoryPanel(
    lastResult: TrainingGameResult?,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Victory!",
                style = MaterialTheme.typography.titleMedium
            )
            val subtitle = when (lastResult) {
                is TrainingGameResult.Victory -> "Attribute +${lastResult.attributeGain} ${lastResult.attributeType.name.lowercase()} - XP +${lastResult.experienceGain}"
                else -> "Attribute boost and experience gained."
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        TextButton(onClick = onRetry) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Train again")
        }
        TextButton(onClick = onExit) {
            Text(text = "Done")
        }
    }
}

@Composable
private fun DefeatPanel(
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(32.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "The nanobot reached the core.",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Adjust your timing and try again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        TextButton(onClick = onRetry) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Retry")
        }
        TextButton(onClick = onExit) {
            Text(text = "Exit")
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectBugTap(
    enabled: Boolean,
    bugRadiusPx: Float,
    bugPositionProvider: () -> Offset,
    onHit: () -> Unit
) {
    if (!enabled) {
        detectTapGestures(onTap = {})
        return
    }
    detectTapGestures { offset ->
        val bugCenter = bugPositionProvider()
        val distance = (bugCenter - offset).getDistance()
        if (distance <= bugRadiusPx) {
            onHit()
        }
    }
}

private fun randomStartPosition(
    widthPx: Float,
    heightPx: Float,
    bugRadiusPx: Float,
    seed: Int
): Offset {
    val radius = min(widthPx, heightPx) / 2f - bugRadiusPx * 1.5f
    val random = Random(seed * 9973 + 61)
    val angle = random.nextDouble(0.0, 2.0 * PI).toFloat()
    val center = Offset(widthPx / 2f, heightPx / 2f)
    val x = center.x + cos(angle).toFloat() * radius
    val y = center.y + sin(angle).toFloat() * radius
    return Offset(x, y)
}

private fun Float.formatSeconds(): String = String.format("%.2fs", this)

@Composable
private fun TrainingGameError(onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Training scenario unavailable.",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onExit) {
            Text(text = "Back")
        }
    }
}
