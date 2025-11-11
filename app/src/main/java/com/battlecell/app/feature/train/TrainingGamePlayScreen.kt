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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.battlecell.app.domain.model.TrainingGameDefinition
import com.battlecell.app.domain.model.TrainingGameType
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
                TrainingGameEvent.Defeat -> snackbarHostState.showSnackbar("Challenge failed. Try again.")
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
                else -> {
                    val definition = uiState.definition!!
                    val submitResult: (Long, Boolean, Int) -> Unit = { elapsed, didWin, score ->
                        viewModel.recordOutcome(elapsed, didWin, score)
                    }
                    when (definition.gameType) {
                        TrainingGameType.BUG_HUNT -> BugHuntGame(
                            state = uiState,
                            onSubmitResult = submitResult,
                            onExit = onExit,
                            onResetResult = viewModel::consumeResult
                        )

                        TrainingGameType.FLAPPY_FLIGHT -> FlappyFlightGame(
                            state = uiState,
                            onSubmitResult = submitResult,
                            onExit = onExit,
                            onResetResult = viewModel::consumeResult
                        )

                        TrainingGameType.DOODLE_JUMP -> DoodleJumpGame(
                            state = uiState,
                            onSubmitResult = submitResult,
                            onExit = onExit,
                            onResetResult = viewModel::consumeResult
                        )

                        TrainingGameType.SUBWAY_RUN -> SubwayRunGame(
                            state = uiState,
                            onSubmitResult = submitResult,
                            onExit = onExit,
                            onResetResult = viewModel::consumeResult
                        )
                    }
                }
            }
        }
    }
}

private enum class GamePhase { Idle, Playing, Victory, Defeat }

@Composable
private fun BugHuntGame(
    state: TrainingGameUiState,
    onSubmitResult: (elapsedMillis: Long, didWin: Boolean, score: Int) -> Unit,
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
                    onSubmitResult(elapsed, false, 0)
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
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val arenaCenter = Offset(widthPx / 2f, heightPx / 2f)

            if (startPosition == Offset.Zero || gamePhase == GamePhase.Idle) {
                startPosition = randomStartPosition(widthPx, heightPx, bugRadiusPx, runId)
            }

            val currentBugPosition = lerp(startPosition, arenaCenter, progress.value)
            val bugActiveState = rememberUpdatedState(gamePhase == GamePhase.Playing && bugVisible)
            val bugPositionProviderState = rememberUpdatedState(
                newValue = {
                    val start = startPosition
                    lerp(start, arenaCenter, progress.value)
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(gamePhase, runId) {
                        detectBugTap(
                            enabledProvider = { bugActiveState.value },
                            bugRadiusPx = bugRadiusPx,
                            bugPositionProvider = { bugPositionProviderState.value.invoke() }
                        ) {
                            coroutineScope.launch {
                                progress.stop()
                            }
                            val elapsed = SystemClock.elapsedRealtime() - startTimestamp
                            elapsedMillis = elapsed
                            val remaining =
                                (definition.behavior.totalDurationMillis - elapsed.toInt()).coerceAtLeast(0)
                            onSubmitResult(elapsed, true, remaining)
                            gamePhase = GamePhase.Victory
                        }
                    }
            ) {
                BugHuntArena(
                    bugVisible = bugVisible && gamePhase == GamePhase.Playing,
                    bugPosition = currentBugPosition,
                    bugRadiusPx = bugRadiusPx,
                    center = arenaCenter
                )
            }
        }

        ControlPanel(
            phase = gamePhase,
            definition = definition,
            elapsedMillis = elapsedMillis,
            score = null,
            scoreLabel = null,
            playingHint = "Anticipate its path and strike before it reaches the core.",
            defeatMessage = "The nanobot breached the core defenses.",
            lastResult = state.lastResult,
            onStart = {
                bugVisible = true
                onResetResult()
                gamePhase = GamePhase.Playing
                startPosition = Offset.Zero
                runId++
            },
            onRetry = {
                bugVisible = true
                onResetResult()
                gamePhase = GamePhase.Idle
                startPosition = Offset.Zero
                runId++
            },
            onExit = onExit
        )
    }
}

