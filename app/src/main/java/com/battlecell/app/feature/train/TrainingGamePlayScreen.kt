package com.battlecell.app.feature.train

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import kotlin.math.roundToInt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.max
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
                    "Victory! +${event.result.attributeSigilGain + event.result.bonusSigilGain} ${event.result.attributeType.name.lowercase()} sigils"
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

                        TrainingGameType.TETRIS_SIEGE -> TetrisSiegeGame(
                            state = uiState,
                            onSubmitResult = submitResult,
                            onExit = onExit,
                            onResetResult = viewModel::consumeResult,
                            onDifficultySelected = viewModel::setDifficulty
                        )

                        TrainingGameType.RUNE_MATCH -> RuneMatchGame(
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
                              val remainingMillis =
                                  (missionDuration.toLong() - elapsed).coerceAtLeast(0L)
                              val scoreTenths = (remainingMillis / 100.0).roundToInt()
                              onSubmitResult(elapsed, true, scoreTenths)
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

private data class TetrominoInstance(
    val type: TetrominoType,
    val rotation: Int,
    val row: Int,
    val col: Int
)

private enum class TetrominoType { I, O, T, L, J, S, Z }

private data class JumpPowerOrb(
    val id: Int,
    val x: Float,
    val y: Float,
    val type: JumpPowerType
)

private enum class JumpPowerType { FEATHERFALL, RUNESPRING, SKYBRIDGE, SIGILBLOOM, AEGIS }

private data class JumpBuffState(
    val featherUntil: Long = 0L,
    val springUntil: Long = 0L,
    val hasAegis: Boolean = false
)

private data class RunnerPowerOrb(
    val id: Int,
    val lane: Int,
    val y: Float,
    val type: RunnerPowerType
)

private enum class RunnerPowerType { BULWARK, TEMPO_VEIL, BANNER_CHARGE, LANE_PULSE, STRIDE_SURGE }

private data class RunnerBuffState(
    val shieldCharges: Int = 0,
    val tempoUntil: Long = 0L,
    val strideUntil: Long = 0L
)

private data class RuneSelection(val row: Int, val col: Int)

private val tetrominoShapes: Map<TetrominoType, List<List<Pair<Int, Int>>>> = mapOf(
    TetrominoType.I to listOf(
        listOf(0 to -1, 0 to 0, 0 to 1, 0 to 2),
        listOf(-1 to 1, 0 to 1, 1 to 1, 2 to 1)
    ),
    TetrominoType.O to listOf(
        listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1)
    ),
    TetrominoType.T to listOf(
        listOf(0 to -1, 0 to 0, 0 to 1, 1 to 0),
        listOf(-1 to 0, 0 to 0, 1 to 0, 0 to 1),
        listOf(0 to -1, -1 to 0, 0 to 0, 0 to 1),
        listOf(-1 to 0, 0 to 0, 1 to 0, 0 to -1)
    ),
    TetrominoType.L to listOf(
        listOf(0 to -1, 0 to 0, 0 to 1, 1 to -1),
        listOf(-1 to 0, 0 to 0, 1 to 0, 1 to 1),
        listOf(0 to -1, -1 to 1, 0 to 0, 0 to 1),
        listOf(-1 to -1, -1 to 0, 0 to 0, 1 to 0)
    ),
    TetrominoType.J to listOf(
        listOf(0 to -1, 0 to 0, 0 to 1, 1 to 1),
        listOf(-1 to 0, 0 to 0, 1 to 0, 1 to -1),
        listOf(0 to -1, -1 to -1, 0 to 0, 0 to 1),
        listOf(-1 to 0, -1 to 1, 0 to 0, 1 to 0)
    ),
    TetrominoType.S to listOf(
        listOf(0 to 0, 0 to 1, 1 to -1, 1 to 0),
        listOf(-1 to 0, 0 to 0, 0 to 1, 1 to 1)
    ),
    TetrominoType.Z to listOf(
        listOf(0 to -1, 0 to 0, 1 to 0, 1 to 1),
        listOf(-1 to 1, 0 to 0, 0 to 1, 1 to 0)
    )
)

private val tetrisPalette = listOf(
    Color(0xFFC77966),
    Color(0xFF8C4A2F),
    Color(0xFFB86B34),
    Color(0xFF6F4F28),
    Color(0xFF9E5634),
    Color(0xFF7E3E2C),
    Color(0xFFC4873C)
)

private val runePalette = listOf(
    Color(0xFF8E3B12),
    Color(0xFFD97723),
    Color(0xFF5A3E2B),
    Color(0xFF9E6D3A),
    Color(0xFF7C4F2A),
    Color(0xFFB35C3D)
)

private fun TetrominoType.cells(rotation: Int): List<Pair<Int, Int>> {
    val variants = tetrominoShapes[this] ?: emptyList()
    if (variants.isEmpty()) return emptyList()
    return variants[rotation % variants.size]
}

