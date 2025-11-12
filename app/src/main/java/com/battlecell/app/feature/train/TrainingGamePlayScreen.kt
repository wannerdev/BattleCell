package com.battlecell.app.feature.train

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import com.battlecell.app.domain.model.AttributeType
import com.battlecell.app.domain.model.Difficulty
import com.battlecell.app.domain.model.TrainingGameDefinition
import com.battlecell.app.domain.model.TrainingGameType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
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
                is TrainingGameEvent.Victory -> {
                    val attributeLabel = event.result.attributeType.name.lowercase()
                    val rewardParts = buildList {
                        add("+${event.result.attributeGain} $attributeLabel")
                        if (event.result.experienceGain > 0) {
                            add("+${event.result.experienceGain} xp")
                        }
                        if (event.result.variantSkillPointGain > 0) {
                            add("+${event.result.variantSkillPointGain} $attributeLabel sigils")
                        }
                        if (event.result.generalSkillPointGain > 0) {
                            add("+${event.result.generalSkillPointGain} universal skill points")
                        }
                    }
                    snackbarHostState.showSnackbar(
                        "Victory! ${rewardParts.joinToString(separator = " · ")}"
                    )
                }
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
                            onResetResult = viewModel::consumeResult,
                            onDifficultySelected = viewModel::setDifficulty
                        )

                        TrainingGameType.FLAPPY_FLIGHT -> FlappyFlightGame(
                            state = uiState,
                            onSubmitResult = submitResult,
                            onExit = onExit,
                            onResetResult = viewModel::consumeResult,
                            onDifficultySelected = viewModel::setDifficulty
                        )

                        TrainingGameType.DOODLE_JUMP -> DoodleJumpGame(
                            state = uiState,
                            onSubmitResult = submitResult,
                            onExit = onExit,
                            onResetResult = viewModel::consumeResult,
                            onDifficultySelected = viewModel::setDifficulty
                        )

                        TrainingGameType.SUBWAY_RUN -> SubwayRunGame(
                            state = uiState,
                            onSubmitResult = submitResult,
                            onExit = onExit,
                            onResetResult = viewModel::consumeResult,
                            onDifficultySelected = viewModel::setDifficulty
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
    onResetResult: () -> Unit,
    onDifficultySelected: (Difficulty) -> Unit
) {
    val definition = state.definition ?: return
    val behavior = definition.behavior
    val density = LocalDensity.current
    val difficulty = state.selectedDifficulty
    val missionDuration = (behavior.totalDurationMillis * difficulty.bugHuntDurationFactor()).roundToInt().coerceAtLeast(2_000)

    var gamePhase by remember(definition.id) { mutableStateOf(GamePhase.Idle) }
    var runId by remember(definition.id) { mutableIntStateOf(0) }
    val progress = remember(definition.id) { Animatable(0f) }
    var bugVisible by remember { mutableStateOf(true) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var startTimestamp by remember { mutableLongStateOf(0L) }

    val coroutineScope = rememberCoroutineScope()

    val bugRadiusPx = with(density) { behavior.bugRadiusDp.dp.toPx() * difficulty.bugHuntRadiusScale() }

    var pathSeed by remember { mutableStateOf<SpiralSeed?>(null) }

    LaunchedEffect(gamePhase, runId) {
        if (gamePhase == GamePhase.Playing) {
            startTimestamp = SystemClock.elapsedRealtime()
            progress.snapTo(0f)
            try {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = missionDuration,
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
            val visibleMillis = difficulty.bugVisibleMillis(behavior.visibleWindowMillis)
            val hiddenMillis = difficulty.bugHiddenMillis(behavior.invisibleWindowMillis)
            while (isActive && gamePhase == GamePhase.Playing) {
                bugVisible = true
                delay(visibleMillis)
                if (gamePhase != GamePhase.Playing) break
                bugVisible = false
                delay(hiddenMillis)
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

              if (pathSeed == null || gamePhase == GamePhase.Idle) {
                  pathSeed = createSpiralSeed(
                      widthPx = widthPx,
                      heightPx = heightPx,
                      bugRadiusPx = bugRadiusPx,
                      runId = runId,
                      difficulty = difficulty
                  )
              }

              val seed = pathSeed
              val currentBugPosition = seed?.let { spiralPosition(it, arenaCenter, progress.value) } ?: arenaCenter
              val bugActiveState = rememberUpdatedState(gamePhase == GamePhase.Playing && bugVisible)
              val bugPositionProviderState = rememberUpdatedState(
                  newValue = {
                      val currentSeed = seed
                      if (currentSeed == null) {
                          arenaCenter
                      } else {
                          spiralPosition(currentSeed, arenaCenter, progress.value)
                      }
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
                                (missionDuration - elapsed.toInt()).coerceAtLeast(0)
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
            playingHint = "Anticipate its path and strike before it meets the core ward.",
            defeatMessage = "The rogue spark slipped past the ward.",
            lastResult = state.lastResult,
            selectedDifficulty = state.selectedDifficulty,
            onDifficultySelected = onDifficultySelected,
            onStart = {
                bugVisible = true
                onResetResult()
                gamePhase = GamePhase.Playing
                  pathSeed = null
                runId++
            },
            onRetry = {
                bugVisible = true
                onResetResult()
                gamePhase = GamePhase.Idle
                  pathSeed = null
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

private data class SpiralSeed(
    val initialAngle: Float,
    val initialRadius: Float,
    val rotations: Float
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

private data class JumpPowerUpDefinition(
    val id: Int,
    val name: String,
    val color: Color,
    val durationMillis: Long? = null,
    val jumpImpulseMultiplier: Float = 1f,
    val gravityMultiplier: Float = 1f,
    val horizontalImpulseMultiplier: Float = 1f,
    val platformWidthMultiplier: Float = 1f,
    val timeScale: Float = 1f,
    val scoreMultiplier: Float = 1f,
    val safetyNetCharges: Int = 0,
    val scoreBonus: Int = 0
)

private data class JumpPowerUpSpawn(
    val definition: JumpPowerUpDefinition,
    val x: Float,
    val y: Float,
    val radius: Float,
    val collected: Boolean = false
)

private data class ActiveJumpPowerUp(
    val definition: JumpPowerUpDefinition,
    val expiresAt: Long
)

private data class RunnerPowerUpDefinition(
    val id: Int,
    val name: String,
    val color: Color,
    val durationMillis: Long? = null,
    val speedMultiplier: Float = 1f,
    val spawnSpacingMultiplier: Float = 1f,
    val hitboxMultiplier: Float = 1f,
    val timeScale: Float = 1f,
    val scoreMultiplier: Float = 1f,
    val shieldCharges: Int = 0,
    val scoreBonus: Int = 0
)

private data class RunnerPowerUpSpawn(
    val definition: RunnerPowerUpDefinition,
    val lane: Int,
    val y: Float,
    val radius: Float,
    val collected: Boolean = false
)

private data class ActiveRunnerPowerUp(
    val definition: RunnerPowerUpDefinition,
    val expiresAt: Long
)

private data class CollectedBoost(
    val name: String,
    val expiresAt: Long
)

private data class JumpEffectAggregate(
    val jumpImpulseMultiplier: Float = 1f,
    val gravityMultiplier: Float = 1f,
    val horizontalImpulseMultiplier: Float = 1f,
    val platformWidthMultiplier: Float = 1f,
    val timeScale: Float = 1f,
    val scoreMultiplier: Float = 1f
)

private data class RunnerEffectAggregate(
    val speedMultiplier: Float = 1f,
    val spawnSpacingMultiplier: Float = 1f,
    val hitboxMultiplier: Float = 1f,
    val timeScale: Float = 1f,
    val scoreMultiplier: Float = 1f
)

@Composable
private fun FlappyFlightGame(
    state: TrainingGameUiState,
    onSubmitResult: (elapsedMillis: Long, didWin: Boolean, score: Int) -> Unit,
    onExit: () -> Unit,
    onResetResult: () -> Unit,
    onDifficultySelected: (Difficulty) -> Unit
) {
    val definition = state.definition ?: return
    val behavior = definition.behavior
    val density = LocalDensity.current
    val difficulty = state.selectedDifficulty
    val targetScore = (behavior.targetScore.takeIf { it > 0 } ?: difficulty.flappyVictoryTarget()).coerceAtLeast(3)

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
                (behavior.bugRadiusDp.takeIf { it > 0f } ?: 24f).dp.toPx() * difficulty.flappyRadiusScale()
            }
              val birdX = widthPx * 0.28f
            val pipeWidth = with(density) { 56.dp.toPx() }
              val pipeSpacing = widthPx * (0.45f * difficulty.flappySpacingFactor())
            val missionDuration = ((behavior.totalDurationMillis.takeIf { it > 0 } ?: 36_000) * difficulty.flappyDurationFactor()).roundToInt()
              val gapFactor = difficulty.flappyGapScale()
              val speedScale = difficulty.flappySpeedScale()
            val gravity = 980f * speedScale
            val flapImpulse = -480f * speedScale
            val pipeSpeed = (widthPx / 3.1f) * speedScale

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
                        seed = seed,
                        gapFactor = gapFactor
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
                    seed = runId * 1337 + 19,
                    gapFactor = gapFactor
                )
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

                    if (elapsedMillis >= missionDuration) {
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
                            seed = seed,
                            gapFactor = gapFactor
                        )
                    }

                        score = localScore

                        if (localScore >= targetScore) {
                            finishRound(true)
                            break
                        }

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
                                  birdVelocity = flapImpulse
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

                        drawSkillGlyph(
                            attributeType = AttributeType.AGILITY,
                            center = Offset(birdX, birdY),
                            radius = birdRadiusPx
                        )
                }

                Text(
                    text = "$score / $targetScore",
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
            scoreLabel = "Gates cleared (goal $targetScore)",
            playingHint = "Tap to give your falcon lift. Thread the warded pylons. Goal: $targetScore gates.",
            defeatMessage = "The scout clipped a charged pylon.",
            lastResult = state.lastResult,
            selectedDifficulty = state.selectedDifficulty,
            onDifficultySelected = onDifficultySelected,
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
    onResetResult: () -> Unit,
    onDifficultySelected: (Difficulty) -> Unit
) {
    val definition = state.definition ?: return
    val behavior = definition.behavior
    val density = LocalDensity.current
    val difficulty = state.selectedDifficulty
    val powerUpCatalog = remember { runnerPowerUpCatalog() }

    var gamePhase by remember(definition.id + "_runner") { mutableStateOf(GamePhase.Idle) }
    var runId by remember(definition.id + "_runner") { mutableIntStateOf(0) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var score by remember { mutableIntStateOf(0) }
    var playerLane by remember { mutableIntStateOf(1) }
    val obstacles = remember { mutableStateListOf<RunnerObstacle>() }
    val runnerPowerUps = remember { mutableStateListOf<RunnerPowerUpSpawn>() }
    val activeRunnerEffects = remember { mutableStateListOf<ActiveRunnerPowerUp>() }
    var shieldCharges by remember { mutableIntStateOf(0) }
    var recentBoost by remember { mutableStateOf<CollectedBoost?>(null) }
    var clockTick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            clockTick = SystemClock.elapsedRealtime()
            delay(250)
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
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val laneCount = 3
            val laneWidth = widthPx / laneCount
            val playerY = heightPx * 0.78f
            val avatarRadius = with(density) { 20.dp.toPx() * difficulty.runnerAvatarScale() }
            val obstacleHeight = with(density) { 46.dp.toPx() }
            val obstacleWidth = laneWidth * 0.6f
            val missionDuration = ((behavior.totalDurationMillis.takeIf { it > 0 } ?: 60_000) * difficulty.runnerDurationFactor()).roundToInt()
            val baseSpawnFactor = difficulty.runnerSpawnFactor()
            val powerUpRadius = laneWidth * 0.22f

            fun resetCourse() {
                val random = Random(runId * 101 + 3)
                obstacles.clear()
                obstacles += generateRunnerObstacle(
                    laneCount = laneCount,
                    startY = -obstacleHeight * 1.5f,
                    seed = random.nextInt()
                )
                runnerPowerUps.clear()
                val shuffledCatalog = powerUpCatalog.shuffled(Random(runId * 2909 + 41))
                runnerPowerUps += generateRunnerPowerUpsForRun(
                    catalog = shuffledCatalog,
                    laneCount = laneCount,
                    laneWidth = laneWidth,
                    heightPx = heightPx,
                    runSeed = runId,
                    radius = powerUpRadius
                )
                activeRunnerEffects.clear()
                shieldCharges = 0
                recentBoost = null
                playerLane = 1
                score = 0
                elapsedMillis = 0L
            }

            LaunchedEffect(gamePhase, runId, widthPx, heightPx) {
                if (gamePhase != GamePhase.Playing) {
                    resetCourse()
                }
            }

            LaunchedEffect(gamePhase, runId, widthPx, heightPx) {
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
                    lastTime = now

                    activeRunnerEffects.removeAll { it.expiresAt != Long.MAX_VALUE && now > it.expiresAt }
                    if (recentBoost?.expiresAt?.let { now > it } == true) {
                        recentBoost = null
                    }

                    val aggregate = activeRunnerEffects.aggregateRunnerEffects()
                    val adjustedDeltaSeconds = (deltaMillis / 1000f) * aggregate.timeScale
                    elapsedMillis = now - start

                    val baseSpeed = ((heightPx / 2.6f) + (elapsedMillis / 4000f))
                    val speed = baseSpeed * difficulty.runnerSpeedScale() * aggregate.speedMultiplier
                    val collisionRadius = avatarRadius * aggregate.hitboxMultiplier
                    val spawnThreshold = heightPx * baseSpawnFactor * aggregate.spawnSpacingMultiplier

                    for (index in runnerPowerUps.indices) {
                        val spawn = runnerPowerUps[index]
                        if (spawn.collected) continue
                        val newY = spawn.y + speed * adjustedDeltaSeconds
                        var updatedSpawn = spawn.copy(y = newY)

                        if (spawn.lane == playerLane &&
                            newY + spawn.radius > playerY - collisionRadius &&
                            newY - spawn.radius < playerY + collisionRadius
                        ) {
                            val collectedAt = SystemClock.elapsedRealtime()
                            if (spawn.definition.hasContinuousEffect()) {
                                val expiresAt = if (spawn.definition.durationMillis == null) {
                                    Long.MAX_VALUE
                                } else {
                                    collectedAt + spawn.definition.durationMillis
                                }
                                activeRunnerEffects += ActiveRunnerPowerUp(spawn.definition, expiresAt)
                            }
                            if (spawn.definition.shieldCharges > 0) {
                                shieldCharges += spawn.definition.shieldCharges
                            }
                            if (spawn.definition.scoreBonus != 0) {
                                localScore += spawn.definition.scoreBonus
                            }
                            recentBoost = CollectedBoost(spawn.definition.name, collectedAt + 3200L)
                            updatedSpawn = spawn.copy(collected = true, y = -heightPx * 2f)
                        } else if (newY > heightPx + spawn.radius) {
                            val resetSeed = runId * 613 + index * 29 + localScore * 7
                            val random = Random(resetSeed)
                            updatedSpawn = spawn.copy(
                                lane = random.nextInt(laneCount),
                                y = -heightPx * random.nextDouble(0.6, 1.2).toFloat(),
                                collected = false
                            )
                        }
                        runnerPowerUps[index] = updatedSpawn
                    }

                    for (index in obstacles.indices) {
                        val obstacle = obstacles[index]
                        val newY = obstacle.y + speed * adjustedDeltaSeconds
                        var newPassed = obstacle.passed
                        var collision = false

                        if (obstacle.lane == playerLane &&
                            newY + obstacleHeight > playerY - collisionRadius &&
                            newY < playerY + collisionRadius
                        ) {
                            collision = true
                        }

                        if (!obstacle.passed && newY > playerY + collisionRadius) {
                            newPassed = true
                            val gain = max(1, aggregate.scoreMultiplier.roundToInt())
                            localScore += gain
                        }

                        if (collision) {
                            if (shieldCharges > 0) {
                                shieldCharges -= 1
                                recentBoost = CollectedBoost("Shield absorbed", now + 2000L)
                                newPassed = true
                            } else {
                                finishRound(false)
                                return@LaunchedEffect
                            }
                        }

                        obstacles[index] = obstacle.copy(y = newY, passed = newPassed)
                    }

                    obstacles.removeAll { it.y > heightPx + obstacleHeight }
                    if (obstacles.isEmpty() || obstacles.last().y > spawnThreshold) {
                        val seed = runId * 373 + localScore * 19 + obstacles.size * 11
                        obstacles += generateRunnerObstacle(
                            laneCount = laneCount,
                            startY = -obstacleHeight * 1.5f,
                            seed = seed
                        )
                    }

                    score = localScore

                    if (elapsedMillis >= missionDuration) {
                        finishRound(true)
                        break
                    }

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
                val runnerAggregate = activeRunnerEffects.aggregateRunnerEffects()
                val renderRadius = avatarRadius * runnerAggregate.hitboxMultiplier.coerceAtLeast(0.65f)
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

                    runnerPowerUps.forEach { spawn ->
                        if (!spawn.collected) {
                            val laneCenter = laneWidth * (spawn.lane + 0.5f)
                            drawCircle(
                                color = spawn.definition.color,
                                radius = spawn.radius,
                                center = Offset(laneCenter, spawn.y)
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.3f),
                                radius = spawn.radius * 0.45f,
                                center = Offset(laneCenter, spawn.y)
                            )
                        }
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
                    drawSkillGlyph(
                        attributeType = AttributeType.ENDURANCE,
                        center = Offset(playerCenterX, playerY),
                        radius = renderRadius
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    recentBoost?.let {
                        Text(
                            text = "Boon: ${it.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                    val activeLabels = activeRunnerEffects.takeIf { it.isNotEmpty() }?.map { effect ->
                        val remaining =
                            if (effect.expiresAt == Long.MAX_VALUE) null
                            else ((effect.expiresAt - clockTick).coerceAtLeast(0L) / 1000f)
                        if (remaining == null) effect.definition.name
                        else "${effect.definition.name} ${"%.1f".format(remaining)}s"
                    }.orEmpty()
                    if (activeLabels.isNotEmpty()) {
                        Text(
                            text = activeLabels.joinToString(separator = "\n"),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    if (shieldCharges > 0) {
                        Text(
                            text = "Shields: $shieldCharges",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
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
            playingHint = "Tap the lanes to weave past the sentry constructs. Survive ${missionDuration / 1000} seconds.",
            defeatMessage = "A sentry construct cut you down.",
            lastResult = state.lastResult,
            selectedDifficulty = state.selectedDifficulty,
            onDifficultySelected = onDifficultySelected,
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
    onResetResult: () -> Unit,
    onDifficultySelected: (Difficulty) -> Unit
) {
    val definition = state.definition ?: return
    val behavior = definition.behavior
    val density = LocalDensity.current
    val difficulty = state.selectedDifficulty
    val powerUpCatalog = remember { jumpPowerUpCatalog() }

    var gamePhase by remember(definition.id + "_jump") { mutableStateOf(GamePhase.Idle) }
    var runId by remember(definition.id + "_jump") { mutableIntStateOf(0) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var score by remember { mutableIntStateOf(0) }
    var heightClimbed by remember { mutableStateOf(0f) }
    val platforms = remember { mutableStateListOf<JumpPlatform>() }
    val jumpPowerUps = remember { mutableStateListOf<JumpPowerUpSpawn>() }
    val activeJumpEffects = remember { mutableStateListOf<ActiveJumpPowerUp>() }
    var safetyNetCharges by remember { mutableIntStateOf(0) }
    var scoreBonusOffset by remember { mutableIntStateOf(0) }
    var recentBoost by remember { mutableStateOf<CollectedBoost?>(null) }
    var playerX by remember { mutableStateOf(0f) }
    var playerY by remember { mutableStateOf(0f) }
    var playerVelocityX by remember { mutableStateOf(0f) }
    var playerVelocityY by remember { mutableStateOf(0f) }
    var pointerHorizontalImpulse by remember { mutableStateOf(0f) }
    var lastJumpImpulse by remember { mutableStateOf(0f) }
    var clockTick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            clockTick = SystemClock.elapsedRealtime()
            delay(250)
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
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val avatarRadius = with(density) {
                (behavior.bugRadiusDp.takeIf { it > 0f } ?: 20f).dp.toPx()
            }
            val platformHeight = with(density) { 14.dp.toPx() }
            val platformCount = difficulty.jumpPlatformCount()
            val baseTarget = behavior.targetScore.takeIf { it > 0 } ?: 650
            val requiredScore = max(
                180,
                (baseTarget * difficulty.jumpScoreMultiplier()).roundToInt()
            )
            val platformWidthScale = difficulty.jumpPlatformWidthScale()
            val spacing = heightPx / (platformCount - 1)
            val horizontalImpulseState = rememberUpdatedState(pointerHorizontalImpulse)

            fun resetCourse() {
                val random = Random(runId * 311 + 7)
                platforms.clear()
                repeat(platformCount) { index ->
                    val y = heightPx - index * spacing
                    platforms += generateJumpPlatform(
                        widthPx = widthPx,
                        y = y,
                        seed = random.nextInt(),
                        widthScale = platformWidthScale
                    )
                }
                val radius = (avatarRadius * 0.72f).coerceAtLeast(avatarRadius * 0.55f)
                val shuffledCatalog = powerUpCatalog.shuffled(random)
                jumpPowerUps.clear()
                jumpPowerUps += generateJumpPowerUpsForRun(
                    catalog = shuffledCatalog,
                    platforms = platforms,
                    spacing = spacing,
                    widthPx = widthPx,
                    avatarRadius = radius,
                    runSeed = runId
                )
                activeJumpEffects.clear()
                safetyNetCharges = 0
                scoreBonusOffset = 0
                recentBoost = null
                heightClimbed = 0f
                score = 0
                elapsedMillis = 0L
                playerX = widthPx / 2f
                playerY = heightPx * 0.32f
                playerVelocityX = 0f
                playerVelocityY = 0f
                pointerHorizontalImpulse = (widthPx / 1.8f) * difficulty.jumpHorizontalScale()
                lastJumpImpulse = -1350f * difficulty.jumpImpulseScale()
            }

            LaunchedEffect(gamePhase, runId, widthPx, heightPx) {
                if (gamePhase != GamePhase.Playing) {
                    resetCourse()
                }
            }

            LaunchedEffect(gamePhase, runId, widthPx, heightPx) {
                if (gamePhase != GamePhase.Playing) return@LaunchedEffect
                val start = SystemClock.elapsedRealtime()
                val baseGravity = 1900f * difficulty.jumpGravityScale()
                val baseJumpImpulse = -1250f * difficulty.jumpImpulseScale()
                val baseHorizontalImpulse = (widthPx / 1.9f) * difficulty.jumpHorizontalScale()
                val friction = 0.86f
                var lastTime = start
                var bestScore = 0

                fun finishRound(didWin: Boolean) {
                    val elapsed = SystemClock.elapsedRealtime() - start
                    elapsedMillis = elapsed
                    score = bestScore
                    onSubmitResult(elapsed, didWin, bestScore)
                    gamePhase = if (didWin) GamePhase.Victory else GamePhase.Defeat
                }

                playerX = widthPx / 2f
                playerY = heightPx * 0.35f
                playerVelocityX = 0f
                playerVelocityY = 0f
                heightClimbed = 0f
                score = 0
                scoreBonusOffset = 0

                while (isActive && gamePhase == GamePhase.Playing) {
                    val now = SystemClock.elapsedRealtime()
                    val deltaMillis = (now - lastTime).coerceAtMost(48L)
                    lastTime = now

                    activeJumpEffects.removeAll { it.expiresAt != Long.MAX_VALUE && now > it.expiresAt }
                    if (recentBoost?.expiresAt?.let { now > it } == true) {
                        recentBoost = null
                    }

                    val aggregate = activeJumpEffects.aggregateJumpEffects()
                    val adjustedDeltaSeconds = (deltaMillis / 1000f) * aggregate.timeScale
                    val gravity = baseGravity * aggregate.gravityMultiplier
                    val jumpImpulse = baseJumpImpulse * aggregate.jumpImpulseMultiplier
                    val horizontalImpulse = baseHorizontalImpulse * aggregate.horizontalImpulseMultiplier
                    lastJumpImpulse = jumpImpulse
                    pointerHorizontalImpulse = horizontalImpulse

                    elapsedMillis = now - start

                    playerVelocityY += gravity * adjustedDeltaSeconds
                    playerY += playerVelocityY * adjustedDeltaSeconds

                    playerX += playerVelocityX * adjustedDeltaSeconds
                    playerVelocityX *= friction

                    if (playerX < avatarRadius) {
                        playerX = avatarRadius
                        playerVelocityX = 0f
                    } else if (playerX > widthPx - avatarRadius) {
                        playerX = widthPx - avatarRadius
                        playerVelocityX = 0f
                    }

                    val widthBoost = aggregate.platformWidthMultiplier

                    if (playerVelocityY > 0f) {
                        for (index in platforms.indices) {
                            val platform = platforms[index]
                            val actualWidth = platform.width * widthBoost
                            val left = platform.x + (platform.width - actualWidth) / 2f
                            val right = left + actualWidth
                            val top = platform.y
                            val overlapX = playerX + avatarRadius > left && playerX - avatarRadius < right
                            val overlapY = playerY + avatarRadius >= top && playerY + avatarRadius <= top + platformHeight
                            if (overlapX && overlapY) {
                                playerY = top - avatarRadius
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
                        for (index in jumpPowerUps.indices) {
                            val spawn = jumpPowerUps[index]
                            jumpPowerUps[index] = spawn.copy(y = spawn.y + shift)
                        }
                    }

                    for (index in platforms.indices) {
                        val platform = platforms[index]
                        if (platform.y > heightPx + platformHeight) {
                            val seed = runId * 773 + index * 37 + score * 5
                            val newPlatform = generateJumpPlatform(
                                widthPx = widthPx,
                                y = platform.y - heightPx - spacing,
                                seed = seed,
                                widthScale = platformWidthScale
                            )
                            platforms[index] = newPlatform
                        }
                    }

                    for (index in jumpPowerUps.indices) {
                        val spawn = jumpPowerUps[index]
                        if (spawn.collected) continue
                        val dx = playerX - spawn.x
                        val dy = playerY - spawn.y
                        val distance = hypot(dx, dy)
                        if (distance <= avatarRadius + spawn.radius) {
                            jumpPowerUps[index] = spawn.copy(collected = true)
                            val collectedAt = SystemClock.elapsedRealtime()
                            if (spawn.definition.hasContinuousEffect()) {
                                val expiresAt = if (spawn.definition.durationMillis == null) {
                                    Long.MAX_VALUE
                                } else {
                                    collectedAt + spawn.definition.durationMillis
                                }
                                activeJumpEffects += ActiveJumpPowerUp(spawn.definition, expiresAt)
                            }
                            if (spawn.definition.safetyNetCharges > 0) {
                                safetyNetCharges += spawn.definition.safetyNetCharges
                            }
                            if (spawn.definition.scoreBonus != 0) {
                                scoreBonusOffset += spawn.definition.scoreBonus
                            }
                            recentBoost = CollectedBoost(spawn.definition.name, collectedAt + 3200L)
                        }
                    }

                    val climbScore = (heightClimbed / 11.5f).roundToInt()
                    val scaledScore = (climbScore * aggregate.scoreMultiplier).roundToInt()
                    val totalScore = max(bestScore, scaledScore + scoreBonusOffset)
                    if (totalScore > score) {
                        score = totalScore
                    }
                    bestScore = max(bestScore, totalScore)

                    if (score >= requiredScore) {
                        finishRound(true)
                        break
                    }

                    if (playerY - avatarRadius > heightPx) {
                        if (safetyNetCharges > 0) {
                            safetyNetCharges -= 1
                            playerY = heightPx * 0.45f
                            playerVelocityY = jumpImpulse
                            playerVelocityX = 0f
                            recentBoost = CollectedBoost("Safety net engaged", now + 2000L)
                        } else {
                            finishRound(false)
                            break
                        }
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
                                val impulse = horizontalImpulseState.value
                                playerVelocityX = if (tapOffset.x < widthPx / 2f) {
                                    -impulse
                                } else {
                                    impulse
                                }
                            }
                        }
                    }
            ) {
                val widthBoostForDrawing = activeJumpEffects.aggregateJumpEffects().platformWidthMultiplier
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val background = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF120C2B),
                            Color(0xFF06020F)
                        )
                    )
                    drawRect(brush = background, size = size)

                    platforms.forEach { platform ->
                        val actualWidth = platform.width * widthBoostForDrawing
                        val left = platform.x + (platform.width - actualWidth) / 2f
                        drawRoundRect(
                            color = Color(0xFF7C4DFF),
                            topLeft = Offset(left, platform.y),
                            size = Size(actualWidth, platformHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                        )
                    }

                    jumpPowerUps.forEach { spawn ->
                        if (!spawn.collected) {
                            drawCircle(
                                color = spawn.definition.color,
                                radius = spawn.radius,
                                center = Offset(spawn.x, spawn.y)
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.35f),
                                radius = spawn.radius * 0.45f,
                                center = Offset(spawn.x, spawn.y)
                            )
                        }
                    }

                    drawSkillGlyph(
                        attributeType = AttributeType.FOCUS,
                        center = Offset(playerX, playerY),
                        radius = avatarRadius
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    recentBoost?.let {
                        Text(
                            text = "Boon: ${it.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                    val activeLabels = activeJumpEffects.takeIf { it.isNotEmpty() }?.map { effect ->
                        val remaining =
                            if (effect.expiresAt == Long.MAX_VALUE) null
                            else ((effect.expiresAt - clockTick).coerceAtLeast(0L) / 1000f)
                        if (remaining == null) effect.definition.name
                        else "${effect.definition.name} ${"%.1f".format(remaining)}s"
                    }.orEmpty()
                    if (activeLabels.isNotEmpty()) {
                        Text(
                            text = activeLabels.joinToString(separator = "\n"),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    if (safetyNetCharges > 0) {
                        Text(
                            text = "Safety nets: $safetyNetCharges",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                Text(
                    text = "$score / $requiredScore",
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
            scoreLabel = "Height reached (goal $requiredScore)",
            playingHint = "Tap left or right to guide each leap. Gather runes and reach $requiredScore focus.",
            defeatMessage = "You tumbled into the depths.",
            lastResult = state.lastResult,
            selectedDifficulty = state.selectedDifficulty,
            onDifficultySelected = onDifficultySelected,
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
        val stance = state.selectedDifficulty
        val rewardValue = (definition.baseReward * stance.multiplier).roundToInt()
        val attributeLabel = definition.attributeReward.name.lowercase().replaceFirstChar { it.titlecase() }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = CircleShape
                    )
                    .padding(14.dp)
            ) {
                SkillGlyphIcon(
                    attributeType = definition.attributeReward,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
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
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Badge(text = stance.displayName)
            Badge(text = "Reward $rewardValue")
            Badge(text = "Target $attributeLabel")
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
            drawSkillGlyph(
                attributeType = AttributeType.POWER,
                center = bugPosition,
                radius = bugRadiusPx
            )
        }
    }
}

private fun createSpiralSeed(
    widthPx: Float,
    heightPx: Float,
    bugRadiusPx: Float,
    runId: Int,
    difficulty: Difficulty
): SpiralSeed {
    val maxRadius = min(widthPx, heightPx) / 2f - bugRadiusPx * difficulty.bugStartRadiusScale()
    val random = Random(runId * 9176 + 73)
    val baseAngle = when (difficulty) {
        Difficulty.EASY -> (-PI / 2f).toFloat()
        else -> random.nextFloat() * (2f * PI).toFloat()
    }
    val rotations = difficulty.bugSpiralRotations()
    return SpiralSeed(
        initialAngle = baseAngle,
        initialRadius = maxRadius.coerceAtLeast(bugRadiusPx * 3f),
        rotations = rotations
    )
}

private fun spiralPosition(
    seed: SpiralSeed,
    center: Offset,
    progress: Float
): Offset {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val eased = 1f - (1f - clampedProgress) * (1f - clampedProgress)
    val angle = seed.initialAngle + (seed.rotations * eased * (2f * PI).toFloat())
    val radius = seed.initialRadius * (1f - eased * 0.85f)
    val wobble = sin(eased * PI * 4f).toFloat() * seed.initialRadius * 0.05f
    return Offset(
        x = center.x + cos(angle) * (radius + wobble),
        y = center.y + sin(angle) * (radius + wobble)
    )
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
    selectedDifficulty: Difficulty,
    onDifficultySelected: (Difficulty) -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val attributeLabel = definition.attributeReward.name.lowercase().replaceFirstChar { it.titlecase() }
        val rewardLabel = if (selectedDifficulty == Difficulty.LEGENDARY) {
            "universal skill points"
        } else {
            "$attributeLabel sigils"
        }
        Text(
            text = "Difficulty: ${selectedDifficulty.displayName} • Reward ${selectedDifficulty.skillPointReward} $rewardLabel",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        when (phase) {
            GamePhase.Idle -> {
                Text(
                    text = "Trial: ${definition.title}. Rally when you're ready.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Difficulty.values().forEach { difficulty ->
                        FilterChip(
                            selected = difficulty == selectedDifficulty,
                            onClick = { onDifficultySelected(difficulty) },
                            label = { Text(text = difficulty.displayName) },
                            leadingIcon = if (difficulty == selectedDifficulty) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null
                                    )
                                }
                            } else null
                        )
                    }
                }
                Text(
                    text = "Victory yields ${selectedDifficulty.skillPointReward} $rewardLabel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Flag, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Begin trial")
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
                is TrainingGameResult.Victory -> {
                    val attributeLabel = lastResult.attributeType.name.lowercase().replaceFirstChar { it.titlecase() }
                    val rewardParts = buildList {
                        add("Attribute +${lastResult.attributeGain} $attributeLabel")
                        if (lastResult.experienceGain > 0) {
                            add("XP +${lastResult.experienceGain}")
                        }
                        if (lastResult.variantSkillPointGain > 0) {
                            add("${lastResult.variantSkillPointGain} $attributeLabel sigils")
                        }
                        if (lastResult.generalSkillPointGain > 0) {
                            add("${lastResult.generalSkillPointGain} universal skill points")
                        }
                    }
                    "${rewardParts.joinToString(separator = " • ")} (${lastResult.difficulty.displayName})"
                }
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
    seed: Int,
    widthScale: Float = 1f
): JumpPlatform {
    val random = Random(seed)
    val clampedScale = widthScale.coerceIn(0.5f, 1.4f)
    val minWidth = widthPx * 0.12f * clampedScale
    val maxWidth = widthPx * 0.24f * clampedScale
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
    seed: Int,
    gapFactor: Float = 1f
): FlappyPipe {
    val random = Random(seed)
    val clamped = gapFactor.coerceIn(0.6f, 1.4f)
    val minGap = heightPx * 0.28f * clamped
    val maxGap = heightPx * 0.42f * clamped
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

private fun jumpPowerUpCatalog(): List<JumpPowerUpDefinition> = listOf(
    JumpPowerUpDefinition(
        id = 1,
        name = "Featherfall Rune",
        color = Color(0xFF9CDBFF),
        durationMillis = 9_000,
        gravityMultiplier = 0.65f,
        timeScale = 0.85f
    ),
    JumpPowerUpDefinition(
        id = 2,
        name = "Springstep Sigil",
        color = Color(0xFFFFC857),
        durationMillis = 8_000,
        jumpImpulseMultiplier = 1.45f,
        scoreMultiplier = 1.15f
    ),
    JumpPowerUpDefinition(
        id = 3,
        name = "Windlash Charm",
        color = Color(0xFF6EE7B7),
        durationMillis = 7_000,
        horizontalImpulseMultiplier = 1.6f
    ),
    JumpPowerUpDefinition(
        id = 4,
        name = "Beacon Glyph",
        color = Color(0xFFB5A5FF),
        durationMillis = 9_500,
        platformWidthMultiplier = 1.35f,
        scoreMultiplier = 1.1f
    ),
    JumpPowerUpDefinition(
        id = 5,
        name = "Chronicle Bloom",
        color = Color(0xFFFFA1CF),
        durationMillis = 7_500,
        timeScale = 0.78f,
        scoreMultiplier = 1.3f
    ),
    JumpPowerUpDefinition(
        id = 6,
        name = "Mirage Veil",
        color = Color(0xFF34D399),
        safetyNetCharges = 1
    ),
    JumpPowerUpDefinition(
        id = 7,
        name = "Oracle's Insight",
        color = Color(0xFFFB7185),
        scoreBonus = 120
    ),
    JumpPowerUpDefinition(
        id = 8,
        name = "Echo Step",
        color = Color(0xFF38BDF8),
        durationMillis = 6_500,
        jumpImpulseMultiplier = 1.25f,
        horizontalImpulseMultiplier = 1.3f
    ),
    JumpPowerUpDefinition(
        id = 9,
        name = "Aether Coil",
        color = Color(0xFF818CF8),
        durationMillis = 8_000,
        gravityMultiplier = 0.8f,
        platformWidthMultiplier = 1.18f
    ),
    JumpPowerUpDefinition(
        id = 10,
        name = "Focus Tether",
        color = Color(0xFFFACC15),
        durationMillis = 7_200,
        scoreMultiplier = 1.25f,
        safetyNetCharges = 1
    )
)

private fun JumpPowerUpDefinition.hasContinuousEffect(): Boolean {
    if (durationMillis == null) return false
    return jumpImpulseMultiplier != 1f ||
        gravityMultiplier != 1f ||
        horizontalImpulseMultiplier != 1f ||
        platformWidthMultiplier != 1f ||
        timeScale != 1f ||
        scoreMultiplier != 1f
}

private fun Collection<ActiveJumpPowerUp>.aggregateJumpEffects(): JumpEffectAggregate {
    if (isEmpty()) return JumpEffectAggregate()
    var jump = 1f
    var gravity = 1f
    var horizontal = 1f
    var width = 1f
    var time = 1f
    var score = 1f
    for (effect in this) {
        val definition = effect.definition
        jump *= definition.jumpImpulseMultiplier
        gravity *= definition.gravityMultiplier
        horizontal *= definition.horizontalImpulseMultiplier
        width *= definition.platformWidthMultiplier
        time *= definition.timeScale
        score *= definition.scoreMultiplier
    }
    return JumpEffectAggregate(
        jumpImpulseMultiplier = jump,
        gravityMultiplier = gravity,
        horizontalImpulseMultiplier = horizontal,
        platformWidthMultiplier = width,
        timeScale = time,
        scoreMultiplier = score
    )
}

private fun generateJumpPowerUpsForRun(
    catalog: List<JumpPowerUpDefinition>,
    platforms: List<JumpPlatform>,
    spacing: Float,
    widthPx: Float,
    avatarRadius: Float,
    runSeed: Int
): List<JumpPowerUpSpawn> {
    if (catalog.isEmpty() || platforms.isEmpty()) return emptyList()
    val random = Random(runSeed * 8101 + 17)
    return catalog.mapIndexed { index, definition ->
        val platform = platforms[index % platforms.size]
        val band = index / platforms.size
        val verticalOffset = spacing * (band + 0.65f)
        val jitter = random.nextDouble(-platform.width * 0.25, platform.width * 0.25).toFloat()
        val baseX = platform.x + platform.width / 2f + jitter
        val x = baseX.coerceIn(avatarRadius, widthPx - avatarRadius)
        val y = platform.y - verticalOffset
        JumpPowerUpSpawn(
            definition = definition,
            x = x,
            y = y,
            radius = avatarRadius * 0.7f
        )
    }
}

private fun runnerPowerUpCatalog(): List<RunnerPowerUpDefinition> = listOf(
    RunnerPowerUpDefinition(
        id = 1,
        name = "Aegis Core",
        color = Color(0xFF60A5FA),
        shieldCharges = 1
    ),
    RunnerPowerUpDefinition(
        id = 2,
        name = "Phase Thread",
        color = Color(0xFF14B8A6),
        durationMillis = 8_000,
        hitboxMultiplier = 0.7f
    ),
    RunnerPowerUpDefinition(
        id = 3,
        name = "Temporal Anchor",
        color = Color(0xFFF472B6),
        durationMillis = 7_000,
        timeScale = 0.72f
    ),
    RunnerPowerUpDefinition(
        id = 4,
        name = "Stride Ember",
        color = Color(0xFFF97316),
        durationMillis = 6_500,
        speedMultiplier = 1.12f,
        scoreMultiplier = 1.2f
    ),
    RunnerPowerUpDefinition(
        id = 5,
        name = "Siphon Pulse",
        color = Color(0xFF8B5CF6),
        durationMillis = 7_500,
        spawnSpacingMultiplier = 1.35f
    ),
    RunnerPowerUpDefinition(
        id = 6,
        name = "Sentinel Jam",
        color = Color(0xFF0EA5E9),
        scoreBonus = 3
    ),
    RunnerPowerUpDefinition(
        id = 7,
        name = "Lumen Beacon",
        color = Color(0xFFF59E0B),
        durationMillis = 6_000,
        timeScale = 0.8f,
        hitboxMultiplier = 0.85f
    ),
    RunnerPowerUpDefinition(
        id = 8,
        name = "Momentum Surge",
        color = Color(0xFF4ADE80),
        durationMillis = 7_000,
        speedMultiplier = 0.85f
    ),
    RunnerPowerUpDefinition(
        id = 9,
        name = "Pulse Resonator",
        color = Color(0xFFEC4899),
        durationMillis = 6_000,
        scoreMultiplier = 1.5f
    ),
    RunnerPowerUpDefinition(
        id = 10,
        name = "Bulwark Ring",
        color = Color(0xFF38BDF8),
        durationMillis = 8_000,
        hitboxMultiplier = 0.8f,
        shieldCharges = 1
    )
)

private fun RunnerPowerUpDefinition.hasContinuousEffect(): Boolean {
    if (durationMillis == null) return false
    return speedMultiplier != 1f ||
        spawnSpacingMultiplier != 1f ||
        hitboxMultiplier != 1f ||
        timeScale != 1f ||
        scoreMultiplier != 1f
}

private fun Collection<ActiveRunnerPowerUp>.aggregateRunnerEffects(): RunnerEffectAggregate {
    if (isEmpty()) return RunnerEffectAggregate()
    var speed = 1f
    var spacing = 1f
    var hitbox = 1f
    var time = 1f
    var score = 1f
    for (effect in this) {
        val definition = effect.definition
        speed *= definition.speedMultiplier
        spacing *= definition.spawnSpacingMultiplier
        hitbox *= definition.hitboxMultiplier
        time *= definition.timeScale
        score *= definition.scoreMultiplier
    }
    return RunnerEffectAggregate(
        speedMultiplier = speed,
        spawnSpacingMultiplier = spacing,
        hitboxMultiplier = hitbox,
        timeScale = time,
        scoreMultiplier = score
    )
}

private fun generateRunnerPowerUpsForRun(
    catalog: List<RunnerPowerUpDefinition>,
    laneCount: Int,
    laneWidth: Float,
    heightPx: Float,
    runSeed: Int,
    radius: Float
): List<RunnerPowerUpSpawn> {
    if (catalog.isEmpty()) return emptyList()
    val random = Random(runSeed * 4703 + 23)
    val spacing = heightPx * 0.55f
    return catalog.mapIndexed { index, definition ->
        val lane = random.nextInt(laneCount)
        val offsetFactor = random.nextDouble(0.7, 1.25).toFloat()
        val y = -spacing * (index + 1) * offsetFactor
        RunnerPowerUpSpawn(
            definition = definition,
            lane = lane,
            y = y,
            radius = radius,
            collected = false
        )
    }
}

private fun Difficulty.bugHuntDurationFactor(): Double = when (this) {
    Difficulty.EASY -> 1.45
    Difficulty.NORMAL -> 1.0
    Difficulty.HARD -> 0.78
    Difficulty.LEGENDARY -> 0.6
}

private fun Difficulty.bugHuntRadiusScale(): Float = when (this) {
    Difficulty.EASY -> 1.15f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 0.88f
    Difficulty.LEGENDARY -> 0.78f
}

private fun Difficulty.bugVisibleMillis(base: Long): Long = when (this) {
    Difficulty.EASY -> (base * 3.0).toLong()
    Difficulty.NORMAL -> (base * 2.0).toLong()
    Difficulty.HARD -> (base * 1.2).toLong()
    Difficulty.LEGENDARY -> (base * 0.75).toLong().coerceAtLeast(120L)
}

private fun Difficulty.bugHiddenMillis(base: Long): Long = when (this) {
    Difficulty.EASY -> (base * 0.5).toLong().coerceAtLeast(120L)
    Difficulty.NORMAL -> (base * 0.9).toLong()
    Difficulty.HARD -> (base * 1.3).toLong()
    Difficulty.LEGENDARY -> (base * 1.8).toLong()
}

private fun Difficulty.bugSpiralRotations(): Float = when (this) {
    Difficulty.EASY -> 1.6f
    Difficulty.NORMAL -> 2.2f
    Difficulty.HARD -> 2.8f
    Difficulty.LEGENDARY -> 3.3f
}

private fun Difficulty.bugStartRadiusScale(): Float = when (this) {
    Difficulty.EASY -> 2.6f
    Difficulty.NORMAL -> 2.2f
    Difficulty.HARD -> 2.0f
    Difficulty.LEGENDARY -> 1.8f
}

private fun Difficulty.flappySpeedScale(): Float = when (this) {
    Difficulty.EASY -> 0.58f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 1.16f
    Difficulty.LEGENDARY -> 1.28f
}

private fun Difficulty.flappyGapScale(): Float = when (this) {
    Difficulty.EASY -> 1.55f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 0.85f
    Difficulty.LEGENDARY -> 0.72f
}

private fun Difficulty.flappySpacingFactor(): Float = when (this) {
    Difficulty.EASY -> 1.55f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 0.9f
    Difficulty.LEGENDARY -> 0.8f
}

private fun Difficulty.flappyDurationFactor(): Double = when (this) {
    Difficulty.EASY -> 1.05
    Difficulty.NORMAL -> 1.0
    Difficulty.HARD -> 1.08
    Difficulty.LEGENDARY -> 1.18
}

private fun Difficulty.flappyRadiusScale(): Float = when (this) {
    Difficulty.EASY -> 1.2f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 0.9f
    Difficulty.LEGENDARY -> 0.82f
}

private fun Difficulty.flappyVictoryTarget(): Int = when (this) {
    Difficulty.EASY -> 6
    Difficulty.NORMAL -> 9
    Difficulty.HARD -> 12
    Difficulty.LEGENDARY -> 15
}

private fun Difficulty.runnerSpeedScale(): Float = when (this) {
    Difficulty.EASY -> 0.95f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 1.18f
    Difficulty.LEGENDARY -> 1.32f
}

private fun Difficulty.runnerDurationFactor(): Double = when (this) {
    Difficulty.EASY -> 0.85
    Difficulty.NORMAL -> 1.0
    Difficulty.HARD -> 1.12
    Difficulty.LEGENDARY -> 1.25
}

private fun Difficulty.runnerSpawnFactor(): Float = when (this) {
    Difficulty.EASY -> 0.42f
    Difficulty.NORMAL -> 0.35f
    Difficulty.HARD -> 0.28f
    Difficulty.LEGENDARY -> 0.22f
}

private fun Difficulty.runnerAvatarScale(): Float = when (this) {
    Difficulty.EASY -> 1.05f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 0.95f
    Difficulty.LEGENDARY -> 0.9f
}

private fun Difficulty.jumpPlatformCount(): Int = when (this) {
    Difficulty.EASY -> 9
    Difficulty.NORMAL -> 8
    Difficulty.HARD -> 7
    Difficulty.LEGENDARY -> 6
}

private fun Difficulty.jumpScoreMultiplier(): Double = when (this) {
    Difficulty.EASY -> 0.85
    Difficulty.NORMAL -> 1.0
    Difficulty.HARD -> 1.18
    Difficulty.LEGENDARY -> 1.35
}

private fun Difficulty.jumpPlatformWidthScale(): Float = when (this) {
    Difficulty.EASY -> 1.2f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 0.85f
    Difficulty.LEGENDARY -> 0.72f
}

private fun Difficulty.jumpGravityScale(): Float = when (this) {
    Difficulty.EASY -> 0.9f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 1.12f
    Difficulty.LEGENDARY -> 1.28f
}

private fun Difficulty.jumpImpulseScale(): Float = when (this) {
    Difficulty.EASY -> 1.1f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 0.93f
    Difficulty.LEGENDARY -> 0.85f
}

private fun Difficulty.jumpHorizontalScale(): Float = when (this) {
    Difficulty.EASY -> 1.05f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 0.9f
    Difficulty.LEGENDARY -> 0.82f
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
