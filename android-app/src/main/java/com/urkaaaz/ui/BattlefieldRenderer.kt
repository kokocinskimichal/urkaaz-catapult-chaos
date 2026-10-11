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
        combatFeedback: List<CombatFeedbackRenderState>,
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
        drawCombatFeedback(canvas, combatFeedback, viewportWidth, viewportHeight, groundTop)

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
            val defeated = unit.actionState == "DEAD"
            val attacking = unit.actionState == "WINDING_UP" ||
                unit.actionState == "ATTACKING" ||
                unit.actionState == "RECOVERING" ||
                (
                    unit.actionState == "SEEKING_TARGET" &&
                        unit.targetId != null &&
                        !unit.moving &&
                        (unit.attackCycleId > 0 || unit.attackGroupId != null)
                    )
            val frames = if (defeated) {
                assets.unitDeathAnimationFrames(unit.unitType, unit.teamLabel)
            } else {
                assets.unitAnimationFrames(
                    unit.unitType,
                    unit.teamLabel,
                    unit.moving,
                    attacking,
                )
            }
            val frame = if (frames.isEmpty()) {
                null
            } else if (defeated) {
                frames[(animationFrame / 3).coerceIn(0, frames.lastIndex)]
            } else if (attacking) {
                frames[(unit.attackProgress * frames.size)
                    .toInt()
                    .coerceIn(0, frames.lastIndex)]
            } else {
                frames[
                    (animationFrame / 4 +
                        Math.floorMod(unit.entityId.hashCode(), frames.size)) %
                        frames.size
                ]
            }
            val bitmap = frame ?: assets.unitBitmap(unit.unitType, unit.teamLabel)
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
                drawUnitHealthBar(
                    canvas = canvas,
                    centerX = centerX,
                    centerY = baselineY - spriteHeight - 14f * worldToViewportScale,
                    width = UNIT_HEALTH_BAR_WIDTH_WORLD * worldToViewportScale,
                    health = unit.health,
                    maxHealth = unit.maxHealth,
                )
                if (unit.unitType == "SAPPER") {
                    drawSapperBombIndicator(
                        canvas,
                        centerX,
                        baselineY - spriteHeight - 30f * worldToViewportScale,
                        unit.sapperHasBomb,
                    )
                }
            } else {
                unitPaint.color = if (unit.teamLabel == "BLUE") {
                    Color.rgb(65, 125, 220)
                } else {
                    Color.rgb(210, 70, 60)
                }
                canvas.drawCircle(centerX, baselineY - 30f * worldToViewportScale, 18f, unitPaint)
                drawUnitHealthBar(
                    canvas = canvas,
                    centerX = centerX,
                    centerY = baselineY - 48f * worldToViewportScale,
                    width = UNIT_HEALTH_BAR_WIDTH_WORLD * worldToViewportScale,
                    health = unit.health,
                    maxHealth = unit.maxHealth,
                )
            }
        }
    }

    private fun drawUnitHealthBar(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        width: Float,
        health: Int,
        maxHealth: Int,
    ) {
        val fraction = if (maxHealth > 0) {
            (health.toFloat() / maxHealth).coerceIn(0f, 1f)
        } else {
            0f
        }

        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 25, 20, 16)
        }
        canvas.drawRoundRect(
            RectF(centerX - width / 2f, centerY - 4f, centerX + width / 2f, centerY + 4f),
            3f,
            3f,
            background,
        )
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                fraction <= 0f -> Color.rgb(100, 100, 100)
                fraction <= 0.3f -> Color.rgb(235, 70, 50)
                fraction <= 0.6f -> Color.rgb(245, 170, 45)
                else -> Color.rgb(85, 205, 90)
            }
        }
        canvas.drawRoundRect(
            RectF(
                centerX - width / 2f,
                centerY - 4f,
                centerX - width / 2f + width * fraction,
                centerY + 4f,
            ),
            3f,
            3f,
            fill,
        )
    }

    private fun drawCombatFeedback(
        canvas: Canvas,
        feedback: List<CombatFeedbackRenderState>,
        viewportWidth: Float,
        viewportHeight: Float,
        groundTop: Float,
    ) {
        feedback.forEach { item ->
            val x = worldToViewX(item.x, viewportWidth)
            val y = worldToViewY(item.y, viewportHeight).coerceIn(32f, groundTop)
            when (item.kind) {
                CombatFeedbackRenderState.Kind.DAMAGE -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(255, 224, 120)
                        textSize = 28f
                        typeface = Typeface.DEFAULT_BOLD
                        setShadowLayer(4f, 1f, 1f, Color.BLACK)
                    }

                    canvas.drawText("-${item.amount}", x - 18f, y - 42f, paint)
                    item.label?.let {
                        paint.color = Color.WHITE
                        paint.textSize = 20f
                        canvas.drawText(it, x - 18f, y - 66f, paint)
                    }
                }
                CombatFeedbackRenderState.Kind.HIT_FLASH -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(180, 255, 245, 190)
                        style = Paint.Style.STROKE
                        strokeWidth = 5f
                    }
                    canvas.drawCircle(x, y - 35f, 28f, paint)
                }
                CombatFeedbackRenderState.Kind.EXPLOSION -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(105, 255, 120, 35)
                        style = Paint.Style.FILL
                    }
                    canvas.drawCircle(
                        x,
                        y,
                        item.radius * viewportWidth / WORLD_WIDTH,
                        paint,
                    )
                }
                CombatFeedbackRenderState.Kind.SYNERGY -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(160, 235, 255)
                        textSize = 18f
                        typeface = Typeface.DEFAULT_BOLD
                        setShadowLayer(3f, 1f, 1f, Color.BLACK)
                    }
                    canvas.drawText("GROUP", x - 28f, y - 105f, paint)
                }
            }
        }
    }

    private fun drawSapperBombIndicator(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        hasBomb: Boolean,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (hasBomb) Color.rgb(255, 205, 65) else Color.GRAY
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
        }
        canvas.drawText(if (hasBomb) "BOMB" else "EMPTY", centerX - 24f, centerY, paint)
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
        private const val UNIT_HEALTH_BAR_WIDTH_WORLD = 46.2f
        private const val FORTRESS_RENDER_WIDTH = 280f
        private const val FORTRESS_RENDER_HEIGHT = 210f
        private const val CATAPULT_RENDER_WIDTH = 95f
        private const val RELOAD_INDICATOR_OFFSET = 72f
        private const val CATAPULT_PLATFORM_HEIGHT_RATIO = 0.40f
        private const val PROJECTILE_DEBUG_RADIUS = 30f
    }
}