private fun TetrominoType.previewBounds(): Pair<IntRange, IntRange> {
    val points = cells(0)
    val rows = points.minOf { it.first }..points.maxOf { it.first }
    val cols = points.minOf { it.second }..points.maxOf { it.second }
    return rows to cols
}

private fun TetrominoType.color(): Color = tetrisPalette[ordinal % tetrisPalette.size]

private fun JumpPowerType.color(): Color = when (this) {
    JumpPowerType.FEATHERFALL -> Color(0xFFB26745)
    JumpPowerType.RUNESPRING -> Color(0xFFE2A76F)
    JumpPowerType.SKYBRIDGE -> Color(0xFF8B5E3C)
    JumpPowerType.SIGILBLOOM -> Color(0xFFCA8243)
    JumpPowerType.AEGIS -> Color(0xFFAA6F3D)
}

private fun RunnerPowerType.color(): Color = when (this) {
    RunnerPowerType.BULWARK -> Color(0xFFAE7C45)
    RunnerPowerType.TEMPO_VEIL -> Color(0xFF7F4B24)
    RunnerPowerType.BANNER_CHARGE -> Color(0xFFD08752)
    RunnerPowerType.LANE_PULSE -> Color(0xFF9D5C38)
    RunnerPowerType.STRIDE_SURGE -> Color(0xFFB86B34)
}