private data class FlappyPipe(
    val x: Float,
    val gapCenter: Float,
    val gapHeight: Float,
    val scored: Boolean
)

private data class JumpPlatform(
    val x: Float,
    val y: Float,
    val width: Float
)

private data class RunnerObstacle(
    val lane: Int,
    val y: Float,
    val passed: Boolean
)

@Composable
private fun FlappyFlightGame(
    state: TrainingGameUiState,
    onSubmitResult: (elapsedMillis: Long, didWin: Boolean, score: Int) -> Unit,
    onExit: () -> Unit,
    onResetResult: () -> Unit
) {
    val definition = state.definition ?: return
    val behavior = definition.behavior
    val density = LocalDensity.current

    var gamePhase by remember(definition.id + "_flappy") { mutableStateOf(GamePhase.Idle) }
    var runId by remember(definition.id + "_flappy") { mutableIntStateOf(0) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var score by remember { mutableIntStateOf(0) }
    val pipes = remember { mutableStateListOf<FlappyPipe>() }
    var birdY by remember { mutableStateOf(0f) }
    var birdVelocity by remember { mutableStateOf(0f) }

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
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val birdRadiusPx = with(density) {
                (behavior.bugRadiusDp.takeIf { it > 0f } ?: 24f).dp.toPx()
            }
            val birdX = widthPx * 0.28f
            val pipeWidth = with(density) { 52.dp.toPx() }
            val pipeSpacing = widthPx * 0.45f

            LaunchedEffect(gamePhase, runId, widthPx, heightPx) {
                if (gamePhase != GamePhase.Playing) {
                    birdY = heightPx / 2f
                    birdVelocity = 0f
                    pipes.clear()
                    val seed = runId * 997 + 11
                    pipes += generateFlappyPipe(
                        startX = widthPx * 1.2f,
                        pipeWidth = pipeWidth,
                        heightPx = heightPx,
                        seed = seed
                    )
                }
            }

            LaunchedEffect(gamePhase, runId, widthPx, heightPx) {
                if (gamePhase != GamePhase.Playing) return@LaunchedEffect
                val start = SystemClock.elapsedRealtime()
                elapsedMillis = 0L
                score = 0
                birdY = heightPx / 2f
                birdVelocity = 0f
                pipes.clear()
                pipes += generateFlappyPipe(
                    startX = widthPx * 1.2f,
                    pipeWidth = pipeWidth,
                    heightPx = heightPx,
                    seed = runId * 1337 + 19
                )
                val duration = behavior.totalDurationMillis.takeIf { it > 0 } ?: 45_000
                val gravity = 1200f
                val flapImpulse = -520f
                val pipeSpeed = widthPx / 2.6f
                var localScore = 0
                var lastTime = start

                fun finishRound(didWin: Boolean) {
                    val elapsed = SystemClock.elapsedRealtime() - start
                    elapsedMillis = elapsed
                    score = localScore
                    onSubmitResult(elapsed, didWin, localScore)
                    gamePhase = if (didWin) GamePhase.Victory else GamePhase.Defeat
                }

                while (isActive && gamePhase == GamePhase.Playing) {
                    val now = SystemClock.elapsedRealtime()
                    val deltaMillis = (now - lastTime).coerceAtMost(48L)
                    val deltaSeconds = deltaMillis / 1000f
                    lastTime = now
                    elapsedMillis = now - start

                    if (elapsedMillis >= duration) {
                        finishRound(true)
                        break
                    }

                    birdVelocity += gravity * deltaSeconds
                    birdY += birdVelocity * deltaSeconds

                    var defeated = birdY <= birdRadiusPx || birdY >= heightPx - birdRadiusPx

                    for (index in pipes.indices) {
                        val pipe = pipes[index]
                        val newX = pipe.x - pipeSpeed * deltaSeconds
                        var newScored = pipe.scored
                        if (!pipe.scored && newX + pipeWidth / 2f < birdX - birdRadiusPx) {
                            newScored = true
                            localScore += 1
                        }

                        if (!defeated) {
                            val overlapsHorizontally =
                                birdX + birdRadiusPx > newX && birdX - birdRadiusPx < newX + pipeWidth
                            if (overlapsHorizontally) {
                                val gapHalf = pipe.gapHeight / 2f
                                val topLimit = pipe.gapCenter - gapHalf
                                val bottomLimit = pipe.gapCenter + gapHalf
                                if (birdY - birdRadiusPx < topLimit || birdY + birdRadiusPx > bottomLimit) {
                                    defeated = true
                                }
                            }
                        }

                        pipes[index] = pipe.copy(x = newX, scored = newScored)
                    }

                    pipes.removeAll { it.x + pipeWidth < 0f }
                    if (pipes.isEmpty() || pipes.last().x < widthPx - pipeSpacing) {
                        val seed = runId * 971 + localScore * 41 + pipes.size * 7
                        pipes += generateFlappyPipe(
                            startX = widthPx + pipeWidth,
                            pipeWidth = pipeWidth,
                            heightPx = heightPx,
                            seed = seed
                        )
                    }

                    score = localScore

                    if (defeated) {
                        finishRound(false)
                        break
                    }

                    delay(16)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(gamePhase, runId) {
                        detectTapGestures {
                            if (gamePhase == GamePhase.Playing) {
                                birdVelocity = -520f
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val background = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF091833),
                            Color(0xFF03111F)
                        )
                    )
                    drawRect(brush = background, size = size)

                    pipes.forEach { pipe ->
                        val topHeight = (pipe.gapCenter - pipe.gapHeight / 2f).coerceAtLeast(0f)
                        if (topHeight > 0f) {
                            drawRect(
                                color = Color(0xFF1EC8FF),
                                topLeft = Offset(pipe.x, 0f),
                                size = Size(pipeWidth, topHeight)
                            )
                        }
                        val bottomStart = pipe.gapCenter + pipe.gapHeight / 2f
                        val bottomHeight = (size.height - bottomStart).coerceAtLeast(0f)
                        if (bottomHeight > 0f) {
                            drawRect(
                                color = Color(0xFF1EC8FF),
                                topLeft = Offset(pipe.x, bottomStart),
                                size = Size(pipeWidth, bottomHeight)
                            )
                        }
                    }

                    val birdBrush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFC107), Color(0xFFFF5722)),
                        center = Offset(birdX, birdY),
                        radius = birdRadiusPx * 1.3f
                    )
                    drawCircle(
                        brush = birdBrush,
                        radius = birdRadiusPx,
                        center = Offset(birdX, birdY)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.75f),
                        radius = birdRadiusPx / 4f,
                        center = Offset(birdX + birdRadiusPx / 3f, birdY - birdRadiusPx / 3f)
                    )
                }

                Text(
                    text = score.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                )
            }
        }

        ControlPanel(
            phase = gamePhase,
            definition = definition,
            elapsedMillis = elapsedMillis,
            score = score,
            scoreLabel = "Gates cleared",
            playingHint = "Tap anywhere to thrust upward. Glide through every gap.",
            defeatMessage = "The drone collided with a containment pylon.",
            lastResult = state.lastResult,
            onStart = {
                onResetResult()
                score = 0
                elapsedMillis = 0L
                gamePhase = GamePhase.Playing
                runId++
            },
            onRetry = {
                onResetResult()
                score = 0
                elapsedMillis = 0L
                gamePhase = GamePhase.Idle
                runId++
            },
            onExit = onExit
        )
    }
}

