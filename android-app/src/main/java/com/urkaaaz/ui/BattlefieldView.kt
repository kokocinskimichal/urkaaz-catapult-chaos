package com.urkaaaz.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.BitmapFactory
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.abs
import kotlin.math.hypot
import com.urkaaaz.android.R

/** Minimal renderer that draws only the presentation model, never the engine. */
class BattlefieldView(context: Context) : View(context) {
    var onAimChanged: ((SlingshotAim) -> Unit)? = null
    var onAimReleased: ((SlingshotAim) -> Unit)? = null
    var onAimCancelled: (() -> Unit)? = null
    private val flightFrames = loadAnimationFrames("rock_flight")
    private val impactFrames = loadAnimationFrames("rock_impact")
    private val animations = mapOf(
        "ROCK" to (flightFrames to impactFrames),
        "SIEGE_BOMB" to (loadAnimationFrames("siege_bomb_flight") to loadAnimationFrames("siege_bomb_impact")),
        "POWDER_BARREL" to (loadAnimationFrames("powder_barrel_flight") to loadAnimationFrames("powder_barrel_impact")),
        "CLUSTER_BOMB" to (loadAnimationFrames("cluster_bomb_flight") to loadAnimationFrames("cluster_bomb_impact")),
        "FIRE_RAIN" to (loadAnimationFrames("fire_rain_flight") to loadAnimationFrames("fire_rain_impact")),
        "PLAGUE_CAULDRON" to (loadAnimationFrames("plague_cauldron_flight") to loadAnimationFrames("plague_cauldron_impact")),
    )
    private val terrainSoil = BitmapFactory.decodeResource(resources, R.drawable.terrain_soil_fill_00)
    private val grassCap = BitmapFactory.decodeResource(resources, R.drawable.terrain_grass_cap)
    private data class TerrainTheme(
        val background: android.graphics.Bitmap?,
        val soil: android.graphics.Bitmap?,
        val cap: android.graphics.Bitmap?,
        val decorations: List<android.graphics.Bitmap?>,
        val fallbackColor: Int,
    )
    private val terrainThemes = mapOf(
        "GREEN_FRONTIER" to TerrainTheme(
            background = loadBitmap("background_green_frontier"),
            soil = terrainSoil,
            cap = loadBitmap("terrain_green_frontier_cap") ?: grassCap,
            decorations = listOf(loadBitmap("terrain_tree_00"), loadBitmap("terrain_tree_01")),
            fallbackColor = Color.rgb(111, 145, 77),
        ),
        "FROSTBOUND" to TerrainTheme(
            background = loadBitmap("background_frostbound_pass"),
            soil = loadBitmap("terrain_frost_soil_fill_00"),
            cap = loadBitmap("terrain_frost_snow_cap"),
            decorations = listOf(
                loadBitmap("terrain_frost_tree_00"),
                loadBitmap("terrain_frost_tree_01"),
                loadBitmap("terrain_frost_rock_00"),
            ),
            fallbackColor = Color.rgb(169, 185, 193),
        ),
        "COPPERWOOD" to TerrainTheme(
            background = loadBitmap("background_copperwood"),
            soil = loadBitmap("terrain_copperwood_soil_fill_00"),
            cap = loadBitmap("terrain_copperwood_cap"),
            decorations = listOf(
                loadBitmap("terrain_copperwood_tree_00"),
                loadBitmap("terrain_copperwood_tree_01"),
                loadBitmap("terrain_copperwood_rock_00"),
            ),
            fallbackColor = Color.rgb(111, 75, 48),
        ),
        "ASHEN_MARCH" to TerrainTheme(
            background = loadBitmap("background_ashen_march"),
            soil = loadBitmap("terrain_ashen_march_soil_fill_00"),
            cap = loadBitmap("terrain_ashen_march_cap"),
            decorations = listOf(
                loadBitmap("terrain_ashen_march_tree_00"),
                loadBitmap("terrain_ashen_march_tree_01"),
                loadBitmap("terrain_ashen_march_rock_00"),
            ),
            fallbackColor = Color.rgb(81, 75, 68),
        ),
        "SUNKEN_MARSHES" to TerrainTheme(
            background = loadBitmap("background_sunken_marshes"),
            soil = terrainSoil,
            cap = grassCap,
            decorations = listOf(loadBitmap("terrain_prop_00"), loadBitmap("terrain_prop_01")),
            fallbackColor = Color.rgb(77, 98, 76),
        ),
    )
    private val blueFortress = BitmapFactory.decodeResource(resources, R.drawable.fortress_left)
    private val redFortress = BitmapFactory.decodeResource(resources, R.drawable.fortress_right)
    private val blueCatapult = BitmapFactory.decodeResource(resources, R.drawable.catapult_left)
    private val redCatapult = BitmapFactory.decodeResource(resources, R.drawable.catapult_right)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val terrainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(111, 145, 77)
    }

    private fun drawThemeDecorations(canvas: Canvas, theme: TerrainTheme, groundTop: Float) {
        val positions = floatArrayOf(0.34f, 0.48f, 0.62f)
        theme.decorations.forEachIndexed { index, bitmap ->
            if (bitmap != null) {
                val x = width * positions[index % positions.size]
                drawTexture(canvas, bitmap, x, groundTop - 94f, 94f)
            }
        }
    }
    private val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(55, 105, 190)
    }

    private fun loadBitmap(name: String): android.graphics.Bitmap? {
    val resourceId = resources.getIdentifier(name, "drawable", context.packageName)
    return resourceId.takeIf { it != 0 }?.let { BitmapFactory.decodeResource(resources, it) }
    }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(190, 65, 55)
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
    )
    private var animationFrame = 0
    private var impactFrame = 0
    private var activeImpact: ImpactRenderState? = null
    private var draggingAim = false
    private var dragAim: SlingshotAim? = null
    private var dragX = 0f
    private var dragY = 0f
    private var mapScale = 1f
    private var mapOffsetX = 0f
    private var mapOffsetY = 0f
    private var trackedTeamLabel: String? = null
    private var lastFramedTeamLabel: String? = null
    private var lastPhaseLabel: String? = null
    private var postImpactHoldSeconds = 0f
    private var cameraReturnActive = false
    private var wasFollowingProjectile = false
    private var pinchActive = false
    private var panningMap = false
    private var panDragging = false
    private var manualPanActive = false
    private var panLastX = 0f
    private var panLastY = 0f
    private var panStartX = 0f
    private var panStartY = 0f
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                pinchActive = true
                panningMap = false
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

    fun render(newState: RenderState) {
        updateCameraTracking(newState)
        state = newState
        animationFrame = if (newState.projectiles.isEmpty()) {
            0
        } else {
            (animationFrame + 1) % flightFrames.size.coerceAtLeast(1)
        }
        if (newState.impact != null && newState.impact != activeImpact) {
            activeImpact = newState.impact
            impactFrame = 0
        } else if (activeImpact != null) {
            impactFrame += 1
            if (impactFrame >= impactFrames.size) activeImpact = null
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(mapScale, mapScale)
        canvas.translate(mapOffsetX / mapScale, mapOffsetY / mapScale)
        val theme = terrainThemes[state.terrainBiome] ?: terrainThemes.getValue("GREEN_FRONTIER")
        if (theme.background != null) {
            canvas.drawBitmap(
                theme.background,
                null,
                android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()),
                backgroundPaint,
            )
        } else {
            backgroundPaint.shader = LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat(),
                Color.rgb(123, 177, 211),
                Color.rgb(242, 211, 154),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        }
        val groundTop = height * 0.76f
        terrainPaint.color = theme.fallbackColor
        drawTiledTexture(canvas, theme.soil, groundTop, height.toFloat(), 128f)
        drawTiledTexture(canvas, theme.cap, groundTop - 20f, groundTop + 6f, 150f)
        drawThemeDecorations(canvas, theme, groundTop)
        drawTexture(canvas, blueFortress, width * 0.06f, groundTop - 175f, 175f)
        drawTexture(canvas, redFortress, width * 0.80f, groundTop - 175f, 175f)
        drawTexture(canvas, blueCatapult, width * 0.20f, groundTop - 105f, 105f)
        drawTexture(canvas, redCatapult, width * 0.70f, groundTop - 105f, 105f)
        state.projectiles.forEach { projectile ->
            val projectileX = (projectile.x / 1_600f * width).coerceIn(0f, width.toFloat())
            val projectileY = (projectile.y / 900f * height).coerceIn(0f, groundTop)
            val frames = animations[projectile.ammunitionType]?.first ?: flightFrames
            val frame = frames.getOrNull(animationFrame % frames.size.coerceAtLeast(1))
            drawTexture(canvas, frame, projectileX - 30f, projectileY - 30f, 60f)
        }
        activeImpact?.let { impact ->
            val frames = animations[impact.ammunitionType]?.second ?: impactFrames
            val impactFrameBitmap = frames.getOrNull(impactFrame % frames.size.coerceAtLeast(1))
            val impactX = (impact.x / 1_600f * width).coerceIn(0f, width.toFloat())
            val impactY = (impact.y / 900f * height).coerceIn(0f, groundTop)
            drawTexture(canvas, impactFrameBitmap, impactX - 54f, impactY - 54f, 108f)
        }
        drawHealthBar(
            canvas,
            width * 0.06f,
            groundTop - 198f,
            175f,
            state.blueFortressHealth,
            state.blueFortressMaxHealth,
            bluePaint,
        )
        drawHealthBar(
            canvas,
            width * 0.80f,
            groundTop - 198f,
            175f,
            state.redFortressHealth,
            state.redFortressMaxHealth,
            redPaint,
        )
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }
        canvas.drawText("YOU", width * 0.06f, groundTop - 205f, labelPaint)
        canvas.drawText("ENEMY", width * 0.80f, groundTop - 205f, labelPaint)
        state.outcomeLabel?.let { outcome ->
            val overlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(190, 20, 24, 28)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlay)
            val resultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 42f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            val result = when (outcome) {
                "BLUE_WIN" -> "VICTORY"
                "RED_WIN" -> "DEFEAT"
                else -> "DRAW"
            }
            canvas.drawText(result, width / 2f, height / 2f, resultPaint)
        }
        if (draggingAim) {
            drawAimGesture(canvas, groundTop)
        }
        canvas.restore()
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
        if (!panningMap) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!panDragging) {
                    if (hypot(event.x - panStartX, event.y - panStartY) < PAN_START_DISTANCE) {
                        return true
                    }
                    panDragging = true
                    panLastX = event.x
                    panLastY = event.y
                    return true
                }
                translateMap(
                    deltaX = event.x - panLastX,
                    deltaY = event.y - panLastY,
                )
                panLastX = event.x
                panLastY = event.y
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                panningMap = false
                panDragging = false
            }
            else -> return false
        }
        return true
    }

    private fun canStartAiming(): Boolean =
        state.outcomeLabel == null &&
            !state.playerProjectileActive &&
            state.playerReloadRemainingSeconds <= 0f

    private fun handleAimTouch(event: MotionEvent): Boolean {
        val originX = width * 0.25f
        val originY = height * 0.76f - 82f
        val mapX = (event.x - mapOffsetX) / mapScale
        val mapY = (event.y - mapOffsetY) / mapScale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (hypot(mapX - originX, mapY - originY) > 120f) {
                    cancelAiming()
                    startMapPan(event)
                    return true
                }
                if (!canStartAiming()) return true
                draggingAim = true
                dragX = mapX
                dragY = mapY
                dragAim = SlingshotAimCalculator.calculate(originX, originY, dragX, dragY)
                onAimChanged?.invoke(requireNotNull(dragAim))
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!draggingAim) return false
                dragX = mapX
                dragY = mapY
                dragAim = SlingshotAimCalculator.calculate(originX, originY, dragX, dragY)
                onAimChanged?.invoke(requireNotNull(dragAim))
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!draggingAim) return false
                val aim = requireNotNull(dragAim)
                draggingAim = false
                dragAim = null
                if (aim.shouldFire) onAimReleased?.invoke(aim) else onAimCancelled?.invoke()
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
        if (wasFollowingProjectile) return
        panningMap = true
        panDragging = false
        manualPanActive = true
        panStartX = event.x
        panStartY = event.y
        panLastX = event.x
        panLastY = event.y
    }

    private fun cancelAiming() {
        if (!draggingAim && dragAim == null) return
        draggingAim = false
        dragAim = null
        onAimCancelled?.invoke()
    }

    private fun drawAimGesture(canvas: Canvas, groundTop: Float) {
        val originX = width * 0.25f
        val originY = groundTop - 82f
        val dx = dragX - originX
        val dy = dragY - originY
        val distance = hypot(dx, dy)
        val scale = if (distance > SlingshotAimCalculator.MAX_PULL_DISTANCE) {
            SlingshotAimCalculator.MAX_PULL_DISTANCE / distance
        } else {
            1f
        }
        val pullX = originX + dx * scale
        val pullY = originY + dy * scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dragAim?.directionValid == true) Color.rgb(255, 224, 110) else Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 8f
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

    private fun loadAnimationFrames(prefix: String): List<android.graphics.Bitmap> =
        (0..31).mapNotNull { index ->
            val resourceId = resources.getIdentifier(
                "${prefix}_%02d".format(index),
                "drawable",
                context.packageName,
            )
            resourceId.takeIf { it != 0 }?.let { BitmapFactory.decodeResource(resources, it) }
        }

    private fun drawTiledTexture(
        canvas: Canvas,
        bitmap: android.graphics.Bitmap?,
        top: Float,
        bottom: Float,
        tileWidth: Float,
    ) {
        if (bitmap == null) {
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
        if (!scaleFactor.isFinite() || scaleFactor <= 0f) return
        val oldScale = mapScale
        val newScale = (oldScale * scaleFactor).coerceIn(MIN_MAP_SCALE, MAX_MAP_SCALE)
        val contentX = (focusX - mapOffsetX) / oldScale
        val contentY = (focusY - mapOffsetY) / oldScale
        mapScale = newScale
        mapOffsetX = focusX - contentX * newScale
        mapOffsetY = focusY - contentY * newScale
        clampMapOffset()
        invalidate()
    }

    private fun translateMap(deltaX: Float, deltaY: Float) {
        if (wasFollowingProjectile) return
        mapOffsetX += deltaX
        mapOffsetY += deltaY
        clampMapOffset()
        invalidate()
    }

    private fun clampMapOffset() {
        val minOffsetX = width * (1f - mapScale)
        val minOffsetY = height * (1f - mapScale)
        mapOffsetX = mapOffsetX.coerceIn(minOf(minOffsetX, 0f), maxOf(minOffsetX, 0f))
        mapOffsetY = mapOffsetY.coerceIn(minOf(minOffsetY, 0f), maxOf(minOffsetY, 0f))
    }

    private fun updateCameraTracking(newState: RenderState) {
        val trackedProjectile = newState.projectiles.firstOrNull { it.teamLabel == "BLUE" }

        if (trackedProjectile != null) {
            manualPanActive = false
            if (!wasFollowingProjectile) {
                mapScale = maxOf(mapScale, AUTO_TRACK_SCALE)
            }
            wasFollowingProjectile = true
            cameraReturnActive = false
            trackedTeamLabel = trackedProjectile.teamLabel
            centerMapOnWorld(trackedProjectile.x, trackedProjectile.y)
        } else {
            val phaseChanged = lastPhaseLabel != null && lastPhaseLabel != newState.phaseLabel
            if (phaseChanged) {
                manualPanActive = false
            }
            if (manualPanActive || panningMap) {
                lastPhaseLabel = newState.phaseLabel
                clampMapOffset()
                return
            }
            if (wasFollowingProjectile) {
                wasFollowingProjectile = false
                postImpactHoldSeconds = POST_IMPACT_HOLD_SECONDS
            } else if (postImpactHoldSeconds > 0f) {
                postImpactHoldSeconds = (postImpactHoldSeconds - RENDER_STEP_SECONDS).coerceAtLeast(0f)
                if (postImpactHoldSeconds == 0f) {
                    cameraReturnActive = true
                }
            } else if (cameraReturnActive) {
                val targetTeam = trackedTeamLabel ?: "BLUE"
                if (animateCameraToTeam(newState, targetTeam)) {
                    cameraReturnActive = false
                    lastFramedTeamLabel = targetTeam
                }
            } else {
                val targetTeam = newState.activeTeamLabel.takeUnless { it == "NONE" }
                    ?: trackedTeamLabel
                    ?: "BLUE"
                if (phaseChanged || trackedTeamLabel == null || lastFramedTeamLabel != targetTeam) {
                    centerMapOnTeam(newState, targetTeam)
                    lastFramedTeamLabel = targetTeam
                }
            }
        }

        lastPhaseLabel = newState.phaseLabel
        clampMapOffset()
    }

    private fun centerMapOnTeam(newState: RenderState, teamLabel: String) {
        val (x, y) = if (teamLabel == "RED") {
            newState.redCatapultX to newState.redCatapultY
        } else {
            newState.blueCatapultX to newState.blueCatapultY
        }
        centerMapOnWorld(x, y)
    }

    private fun animateCameraToTeam(newState: RenderState, teamLabel: String): Boolean {
        val (x, y) = if (teamLabel == "RED") {
            newState.redCatapultX to newState.redCatapultY
        } else {
            newState.blueCatapultX to newState.blueCatapultY
        }
        val targetX = width / 2f - (x / WORLD_WIDTH * width) * mapScale
        val targetY = height / 2f - (y / WORLD_HEIGHT * height) * mapScale
        mapOffsetX += (targetX - mapOffsetX) * CAMERA_RETURN_LERP
        mapOffsetY += (targetY - mapOffsetY) * CAMERA_RETURN_LERP
        return abs(targetX - mapOffsetX) < CAMERA_RETURN_EPSILON &&
            abs(targetY - mapOffsetY) < CAMERA_RETURN_EPSILON
    }

    private fun centerMapOnWorld(worldX: Float, worldY: Float) {
        val sceneX = worldX / WORLD_WIDTH * width
        val sceneY = worldY / WORLD_HEIGHT * height
        mapOffsetX = width / 2f - sceneX * mapScale
        mapOffsetY = height / 2f - sceneY * mapScale
    }

    companion object {
        private const val MIN_MAP_SCALE = 1f
        private const val MAX_MAP_SCALE = 2.5f
        private const val AUTO_TRACK_SCALE = 1.25f
        private const val POST_IMPACT_HOLD_SECONDS = 0.4f
        private const val RENDER_STEP_SECONDS = 0.033f
        private const val CAMERA_RETURN_LERP = 0.18f
        private const val CAMERA_RETURN_EPSILON = 1f
        private const val PAN_START_DISTANCE = 12f
        private const val WORLD_WIDTH = 1_600f
        private const val WORLD_HEIGHT = 900f
    }
}
