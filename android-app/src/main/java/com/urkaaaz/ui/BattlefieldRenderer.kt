package com.urkaaaz.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.urkaaaz.contracts.FortressHitboxProfile

class BattlefieldRenderer(
    private val assets: BattlefieldAssetCatalog,
    private val drawThemeDecorations: (Canvas, BattlefieldTheme, Float) -> Unit,
    private val drawTiledTexture: (Canvas, Bitmap?, Float, Float, Float, Int) -> Unit,
    private val drawTexture: (Canvas, Bitmap?, Float, Float, Float) -> Unit,
    private val drawTextureAtBaseline: (Canvas, Bitmap?, Float, Float, Float) -> Unit,
    private val drawReloadIndicator: (Canvas, Float, Float, Float, Boolean) -> Unit,
    private val drawTrajectoryPreview: (Canvas) -> Unit,
    private val drawProjectileTrails: (Canvas) -> Unit,
    private val drawAimGesture: (Canvas, Float) -> Unit,
) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(55, 105, 190)
    }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(190, 65, 55)
    }
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun render(
        canvas: Canvas,
        state: RenderState,
        camera: CameraTransform,
        activeImpact: ImpactRenderState?,
        animationFrame: Int,
        impactFrame: Int,
        isAiming: Boolean,
        viewportWidth: Float,
        viewportHeight: Float,
        debugHitboxesVisible: Boolean,
    ) {
        canvas.save()
        canvas.scale(camera.scale, camera.scale)
        canvas.translate(
            camera.offsetX / camera.scale,
            camera.offsetY / camera.scale,
        )

        val theme = assets.themeFor(state.terrainBiome)
        drawBackground(canvas, theme, viewportWidth, viewportHeight)
        val groundTop = viewportHeight * 0.76f
        drawTiledTexture(canvas, theme.soil, groundTop, viewportHeight, 128f, theme.fallbackColor)
        drawTiledTexture(canvas, theme.cap, groundTop - 20f, groundTop + 6f, 150f, theme.fallbackColor)
        drawThemeDecorations(canvas, theme, groundTop)

        val blueFortressCenterX = worldToViewX(160f, viewportWidth)
        val redFortressCenterX = worldToViewX(WORLD_WIDTH - 160f, viewportWidth)
        val fortressBaseline = groundTop + 4f
        drawTextureAtBaseline(
            canvas,
            assets.blueFortress,
            blueFortressCenterX,
            fortressBaseline,
            FORTRESS_RENDER_WIDTH,
        )
        drawTextureAtBaseline(
            canvas,
            assets.redFortress,
            redFortressCenterX,
            fortressBaseline,
            FORTRESS_RENDER_WIDTH,
        )

        val blueCatapultCenterX = worldToViewX(state.blueCatapultX, viewportWidth)
        val redCatapultCenterX = worldToViewX(state.redCatapultX, viewportWidth)
        val catapultBaseline =
            fortressBaseline - FORTRESS_RENDER_HEIGHT * CATAPULT_PLATFORM_HEIGHT_RATIO
        drawTextureAtBaseline(
            canvas,
            assets.blueCatapult,
            blueCatapultCenterX,
            catapultBaseline,
            CATAPULT_RENDER_WIDTH,
        )
        drawTextureAtBaseline(
            canvas,
            assets.redCatapult,
            redCatapultCenterX,
            catapultBaseline,
            CATAPULT_RENDER_WIDTH,
        )
        drawReloadIndicator(
            canvas,
            blueCatapultCenterX,
            catapultBaseline - RELOAD_INDICATOR_OFFSET,
            state.playerReloadRemainingSeconds,
            state.playerProjectileActive,
        )
        drawReloadIndicator(
            canvas,
            redCatapultCenterX,
            catapultBaseline - RELOAD_INDICATOR_OFFSET,
            state.enemyReloadRemainingSeconds,
            state.projectiles.any { it.teamLabel == "RED" },
        )

        drawTrajectoryPreview(canvas)
        drawProjectileTrails(canvas)
        state.projectiles.forEach { projectile ->
            val projectileX = worldToViewX(projectile.x, viewportWidth)
                .coerceIn(0f, viewportWidth)
            val projectileY = worldToViewY(projectile.y, viewportHeight)
                .coerceIn(0f, groundTop)
            val frames = assets.animationFor(projectile.ammunitionType).flightFrames
            val frame = frames.getOrNull(animationFrame % frames.size.coerceAtLeast(1))
            drawTexture(canvas, frame, projectileX - 30f, projectileY - 30f, 60f)
        }
        drawUnits(canvas, state, viewportWidth, viewportHeight, groundTop, animationFrame)
        activeImpact?.let { impact ->
            val frames = assets.animationFor(impact.ammunitionType).impactFrames
            val frame = frames.getOrNull(impactFrame % frames.size.coerceAtLeast(1))
            val impactX = worldToViewX(impact.x, viewportWidth).coerceIn(0f, viewportWidth)
            val impactY = worldToViewY(impact.y, viewportHeight).coerceIn(0f, groundTop)
            drawTexture(canvas, frame, impactX - 54f, impactY - 54f, 108f)
        }

        drawHealthBar(
            canvas,
            blueFortressCenterX - FORTRESS_RENDER_WIDTH / 2f,
            fortressBaseline - FORTRESS_RENDER_HEIGHT - 18f,
            state.blueFortressHealth,
            state.blueFortressMaxHealth,
            bluePaint,
        )
        drawHealthBar(
            canvas,
            redFortressCenterX - FORTRESS_RENDER_WIDTH / 2f,
            fortressBaseline - FORTRESS_RENDER_HEIGHT - 18f,
            state.redFortressHealth,
            state.redFortressMaxHealth,
            redPaint,
        )
        drawFortressLabels(canvas, blueFortressCenterX, redFortressCenterX, fortressBaseline)
        if (debugHitboxesVisible) {
            drawDebugHitboxes(
                canvas = canvas,
                state = state,
                groundTop = groundTop,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            )
        }
        drawOutcome(canvas, state.outcomeLabel, viewportWidth, viewportHeight)
        if (isAiming) drawAimGesture(canvas, groundTop)
        canvas.restore()
    }

    private fun drawBackground(
        canvas: Canvas,
        theme: BattlefieldTheme,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        if (theme.background != null) {
            canvas.drawBitmap(
                theme.background,
                null,
                RectF(0f, 0f, viewportWidth, viewportHeight),
                backgroundPaint,
            )
        } else {
            backgroundPaint.shader = LinearGradient(
                0f,
                0f,
                0f,
                viewportHeight,
                Color.rgb(123, 177, 211),
                Color.rgb(242, 211, 154),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, viewportWidth, viewportHeight, backgroundPaint)
            backgroundPaint.shader = null
        }
    }

    private fun drawHealthBar(
        canvas: Canvas,
        left: Float,
        top: Float,
        health: Int,
        maxHealth: Int,
        color: Paint,
    ) {
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.DKGRAY }
        canvas.drawRect(left, top, left + FORTRESS_RENDER_WIDTH, top + 16f, background)
        val ratio = if (maxHealth > 0) {
            (health.toFloat() / maxHealth).coerceIn(0f, 1f)
        } else {
            0f
        }
        canvas.drawRect(left, top, left + FORTRESS_RENDER_WIDTH * ratio, top + 16f, color)
    }

    private fun drawUnits(
        canvas: Canvas,
        state: RenderState,
        viewportWidth: Float,
        viewportHeight: Float,
        groundTop: Float,
        animationFrame: Int,
    ) {
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val worldToViewportScale = viewportWidth / WORLD_WIDTH
        state.units.forEach { unit ->
            val centerX = worldToViewX(unit.x, viewportWidth)
            val baselineY = groundTop + 4f * worldToViewportScale
            val frames = assets.unitAnimationFrames(unit.unitType, unit.teamLabel, unit.moving)
            val frameOffset = if (frames.isEmpty()) {
                0
            } else {
                Math.floorMod(unit.entityId.hashCode(), frames.size)
            }
            val bitmap = frames.getOrNull(
                (animationFrame / 4 + frameOffset) % frames.size.coerceAtLeast(1),
            )
                ?: assets.unitBitmap(unit.unitType, unit.teamLabel)
            if (bitmap != null) {
                val spriteHeightWorld = when (unit.unitType) {
                    "SAPPER" -> 144f
                    "RAGING_BOAR" -> 220f * 1.6f
                    "SLINGMASTER" -> 170f
                    else -> 150f
                } * 0.96f * 0.308f
                val spriteHeight = spriteHeightWorld * worldToViewportScale
                val spriteWidth = spriteHeight * bitmap.width / bitmap.height
                canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(
                        centerX - spriteWidth / 2f,
                        baselineY - spriteHeight,
                        centerX + spriteWidth / 2f,
                        baselineY,
                    ),
                    spritePaint,
                )
            } else {
                unitPaint.color = if (unit.teamLabel == "BLUE") {
                    Color.rgb(65, 125, 220)
                } else {
                    Color.rgb(210, 70, 60)
                }
                canvas.drawCircle(centerX, baselineY - 30f * worldToViewportScale, 18f, unitPaint)
            }
        }
    }

    private fun drawDebugHitboxes(
        canvas: Canvas,
        state: RenderState,
        groundTop: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        val hitboxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 255, 80, 80)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }
        val fortressTop = groundTop - FORTRESS_RENDER_HEIGHT
        listOf(
            "BLUE FORTRESS" to (160f to fortressTop + FORTRESS_RENDER_HEIGHT / 2f),
            "RED FORTRESS" to (WORLD_WIDTH - 160f to fortressTop + FORTRESS_RENDER_HEIGHT / 2f),
        ).forEach { (label, center) ->
            val centerX = worldToViewX(center.first, viewportWidth)
            val centerY = center.second
            val centerWorldY = centerY / viewportHeight * WORLD_HEIGHT
            FortressHitboxProfile.rectangles.forEach { rectangle ->
                canvas.drawRect(
                    worldToViewX(center.first + rectangle.left, viewportWidth),
                    worldToViewY(centerWorldY + rectangle.top, viewportHeight),
                    worldToViewX(center.first + rectangle.right, viewportWidth),
                    worldToViewY(centerWorldY + rectangle.bottom, viewportHeight),
                    hitboxPaint,
                )
            }
            canvas.drawText(label, centerX - 70f, centerY - 100f, labelPaint)
        }

        state.projectiles.forEach { projectile ->
            val centerX = worldToViewX(projectile.x, viewportWidth)
            val centerY = worldToViewY(projectile.y, viewportHeight)
            canvas.drawCircle(centerX, centerY, PROJECTILE_DEBUG_RADIUS, hitboxPaint)
        }
    }

    private fun drawFortressLabels(
        canvas: Canvas,
        blueCenterX: Float,
        redCenterX: Float,
        baseline: Float,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }
        val labelY = baseline - FORTRESS_RENDER_HEIGHT - 25f
        canvas.drawText("YOU", blueCenterX - FORTRESS_RENDER_WIDTH / 2f, labelY, paint)
        canvas.drawText("ENEMY", redCenterX - FORTRESS_RENDER_WIDTH / 2f, labelY, paint)
    }

    private fun drawOutcome(
        canvas: Canvas,
        outcome: String?,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        outcome ?: return
        val overlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 20, 24, 28)
        }
        canvas.drawRect(0f, 0f, viewportWidth, viewportHeight, overlay)
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
        canvas.drawText(result, viewportWidth / 2f, viewportHeight / 2f, resultPaint)
    }

    private fun worldToViewX(worldX: Float, viewportWidth: Float): Float =
        worldX / WORLD_WIDTH * viewportWidth

    private fun worldToViewY(worldY: Float, viewportHeight: Float): Float =
        worldY / WORLD_HEIGHT * viewportHeight

    companion object {
        private const val WORLD_WIDTH = 1_600f
        private const val WORLD_HEIGHT = 900f
        private const val FORTRESS_RENDER_WIDTH = 280f
        private const val FORTRESS_RENDER_HEIGHT = 210f
        private const val CATAPULT_RENDER_WIDTH = 95f
        private const val RELOAD_INDICATOR_OFFSET = 72f
        private const val CATAPULT_PLATFORM_HEIGHT_RATIO = 0.40f
        private const val PROJECTILE_DEBUG_RADIUS = 30f
    }
}