@Composable
private fun SubwayRunGame(
    state: TrainingGameUiState,
    onSubmitResult: (elapsedMillis: Long, didWin: Boolean, score: Int) -> Unit,
    onExit: () -> Unit,
    onResetResult: () -> Unit
) {
    val definition = state.definition ?: return
    val behavior = definition.behavior
    val density = LocalDensity.current

    var gamePhase by remember(definition.id + "_runner") { mutableStateOf(GamePhase.Idle) }
    var runId by remember(definition.id + "_runner") { mutableIntStateOf(0) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var score by remember { mutableIntStateOf(0) }
    var playerLane by remember { mutableIntStateOf(1) }
    val obstacles = remember { mutableStateListOf<RunnerObstacle>() }

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
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val laneCount = 3
            val laneWidth = widthPx / laneCount
            val playerY = heightPx * 0.78f
            val avatarRadius = with(density) { 24.dp.toPx() }
            val obstacleHeight = with(density) { 46.dp.toPx() }
            val obstacleWidth = laneWidth * 0.6f
            val duration = behavior.totalDurationMillis.takeIf { it > 0 } ?: 60_000

            LaunchedEffect(gamePhase, runId) {
                if (gamePhase != GamePhase.Playing) {
                    obstacles.clear()
                    obstacles += generateRunnerObstacle(
                        laneCount = laneCount,
                        startY = -obstacleHeight * 1.5f,
                        seed = runId * 101 + 3
                    )
                    score = 0
                    playerLane = 1
                    elapsedMillis = 0L
                }
            }

            LaunchedEffect(gamePhase, runId) {
                if (gamePhase != GamePhase.Playing) return@LaunchedEffect
                val start = SystemClock.elapsedRealtime()
                var lastTime = start
                var localScore = 0

                fun finishRound(didWin: Boolean) {
                    val elapsed = SystemClock.elapsedRealtime() - start
                    elapsedMillis = elapsed
                    score = localScore
                    onSubmitResult(elapsed, didWin, localScore)
                    gamePhase = if (didWin) GamePhase.Victory else GamePhase.Defeat
                }

                while (isActive && gamePhase == GamePhase.Playing) {
                    val now = SystemClock.elapsedRealtime()
                    val deltaMillis = (now - lastTime).coerceAtMost(48L)
                    val deltaSeconds = deltaMillis / 1000f
                    lastTime = now
                    elapsedMillis = now - start

                    if (elapsedMillis >= duration) {
                        finishRound(true)
                        break
                    }

                    val speed = (heightPx / 2.6f) + (elapsedMillis / 4000f)

                    for (index in obstacles.indices) {
                        val obstacle = obstacles[index]
                        val newY = obstacle.y + speed * deltaSeconds
                        var newPassed = obstacle.passed

                        val collision = obstacle.lane == playerLane &&
                                newY + obstacleHeight > playerY - avatarRadius &&
                                newY < playerY + avatarRadius

                        if (collision) {
                            finishRound(false)
                            return@LaunchedEffect
                        }

                        if (!obstacle.passed && newY > playerY + avatarRadius) {
                            newPassed = true
                            localScore += 1
                        }

                        obstacles[index] = obstacle.copy(y = newY, passed = newPassed)
                    }

                    obstacles.removeAll { it.y > heightPx + obstacleHeight }

                    if (obstacles.isEmpty() || obstacles.last().y > heightPx / 3f) {
                        val seed = runId * 373 + localScore * 19 + obstacles.size * 11
                        obstacles += generateRunnerObstacle(
                            laneCount = laneCount,
                            startY = -obstacleHeight * 1.5f,
                            seed = seed
                        )
                    }

                    score = localScore

                    delay(16)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(gamePhase, runId) {
                        detectTapGestures { offset ->
                            if (gamePhase == GamePhase.Playing) {
                                val tappedLane = (offset.x / laneWidth).toInt().coerceIn(0, laneCount - 1)
                                when {
                                    tappedLane < playerLane -> playerLane = (playerLane - 1).coerceAtLeast(0)
                                    tappedLane > playerLane -> playerLane = (playerLane + 1).coerceAtMost(laneCount - 1)
                                }
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val background = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF011A27),
                            Color(0xFF022B40)
                        )
                    )
                    drawRect(brush = background, size = size)

                    for (lane in 1 until laneCount) {
                        val x = laneWidth * lane
                        drawLine(
                            color = Color.White.copy(alpha = 0.1f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 4f
                        )
                    }

                    obstacles.forEach { obstacle ->
                        val laneCenter = laneWidth * (obstacle.lane + 0.5f)
                        drawRoundRect(
                            color = Color(0xFFFF8A65),
                            topLeft = Offset(
                                laneCenter - obstacleWidth / 2f,
                                obstacle.y
                            ),
                            size = Size(obstacleWidth, obstacleHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
                        )
                    }

                    val playerCenterX = laneWidth * (playerLane + 0.5f)
                    val avatarBrush = Brush.radialGradient(
                        colors = listOf(Color(0xFF4CAF50), Color(0xFF1B5E20)),
                        center = Offset(playerCenterX, playerY),
                        radius = avatarRadius * 1.4f
                    )
                    drawCircle(
                        brush = avatarBrush,
                        radius = avatarRadius,
                        center = Offset(playerCenterX, playerY)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.7f),
                        radius = avatarRadius / 4f,
                        center = Offset(playerCenterX + avatarRadius / 3f, playerY - avatarRadius / 3f)
                    )
                }

                Text(
                    text = score.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                )
            }
        }

        ControlPanel(
            phase = gamePhase,
            definition = definition,
            elapsedMillis = elapsedMillis,
            score = score,
            scoreLabel = "Obstacles cleared",
            playingHint = "Tap between lanes to dodge incoming security drones.",
            defeatMessage = "You collided with a security drone.",
            lastResult = state.lastResult,
            onStart = {
                onResetResult()
                score = 0
                elapsedMillis = 0L
                gamePhase = GamePhase.Playing
                runId++
            },
            onRetry = {
                onResetResult()
                score = 0
                elapsedMillis = 0L
                gamePhase = GamePhase.Idle
                runId++
            },
            onExit = onExit
        )
    }
}

