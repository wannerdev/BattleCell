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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.roundToInt
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

private interface TrainingPowerUpDescriptor {
    val id: String
    val label: String
    val summary: String
}

private data class OracleModifiers(
    val gravityMultiplier: Float = 1f,
    val jumpImpulseMultiplier: Float = 1f,
    val horizontalImpulseMultiplier: Float = 1f,
    val platformWidthBonus: Float = 0f,
    val platformCountBonus: Int = 0,
    val frictionMultiplier: Float = 1f,
    val scoreGoalReduction: Float = 0f,
    val timeDilation: Float = 1f,
    val landingForgivenessMultiplier: Float = 1f,
    val safetyCharges: Int = 0
)

private data class OraclePowerUp(
    override val id: String,
    override val label: String,
    override val summary: String,
    val apply: (OracleModifiers) -> OracleModifiers
) : TrainingPowerUpDescriptor

private val ORACLE_POWER_UPS = listOf(
    OraclePowerUp(
        id = "featherfall",
        label = "Featherfall Veil",
        summary = "Gravity eased by 25%.",
        apply = { modifiers ->
            modifiers.copy(gravityMultiplier = modifiers.gravityMultiplier * 0.75f)
        }
    ),
    OraclePowerUp(
        id = "springstep",
        label = "Springstep Greaves",
        summary = "Jump impulse amplified by 20%.",
        apply = { modifiers ->
            modifiers.copy(jumpImpulseMultiplier = modifiers.jumpImpulseMultiplier * 1.2f)
        }
    ),
    OraclePowerUp(
        id = "skydance",
        label = "Skydance Anklets",
        summary = "Horizontal dash extends by 30%.",
        apply = { modifiers ->
            modifiers.copy(horizontalImpulseMultiplier = modifiers.horizontalImpulseMultiplier * 1.3f)
        }
    ),
    OraclePowerUp(
        id = "glyphloom",
        label = "Glyphloom Wideners",
        summary = "Platforms widen noticeably.",
        apply = { modifiers ->
            modifiers.copy(platformWidthBonus = (modifiers.platformWidthBonus + 0.18f).coerceAtMost(0.6f))
        }
    ),
    OraclePowerUp(
        id = "echo-grid",
        label = "Echo Grid",
        summary = "Two extra platforms manifest.",
        apply = { modifiers ->
            modifiers.copy(platformCountBonus = modifiers.platformCountBonus + 2)
        }
    ),
    OraclePowerUp(
        id = "aether-glide",
        label = "Aether Glide",
        summary = "Momentum lingers 8% longer.",
        apply = { modifiers ->
            modifiers.copy(frictionMultiplier = (modifiers.frictionMultiplier * 1.08f).coerceAtMost(1.15f))
        }
    ),
    OraclePowerUp(
        id = "clairvoyance",
        label = "Clairvoyant Focus",
        summary = "Goal threshold cut by 25%.",
        apply = { modifiers ->
            modifiers.copy(scoreGoalReduction = (modifiers.scoreGoalReduction + 0.25f).coerceAtMost(0.55f))
        }
    ),
    OraclePowerUp(
        id = "chronowave",
        label = "Chronowave Field",
        summary = "Time flow slowed by 15%.",
        apply = { modifiers ->
            modifiers.copy(timeDilation = (modifiers.timeDilation * 0.85f).coerceAtLeast(0.65f))
        }
    ),
    OraclePowerUp(
        id = "softlight",
        label = "Softlight Halo",
        summary = "Landing window widens generously.",
        apply = { modifiers ->
            modifiers.copy(landingForgivenessMultiplier = (modifiers.landingForgivenessMultiplier * 1.6f).coerceAtMost(2.8f))
        }
    ),
    OraclePowerUp(
        id = "warded-core",
        label = "Seraph Ward",
        summary = "One mist-step shield is granted.",
        apply = { modifiers ->
            modifiers.copy(safetyCharges = modifiers.safetyCharges + 1)
        }
    )
)

