package com.urkaaaz.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.urkaaaz.contracts.WorldDimensions
import java.util.Locale
import kotlin.math.hypot

private data class DebugCursorPosition(
    val screenX: Float,
    val screenY: Float,
)

/** Minimal renderer that draws only the presentation model, never the engine. */
class BattlefieldView(context: Context) : View(context) {
    var debugHitboxesVisible: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var debugCoordinatesVisible: Boolean = false
        set(value) {
            field = value
            debugCursorPosition = null
            invalidate()
        }

    var onAimChanged: ((SlingshotAim) -> Unit)? = null
    var onAimReleased: ((SlingshotAim) -> Unit)? = null
    var onAimCancelled: ((SlingshotAim?) -> Unit)? = null
    private val assets = BattlefieldAssetCatalog(context)
    private val terrainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(111, 145, 77)
    }

    private var state = RenderState(
        statusLabel = "NOT_STARTED",
        phaseLabel = "CREATED",
        activeTeamLabel = "NONE",
        projectileCount = 0,
        terrainRevision = 0,
        terrainBiome = "GREEN_FRONTIER",
        projectiles = emptyList(),
        blueCatapultX = 450f,
        blueCatapultY = 648f,
        redCatapultX = 6750f,
        redCatapultY = 648f,
        playerAngleDegrees = 45f,
        playerPower = 50f,
        blueFortressHealth = 0,
        blueFortressMaxHealth = 0,
        redFortressHealth = 0,
        redFortressMaxHealth = 0,
        windStrength = 0f,
        impact = null,
        outcomeLabel = null,
        playerReloadRemainingSeconds = 0f,
        enemyReloadRemainingSeconds = 0f,
        playerProjectileActive = false,
        playerAmmunition = emptyMap(),
        playerGold = 0,
        playerSupply = 0,
        units = emptyList(),
        matchTimeRemainingMilliseconds = 300_000L,
        windDirection = 0f,
        combatFeedback = emptyList(),
    )
    private var animationFrame = 0
    private var showOutcomeLabel = true
    private var impactFrame = 0
    private var lifecyclePaused = false
    private val fireRainPresentationClock = FireRainPresentationClock()
    private var fireRainPresentationFrame = FireRainPresentationFrame()
    private val plagueCauldronPresentationClock = PlagueCauldronPresentationClock()
    private var plagueCauldronPresentationFrame = PlagueCauldronPresentationFrame()
    private val runicBastionPresentationClock = RunicBastionPresentationClock()
    private var runicBastionPresentationFrame = RunicBastionPresentationFrame()
    private val deathAnimationClock = UnitDeathAnimationClock()
    private val unitDeathAnimationRetention =
        UnitDeathAnimationRetention(deathAnimationClock)
    private val sapperReturnAnimationClock = SapperReturnAnimationClock()
    private val sapperMoveAnimationClock = SapperMoveAnimationClock()
    private val sapperBombFlightClock = SapperBombFlightClock()
    private val floatingRewardFeedbackClock = FloatingRewardFeedbackClock()
    private var sapperBombFlights = emptyList<SapperBombFlightFrame>()
    private var activeImpact: ImpactRenderState? = null
    private var lastImpactEventId: String? = null
    private var activeFortressShieldHit: FortressShieldHitRenderState? = null
    private var fortressShieldHitElapsedRenderFrames = 0
    private var lastFortressShieldHitEventId: String? = null
    private var debugCursorPosition: DebugCursorPosition? = null
    private val projectileTrails = mutableMapOf<String, MutableList<Pair<Float, Float>>>()
    private val aimController = AimGestureController()
    private val camera = BattlefieldCameraController()
    private val debugCoordinateBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(224, 23, 37, 54)
    }
    private val debugCoordinateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * resources.displayMetrics.scaledDensity
        typeface = UiTypography.typeface(context)
    }
    private var pinchActive = false
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                pinchActive = true
                cancelAiming()
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                applyZoom(
                    scaleFactor = detector.scaleFactor,
                    focusX = detector.focusX,
                    focusY = detector.focusY,
                )
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                pinchActive = false
            }
        },
    )
    private val renderer = BattlefieldRenderer(
        assets = assets,
        textTypeface = UiTypography.typeface(context),
        drawTiledTexture = ::drawTiledTexture,
        drawTexture = ::drawTexture,
        drawTextureAtBaseline = ::drawTextureAtBaseline,
        drawTextureAtWorldSize = ::drawTextureAtWorldSize,
        drawReloadIndicator = ::drawReloadIndicator,
        drawTrajectoryPreview = ::drawTrajectoryPreview,
        drawProjectileTrails = ::drawProjectileTrails,
        drawAimGesture = ::drawAimGesture,
    )

    fun render(newState: RenderState, showOutcomeLabel: Boolean = true) {
        this.showOutcomeLabel = showOutcomeLabel
        updateProjectileTrails(newState)
        val matchGenerationChanged =
            newState.matchId != state.matchId ||
                newState.matchGeneration != state.matchGeneration
        if (matchGenerationChanged) floatingRewardFeedbackClock.reset()
        val presentationPaused =
            newState.isPaused || lifecyclePaused || !isAttachedToWindow
        val animatedRewardFeedback = floatingRewardFeedbackClock.update(
            feedback = newState.combatFeedback,
            nowMilliseconds = SystemClock.uptimeMillis(),
            paused = presentationPaused,
        )
        camera.update(newState, width.toFloat(), height.toFloat())
        val presentationState = newState.copy(
            isPaused = presentationPaused,
        )
        val nowMilliseconds = SystemClock.uptimeMillis()
        fireRainPresentationFrame = fireRainPresentationClock.update(
            presentationState,
            nowMilliseconds,
        )
        plagueCauldronPresentationFrame = plagueCauldronPresentationClock.update(
            presentationState,
            nowMilliseconds,
        )
        runicBastionPresentationFrame = runicBastionPresentationClock.update(
            presentationState,
            nowMilliseconds,
        )
        if (
            matchGenerationChanged
        ) {
            unitDeathAnimationRetention.reset()
            sapperReturnAnimationClock.reset()
            sapperMoveAnimationClock.reset()
            activeImpact = null
            impactFrame = 0
            lastImpactEventId = null
            activeFortressShieldHit = null
            fortressShieldHitElapsedRenderFrames = 0
            lastFortressShieldHitEventId = null
        }
        sapperBombFlights = sapperBombFlightClock.update(presentationState, nowMilliseconds)
        sapperReturnAnimationClock.advance(newState.units, presentationState.isPaused)
        sapperMoveAnimationClock.advance(newState.units, presentationState.isPaused)
        state = newState.copy(
            units = unitDeathAnimationRetention.update(
                units = newState.units,
                nowMilliseconds = nowMilliseconds,
                allowAnticipation = !presentationState.isPaused,
            ),
            combatFeedback = newState.combatFeedback
                .filterNot { it.kind == CombatFeedbackRenderState.Kind.GOLD_REWARD } +
                animatedRewardFeedback,
        )
        animationFrame = (animationFrame + 1) % 1024
        val shieldHit = newState.fortressShieldHits.lastOrNull()
        if (shieldHit != null && shieldHit.eventId != lastFortressShieldHitEventId) {
            activeFortressShieldHit = shieldHit
            lastFortressShieldHitEventId = shieldHit.eventId
            fortressShieldHitElapsedRenderFrames = 0
        } else if (activeFortressShieldHit != null) {
            fortressShieldHitElapsedRenderFrames += 1
            if (
                fortressShieldHitElapsedRenderFrames >=
                FortressShieldPresentation.hitAnimationDurationRenderFrames()
            ) {
                activeFortressShieldHit = null
            }
        }
        if (newState.impact != null && newState.impact.eventId != lastImpactEventId) {
            activeImpact = newState.impact
            lastImpactEventId = newState.impact.eventId
            impactFrame = 0
        } else {
            if (newState.impact?.eventId == activeImpact?.eventId) {
                activeImpact = newState.impact
            }
            activeImpact?.let { currentImpact ->
                impactFrame += 1
                if (
                    impactFrame >=
                    assets.animationFor(currentImpact.ammunitionType).impactFrames.size
                ) {
                    activeImpact = null
                }
            }
        }
        invalidate()
    }

    fun setLifecyclePaused(paused: Boolean) {
        if (lifecyclePaused == paused) return
        lifecyclePaused = paused
        floatingRewardFeedbackClock.update(
            feedback = emptyList(),
            nowMilliseconds = SystemClock.uptimeMillis(),
            paused = true,
        )
        fireRainPresentationFrame = fireRainPresentationClock.update(
            state.copy(
                isPaused = state.isPaused || lifecyclePaused || !isAttachedToWindow,
            ),
            SystemClock.uptimeMillis(),
        )
        plagueCauldronPresentationFrame = plagueCauldronPresentationClock.update(
            state.copy(
                isPaused = state.isPaused || lifecyclePaused || !isAttachedToWindow,
            ),
            SystemClock.uptimeMillis(),
        )
        runicBastionPresentationFrame = runicBastionPresentationClock.update(
            state.copy(
                isPaused = state.isPaused || lifecyclePaused || !isAttachedToWindow,
            ),
            SystemClock.uptimeMillis(),
        )
        sapperBombFlights = sapperBombFlightClock.update(
            state.copy(
                isPaused = state.isPaused || lifecyclePaused || !isAttachedToWindow,
            ),
            SystemClock.uptimeMillis(),
        )
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        fireRainPresentationFrame = fireRainPresentationClock.update(
            state.copy(isPaused = state.isPaused || lifecyclePaused),
            SystemClock.uptimeMillis(),
        )
        plagueCauldronPresentationFrame = plagueCauldronPresentationClock.update(
            state.copy(isPaused = state.isPaused || lifecyclePaused),
            SystemClock.uptimeMillis(),
        )
        runicBastionPresentationFrame = runicBastionPresentationClock.update(
            state.copy(isPaused = state.isPaused || lifecyclePaused),
            SystemClock.uptimeMillis(),
        )
        sapperBombFlights = sapperBombFlightClock.update(
            state.copy(isPaused = state.isPaused || lifecyclePaused),
            SystemClock.uptimeMillis(),
        )
        invalidate()
    }

    override fun onDetachedFromWindow() {
        floatingRewardFeedbackClock.update(
            feedback = emptyList(),
            nowMilliseconds = SystemClock.uptimeMillis(),
            paused = true,
        )
        fireRainPresentationFrame = fireRainPresentationClock.update(
            state.copy(isPaused = true),
            SystemClock.uptimeMillis(),
        )
        plagueCauldronPresentationFrame = plagueCauldronPresentationClock.update(
            state.copy(isPaused = true),
            SystemClock.uptimeMillis(),
        )
        runicBastionPresentationFrame = runicBastionPresentationClock.update(
            state.copy(isPaused = true),
            SystemClock.uptimeMillis(),
        )
        sapperBombFlights = sapperBombFlightClock.update(
            state.copy(isPaused = true),
            SystemClock.uptimeMillis(),
        )
        projectileTrails.clear()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.render(
            canvas = canvas,
            state = state,
            camera = camera.transform,
            activeImpact = activeImpact,
            animationFrame = animationFrame,
            deathAnimationClock = deathAnimationClock,
            sapperReturnAnimationClock = sapperReturnAnimationClock,
            sapperMoveAnimationClock = sapperMoveAnimationClock,
            impactFrame = impactFrame,
            fortressShieldHitAnimation = activeFortressShieldHit?.let {
                FortressShieldHitAnimationFrame(
                    teamLabel = it.teamLabel,
                    frameIndex = FortressShieldPresentation.hitFrameIndex(
                        fortressShieldHitElapsedRenderFrames,
                    ),
                )
            },
            runicBastionPresentation = runicBastionPresentationFrame,
            fireRainPresentation = fireRainPresentationFrame,
            plagueCauldronPresentation = plagueCauldronPresentationFrame,
            sapperBombFlights = sapperBombFlights,
            isAiming = aimController.isDragging,
            viewportWidth = width.toFloat(),
            viewportHeight = height.toFloat(),
            debugHitboxesVisible = debugHitboxesVisible,
            combatFeedback = state.combatFeedback,
            showOutcomeLabel = showOutcomeLabel,
        )
        if (debugCoordinatesVisible) {
            drawDebugCursorCoordinates(canvas)
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        camera.update(state, width.toFloat(), height.toFloat())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (debugCoordinatesVisible && event.actionMasked == MotionEvent.ACTION_MOVE) {
            updateDebugCursorPosition(event.x, event.y)
        }
        if (handlePinchTouch(event)) return true
        if (handleMapPanTouch(event)) return true
        return handleAimTouch(event)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (!debugCoordinatesVisible) return super.onHoverEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_MOVE -> {
                updateDebugCursorPosition(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                clearDebugCursorPosition()
                return true
            }
            else -> return super.onHoverEvent(event)
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!debugCoordinatesVisible) return super.onGenericMotionEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_MOVE -> {
                updateDebugCursorPosition(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                clearDebugCursorPosition()
                return true
            }
            else -> return super.onGenericMotionEvent(event)
        }
    }

    private fun updateDebugCursorPosition(screenX: Float, screenY: Float) {
        if (width <= 0 || height <= 0) {
            clearDebugCursorPosition()
            return
        }
        debugCursorPosition = DebugCursorPosition(screenX, screenY)
        invalidate()
    }

    private fun clearDebugCursorPosition() {
        debugCursorPosition = null
        invalidate()
    }

    private fun drawDebugCursorCoordinates(canvas: Canvas) {
        val cursor = debugCursorPosition ?: return
        if (width <= 0 || height <= 0) return
        val (worldX, worldY) = camera.screenToGameWorld(
            cursor.screenX,
            cursor.screenY,
            width.toFloat(),
            height.toFloat(),
        )
        val density = resources.displayMetrics.density
        val padding = 8f * density
        val text = String.format(Locale.US, "X: %.1f  Y: %.1f", worldX, worldY)
        val boxWidth = debugCoordinateTextPaint.measureText(text) + padding * 2f
        val boxHeight = debugCoordinateTextPaint.textSize + padding * 2f
        val preferredLeft = cursor.screenX + 12f * density
        val preferredTop = cursor.screenY + 12f * density
        val left = (if (preferredLeft + boxWidth <= width) {
            preferredLeft
        } else {
            cursor.screenX - 12f * density - boxWidth
        }).coerceIn(0f, (width - boxWidth).coerceAtLeast(0f))
        val top = (if (preferredTop + boxHeight <= height) {
            preferredTop
        } else {
            cursor.screenY - 12f * density - boxHeight
        }).coerceIn(0f, (height - boxHeight).coerceAtLeast(0f))

        canvas.drawRoundRect(
            RectF(left, top, left + boxWidth, top + boxHeight),
            5f * density,
            5f * density,
            debugCoordinateBackgroundPaint,
        )
        canvas.drawText(
            text,
            left + padding,
            top + padding - debugCoordinateTextPaint.fontMetrics.top,
            debugCoordinateTextPaint,
        )
    }

    private fun handlePinchTouch(event: MotionEvent): Boolean {
        val wasPinchActive = pinchActive
        scaleDetector.onTouchEvent(event)
        return wasPinchActive || pinchActive || event.pointerCount > 1
    }

    private fun handleMapPanTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                return camera.movePan(
                    x = event.x,
                    y = event.y,
                    viewportWidth = width.toFloat(),
                    viewportHeight = height.toFloat(),
                )
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val wasPanning = camera.isPanning
                camera.endPan()
                return wasPanning
            }
            else -> return false
        }
    }

    private fun canStartAiming(): Boolean =
        state.outcomeLabel == null &&
            !state.playerProjectileActive &&
            state.playerReloadRemainingSeconds <= 0f

    private fun handleAimTouch(event: MotionEvent): Boolean {
        val (originX, originY) = playerAimOriginView()
        val screenSpaceScale = camera.transform.scale
        val mapX = camera.screenToWorldX(event.x)
        val mapY = camera.screenToWorldY(event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (hypot(mapX - originX, mapY - originY) * screenSpaceScale > 120f) {
                    cancelAiming()
                    startMapPan(event)
                    return true
                }
                if (!canStartAiming()) return true
                handleAimResult(
                    aimController.begin(
                        originX = originX,
                        originY = originY,
                        x = mapX,
                        y = mapY,
                        currentAim = SlingshotAim(
                            angleDegrees = state.playerAngleDegrees,
                            power = state.playerPower,
                            pullDistance = 0f,
                            directionValid = false,
                            shouldFire = false,
                        ),
                        screenSpaceScale = screenSpaceScale,
                    ),
                )
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!aimController.isDragging) return false
                aimController.move(
                    originX,
                    originY,
                    mapX,
                    mapY,
                    screenSpaceScale = screenSpaceScale,
                )?.let(::handleAimResult)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!aimController.isDragging) return false
                aimController.release()?.let(::handleAimResult)
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelAiming()
                invalidate()
                return true
            }
        }
        return true
    }

    private fun startMapPan(event: MotionEvent) {
        camera.beginPan(event.x, event.y)
    }

    private fun cancelAiming() {
        aimController.cancel()?.let(::handleAimResult)
    }

    private fun handleAimResult(result: AimGestureResult) {
        when (result) {
            is AimGestureResult.Changed -> onAimChanged?.invoke(result.aim)
            is AimGestureResult.Released -> onAimReleased?.invoke(result.aim)
            is AimGestureResult.Cancelled -> onAimCancelled?.invoke(result.previousAim)
        }
    }

    private fun drawAimGesture(canvas: Canvas, groundTop: Float) {
        val (originX, originY) = playerAimOriginView()
        val screenSpaceScale = camera.transform.scale
        val visualRadius = visualPullRadius(originX, originY)
        val limitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f / screenSpaceScale
        }
        canvas.drawCircle(
            originX,
            originY,
            visualRadius,
            limitPaint,
        )
        val dx = aimController.state.x - originX
        val dy = aimController.state.y - originY
        val distance = hypot(dx, dy)
        val scale = if (distance > visualRadius) {
            visualRadius / distance
        } else {
            1f
        }
        val pullX = originX + dx * scale
        val pullY = originY + dy * scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (aimController.state.aim?.directionValid == true) {
                Color.rgb(255, 224, 110)
            } else {
                Color.RED
            }
            style = Paint.Style.STROKE
            strokeWidth = 6f / screenSpaceScale
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(originX, originY, pullX, pullY, paint)
        canvas.drawCircle(pullX, pullY, 28f / screenSpaceScale, paint)
    }

    private fun visualPullRadius(originX: Float, originY: Float): Float {
        val cameraTransform = camera.transform
        val screenOriginX = originX * cameraTransform.scale + cameraTransform.offsetX
        val screenOriginY = originY * cameraTransform.scale + cameraTransform.offsetY
        val availableScreenRadius = minOf(
            screenOriginX,
            width - screenOriginX,
            screenOriginY,
            height - screenOriginY,
        ) - AIM_CIRCLE_EDGE_MARGIN
        val screenRadius = minOf(
            VISUAL_MAX_PULL_DISTANCE,
            availableScreenRadius.coerceAtLeast(MIN_VISUAL_PULL_RADIUS),
        )
        return screenRadius / cameraTransform.scale
    }

    private fun drawHealthBar(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        health: Int,
        maxHealth: Int,
        color: Paint,
    ) {
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.DKGRAY }
        canvas.drawRect(left, top, left + width, top + 16f, background)
        val ratio = if (maxHealth > 0) (health.toFloat() / maxHealth).coerceIn(0f, 1f) else 0f
        canvas.drawRect(left, top, left + width * ratio, top + 16f, color)
    }

    private fun drawReloadIndicator(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        remainingSeconds: Float,
        projectileActive: Boolean,
    ) {
        val radius = RELOAD_INDICATOR_RADIUS
        val ready = remainingSeconds <= 0f && !projectileActive
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(205, 25, 22, 18)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, radius + 3f, backgroundPaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (ready) Color.rgb(95, 220, 105) else Color.rgb(235, 65, 55)
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
        }
        val sweep = if (ready) {
            360f
        } else {
            val progress = if (remainingSeconds > 0f) {
                1f - (remainingSeconds / RELOAD_INDICATOR_DURATION).coerceIn(0f, 1f)
            } else {
                0f
            }
            360f * progress
        }
        canvas.drawArc(
            RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius),
            -90f,
            sweep,
            false,
            ringPaint,
        )

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 10f
            typeface = UiTypography.typeface(context)
        }
        canvas.drawText(
            if (ready) "OK" else kotlin.math.ceil(remainingSeconds).toInt().coerceAtLeast(1).toString(),
            centerX,
            centerY + 4f,
            textPaint,
        )
    }

    private fun drawTrajectoryPreview(canvas: Canvas) {
        val aim = aimController.state.aim ?: return
        if (!aim.directionValid || state.playerProjectileActive) return

        val physicsOriginX = state.blueCatapultAnchorX
        val physicsOriginY = state.blueCatapultAnchorY
        val angleRadians = Math.toRadians(aim.angleDegrees.toDouble())
        var x = physicsOriginX
        var y = physicsOriginY
        var velocityX = kotlin.math.cos(angleRadians).toFloat() *
            aim.power * PROJECTILE_SPEED_SCALE * PHYSICS_WORLD_SCALE
        var velocityY = -kotlin.math.sin(angleRadians).toFloat() *
            aim.power * PROJECTILE_SPEED_SCALE * PHYSICS_WORLD_SCALE
        val windAccelerationX = state.windDirection * state.windStrength * WIND_ACCELERATION_SCALE
        val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        drawTrajectoryPoint(canvas, physicsOriginX, physicsOriginY, 0f, previewPaint)
        for (index in 0 until TRAJECTORY_PREVIEW_POINTS) {
            val step = TRAJECTORY_PREVIEW_STEP_SECONDS
            velocityX += windAccelerationX * step
            velocityY += GRAVITY_ACCELERATION_Y * PHYSICS_WORLD_SCALE * step
            x += velocityX * step
            y += velocityY * step
            if (x !in 0f..WORLD_WIDTH || y !in 0f..WORLD_HEIGHT) break
            val fraction = index / TRAJECTORY_PREVIEW_POINTS.toFloat()
            previewPaint.color = Color.argb(
                (255f - 75f * fraction).toInt(),
                255,
                244,
                190,
            )
            drawTrajectoryPoint(canvas, x, y, fraction, previewPaint)
        }
    }

    private fun drawTrajectoryPoint(
        canvas: Canvas,
        worldX: Float,
        worldY: Float,
        fraction: Float,
        paint: Paint,
    ) {
        canvas.drawCircle(
            worldToViewX(worldX),
            worldToViewY(worldY),
            4.5f - 3.5f * fraction,
            paint,
        )
    }

    private fun drawProjectileTrails(
        canvas: Canvas,
        suppressedProjectileIds: Set<String>,
    ) {
        val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }
        projectileTrails.forEach { (projectileId, trail) ->
            if (projectileId in suppressedProjectileIds) return@forEach
            for (index in 1 until trail.size) {
                val alphaFraction = index / trail.size.toFloat()
                trailPaint.color = Color.argb(
                    (180f * alphaFraction).toInt(),
                    255,
                    255,
                    255,
                )
                val (x0, y0) = trail[index - 1]
                val (x1, y1) = trail[index]
                canvas.drawLine(
                    worldToViewX(x0),
                    worldToViewY(y0),
                    worldToViewX(x1),
                    worldToViewY(y1),
                    trailPaint,
                )
            }
        }
    }

    private fun updateProjectileTrails(newState: RenderState) {
        val activeProjectiles = newState.projectiles.map { it.projectileId }.toSet()
        projectileTrails.keys.retainAll(activeProjectiles)
        newState.projectiles.forEach { projectile ->
            val trail = projectileTrails.getOrPut(projectile.projectileId) { mutableListOf() }
            val position = projectile.x to projectile.y
            if (trail.lastOrNull() != position) {
                trail += position
            }
            while (trail.size > PROJECTILE_TRAIL_POINTS) {
                trail.removeAt(0)
            }
        }
    }

    private fun drawTexture(canvas: Canvas, bitmap: android.graphics.Bitmap?, left: Float, top: Float, size: Float) {
        if (bitmap == null) return
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        val targetHeight = size / aspectRatio
        canvas.drawBitmap(
            bitmap,
            null,
            android.graphics.RectF(left, top, left + size, top + targetHeight),
            null,
        )
    }

    private fun drawTextureAtBaseline(
        canvas: Canvas,
        bitmap: android.graphics.Bitmap?,
        centerX: Float,
        baselineY: Float,
        width: Float,
    ) {
        if (bitmap == null) return
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        val height = width / aspectRatio
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(
                centerX - width / 2f,
                baselineY - height,
                centerX + width / 2f,
                baselineY,
            ),
            null,
        )
    }

    private fun drawTextureAtWorldSize(
        canvas: Canvas,
        bitmap: android.graphics.Bitmap?,
        centerX: Float,
        baselineY: Float,
        width: Float,
        height: Float,
    ) {
        if (bitmap == null) return
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(
                centerX - width / 2f,
                baselineY - height,
                centerX + width / 2f,
                baselineY,
            ),
            null,
        )
    }

    private fun worldToViewX(worldX: Float): Float = worldX / WORLD_WIDTH * width

    private fun worldToViewY(worldY: Float): Float = worldY / WORLD_HEIGHT * height

    private fun catapultBaseline(worldY: Float, groundTop: Float): Float =
        minOf(groundTop + 6f, worldToViewY(worldY) + 10f)

    private fun playerAimOriginView(): Pair<Float, Float> =
        worldToViewX(state.blueCatapultAnchorX) to worldToViewY(state.blueCatapultAnchorY)

    private fun drawTiledTexture(
        canvas: Canvas,
        bitmap: android.graphics.Bitmap?,
        top: Float,
        bottom: Float,
        tileWidth: Float,
        fallbackColor: Int,
    ) {
        if (bitmap == null) {
            terrainPaint.color = fallbackColor
            canvas.drawRect(0f, top, width.toFloat(), bottom, terrainPaint)
            return
        }

        val tileHeight = (tileWidth * bitmap.height / bitmap.width.coerceAtLeast(1)).coerceAtLeast(1f)
        var tileTop = top
        while (tileTop < bottom) {
            var left = 0f
            while (left < width) {
                canvas.drawBitmap(
                    bitmap,
                    null,
                    android.graphics.RectF(left, tileTop, left + tileWidth, tileTop + tileHeight),
                    null,
                )
                left += tileWidth
            }
            tileTop += tileHeight
        }
    }

    private fun applyZoom(scaleFactor: Float, focusX: Float, focusY: Float) {
        camera.applyZoom(
            scaleFactor = scaleFactor,
            focusX = focusX,
            focusY = focusY,
            viewportWidth = width.toFloat(),
            viewportHeight = height.toFloat(),
        )
        invalidate()
    }

    fun setSnapshotMode(enabled: Boolean) {
        camera.setSnapshotMode(enabled)
        invalidate()
    }

    companion object {
        private const val VISUAL_MAX_PULL_DISTANCE = 96f
        private const val RELOAD_INDICATOR_RADIUS = 12f
        private const val RELOAD_INDICATOR_DURATION = 5.5f
        private const val FORTRESS_RENDER_WIDTH = 280f
        private const val FORTRESS_CENTER_MARGIN = 490f
        private const val PROJECTILE_SPEED_SCALE = 8f
        private const val GRAVITY_ACCELERATION_Y = 420f
        private const val WIND_ACCELERATION_SCALE = 390f
        private const val PHYSICS_WORLD_SCALE = 4.5f
        private const val AIM_CIRCLE_EDGE_MARGIN = 8f
        private const val MIN_VISUAL_PULL_RADIUS = 24f
        private const val TRAJECTORY_PREVIEW_POINTS = 42
        private const val TRAJECTORY_PREVIEW_STEP_SECONDS = 1f / 30f
        private const val PROJECTILE_TRAIL_POINTS = 60
        private const val DECORATION_GROUND_SINK = 12f
        private const val WORLD_WIDTH = WorldDimensions.WIDTH
        private const val WORLD_HEIGHT = WorldDimensions.HEIGHT
    }
}