private fun randomTetrominoType(): TetrominoType = TetrominoType.values().random()

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
    val gateTarget = (behavior.targetScore.takeIf { it > 0 } ?: 16).coerceAtLeast(8)

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
              val pipeWidth = with(density) { 52.dp.toPx() }
              val pipeSpacing = widthPx * (0.45f * difficulty.flappySpacingFactor())
            val missionDuration =
                ((behavior.totalDurationMillis.takeIf { it > 0 } ?: 45_000) * difficulty.flappyDurationFactor()).roundToInt()
              val gapFactor = difficulty.flappyGapScale()
              val speedScale = difficulty.flappySpeedScale()
              val gravity = 1200f * speedScale
              val flapImpulse = -520f * speedScale
              val pipeSpeed = (widthPx / 2.8f) * speedScale
              val collisionRadius = birdRadiusPx * 0.78f

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

                    if (elapsedMillis >= missionDuration && localScore < gateTarget) {
                        finishRound(false)
                        break
                    }

                      birdVelocity += gravity * deltaSeconds
                      birdY += birdVelocity * deltaSeconds

                      var defeated = birdY - collisionRadius <= 0f || birdY + collisionRadius >= heightPx

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
                                  birdX + collisionRadius > newX && birdX - collisionRadius < newX + pipeWidth
                              if (overlapsHorizontally) {
                                  val gapHalf = pipe.gapHeight / 2f
                                  val topLimit = pipe.gapCenter - gapHalf
                                  val bottomLimit = pipe.gapCenter + gapHalf
                                  if (birdY - collisionRadius < topLimit || birdY + collisionRadius > bottomLimit) {
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

                    if (localScore >= gateTarget) {
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
            playingHint = "Tap to keep altitude. Clear $gateTarget pennant gates before time expires.",
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

    var gamePhase by remember(definition.id + "_runner") { mutableStateOf(GamePhase.Idle) }
    var runId by remember(definition.id + "_runner") { mutableIntStateOf(0) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var score by remember { mutableIntStateOf(0) }
    var playerLane by remember { mutableIntStateOf(1) }
    val obstacles = remember { mutableStateListOf<RunnerObstacle>() }
    val runnerPowerUps = remember(definition.id + "_runner_orbs") { mutableStateListOf<RunnerPowerOrb>() }
    var runnerBuffState by remember(definition.id + "_runner_buff") { mutableStateOf(RunnerBuffState()) }
    var lastRunnerOrbSpawn by remember(definition.id + "_runner_spawn") { mutableLongStateOf(0L) }
    var runnerBuffClock by remember(definition.id + "_runner_clock") { mutableLongStateOf(0L) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Header(definition = definition, state = state, elapsedMillis = elapsedMillis)
        val buffTick = runnerBuffClock
        val runnerBuffLabels = remember(buffTick, runnerBuffState) {
            val now = buffTick
            buildList {
                if (runnerBuffState.shieldCharges > 0) add("Bulwark x${runnerBuffState.shieldCharges}")
                if (now < runnerBuffState.tempoUntil) add("Tempo veil")
                if (now < runnerBuffState.strideUntil) add("Stride surge")
            }
        }
        PowerUpTicker(labels = runnerBuffLabels)

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
            val avatarRadius = with(density) { 24.dp.toPx() * difficulty.runnerAvatarScale() }
            val obstacleHeight = with(density) { 46.dp.toPx() }
            val obstacleWidth = laneWidth * 0.6f
            val missionDuration = ((behavior.totalDurationMillis.takeIf { it > 0 } ?: 60_000) * difficulty.runnerDurationFactor()).roundToInt()
            val spawnThreshold = heightPx * difficulty.runnerSpawnFactor()

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
                    runnerPowerUps.clear()
                    runnerBuffState = RunnerBuffState()
                    lastRunnerOrbSpawn = 0L
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

                    if (elapsedMillis >= missionDuration) {
                        finishRound(true)
                        break
                    }

                    val baseSpeed = ((heightPx / 2.6f) + (elapsedMillis / 4000f)) * difficulty.runnerSpeedScale()
                    val tempoFactor = if (now < runnerBuffState.tempoUntil) 0.78f else 1f
                    val speed = baseSpeed * tempoFactor

                    for (index in obstacles.indices) {
                        val obstacle = obstacles[index]
                        val newY = obstacle.y + speed * deltaSeconds
                        var newPassed = obstacle.passed

                        val collision = obstacle.lane == playerLane &&
                                newY + obstacleHeight > playerY - avatarRadius &&
                                newY < playerY + avatarRadius

                        if (collision) {
                            if (runnerBuffState.shieldCharges > 0) {
                                runnerBuffState = runnerBuffState.copy(shieldCharges = runnerBuffState.shieldCharges - 1)
                            } else {
                                finishRound(false)
                                return@LaunchedEffect
                            }
                        }

                        if (!obstacle.passed && newY > playerY + avatarRadius) {
                            newPassed = true
                            localScore += 1
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

                    if (runnerPowerUps.size < 2 && now - lastRunnerOrbSpawn > 4_500L) {
                        val orbLane = Random.nextInt(laneCount)
                        runnerPowerUps += RunnerPowerOrb(
                            id = runId * 997 + runnerPowerUps.size,
                            lane = orbLane,
                            y = -60f,
                            type = RunnerPowerType.values().random()
                        )
                        lastRunnerOrbSpawn = now
                    }

                    for (i in runnerPowerUps.indices.reversed()) {
                        val orb = runnerPowerUps[i]
                        val newY = orb.y + speed * 0.75f * deltaSeconds
                        if (newY > heightPx + 40f) {
                            runnerPowerUps.removeAt(i)
                            continue
                        }
                        runnerPowerUps[i] = orb.copy(y = newY)
                        val playerCenterLane = playerLane
                        if (orb.lane == playerCenterLane && abs(newY - playerY) < avatarRadius + 28f) {
                            runnerPowerUps.removeAt(i)
                            when (orb.type) {
                                RunnerPowerType.BULWARK -> runnerBuffState =
                                    runnerBuffState.copy(shieldCharges = (runnerBuffState.shieldCharges + 1).coerceAtMost(3))
                                RunnerPowerType.TEMPO_VEIL -> runnerBuffState =
                                    runnerBuffState.copy(tempoUntil = now + 5_000L)
                                RunnerPowerType.BANNER_CHARGE -> localScore += 2
                                RunnerPowerType.LANE_PULSE -> {
                                    if (obstacles.isNotEmpty()) {
                                        obstacles.removeAt(0)
                                    }
                                }
                                RunnerPowerType.STRIDE_SURGE -> runnerBuffState =
                                    runnerBuffState.copy(strideUntil = now + 4_000L)
                            }
                        }
                    }

                    delay(16)
                }
            }

            LaunchedEffect(gamePhase, runId, "runnerBuffTicker") {
                if (gamePhase != GamePhase.Playing) {
                    runnerBuffClock = SystemClock.elapsedRealtime()
                    return@LaunchedEffect
                }
                while (isActive) {
                    runnerBuffClock = SystemClock.elapsedRealtime()
                    delay(500L)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(gamePhase, runId) {
                          detectTapGestures { offset ->
                              if (gamePhase == GamePhase.Playing) {
                                  val tappedLane = (offset.x / laneWidth).toInt().coerceIn(0, laneCount - 1)
                                  val strideActive = SystemClock.elapsedRealtime() < runnerBuffState.strideUntil
                                  when {
                                      tappedLane < playerLane -> playerLane = if (strideActive) tappedLane else (playerLane - 1).coerceAtLeast(0)
                                      tappedLane > playerLane -> playerLane = if (strideActive) tappedLane else (playerLane + 1).coerceAtMost(laneCount - 1)
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
                      runnerPowerUps.forEach { orb ->
                          val laneCenter = laneWidth * (orb.lane + 0.5f)
                          drawCircle(
                              color = orb.type.color(),
                              radius = avatarRadius * 0.6f,
                              center = Offset(laneCenter, orb.y)
                          )
                          drawCircle(
                              color = Color.White.copy(alpha = 0.5f),
                              radius = avatarRadius * 0.25f,
                              center = Offset(laneCenter - 6f, orb.y - 6f)
                          )
                      }

                      drawSkillGlyph(
                          attributeType = AttributeType.ENDURANCE,
                          center = Offset(playerCenterX, playerY),
                          radius = avatarRadius
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
            playingHint = "Tap the lanes to weave past the sentry constructs.",
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
private fun TetrisSiegeGame(
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
    val rows = max(12, behavior.boardHeight.takeIf { it > 0 } ?: 18)
    val cols = max(8, behavior.boardWidth.takeIf { it > 0 } ?: 10)
    val baseTarget = behavior.targetScore.takeIf { it > 0 } ?: 12
    val targetLines = (baseTarget * difficulty.tetrisLineFactor()).roundToInt().coerceAtLeast(6)
    val baseInterval = behavior.totalDurationMillis.takeIf { it > 0 } ?: 700
    val dropInterval = (baseInterval / difficulty.tetrisSpeedMultiplier()).roundToInt().coerceAtLeast(180)

    var gamePhase by remember(definition.id + "_tetris") { mutableStateOf(GamePhase.Idle) }
    var runId by remember(definition.id + "_tetris") { mutableIntStateOf(0) }
    val board = remember(definition.id + "_tetris") {
        mutableStateListOf<Int>().apply {
            repeat(rows * cols) { add(0) }
        }
    }
    var currentPiece by remember(definition.id + "_tetris") { mutableStateOf<TetrominoInstance?>(null) }
    val selectionPool = remember(definition.id + "_tetris") {
        mutableStateListOf<TetrominoType>().apply {
            repeat(3) { add(randomTetrominoType()) }
        }
    }
    var pendingSelection by remember(definition.id + "_tetris") { mutableStateOf(false) }
    var linesCleared by remember(definition.id + "_tetris") { mutableIntStateOf(0) }
    var elapsedMillis by remember(definition.id + "_tetris") { mutableLongStateOf(0L) }
    var startTimestamp by remember(definition.id + "_tetris") { mutableLongStateOf(0L) }

    fun boardIndex(row: Int, col: Int) = row * cols + col

    fun ensureSelectionPool() {
        while (selectionPool.size < 3) {
            selectionPool.add(randomTetrominoType())
        }
    }

    fun resetState() {
        for (i in board.indices) {
            board[i] = 0
        }
        selectionPool.clear()
        repeat(3) { selectionPool.add(randomTetrominoType()) }
        currentPiece = null
        pendingSelection = false
        linesCleared = 0
        elapsedMillis = 0L
    }

    fun pieceCells(piece: TetrominoInstance): List<Pair<Int, Int>> =
        piece.type.cells(piece.rotation).map { (dr, dc) -> piece.row + dr to piece.col + dc }

    fun canPlace(piece: TetrominoInstance): Boolean {
        val cells = pieceCells(piece)
        for ((row, col) in cells) {
            if (col !in 0 until cols) return false
            if (row >= rows) return false
            if (row >= 0 && board[boardIndex(row, col)] != 0) return false
        }
        return true
    }

    fun spawnPiece(type: TetrominoType): Boolean {
        val piece = TetrominoInstance(
            type = type,
            rotation = 0,
            row = -1,
            col = (cols / 2) - 1
        )
        if (!canPlace(piece)) return false
        currentPiece = piece
        pendingSelection = false
        return true
    }

    fun handleDefeat() {
        val elapsed = elapsedMillis
        onSubmitResult(elapsed, false, linesCleared)
        gamePhase = GamePhase.Defeat
    }

    fun handleVictory() {
        val elapsed = elapsedMillis
        onSubmitResult(elapsed, true, linesCleared)
        gamePhase = GamePhase.Victory
    }

    fun lockPiece() {
        val piece = currentPiece ?: return
        val cells = pieceCells(piece)
        var overflow = false
        cells.forEach { (row, col) ->
            if (row < 0) {
                overflow = true
            } else {
                board[boardIndex(row, col)] = piece.type.ordinal + 1
            }
        }
        currentPiece = null
        if (overflow) {
            handleDefeat()
            return
        }
        var cleared = 0
        var row = rows - 1
        while (row >= 0) {
            val fullRow = (0 until cols).all { col -> board[boardIndex(row, col)] != 0 }
            if (fullRow) {
                cleared++
                for (r in row downTo 1) {
                    for (c in 0 until cols) {
                        board[boardIndex(r, c)] = board[boardIndex(r - 1, c)]
                    }
                }
                for (c in 0 until cols) {
                    board[c] = 0
                }
                row++
            }
            row--
        }
        linesCleared += cleared
        if (linesCleared >= targetLines) {
            handleVictory()
        } else {
            pendingSelection = true
        }
    }

    fun tryMove(deltaRow: Int, deltaCol: Int): Boolean {
        val piece = currentPiece ?: return false
        val shifted = piece.copy(row = piece.row + deltaRow, col = piece.col + deltaCol)
        return if (canPlace(shifted)) {
            currentPiece = shifted
            true
        } else {
            false
        }
    }

    fun rotate(clockwise: Boolean) {
        val piece = currentPiece ?: return
        val rotations = tetrominoShapes[piece.type]?.size ?: 1
        if (rotations <= 1) return
        val newRotation = if (clockwise) {
            (piece.rotation + 1) % rotations
        } else {
            (piece.rotation - 1 + rotations) % rotations
        }
        val rotated = piece.copy(rotation = newRotation)
        if (canPlace(rotated)) {
            currentPiece = rotated
        }
    }

    fun hardDrop() {
        while (tryMove(1, 0)) {
            // drop until collision
        }
        lockPiece()
    }

    fun handleSelection(index: Int) {
        ensureSelectionPool()
        if (!pendingSelection || gamePhase != GamePhase.Playing) return
        val type = selectionPool.getOrNull(index) ?: return
        val success = spawnPiece(type)
        selectionPool[index] = randomTetrominoType()
        if (!success) {
            handleDefeat()
        }
    }

    LaunchedEffect(gamePhase, runId) {
        if (gamePhase != GamePhase.Playing) return@LaunchedEffect
        startTimestamp = SystemClock.elapsedRealtime()
        ensureSelectionPool()
        while (isActive && gamePhase == GamePhase.Playing) {
            val now = SystemClock.elapsedRealtime()
            elapsedMillis = now - startTimestamp
            if (currentPiece == null) {
                pendingSelection = true
            } else {
                delay(dropInterval.toLong())
                if (!tryMove(1, 0)) {
                    lockPiece()
                }
            }
            delay(8)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Header(definition = definition, state = state, elapsedMillis = elapsedMillis)

        PowerUpTicker(
            labels = if (pendingSelection && gamePhase == GamePhase.Playing) {
                listOf("Select the next wardstone")
            } else emptyList()
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1C120B))
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val cellSize = min(widthPx / cols.toFloat(), heightPx / rows.toFloat())

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                drawRect(
                    color = Color(0xFF1C120B),
                    size = size
                )

                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        val value = board[boardIndex(row, col)]
                        if (value > 0) {
                            drawRect(
                                color = tetrisPalette[(value - 1) % tetrisPalette.size],
                                topLeft = Offset(col * cellSize, row * cellSize),
                                size = Size(cellSize - 3f, cellSize - 3f)
                            )
                        }
                    }
                }

                currentPiece?.let { piece ->
                    pieceCells(piece).forEach { (row, col) ->
                        if (row in 0 until rows && col in 0 until cols) {
                            drawRect(
                                color = piece.type.color(),
                                topLeft = Offset(col * cellSize, row * cellSize),
                                size = Size(cellSize - 3f, cellSize - 3f)
                            )
                        }
                    }
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Wardstone queue",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                selectionPool.forEachIndexed { index, type ->
                    TetrominoPreview(
                        type = type,
                        enabled = pendingSelection && gamePhase == GamePhase.Playing,
                        onSelected = { handleSelection(index) }
                    )
                }
            }
        }

        ControlPanel(
            phase = gamePhase,
            definition = definition,
            elapsedMillis = elapsedMillis,
            score = linesCleared,
            scoreLabel = "Lines sealed",
            playingHint = "Tap a wardstone to send it into the breach. Use the controls to steer and rotate.",
            defeatMessage = "The barricade overflowed with broken stones.",
            lastResult = state.lastResult,
            selectedDifficulty = state.selectedDifficulty,
            onDifficultySelected = onDifficultySelected,
            onStart = {
                resetState()
                onResetResult()
                gamePhase = GamePhase.Playing
                runId++
            },
            onRetry = {
                resetState()
                onResetResult()
                gamePhase = GamePhase.Idle
                runId++
            },
            onExit = onExit
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (gamePhase == GamePhase.Playing) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { tryMove(0, -1) },
                    enabled = currentPiece != null && !pendingSelection && gamePhase == GamePhase.Playing,
                    modifier = Modifier.weight(1f)
                ) { Text("Left") }
                Button(
                    onClick = { rotate(true) },
                    enabled = currentPiece != null && !pendingSelection && gamePhase == GamePhase.Playing,
                    modifier = Modifier.weight(1f)
                ) { Text("Rotate") }
                Button(
                    onClick = { tryMove(0, 1) },
                    enabled = currentPiece != null && !pendingSelection && gamePhase == GamePhase.Playing,
                    modifier = Modifier.weight(1f)
                ) { Text("Right") }
                Button(
                    onClick = { if (currentPiece != null && !pendingSelection) hardDrop() },
                    enabled = currentPiece != null && !pendingSelection && gamePhase == GamePhase.Playing,
                    modifier = Modifier.weight(1f)
                ) { Text("Drop") }
            }
        }
    }
}

@Composable
private fun RuneMatchGame(
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
    val rows = max(6, behavior.boardHeight.takeIf { it > 0 } ?: 7)
    val cols = max(6, behavior.boardWidth.takeIf { it > 0 } ?: 7)
    val baseTarget = behavior.targetScore.takeIf { it > 0 } ?: 800
    val targetScore = (baseTarget * difficulty.matchScoreFactor()).roundToInt()
    val baseMoves = behavior.moveLimit.takeIf { it > 0 } ?: 24
    val moveLimit = max(6, (baseMoves * difficulty.matchMoveFactor()).roundToInt())

    var gamePhase by remember(definition.id + "_rune") { mutableStateOf(GamePhase.Idle) }
    var runId by remember(definition.id + "_rune") { mutableIntStateOf(0) }
    val board = remember(definition.id + "_rune") {
        mutableStateListOf<Int>().apply {
            repeat(rows * cols) { add(Random.nextInt(runePalette.size)) }
        }
    }
    var selection by remember(definition.id + "_rune") { mutableStateOf<RuneSelection?>(null) }
    var score by remember(definition.id + "_rune") { mutableIntStateOf(0) }
    var movesUsed by remember(definition.id + "_rune") { mutableIntStateOf(0) }
    var elapsedMillis by remember(definition.id + "_rune") { mutableLongStateOf(0L) }
    var startTimestamp by remember(definition.id + "_rune") { mutableLongStateOf(0L) }

    fun boardIndex(row: Int, col: Int) = row * cols + col

    fun randomRune(): Int = Random.nextInt(runePalette.size)

    fun resetBoard() {
        for (i in board.indices) {
            board[i] = randomRune()
        }
        selection = null
        score = 0
        movesUsed = 0
        elapsedMillis = 0L
    }

    fun swapCells(a: RuneSelection, b: RuneSelection) {
        val idxA = boardIndex(a.row, a.col)
        val idxB = boardIndex(b.row, b.col)
        val temp = board[idxA]
        board[idxA] = board[idxB]
        board[idxB] = temp
    }

    fun findMatches(): Set<Int> {
        val matches = mutableSetOf<Int>()
        for (row in 0 until rows) {
            var col = 0
            while (col < cols) {
                val start = col
                val value = board[boardIndex(row, col)]
                col++
                while (col < cols && board[boardIndex(row, col)] == value) {
                    col++
                }
                if (col - start >= 3) {
                    for (c in start until col) {
                        matches.add(boardIndex(row, c))
                    }
                }
            }
        }
        for (col in 0 until cols) {
            var row = 0
            while (row < rows) {
                val start = row
                val value = board[boardIndex(row, col)]
                row++
                while (row < rows && board[boardIndex(row, col)] == value) {
                    row++
                }
                if (row - start >= 3) {
                    for (r in start until row) {
                        matches.add(boardIndex(r, col))
                    }
                }
            }
        }
        return matches
    }

    fun collapseBoard() {
        for (col in 0 until cols) {
            var writeRow = rows - 1
            for (row in rows - 1 downTo 0) {
                val value = board[boardIndex(row, col)]
                if (value >= 0) {
                    board[boardIndex(writeRow, col)] = value
                    if (writeRow != row) {
                        board[boardIndex(row, col)] = -1
                    }
                    writeRow--
                }
            }
            while (writeRow >= 0) {
                board[boardIndex(writeRow, col)] = randomRune()
                writeRow--
            }
        }
    }

    fun resolveMatches(): Boolean {
        val matches = findMatches()
        if (matches.isEmpty()) return false
        matches.forEach { board[it] = -1 }
        collapseBoard()
        score += matches.size * 10
        return true
    }

    fun handleSwap(target: RuneSelection) {
        val first = selection ?: run {
            selection = target
            return
        }
        if (first == target) {
            selection = null
            return
        }
        val adjacent = (first.row == target.row && abs(first.col - target.col) == 1) ||
                (first.col == target.col && abs(first.row - target.row) == 1)
        if (!adjacent) {
            selection = target
            return
        }
        swapCells(first, target)
        if (resolveMatches()) {
            movesUsed++
            while (resolveMatches()) {
                // continue clearing cascades
            }
            if (score >= targetScore) {
                onSubmitResult(elapsedMillis, true, score)
                gamePhase = GamePhase.Victory
            } else if (movesUsed >= moveLimit) {
                onSubmitResult(elapsedMillis, false, score)
                gamePhase = GamePhase.Defeat
            }
        } else {
            swapCells(first, target)
        }
        selection = null
    }

    LaunchedEffect(gamePhase, runId) {
        if (gamePhase != GamePhase.Playing) return@LaunchedEffect
        startTimestamp = SystemClock.elapsedRealtime()
        while (isActive && gamePhase == GamePhase.Playing) {
            elapsedMillis = SystemClock.elapsedRealtime() - startTimestamp
            delay(200L)
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
                .background(Color(0xFF1E140C))
        ) {
            val cellSizePx = with(density) { (maxWidth / cols.toFloat()).toPx() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(gamePhase, runId, selection, cellSizePx) {
                        detectTapGestures { offset ->
                            if (gamePhase != GamePhase.Playing) return@detectTapGestures
                            val col = (offset.x / cellSizePx).toInt().coerceIn(0, cols - 1)
                            val row = (offset.y / cellSizePx).toInt().coerceIn(0, rows - 1)
                            handleSwap(RuneSelection(row, col))
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = Color(0xFF1E140C), size = size)
                    for (row in 0 until rows) {
                        for (col in 0 until cols) {
                            val value = board[boardIndex(row, col)]
                            val color = if (value >= 0) runePalette[value % runePalette.size] else Color.Transparent
                            drawRect(
                                color = color,
                                topLeft = Offset(col * cellSizePx, row * cellSizePx),
                                size = Size(cellSizePx - 6f, cellSizePx - 6f),
                                style = if (selection?.row == row && selection?.col == col) {
                                    Stroke(width = 4f)
                                } else {
                                    Stroke(width = 0f)
                                }
                            )
                        }
                    }
                }
            }
        }

        ControlPanel(
            phase = gamePhase,
            definition = definition,
            elapsedMillis = elapsedMillis,
            score = score,
            scoreLabel = "Score",
            playingHint = "Swap adjacent runes to harvest $targetScore essence within $moveLimit moves.",
            defeatMessage = "The sandglass ran dry before enough runes were gathered.",
            lastResult = state.lastResult,
            selectedDifficulty = state.selectedDifficulty,
            onDifficultySelected = onDifficultySelected,
            onStart = {
                resetBoard()
                onResetResult()
                var safeBoard = 0
                while (safeBoard < 3 && resolveMatches()) {
                    safeBoard++
                }
                gamePhase = GamePhase.Playing
                runId++
            },
            onRetry = {
                resetBoard()
                onResetResult()
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
    val jumpPowerUps = remember(definition.id + "_jump_orbs") { mutableStateListOf<JumpPowerOrb>() }
    var jumpBuffState by remember(definition.id + "_jump_buff") { mutableStateOf(JumpBuffState()) }
    var lastJumpPowerSpawn by remember(definition.id + "_jump_spawn") { mutableLongStateOf(0L) }
    var buffClock by remember(definition.id + "_jump_clock") { mutableLongStateOf(0L) }

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
            val requiredScore = kotlin.math.max(
                300,
                (baseTarget * difficulty.jumpScoreMultiplier()).roundToInt()
            )
            val platformWidthScale = difficulty.jumpPlatformWidthScale()

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
                            seed = random.nextInt(),
                            widthScale = platformWidthScale
                        )
                    }
                    playerX = widthPx / 2f
                    playerY = heightPx * 0.3f
                    playerVelocityX = 0f
                    playerVelocityY = 0f
                    heightClimbed = 0f
                    score = 0
                jumpPowerUps.clear()
                jumpBuffState = JumpBuffState()
                lastJumpPowerSpawn = 0L
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
                val baseGravity = 2100f * difficulty.jumpGravityScale()
                val baseJumpImpulse = -1350f * difficulty.jumpImpulseScale()
                val horizontalImpulse = (widthPx / 1.8f) * difficulty.jumpHorizontalScale()
                val friction = 0.85f
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

                    val gravityBuff = if (now < jumpBuffState.featherUntil) 0.7f else 1f
                    val jumpBuff = if (now < jumpBuffState.springUntil) 1.2f else 1f
                    val gravity = baseGravity * gravityBuff
                    val jumpImpulse = baseJumpImpulse * jumpBuff

                    if (jumpPowerUps.size < 3 && now - lastJumpPowerSpawn > 4000L) {
                        val anchor = platforms.randomOrNull()
                        if (anchor != null) {
                            jumpPowerUps += JumpPowerOrb(
                                id = runId * 997 + jumpPowerUps.size,
                                x = anchor.x + anchor.width / 2f,
                                y = anchor.y - 28f,
                                type = JumpPowerType.values().random()
                            )
                            lastJumpPowerSpawn = now
                        }
                    }

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

                    val orbIterator = jumpPowerUps.listIterator()
                    while (orbIterator.hasNext()) {
                        val orb = orbIterator.next()
                        val dx = playerX - orb.x
                        val dy = playerY - orb.y
                        val pickupRadius = avatarRadius + 22f
                        if (dx * dx + dy * dy <= pickupRadius * pickupRadius) {
                            orbIterator.remove()
                            when (orb.type) {
                                JumpPowerType.FEATHERFALL -> jumpBuffState =
                                    jumpBuffState.copy(featherUntil = now + 6_000L)
                                JumpPowerType.RUNESPRING -> jumpBuffState =
                                    jumpBuffState.copy(springUntil = now + 6_000L)
                                JumpPowerType.SKYBRIDGE -> {
                                    val bridgeWidth = widthPx * 0.35f
                                    val newPlatform = JumpPlatform(
                                        x = (orb.x - bridgeWidth / 2f).coerceIn(0f, widthPx - bridgeWidth),
                                        y = (playerY - 160f).coerceAtLeast(40f),
                                        width = bridgeWidth
                                    )
                                    platforms.add(newPlatform)
                                }
                                JumpPowerType.SIGILBLOOM -> score += 60
                                JumpPowerType.AEGIS -> jumpBuffState = jumpBuffState.copy(hasAegis = true)
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
                                seed = seed,
                                widthScale = platformWidthScale
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
                        if (jumpBuffState.hasAegis) {
                            jumpBuffState = jumpBuffState.copy(hasAegis = false)
                            playerY = heightPx * 0.3f
                            playerVelocityY = 0f
                            playerVelocityX = 0f
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

                    jumpPowerUps.forEach { orb ->
                        drawCircle(
                            color = orb.type.color(),
                            radius = 18f,
                            center = Offset(orb.x, orb.y)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.6f),
                            radius = 6f,
                            center = Offset(orb.x - 4f, orb.y - 4f)
                        )
                    }

                      drawSkillGlyph(
                          attributeType = AttributeType.FOCUS,
                          center = Offset(playerX, playerY),
                          radius = avatarRadius
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
            playingHint = "Tap left or right to guide each leap. Keep climbing the shifting pillars.",
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
        Text(
            text = "Difficulty: ${selectedDifficulty.displayName} • Reward ${selectedDifficulty.skillPointReward} $attributeLabel sigils",
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
                    text = "Victory yields ${selectedDifficulty.skillPointReward} $attributeLabel sigils.",
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
                    val totalSigils = lastResult.attributeSigilGain + lastResult.bonusSigilGain
                    val breakdown = when {
                        lastResult.attributeSigilGain > 0 && lastResult.bonusSigilGain > 0 ->
                            " (${lastResult.attributeSigilGain} earned + ${lastResult.bonusSigilGain} stance bonus)"
                        lastResult.attributeSigilGain > 0 ->
                            " (${lastResult.attributeSigilGain} earned)"
                        lastResult.bonusSigilGain > 0 ->
                            " (${lastResult.bonusSigilGain} stance bonus)"
                        else -> ""
                    }
                    "Sigils +$totalSigils $attributeLabel • ${lastResult.difficulty.displayName}$breakdown"
                }
                else -> "Sigils gained."
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

@Composable
private fun PowerUpTicker(
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    if (labels.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun TetrominoPreview(
    type: TetrominoType,
    enabled: Boolean,
    onSelected: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .let { base ->
                if (enabled) {
                    base.clickable { onSelected() }
                } else {
                    base
                }
            }
            .padding(6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellSize = size.width / 4f
            val (rowBounds, colBounds) = type.previewBounds()
            val rowOffset = -rowBounds.first
            val colOffset = -colBounds.first
            type.cells(0).forEach { (row, col) ->
                val displayRow = row + rowOffset
                val displayCol = col + colOffset
                drawRect(
                    color = type.color(),
                    topLeft = Offset(displayCol * cellSize, displayRow * cellSize),
                    size = Size(cellSize - 2f, cellSize - 2f)
                )
            }
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
    Difficulty.EASY -> 0.72f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 1.22f
    Difficulty.LEGENDARY -> 1.36f
}

private fun Difficulty.flappyGapScale(): Float = when (this) {
    Difficulty.EASY -> 1.35f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 0.82f
    Difficulty.LEGENDARY -> 0.68f
}

private fun Difficulty.flappySpacingFactor(): Float = when (this) {
    Difficulty.EASY -> 1.35f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 0.88f
    Difficulty.LEGENDARY -> 0.78f
}

private fun Difficulty.flappyDurationFactor(): Double = when (this) {
    Difficulty.EASY -> 1.35
    Difficulty.NORMAL -> 1.0
    Difficulty.HARD -> 1.15
    Difficulty.LEGENDARY -> 1.28
}

private fun Difficulty.flappyRadiusScale(): Float = when (this) {
    Difficulty.EASY -> 1.12f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 0.9f
    Difficulty.LEGENDARY -> 0.82f
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

private fun Difficulty.tetrisLineFactor(): Double = when (this) {
    Difficulty.EASY -> 0.8
    Difficulty.NORMAL -> 1.0
    Difficulty.HARD -> 1.25
    Difficulty.LEGENDARY -> 1.45
}

private fun Difficulty.tetrisSpeedMultiplier(): Double = when (this) {
    Difficulty.EASY -> 0.85
    Difficulty.NORMAL -> 1.0
    Difficulty.HARD -> 1.25
    Difficulty.LEGENDARY -> 1.45
}

private fun Difficulty.matchScoreFactor(): Double = when (this) {
    Difficulty.EASY -> 0.8
    Difficulty.NORMAL -> 1.0
    Difficulty.HARD -> 1.25
    Difficulty.LEGENDARY -> 1.4
}

private fun Difficulty.matchMoveFactor(): Double = when (this) {
    Difficulty.EASY -> 1.2
    Difficulty.NORMAL -> 1.0
    Difficulty.HARD -> 0.9
    Difficulty.LEGENDARY -> 0.75
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