private data class SentinelModifiers(
    val speedMultiplier: Float = 1f,
    val spawnMultiplier: Float = 1f,
    val obstacleWidthMultiplier: Float = 1f,
    val shieldCharges: Int = 0,
    val directLaneSwitch: Boolean = false,
    val timeDilation: Float = 1f,
    val goalReduction: Int = 0,
    val collisionRadiusScale: Float = 1f,
    val scoreBonus: Int = 0,
    val lanePreview: Boolean = false
)

private data class SentinelPowerUp(
    override val id: String,
    override val label: String,
    override val summary: String,
    val apply: (SentinelModifiers) -> SentinelModifiers
) : TrainingPowerUpDescriptor

private val SENTINEL_POWER_UPS = listOf(
    SentinelPowerUp(
        id = "aegis",
        label = "Aegis Reservoir",
        summary = "Grants one protective shield.",
        apply = { modifiers ->
            modifiers.copy(shieldCharges = modifiers.shieldCharges + 1)
        }
    ),
    SentinelPowerUp(
        id = "lane-phase",
        label = "Phase Greaves",
        summary = "Tap any lane to shift there instantly.",
        apply = { modifiers ->
            modifiers.copy(directLaneSwitch = true)
        }
    ),
    SentinelPowerUp(
        id = "tempo-anchor",
        label = "Tempo Anchor",
        summary = "Time flow slowed by 15%.",
        apply = { modifiers ->
            modifiers.copy(timeDilation = (modifiers.timeDilation * 0.85f).coerceAtLeast(0.65f))
        }
    ),
    SentinelPowerUp(
        id = "wind-buffers",
        label = "Wind Buffers",
        summary = "Reduces construct speed 20%.",
        apply = { modifiers ->
            modifiers.copy(speedMultiplier = (modifiers.speedMultiplier * 0.8f).coerceAtLeast(0.55f))
        }
    ),
    SentinelPowerUp(
        id = "spacing-hymn",
        label = "Spacing Hymn",
        summary = "Wider gaps between constructs.",
        apply = { modifiers ->
            modifiers.copy(spawnMultiplier = (modifiers.spawnMultiplier * 1.25f).coerceAtMost(1.8f))
        }
    ),
    SentinelPowerUp(
        id = "trimmed-edges",
        label = "Trimmed Edges",
        summary = "Constructs slim by 18%.",
        apply = { modifiers ->
            modifiers.copy(obstacleWidthMultiplier = (modifiers.obstacleWidthMultiplier * 0.82f).coerceAtLeast(0.55f))
        }
    ),
    SentinelPowerUp(
        id = "warded-pauldrons",
        label = "Warded Pauldrons",
        summary = "Reduces hitbox radius 18%.",
        apply = { modifiers ->
            modifiers.copy(collisionRadiusScale = (modifiers.collisionRadiusScale * 0.82f).coerceAtLeast(0.6f))
        }
    ),
    SentinelPowerUp(
        id = "battle-plan",
        label = "Battle Plan",
        summary = "Victory target lowered by three.",
        apply = { modifiers ->
            modifiers.copy(goalReduction = modifiers.goalReduction + 3)
        }
    ),
    SentinelPowerUp(
        id = "momentum-crest",
        label = "Momentum Crest",
        summary = "Begin with two constructs outrun.",
        apply = { modifiers ->
            modifiers.copy(scoreBonus = modifiers.scoreBonus + 2)
        }
    ),
    SentinelPowerUp(
        id = "lantern-scout",
        label = "Lantern Scout",
        summary = "Highlights incoming lanes.",
        apply = { modifiers ->
            modifiers.copy(lanePreview = true)
        }
    )
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
    val scoreGoal = difficulty.flappyScoreGoal()

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
            val pipeSpacing = widthPx * (0.52f * difficulty.flappySpacingFactor())
            val missionDuration = ((behavior.totalDurationMillis.takeIf { it > 0 } ?: 36_000) * difficulty.flappyDurationFactor()).roundToInt()
              val gapFactor = difficulty.flappyGapScale()
              val speedScale = difficulty.flappySpeedScale()
            val gravity = 1120f * speedScale
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
                            if (localScore >= scoreGoal) {
                                finishRound(true)
                                return@LaunchedEffect
                            }
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
                    text = "$score / $scoreGoal",
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
            playingHint = "Tap to give your falcon lift. Thread the warded pylons.",
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
            onExit = onExit,
            extraContent = {
                Text(
                    text = "Win condition: clear $scoreGoal gates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
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
    val selectedPowerUps = remember { mutableStateListOf<SentinelPowerUp>() }
    val maxSentinelPowerUps = 4
    val sentinelModifiers = selectedPowerUps.fold(SentinelModifiers()) { acc, powerUp ->
        powerUp.apply(acc)
    }
    var shieldCharges by remember { mutableIntStateOf(0) }
    val obstacleGoal = (difficulty.runnerObstacleGoal() - sentinelModifiers.goalReduction).coerceAtLeast(3)
    val scoreBaseline = sentinelModifiers.scoreBonus.coerceAtLeast(0)
    val speedMultiplier = sentinelModifiers.speedMultiplier.coerceAtLeast(0.5f)

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
            val avatarRadius = with(density) { 24.dp.toPx() * difficulty.runnerAvatarScale() }
            val obstacleHeight = with(density) { 46.dp.toPx() }
            val baseObstacleWidth = laneWidth * 0.6f
            val obstacleWidth = (baseObstacleWidth * sentinelModifiers.obstacleWidthMultiplier)
                .coerceIn(laneWidth * 0.35f, laneWidth * 0.9f)
            val collisionRadius = (avatarRadius * 0.78f * sentinelModifiers.collisionRadiusScale)
                .coerceIn(avatarRadius * 0.45f, avatarRadius)
            val missionDuration = ((behavior.totalDurationMillis.takeIf { it > 0 } ?: 52_000) * difficulty.runnerDurationFactor()).roundToInt()
            val spawnThreshold = (heightPx * difficulty.runnerSpawnFactor() * sentinelModifiers.spawnMultiplier)
                .coerceIn(heightPx * 0.28f, heightPx * 0.92f)
            val timeFactor = sentinelModifiers.timeDilation.coerceIn(0.65f, 1.2f)
            val directSwitch = sentinelModifiers.directLaneSwitch
            val lanePreview = sentinelModifiers.lanePreview

            LaunchedEffect(gamePhase, runId) {
                if (gamePhase != GamePhase.Playing) {
                    obstacles.clear()
                    obstacles += generateRunnerObstacle(
                        laneCount = laneCount,
                        startY = -obstacleHeight * 1.5f,
                        seed = runId * 101 + 3
                    )
                    score = scoreBaseline
                    playerLane = 1
                    elapsedMillis = 0L
                    shieldCharges = sentinelModifiers.shieldCharges
                }
            }

            LaunchedEffect(gamePhase, runId) {
                if (gamePhase != GamePhase.Playing) return@LaunchedEffect
                val start = SystemClock.elapsedRealtime()
                var lastTime = start
                var localScore = scoreBaseline
                score = localScore
                shieldCharges = sentinelModifiers.shieldCharges

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
                    val deltaSeconds = (deltaMillis / 1000f) * timeFactor
                    lastTime = now
                    elapsedMillis = now - start

                    if (localScore >= obstacleGoal) {
                        finishRound(true)
                        break
                    }

                    if (elapsedMillis >= missionDuration && localScore < obstacleGoal) {
                        finishRound(true)
                        break
                    }

                    val speed = ((heightPx / 2.6f) + (elapsedMillis / 4200f)) *
                        difficulty.runnerSpeedScale() * speedMultiplier

                    for (index in obstacles.indices) {
                        val obstacle = obstacles[index]
                        val newY = obstacle.y + speed * deltaSeconds
                        var newPassed = obstacle.passed

                        val collision = obstacle.lane == playerLane &&
                            newY + obstacleHeight > playerY - collisionRadius &&
                            newY < playerY + collisionRadius

                        if (collision) {
                            if (shieldCharges > 0) {
                                shieldCharges--
                                newPassed = true
                            } else {
                                finishRound(false)
                                return@LaunchedEffect
                            }
                        }

                        if (!obstacle.passed && newY > playerY + collisionRadius) {
                            newPassed = true
                            localScore += 1
                            if (localScore >= obstacleGoal) {
                                score = localScore
                                finishRound(true)
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
                                    if (directSwitch) {
                                        playerLane = tappedLane
                                    } else {
                                        when {
                                            tappedLane < playerLane -> playerLane = (playerLane - 1).coerceAtLeast(0)
                                            tappedLane > playerLane -> playerLane = (playerLane + 1).coerceAtMost(laneCount - 1)
                                        }
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

                        if (lanePreview) {
                            obstacles
                                .filter { it.y + obstacleHeight >= -obstacleHeight }
                                .minByOrNull { it.y }
                                ?.let { upcoming ->
                                    val laneCenter = laneWidth * (upcoming.lane + 0.5f)
                                    drawRect(
                                        color = Color.White.copy(alpha = 0.12f),
                                        topLeft = Offset(laneCenter - laneWidth / 2f, 0f),
                                        size = Size(laneWidth, size.height)
                                    )
                                }
                        }

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
                        drawSkillGlyph(
                            attributeType = AttributeType.ENDURANCE,
                            center = Offset(playerCenterX, playerY),
                            radius = avatarRadius
                        )
                    }

                    Text(
                        text = "$score / $obstacleGoal",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                    )
                    if (shieldCharges > 0) {
                        Text(
                            text = "Shields $shieldCharges",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 20.dp, end = 20.dp)
                        )
                    }
            }
        }

        ControlPanel(
            phase = gamePhase,
            definition = definition,
            elapsedMillis = elapsedMillis,
            score = score,
            scoreLabel = "Constructs outpaced",
            playingHint = "Tap the lanes to weave past the sentry constructs.",
            defeatMessage = "A sentry construct cut you down.",
            lastResult = state.lastResult,
            selectedDifficulty = state.selectedDifficulty,
            onDifficultySelected = onDifficultySelected,
            onStart = {
                onResetResult()
                score = scoreBaseline
                elapsedMillis = 0L
                shieldCharges = sentinelModifiers.shieldCharges
                gamePhase = GamePhase.Playing
                runId++
            },
            onRetry = {
                onResetResult()
                score = scoreBaseline
                elapsedMillis = 0L
                gamePhase = GamePhase.Idle
                runId++
            },
            onExit = onExit,
            extraContent = {
                PowerUpSelector(
                    title = "Sentinel boons",
                    helpText = "Select up to $maxSentinelPowerUps boons.",
                    options = SENTINEL_POWER_UPS,
                    selected = selectedPowerUps,
                    maxSelected = maxSentinelPowerUps,
                    onToggle = { powerUp ->
                        if (selectedPowerUps.contains(powerUp)) {
                            selectedPowerUps.remove(powerUp)
                        } else if (selectedPowerUps.size < maxSentinelPowerUps) {
                            selectedPowerUps.add(powerUp)
                        }
                    }
                )
                Text(
                    text = "Win condition: outrun $obstacleGoal constructs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (sentinelModifiers.shieldCharges > 0) {
                    Text(
                        text = "Shield reserves: ${sentinelModifiers.shieldCharges}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
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
    val selectedPowerUps = remember { mutableStateListOf<OraclePowerUp>() }
    val maxOraclePowerUps = 4
    val oracleModifiers = selectedPowerUps.fold(OracleModifiers()) { acc, powerUp ->
        powerUp.apply(acc)
    }
    var guardianCharges by remember { mutableIntStateOf(0) }

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
            val platformHeight = with(density) { 14.dp.toPx() }
            val baseTarget = behavior.targetScore.takeIf { it > 0 } ?: 650
            val platformCount = (difficulty.jumpPlatformCount() + oracleModifiers.platformCountBonus).coerceAtLeast(6)
            val requiredScore = (difficulty.jumpScoreGoal(baseTarget) * (1f - oracleModifiers.scoreGoalReduction))
                .roundToInt()
                .coerceAtLeast(80)
            val platformWidthScale = (difficulty.jumpPlatformWidthScale() + oracleModifiers.platformWidthBonus)
                .coerceIn(0.65f, 1.6f)
            val landingForgiveness = avatarRadius *
                (0.18f * oracleModifiers.landingForgivenessMultiplier).coerceAtLeast(0.12f)
            val gravityAcceleration = 2100f * difficulty.jumpGravityScale() * oracleModifiers.gravityMultiplier
            val jumpImpulseValue = -1350f * difficulty.jumpImpulseScale() * oracleModifiers.jumpImpulseMultiplier
            val dashImpulse = (widthPx / 1.8f) * difficulty.jumpHorizontalScale() * oracleModifiers.horizontalImpulseMultiplier
            val frictionFactor = (0.85f * oracleModifiers.frictionMultiplier).coerceIn(0.7f, 0.98f)
            val timeFactor = oracleModifiers.timeDilation.coerceIn(0.65f, 1.2f)

            LaunchedEffect(gamePhase, runId, widthPx, heightPx, oracleModifiers) {
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
                }
            }

            LaunchedEffect(gamePhase, runId, widthPx, heightPx, oracleModifiers) {
                if (gamePhase != GamePhase.Playing) return@LaunchedEffect
                val start = SystemClock.elapsedRealtime()
                elapsedMillis = 0L
                heightClimbed = 0f
                score = 0
                playerX = widthPx / 2f
                playerY = heightPx * 0.35f
                playerVelocityX = 0f
                playerVelocityY = 0f
                guardianCharges = oracleModifiers.safetyCharges
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
                    val deltaSeconds = (deltaMillis / 1000f) * timeFactor
                    lastTime = now
                    elapsedMillis = now - start

                    playerVelocityY += gravityAcceleration * deltaSeconds
                    playerY += playerVelocityY * deltaSeconds
                    playerX += playerVelocityX * deltaSeconds
                    playerVelocityX *= frictionFactor

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
                            val overlapY = playerY + avatarRadius >= platformTop - landingForgiveness &&
                                playerY + avatarRadius <= platformTop + platformHeight + landingForgiveness
                            if (overlapX && overlapY) {
                                playerY = platformTop - avatarRadius
                                playerVelocityY = jumpImpulseValue
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
                                seed = seed,
                                widthScale = platformWidthScale
                            )
                            platforms[index] = newPlatform
                        }
                    }

                    score = kotlin.math.max(score, (heightClimbed / 10f).toInt())

                    if (score >= requiredScore) {
                        finishRound(true)
                        break
                    }

                    if (playerY - avatarRadius > heightPx) {
                        if (guardianCharges > 0) {
                            guardianCharges--
                            playerY = heightPx * 0.5f
                            playerVelocityY = jumpImpulseValue * 0.65f
                            playerVelocityX = 0f
                            continue
                        }
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
                                playerVelocityX = if (tapOffset.x < widthPx / 2f) {
                                    -dashImpulse
                                } else {
                                    dashImpulse
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

                      drawSkillGlyph(
                          attributeType = AttributeType.FOCUS,
                          center = Offset(playerX, playerY),
                          radius = avatarRadius
                      )
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
            scoreLabel = "Resonance gained",
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
                guardianCharges = oracleModifiers.safetyCharges
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
            onExit = onExit,
            extraContent = {
                PowerUpSelector(
                    title = "Oracle boons",
                    helpText = "Select up to $maxOraclePowerUps boons.",
                    options = ORACLE_POWER_UPS,
                    selected = selectedPowerUps,
                    maxSelected = maxOraclePowerUps,
                    onToggle = { powerUp ->
                        if (selectedPowerUps.contains(powerUp)) {
                            selectedPowerUps.remove(powerUp)
                        } else if (selectedPowerUps.size < maxOraclePowerUps) {
                            selectedPowerUps.add(powerUp)
                        }
                    }
                )
                Text(
                    text = "Win condition: reach $requiredScore resonance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (oracleModifiers.safetyCharges > 0) {
                    Text(
                        text = "Safety shields: ${oracleModifiers.safetyCharges}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
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
    onExit: () -> Unit,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val attributeLabel = definition.attributeReward.name.lowercase().replaceFirstChar { it.titlecase() }
        val rewardLabel = if (selectedDifficulty == Difficulty.LEGENDARY) {
            "${selectedDifficulty.skillPointReward} universal sigils"
        } else {
            "${selectedDifficulty.skillPointReward} $attributeLabel sigils"
        }
        Text(
            text = "Difficulty: ${selectedDifficulty.displayName} • Reward $rewardLabel",
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
                val victoryRewardLabel = if (selectedDifficulty == Difficulty.LEGENDARY) {
                    "${selectedDifficulty.skillPointReward} universal sigils"
                } else {
                    "${selectedDifficulty.skillPointReward} $attributeLabel sigils"
                }
                Text(
                    text = "Victory yields $victoryRewardLabel.",
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

        if (phase == GamePhase.Idle) {
            extraContent?.let {
                Spacer(modifier = Modifier.height(8.dp))
                it()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T : TrainingPowerUpDescriptor> PowerUpSelector(
    title: String,
    helpText: String,
    options: List<T>,
    selected: List<T>,
    maxSelected: Int,
    onToggle: (T) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "${selected.size} / $maxSelected selected • $helpText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val selectedIds = selected.map { it.id }.toSet()
            options.forEach { option ->
                val isSelected = selectedIds.contains(option.id)
                val canSelectMore = isSelected || selected.size < maxSelected
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (canSelectMore) {
                            onToggle(option)
                        }
                    },
                    enabled = canSelectMore,
                    label = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = option.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                            )
                        }
                    }
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
                    val parts = mutableListOf<String>()
                    parts += "Attribute +${lastResult.attributeGain} $attributeLabel"
                    parts += "XP +${lastResult.experienceGain}"
                    if (lastResult.universalSkillPointGain > 0) {
                        parts += "${lastResult.universalSkillPointGain} universal sigils"
                    }
                    if (lastResult.variantSkillPointGain > 0) {
                        parts += "${lastResult.variantSkillPointGain} $attributeLabel sigils"
                    }
                    parts.joinToString(separator = " • ") + " (${lastResult.difficulty.displayName})"
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
    Difficulty.EASY -> 0.65f
    Difficulty.NORMAL -> 0.9f
    Difficulty.HARD -> 1.08f
    Difficulty.LEGENDARY -> 1.2f
}

private fun Difficulty.flappyGapScale(): Float = when (this) {
    Difficulty.EASY -> 1.45f
    Difficulty.NORMAL -> 1.1f
    Difficulty.HARD -> 0.95f
    Difficulty.LEGENDARY -> 0.82f
}

private fun Difficulty.flappySpacingFactor(): Float = when (this) {
    Difficulty.EASY -> 1.45f
    Difficulty.NORMAL -> 1.1f
    Difficulty.HARD -> 0.95f
    Difficulty.LEGENDARY -> 0.85f
}

private fun Difficulty.flappyDurationFactor(): Double = when (this) {
    Difficulty.EASY -> 1.12
    Difficulty.NORMAL -> 1.0
    Difficulty.HARD -> 1.18
    Difficulty.LEGENDARY -> 1.28
}

private fun Difficulty.flappyRadiusScale(): Float = when (this) {
    Difficulty.EASY -> 1.2f
    Difficulty.NORMAL -> 1.05f
    Difficulty.HARD -> 0.95f
    Difficulty.LEGENDARY -> 0.88f
}

private fun Difficulty.flappyScoreGoal(): Int = when (this) {
    Difficulty.EASY -> 6
    Difficulty.NORMAL -> 9
    Difficulty.HARD -> 13
    Difficulty.LEGENDARY -> 18
}

private fun Difficulty.runnerSpeedScale(): Float = when (this) {
    Difficulty.EASY -> 0.82f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 1.14f
    Difficulty.LEGENDARY -> 1.25f
}

private fun Difficulty.runnerDurationFactor(): Double = when (this) {
    Difficulty.EASY -> 0.9
    Difficulty.NORMAL -> 1.0
    Difficulty.HARD -> 1.1
    Difficulty.LEGENDARY -> 1.2
}

private fun Difficulty.runnerSpawnFactor(): Float = when (this) {
    Difficulty.EASY -> 0.5f
    Difficulty.NORMAL -> 0.4f
    Difficulty.HARD -> 0.32f
    Difficulty.LEGENDARY -> 0.26f
}

private fun Difficulty.runnerAvatarScale(): Float = when (this) {
    Difficulty.EASY -> 1.05f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 0.95f
    Difficulty.LEGENDARY -> 0.9f
}

private fun Difficulty.runnerObstacleGoal(): Int = when (this) {
    Difficulty.EASY -> 7
    Difficulty.NORMAL -> 11
    Difficulty.HARD -> 15
    Difficulty.LEGENDARY -> 20
}

private fun Difficulty.jumpPlatformCount(): Int = when (this) {
    Difficulty.EASY -> 10
    Difficulty.NORMAL -> 9
    Difficulty.HARD -> 8
    Difficulty.LEGENDARY -> 7
}

private fun Difficulty.jumpPlatformWidthScale(): Float = when (this) {
    Difficulty.EASY -> 1.3f
    Difficulty.NORMAL -> 1.1f
    Difficulty.HARD -> 0.95f
    Difficulty.LEGENDARY -> 0.85f
}

private fun Difficulty.jumpGravityScale(): Float = when (this) {
    Difficulty.EASY -> 0.78f
    Difficulty.NORMAL -> 0.9f
    Difficulty.HARD -> 1.05f
    Difficulty.LEGENDARY -> 1.18f
}

private fun Difficulty.jumpImpulseScale(): Float = when (this) {
    Difficulty.EASY -> 1.18f
    Difficulty.NORMAL -> 1.05f
    Difficulty.HARD -> 1.0f
    Difficulty.LEGENDARY -> 0.95f
}

private fun Difficulty.jumpHorizontalScale(): Float = when (this) {
    Difficulty.EASY -> 1.12f
    Difficulty.NORMAL -> 1.0f
    Difficulty.HARD -> 0.92f
    Difficulty.LEGENDARY -> 0.86f
}

private fun Difficulty.jumpScoreGoal(baseTarget: Int): Int = when (this) {
    Difficulty.EASY -> max(120, (baseTarget * 0.5).roundToInt())
    Difficulty.NORMAL -> max(160, (baseTarget * 0.68).roundToInt())
    Difficulty.HARD -> max(220, (baseTarget * 0.82).roundToInt())
    Difficulty.LEGENDARY -> max(280, (baseTarget * 0.95).roundToInt())
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
