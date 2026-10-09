package com.urkaaaz.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.hypot

/** Minimal renderer that draws only the presentation model, never the engine. */
class BattlefieldView(context: Context) : View(context) {
    var onAimChanged: ((SlingshotAim) -> Unit)? = null
    var onAimReleased: ((SlingshotAim) -> Unit)? = null
    var onAimCancelled: ((SlingshotAim?) -> Unit)? = null
    private val assets = BattlefieldAssetCatalog(context)
    private val terrainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(111, 145, 77)
    }

    private fun drawThemeDecorations(canvas: Canvas, theme: BattlefieldTheme, groundTop: Float) {
        val positions = floatArrayOf(0.34f, 0.48f, 0.62f)
        theme.decorations.forEachIndexed { index, bitmap ->
            if (bitmap != null) {
                val x = width * positions[index % positions.size]
                drawTexture(canvas, bitmap, x, groundTop - 94f, 94f)
            }
        }
    }
    private var state = RenderState(
        statusLabel = "NOT_STARTED",
        phaseLabel = "CREATED",
        activeTeamLabel = "NONE",
        projectileCount = 0,
        terrainRevision = 0,
        terrainBiome = "GREEN_FRONTIER",
        projectiles = emptyList(),
        blueCatapultX = 120f,
        blueCatapultY = 648f,
        redCatapultX = 1480f,
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
        matchTimeRemainingMilliseconds = 300_000L,
        windDirection = 0f,
    )
    private var animationFrame = 0
    private var impactFrame = 0
    private var activeImpact: ImpactRenderState? = null
    private val projectileTrails = mutableMapOf<String, MutableList<Pair<Float, Float>>>()
    private val aimController = AimGestureController()
    private val camera = BattlefieldCameraController()
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
        drawThemeDecorations = ::drawThemeDecorations,
        drawTiledTexture = ::drawTiledTexture,
        drawTexture = ::drawTexture,
        drawTextureAtBaseline = ::drawTextureAtBaseline,
        drawReloadIndicator = ::drawReloadIndicator,
        drawTrajectoryPreview = ::drawTrajectoryPreview,
        drawProjectileTrails = ::drawProjectileTrails,
        drawAimGesture = ::drawAimGesture,
    )

    fun render(newState: RenderState) {
        updateProjectileTrails(newState)
        camera.update(newState, width.toFloat(), height.toFloat())
        state = newState
        animationFrame = if (newState.projectiles.isEmpty()) {
            0
        } else {
            (animationFrame + 1) % assets.animationFor("ROCK").flightFrames.size.coerceAtLeast(1)
        }
        if (newState.impact != null && newState.impact != activeImpact) {
            activeImpact = newState.impact
            impactFrame = 0
        } else if (activeImpact != null) {
            impactFrame += 1
            if (impactFrame >= assets.animationFor(activeImpact?.ammunitionType ?: "ROCK").impactFrames.size) {
                activeImpact = null
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.render(
            canvas = canvas,
            state = state,
            camera = camera.transform,
            activeImpact = activeImpact,
            animationFrame = animationFrame,
            impactFrame = impactFrame,
            isAiming = aimController.isDragging,
            viewportWidth = width.toFloat(),
            viewportHeight = height.toFloat(),
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (handlePinchTouch(event)) return true
        if (handleMapPanTouch(event)) return true
        return handleAimTouch(event)
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
        val (originX, originY) = playerAimOrigin()
        val mapX = camera.screenToWorldX(event.x)
        val mapY = camera.screenToWorldY(event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (hypot(mapX - originX, mapY - originY) > 120f) {
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
                    ),
                )
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!aimController.isDragging) return false
                aimController.move(originX, originY, mapX, mapY)?.let(::handleAimResult)
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
        val (originX, originY) = playerAimOrigin()
        val limitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawCircle(
            originX,
            originY,
            VISUAL_MAX_PULL_DISTANCE,
            limitPaint,
        )
        val dx = aimController.state.x - originX
        val dy = aimController.state.y - originY
        val distance = hypot(dx, dy)
        val scale = if (distance > VISUAL_MAX_PULL_DISTANCE) {
            VISUAL_MAX_PULL_DISTANCE / distance
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
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(originX, originY, pullX, pullY, paint)
        canvas.drawCircle(pullX, pullY, 28f, paint)
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
        canvas.drawCircle(centerX, centerY, radius + 5f, backgroundPaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (ready) Color.rgb(95, 220, 105) else Color.rgb(235, 65, 55)
            style = Paint.Style.STROKE
            strokeWidth = 7f
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
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(
            if (ready) "OK" else kotlin.math.ceil(remainingSeconds).toInt().coerceAtLeast(1).toString(),
            centerX,
            centerY + 7f,
            textPaint,
        )
    }

    private fun drawTrajectoryPreview(canvas: Canvas) {
        val aim = aimController.state.aim ?: return
        if (!aim.directionValid || state.playerProjectileActive) return

        val originX = state.blueCatapultX
        val originY = state.blueCatapultY - MUZZLE_OFFSET
        val angleRadians = Math.toRadians(aim.angleDegrees.toDouble())
        val speed = aim.power * PROJECTILE_SPEED_SCALE
        val velocityX = kotlin.math.cos(angleRadians).toFloat() * speed
        val velocityY = -kotlin.math.sin(angleRadians).toFloat() * speed
        val lastIndex = (TRAJECTORY_PREVIEW_POINTS - 1).toFloat()
        val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        for (index in 0 until TRAJECTORY_PREVIEW_POINTS) {
            val time = index * TRAJECTORY_PREVIEW_STEP_SECONDS
            val x = originX + velocityX * time + 0.5f * WIND_ACCELERATION_X * time * time
            val y = originY + velocityY * time + 0.5f * GRAVITY_ACCELERATION_Y * time * time
            if (x !in 0f..WORLD_WIDTH || y !in 0f..WORLD_HEIGHT) break
            val fraction = index / lastIndex
            previewPaint.color = Color.argb(
                (255f - 75f * fraction).toInt(),
                255,
                244,
                190,
            )
            canvas.drawCircle(
                worldToViewX(x),
                worldToViewY(y),
                10f - 7f * fraction,
                previewPaint,
            )
        }
    }

    private fun drawProjectileTrails(canvas: Canvas) {
        val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }
        projectileTrails.values.forEach { trail ->
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

    private fun worldToViewX(worldX: Float): Float = worldX / WORLD_WIDTH * width

    private fun worldToViewY(worldY: Float): Float = worldY / WORLD_HEIGHT * height

    private fun catapultBaseline(worldY: Float, groundTop: Float): Float =
        minOf(groundTop + 6f, worldToViewY(worldY) + 10f)

    private fun playerAimOrigin(): Pair<Float, Float> {
        val groundTop = height * 0.76f
        val fortressBaseline = groundTop + 4f
        val baseline = fortressBaseline - FORTRESS_RENDER_HEIGHT * CATAPULT_PLATFORM_HEIGHT_RATIO
        return worldToViewX(state.blueCatapultX) to (baseline - AIM_ORIGIN_OFFSET)
    }

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

        val tileHeight = tileWidth * bitmap.height / bitmap.width.coerceAtLeast(1)
        var left = 0f
        while (left < width) {
            canvas.drawBitmap(
                bitmap,
                null,
                android.graphics.RectF(left, top, left + tileWidth, top + tileHeight.coerceAtLeast(bottom - top)),
                null,
            )
            left += tileWidth
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

    companion object {
        private const val VISUAL_MAX_PULL_DISTANCE = 190f
        private const val RELOAD_INDICATOR_RADIUS = 30f
        private const val RELOAD_INDICATOR_DURATION = 5.5f
        private const val FORTRESS_RENDER_WIDTH = 280f
        private const val FORTRESS_RENDER_HEIGHT = 210f
        private const val CATAPULT_RENDER_WIDTH = 95f
        private const val RELOAD_INDICATOR_OFFSET = 72f
        private const val AIM_ORIGIN_OFFSET = 62f
        private const val CATAPULT_PLATFORM_HEIGHT_RATIO = 0.40f
        private const val MUZZLE_OFFSET = 34f
        private const val PROJECTILE_SPEED_SCALE = 8f
        private const val GRAVITY_ACCELERATION_Y = 420f
        private const val WIND_ACCELERATION_X = 0f
        private const val TRAJECTORY_PREVIEW_POINTS = 50
        private const val TRAJECTORY_PREVIEW_STEP_SECONDS = 1f / 30f
        private const val PROJECTILE_TRAIL_POINTS = 60
        private const val WORLD_WIDTH = 1_600f
        private const val WORLD_HEIGHT = 900f
    }
}