@Composable
private fun DoodleJumpGame(
    state: TrainingGameUiState,
    onSubmitResult: (elapsedMillis: Long, didWin: Boolean, score: Int) -> Unit,
    onExit: () -> Unit,
    onResetResult: () -> Unit
) {
    val definition = state.definition ?: return
    val behavior = definition.behavior
    val density = LocalDensity.current

    var gamePhase by remember(definition.id + "_jump") { mutableStateOf(GamePhase.Idle) }
    var runId by remember(definition.id + "_jump") { mutableIntStateOf(0) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var score by remember { mutableIntStateOf(0) }
    var heightClimbed by remember { mutableStateOf(0f) }
    val platforms = remember { mutableStateListOf<JumpPlatform>() }
    var playerX by remember { mutableStateOf(0f) }
    var playerY by remember { mutableStateOf(0f) }
    var playerVelocityX by remember { mutableStateOf(0f) }
    var playerVelocityY by remember { mutableStateOf(0f) }

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
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val avatarRadius = with(density) {
                (behavior.bugRadiusDp.takeIf { it > 0f } ?: 20f).dp.toPx()
            }
            val platformHeight = with(density) { 16.dp.toPx() }
            val platformCount = 10
            val requiredScore = behavior.targetScore.takeIf { it > 0 } ?: 650

            LaunchedEffect(gamePhase, runId, widthPx, heightPx) {
                if (gamePhase != GamePhase.Playing) {
                    val random = Random(runId * 311 + 7)
                    val spacing = heightPx / (platformCount - 1)
                    platforms.clear()
                    repeat(platformCount) { index ->
                        val y = heightPx - index * spacing
                        platforms += generateJumpPlatform(
                            widthPx = widthPx,
                            y = y,
                            seed = random.nextInt()
                        )
                    }
                    playerX = widthPx / 2f
                    playerY = heightPx * 0.3f
                    playerVelocityX = 0f
                    playerVelocityY = 0f
                    heightClimbed = 0f
                    score = 0
                }
            }

            LaunchedEffect(gamePhase, runId, widthPx, heightPx) {
                if (gamePhase != GamePhase.Playing) return@LaunchedEffect
                val start = SystemClock.elapsedRealtime()
                elapsedMillis = 0L
                heightClimbed = 0f
                score = 0
                playerX = widthPx / 2f
                playerY = heightPx * 0.35f
                playerVelocityX = 0f
                playerVelocityY = 0f
                val gravity = 1800f
                val jumpImpulse = -1250f
                val horizontalImpulse = widthPx / 1.4f
                val friction = 0.9f
                val spacing = heightPx / (platformCount - 1)
                var lastTime = start

                fun finishRound(didWin: Boolean) {
                    val elapsed = SystemClock.elapsedRealtime() - start
                    elapsedMillis = elapsed
                    onSubmitResult(elapsed, didWin, score)
                    gamePhase = if (didWin) GamePhase.Victory else GamePhase.Defeat
                }

                while (isActive && gamePhase == GamePhase.Playing) {
                    val now = SystemClock.elapsedRealtime()
                    val deltaMillis = (now - lastTime).coerceAtMost(48L)
                    val deltaSeconds = deltaMillis / 1000f
                    lastTime = now
                    elapsedMillis = now - start

                    playerVelocityY += gravity * deltaSeconds
                    playerY += playerVelocityY * deltaSeconds
                    playerX += playerVelocityX * deltaSeconds
                    playerVelocityX *= friction

                    if (playerX < avatarRadius) {
                        playerX = avatarRadius
                        playerVelocityX = 0f
                    } else if (playerX > widthPx - avatarRadius) {
                        playerX = widthPx - avatarRadius
                        playerVelocityX = 0f
                    }

                    if (playerVelocityY > 0f) {
                        for (index in platforms.indices) {
                            val platform = platforms[index]
                            val platformTop = platform.y
                            val overlapX = playerX + avatarRadius > platform.x &&
                                playerX - avatarRadius < platform.x + platform.width
                            val overlapY = playerY + avatarRadius >= platformTop &&
                                playerY + avatarRadius <= platformTop + platformHeight
                            if (overlapX && overlapY) {
                                playerY = platformTop - avatarRadius
                                playerVelocityY = jumpImpulse
                                break
                            }
                        }
                    }

                    if (playerY < heightPx * 0.35f && playerVelocityY < 0f) {
                        val shift = heightPx * 0.35f - playerY
                        playerY += shift
                        heightClimbed += shift
                        for (index in platforms.indices) {
                            val platform = platforms[index]
                            platforms[index] = platform.copy(y = platform.y + shift)
                        }
                    }

                    for (index in platforms.indices) {
                        val platform = platforms[index]
                        if (platform.y > heightPx + platformHeight) {
                            val seed = runId * 773 + index * 37 + score * 5
                            val newPlatform = generateJumpPlatform(
                                widthPx = widthPx,
                                y = platform.y - heightPx - spacing,
                                seed = seed
                            )
                            platforms[index] = newPlatform
                        }
                    }

                    score = kotlin.math.max(score, (heightClimbed / 12f).toInt())

                    if (score >= requiredScore) {
                        finishRound(true)
                        break
                    }

                    if (playerY - avatarRadius > heightPx) {
                        finishRound(false)
                        break
                    }

                    delay(16)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(gamePhase, runId) {
                        detectTapGestures { tapOffset ->
                            if (gamePhase == GamePhase.Playing) {
                                val horizontalImpulse = widthPx / 1.4f
                                playerVelocityX = if (tapOffset.x < widthPx / 2f) {
                                    -horizontalImpulse
                                } else {
                                    horizontalImpulse
                                }
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val background = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF120C2B),
                            Color(0xFF06020F)
                        )
                    )
                    drawRect(brush = background, size = size)

                    platforms.forEach { platform ->
                        drawRoundRect(
                            color = Color(0xFF7C4DFF),
                            topLeft = Offset(platform.x, platform.y),
                            size = Size(platform.width, platformHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                        )
                    }

                    val avatarBrush = Brush.radialGradient(
                        colors = listOf(Color(0xFF03DAC5), Color(0xFF018786)),
                        center = Offset(playerX, playerY),
                        radius = avatarRadius * 1.3f
                    )
                    drawCircle(
                        brush = avatarBrush,
                        radius = avatarRadius,
                        center = Offset(playerX, playerY)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.8f),
                        radius = avatarRadius / 4f,
                        center = Offset(playerX + avatarRadius / 3f, playerY - avatarRadius / 3f)
                    )
                }

                Text(
                    text = "${score}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                )
            }
        }

        ControlPanel(
            phase = gamePhase,
            definition = definition,
            elapsedMillis = elapsedMillis,
            score = score,
            scoreLabel = "Height reached",
            playingHint = "Tap left or right to nudge your jump. Chain bounces to keep climbing.",
            defeatMessage = "You slipped below the reactor shaft.",
            lastResult = state.lastResult,
            onStart = {
                onResetResult()
                score = 0
                heightClimbed = 0f
                elapsedMillis = 0L
                gamePhase = GamePhase.Playing
                runId++
            },
            onRetry = {
                onResetResult()
                score = 0
                heightClimbed = 0f
                elapsedMillis = 0L
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
            Badge(text = "Timer ${(elapsedMillis / 1000f).formatSeconds()}")
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
private fun BugHuntArena(
    bugVisible: Boolean,
    bugPosition: Offset,
    bugRadiusPx: Float,
    center: Offset
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawBackgroundGrid()
        drawTarget(center, bugRadiusPx)
        if (bugVisible) {
            drawBug(bugPosition, bugRadiusPx)
        }
    }
}

private fun DrawScope.drawBackgroundGrid() {
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

@Composable
private fun ControlPanel(
    phase: GamePhase,
    definition: TrainingGameDefinition,
    elapsedMillis: Long,
    score: Int?,
    scoreLabel: String?,
    playingHint: String,
    defeatMessage: String,
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
                    text = "Mission: ${definition.title}. Launch when you're ready.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Flag, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Start")
                }
            }

            GamePhase.Playing -> {
                Text(
                    text = "Elapsed: ${(elapsedMillis / 1000f).formatSeconds()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (score != null && scoreLabel != null) {
                    Text(
                        text = "$scoreLabel: $score",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = playingHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            GamePhase.Victory -> {
                VictoryPanel(
                    lastResult = lastResult,
                    scoreLabel = scoreLabel,
                    score = score,
                    onRetry = onRetry,
                    onExit = onExit
                )
            }

            GamePhase.Defeat -> {
                DefeatPanel(
                    message = defeatMessage,
                    onRetry = onRetry,
                    onExit = onExit
                )
            }
        }
    }
}

@Composable
private fun VictoryPanel(
    lastResult: TrainingGameResult?,
    scoreLabel: String?,
    score: Int?,
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
            if (scoreLabel != null && score != null) {
                Text(
                    text = "$scoreLabel: $score",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
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
    message: String,
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
                text = "Challenge failed.",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = message,
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

private fun generateJumpPlatform(
    widthPx: Float,
    y: Float,
    seed: Int
): JumpPlatform {
    val random = Random(seed)
    val minWidth = widthPx * 0.18f
    val maxWidth = widthPx * 0.32f
    val platformWidth = random.nextDouble(minWidth.toDouble(), maxWidth.toDouble()).toFloat()
    val x = random.nextDouble(0.0, (widthPx - platformWidth).toDouble()).toFloat()
    return JumpPlatform(
        x = x,
        y = y,
        width = platformWidth
    )
}

private fun generateFlappyPipe(
    startX: Float,
    pipeWidth: Float,
    heightPx: Float,
    seed: Int
): FlappyPipe {
    val random = Random(seed)
    val minGap = heightPx * 0.28f
    val maxGap = heightPx * 0.42f
    val gapHeight = random.nextDouble(minGap.toDouble(), maxGap.toDouble()).toFloat()
    val topMargin = heightPx * 0.12f + pipeWidth
    val bottomMargin = heightPx * 0.12f
    val minCenter = topMargin + gapHeight / 2f
    val maxCenter = heightPx - bottomMargin - gapHeight / 2f
    val gapCenter = random.nextDouble(minCenter.toDouble(), maxCenter.toDouble()).toFloat()
    return FlappyPipe(
        x = startX,
        gapCenter = gapCenter,
        gapHeight = gapHeight,
        scored = false
    )
}

private fun generateRunnerObstacle(
    laneCount: Int,
    startY: Float,
    seed: Int
): RunnerObstacle {
    val random = Random(seed)
    val lane = random.nextInt(laneCount)
    return RunnerObstacle(
        lane = lane,
        y = startY,
        passed = false
    )
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectBugTap(
    enabledProvider: () -> Boolean,
    bugRadiusPx: Float,
    bugPositionProvider: () -> Offset,
    onHit: () -> Unit
) {
    detectTapGestures { offset ->
        if (!enabledProvider()) return@detectTapGestures
        val bugCenter = bugPositionProvider()
        val distance = (bugCenter - offset).getDistance()
        if (distance <= bugRadiusPx * 1.2f) {
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
