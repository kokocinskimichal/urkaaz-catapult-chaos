package com.urkaaaz.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.urkaaaz.contracts.FortressHitboxProfile
import com.urkaaaz.contracts.WorldDimensions
import com.urkaaaz.contracts.WorldScale

class BattlefieldRenderer(
    private val assets: BattlefieldAssetCatalog,
    private val drawTiledTexture: (Canvas, Bitmap?, Float, Float, Float, Int) -> Unit,
    private val drawTexture: (Canvas, Bitmap?, Float, Float, Float) -> Unit,
    private val drawTextureAtBaseline: (Canvas, Bitmap?, Float, Float, Float) -> Unit,
    private val drawTextureAtWorldSize: (Canvas, Bitmap?, Float, Float, Float, Float) -> Unit,
    private val drawReloadIndicator: (Canvas, Float, Float, Float, Boolean) -> Unit,
    private val drawTrajectoryPreview: (Canvas) -> Unit,
    private val drawProjectileTrails: (Canvas) -> Unit,
    private val drawAimGesture: (Canvas, Float) -> Unit,
) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
        drawTerrainSurface(
            canvas = canvas,
            bitmap = theme.soil,
            samples = state.terrainHeightSamples,
            sampleSpacing = state.terrainSampleSpacing,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            fallbackColor = theme.fallbackColor,
            textureTop = state.terrainHeightSamples.minOrNull()?.let {
                worldToViewY(it, viewportHeight) - 16f
            } ?: groundTop,
            textureBottom = viewportHeight,
        )
        drawMapDecorations(
            canvas = canvas,
            state = state,
            theme = theme,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            debugHitboxesVisible = debugHitboxesVisible,
        )
        drawRelics(canvas, state, viewportWidth, viewportHeight, debugHitboxesVisible)
        drawDeformedCap(
            canvas = canvas,
            bitmap = theme.cap,
            samples = state.terrainHeightSamples,
            sampleSpacing = state.terrainSampleSpacing,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            fallbackColor = theme.fallbackColor,
        )

        val fortressRenderWidth = worldToViewWidth(WorldScale.FORTRESS_WIDTH, viewportWidth)
        val fortressRenderHeight = worldToViewHeight(WorldScale.FORTRESS_HEIGHT, viewportHeight)
        val blueFortressCenterX = worldToViewX(state.blueFortressX, viewportWidth)
        val redFortressCenterX = worldToViewX(state.redFortressX, viewportWidth)
        val blueFortressGroundY = state.blueFortressFoundationY
        val redFortressGroundY = state.redFortressFoundationY
        val blueFortressBaseline = worldToViewY(blueFortressGroundY, viewportHeight)
        val redFortressBaseline = worldToViewY(redFortressGroundY, viewportHeight)
        val blueFortressCenterY = blueFortressBaseline - fortressRenderHeight / 2f
        val redFortressCenterY = redFortressBaseline - fortressRenderHeight / 2f
        val fortressHealthBarWidth = fortressRenderWidth * FORTRESS_HEALTH_BAR_WIDTH_RATIO
        val blueHealthBarLeft = blueFortressCenterX - fortressHealthBarWidth / 2f
        val redHealthBarLeft = redFortressCenterX - fortressHealthBarWidth / 2f
        val blueHealthBarTop =
            blueFortressBaseline - fortressRenderHeight -
                worldToViewHeight(FORTRESS_HEALTH_BAR_TOP_OFFSET, viewportHeight)
        val redHealthBarTop =
            redFortressBaseline - fortressRenderHeight -
                worldToViewHeight(FORTRESS_HEALTH_BAR_TOP_OFFSET, viewportHeight)
        drawTextureAtWorldSize(
            canvas,
            assets.blueFortress,
            blueFortressCenterX,
            blueFortressBaseline,
            fortressRenderWidth,
            fortressRenderHeight,
        )
        drawTextureAtWorldSize(
            canvas,
            assets.redFortress,
            redFortressCenterX,
            redFortressBaseline,
            fortressRenderWidth,
            fortressRenderHeight,
        )

        val blueCatapultCenterX = worldToViewX(state.blueCatapultX, viewportWidth)
        val redCatapultCenterX = worldToViewX(state.redCatapultX, viewportWidth)
        val catapultRenderWidth = worldToViewWidth(CATAPULT_RENDER_WIDTH, viewportWidth)
        val catapultBaseline = worldToViewY(state.blueCatapultY, viewportHeight)
        val redCatapultBaseline = worldToViewY(state.redCatapultY, viewportHeight)
        drawTextureAtBaseline(
            canvas,
            assets.blueCatapult,
            blueCatapultCenterX,
            catapultBaseline,
            catapultRenderWidth,
        )
        drawTextureAtBaseline(
            canvas,
            assets.redCatapult,
            redCatapultCenterX,
            redCatapultBaseline,
            catapultRenderWidth,
        )
        drawReloadIndicator(
            canvas,
            blueHealthBarLeft + fortressHealthBarWidth + FORTRESS_RELOAD_BADGE_GAP,
            blueHealthBarTop + HEALTH_BAR_HEIGHT / 2f,
            state.playerReloadRemainingSeconds,
            state.playerProjectileActive,
        )
        drawReloadIndicator(
            canvas,
            redHealthBarLeft - FORTRESS_RELOAD_BADGE_GAP,
            redHealthBarTop + HEALTH_BAR_HEIGHT / 2f,
            state.enemyReloadRemainingSeconds,
            state.projectiles.any { it.teamLabel == "RED" },
        )

        drawTrajectoryPreview(canvas)
        drawProjectileTrails(canvas)
        state.projectiles.forEach { projectile ->
            val projectileX = worldToViewX(projectile.x, viewportWidth)
                .coerceIn(0f, viewportWidth)
            val projectileY = worldToViewY(projectile.y, viewportHeight)
            val frames = assets.animationFor(projectile.ammunitionType).flightFrames
            val frame = frames.getOrNull(animationFrame % frames.size.coerceAtLeast(1))
            val projectileHeight = if (frame == null) {
                PROJECTILE_RENDER_SIZE
            } else {
                PROJECTILE_RENDER_SIZE * frame.height.toFloat() / frame.width.coerceAtLeast(1)
            }
            drawTexture(
                canvas,
                frame,
                projectileX - PROJECTILE_RENDER_SIZE / 2f,
                projectileY - projectileHeight / 2f,
                PROJECTILE_RENDER_SIZE,
            )
        }
        drawUnits(canvas, state, viewportWidth, viewportHeight, groundTop, animationFrame)
        activeImpact?.let { impact ->
            val frames = assets.animationFor(impact.ammunitionType).impactFrames
            val frame = frames.getOrNull(impactFrame % frames.size.coerceAtLeast(1))
            val impactX = worldToViewX(impact.x, viewportWidth).coerceIn(0f, viewportWidth)
            val impactY = worldToViewY(impact.y, viewportHeight)
            drawTexture(canvas, frame, impactX - 54f, impactY - 54f, 108f)
        }
        drawCombatFeedback(canvas, combatFeedback, viewportWidth, viewportHeight, groundTop)

        drawHealthBar(
            canvas,
            blueFortressCenterX,
            blueHealthBarTop + HEALTH_BAR_HEIGHT / 2f,
            fortressHealthBarWidth,
            state.blueFortressHealth,
            state.blueFortressMaxHealth,
        )
        drawHealthBar(
            canvas,
            redFortressCenterX,
            redHealthBarTop + HEALTH_BAR_HEIGHT / 2f,
            fortressHealthBarWidth,
            state.redFortressHealth,
            state.redFortressMaxHealth,
        )
        drawFortressLabels(
            canvas,
            blueHealthBarLeft,
            redHealthBarLeft + fortressHealthBarWidth,
            blueHealthBarTop,
            redHealthBarTop,
        )
        if (debugHitboxesVisible) {
            drawDebugHitboxes(
                canvas = canvas,
                state = state,
                blueFortressCenterX = blueFortressCenterX,
                blueFortressCenterY = blueFortressCenterY,
                redFortressCenterX = redFortressCenterX,
                redFortressCenterY = redFortressCenterY,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            )
        }
        drawOutcome(canvas, state.outcomeLabel, viewportWidth, viewportHeight)
        if (isAiming) drawAimGesture(canvas, groundTop)
        canvas.restore()
    }

    private fun drawRelics(
        canvas: Canvas,
        state: RenderState,
        viewportWidth: Float,
        viewportHeight: Float,
        debugHitboxesVisible: Boolean,
    ) {
        state.mapObjects
            .filter { it.relicSize == "SMALL" }
            .forEach { relic ->
                val bitmap = assets.relicBitmap(relic.assetKey) ?: return@forEach
                val centerX = worldToViewX(relic.x, viewportWidth)
                val centerY = worldToViewY(relic.y, viewportHeight)
                val size = (relic.relicRenderSize ?: 76f) * relic.scale
                canvas.save()
                canvas.rotate(relic.rotationDegrees, centerX, centerY)
                drawTexture(
                    canvas,
                    bitmap,
                    centerX - size / 2f,
                    centerY - size / 2f,
                    size,
                )
                if (debugHitboxesVisible) {
                    drawDebugAssetBounds(
                        canvas = canvas,
                        bitmap = bitmap,
                        left = centerX - size / 2f,
                        top = centerY - size / 2f,
                        width = size,
                        kind = BattlefieldAssetKind.RELIC,
                        rotationDegrees = relic.rotationDegrees,
                    )
                }
                canvas.restore()
            }
    }

    private fun drawMapDecorations(
        canvas: Canvas,
        state: RenderState,
        theme: BattlefieldTheme,
        viewportWidth: Float,
        viewportHeight: Float,
        debugHitboxesVisible: Boolean,
    ) {
        state.mapObjects
            .filter { it.relicSize == null }
            .forEach { decoration ->
                val resolved = assets.mapDecoration(
                    assetKey = decoration.assetKey,
                ) ?: return@forEach
                val centerX = worldToViewX(decoration.x, viewportWidth)
                val baselineY = worldToViewY(decoration.y, viewportHeight) +
                    DECORATION_GROUND_SINK
                val width = DECORATION_RENDER_SIZE * decoration.scale
                val height = width *
                    resolved.bitmap.height.toFloat() /
                    resolved.bitmap.width.coerceAtLeast(1)
                canvas.save()
                canvas.rotate(
                    decoration.rotationDegrees,
                    centerX,
                    baselineY - height / 2f,
                )
                drawTextureAtBaseline(
                    canvas,
                    resolved.bitmap,
                    centerX,
                    baselineY,
                    width,
                )
                canvas.restore()
                if (debugHitboxesVisible) {
                    drawDebugAssetBounds(
                        canvas = canvas,
                        bitmap = resolved.bitmap,
                        left = centerX - width / 2f,
                        top = baselineY - height,
                        width = width,
                        kind = resolved.kind,
                        rotationDegrees = decoration.rotationDegrees,
                    )
                }
            }
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

    private fun drawTerrainSurface(
        canvas: Canvas,
        bitmap: Bitmap?,
        samples: List<Float>,
        sampleSpacing: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        fallbackColor: Int,
        textureTop: Float,
        textureBottom: Float,
    ) {
        if (samples.isEmpty()) {
            drawTiledTexture(
                canvas,
                bitmap,
                textureTop,
                textureBottom,
                SOIL_TILE_WIDTH,
                fallbackColor,
            )
            return
        }
        val surface = Path().apply {
            moveTo(0f, worldToViewY(samples.first(), viewportHeight))
            samples.forEachIndexed { index, height ->
                lineTo(
                    worldToViewX(index * sampleSpacing, viewportWidth),
                    worldToViewY(height, viewportHeight),
                )
            }
            samples.indices.reversed().forEach { index ->
                lineTo(
                    worldToViewX(index * sampleSpacing, viewportWidth),
                    worldToViewY(WORLD_HEIGHT, viewportHeight),
                )
            }
            close()
        }
        canvas.save()
        canvas.clipPath(surface)
        drawTiledTexture(canvas, bitmap, textureTop, textureBottom, SOIL_TILE_WIDTH, fallbackColor)
        canvas.restore()
    }

    private fun drawDeformedCap(
        canvas: Canvas,
        bitmap: Bitmap?,
        samples: List<Float>,
        sampleSpacing: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        fallbackColor: Int,
    ) {
        if (samples.isEmpty() || bitmap == null) {
            drawTiledTexture(
                canvas,
                bitmap,
                viewportHeight * 0.76f - 20f,
                viewportHeight * 0.76f + 6f,
                150f,
                fallbackColor,
            )
            return
        }

        val sliceCount = 96
        val tileWorldWidth = 450f
        val sliceWorldWidth = tileWorldWidth / sliceCount
        val sourceSliceWidth = (bitmap.width / sliceCount).coerceAtLeast(1)
        val source = android.graphics.Rect()
        val destination = RectF()
        var sliceIndex = 0
        while (sliceIndex * sliceWorldWidth <= WORLD_WIDTH + sliceWorldWidth) {
            val worldX = sliceIndex * sliceWorldWidth
            val surfaceY = sampleHeightAt(samples, sampleSpacing, worldX)
            val sourceColumn = sliceIndex % sliceCount
            val sourceLeft = (sourceColumn * sourceSliceWidth)
                .coerceAtMost(bitmap.width - sourceSliceWidth)
            source.set(
                sourceLeft,
                0,
                (sourceLeft + sourceSliceWidth).coerceAtMost(bitmap.width),
                bitmap.height,
            )
            val left = worldToViewX(worldX, viewportWidth)
            val right = worldToViewX(worldX + sliceWorldWidth, viewportWidth)
            destination.set(
                left,
                worldToViewY(surfaceY - 48f, viewportHeight),
                right + 1f,
                worldToViewY(surfaceY + 56f, viewportHeight),
            )
            canvas.drawBitmap(
                bitmap,
                source,
                destination,
                null,
            )
            sliceIndex += 1
        }
    }

    private fun sampleHeightAt(
        samples: List<Float>,
        sampleSpacing: Float,
        worldX: Float,
    ): Float {
        val position = worldX.coerceIn(0f, WORLD_WIDTH) / sampleSpacing
        val left = position.toInt().coerceIn(0, samples.lastIndex)
        val right = (left + 1).coerceAtMost(samples.lastIndex)
        val fraction = (position - left).coerceIn(0f, 1f)
        return samples[left] + (samples[right] - samples[left]) * fraction
    }

    private fun terrainHeightAt(
        samples: List<Float>,
        sampleSpacing: Float,
        worldX: Float,
        fallbackWorldY: Float,
    ): Float = if (samples.isEmpty()) {
        fallbackWorldY
    } else {
        sampleHeightAt(samples, sampleSpacing, worldX)
    }

    private fun drawHealthBar(
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
        val left = centerX - width / 2f
        val top = centerY - HEALTH_BAR_HEIGHT / 2f
        val bounds = RectF(left, top, left + width, top + HEALTH_BAR_HEIGHT)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 25, 20, 16)
        }
        canvas.drawRoundRect(bounds, HEALTH_BAR_CORNER_RADIUS, HEALTH_BAR_CORNER_RADIUS, background)
        if (fraction > 0f) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when {
                    fraction <= 0.3f -> Color.rgb(235, 70, 50)
                    fraction <= 0.6f -> Color.rgb(245, 170, 45)
                    else -> Color.rgb(85, 205, 90)
                }
            }
            canvas.save()
            canvas.clipRect(left, top, left + width * fraction, top + HEALTH_BAR_HEIGHT)
            canvas.drawRoundRect(
                bounds,
                HEALTH_BAR_CORNER_RADIUS,
                HEALTH_BAR_CORNER_RADIUS,
                fill,
            )
            canvas.restore()
        }
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
            val baselineY =
                worldToViewY(unit.y, viewportHeight) + 4f * worldToViewportScale
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
                    "RAGING_BOAR" -> 220f * 1.6f * BOAR_SOURCE_CROP_HEIGHT_RATIO
                    "SLINGMASTER" -> 170f
                    else -> 150f
                } * 0.96f * 0.308f * WorldScale.UNIT_SCALE
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
                drawHealthBar(
                    canvas = canvas,
                    centerX = centerX,
                    centerY = baselineY - spriteHeight - 14f * worldToViewportScale,
                    width = UNIT_HEALTH_BAR_WIDTH_WORLD * WorldScale.UNIT_SCALE *
                        worldToViewportScale,
                    health = unit.health,
                    maxHealth = unit.maxHealth,
                )
                if (unit.unitType == "SAPPER") {
                    drawSapperBombIndicator(
                        canvas,
                        centerX,
                        baselineY - spriteHeight - 30f * WorldScale.UNIT_SCALE *
                            worldToViewportScale,
                        unit.sapperHasBomb,
                    )
                }
            } else {
                unitPaint.color = if (unit.teamLabel == "BLUE") {
                    Color.rgb(65, 125, 220)
                } else {
                    Color.rgb(210, 70, 60)
                }
                canvas.drawCircle(
                    centerX,
                    baselineY - 30f * WorldScale.UNIT_SCALE * worldToViewportScale,
                    18f * WorldScale.UNIT_SCALE * worldToViewportScale,
                    unitPaint,
                )
                drawHealthBar(
                    canvas = canvas,
                    centerX = centerX,
                    centerY = baselineY - 48f * WorldScale.UNIT_SCALE *
                        worldToViewportScale,
                    width = UNIT_HEALTH_BAR_WIDTH_WORLD * WorldScale.UNIT_SCALE *
                        worldToViewportScale,
                    health = unit.health,
                    maxHealth = unit.maxHealth,
                )
            }
        }
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
        blueFortressCenterX: Float,
        blueFortressCenterY: Float,
        redFortressCenterX: Float,
        redFortressCenterY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        val terrainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 255, 220, 70)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        if (state.terrainHeightSamples.isNotEmpty() && state.terrainSampleSpacing > 0f) {
            val terrainPath = Path()
            state.terrainHeightSamples.forEachIndexed { index, worldY ->
                val worldX = index * state.terrainSampleSpacing
                val x = worldToViewX(worldX, viewportWidth)
                val y = worldToViewY(worldY, viewportHeight)
                if (index == 0) {
                    terrainPath.moveTo(x, y)
                } else {
                    terrainPath.lineTo(x, y)
                }
            }
            canvas.drawPath(terrainPath, terrainPaint)
        }

        val hitboxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 255, 80, 80)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val fortressScaleX = viewportWidth / WORLD_WIDTH
        val fortressScaleY = viewportHeight / WORLD_HEIGHT
        listOf(
            blueFortressCenterX to blueFortressCenterY,
            redFortressCenterX to redFortressCenterY,
        ).forEach { (centerViewX, centerViewY) ->
            FortressHitboxProfile.rectangles.forEach { rectangle ->
                canvas.drawRect(
                    centerViewX + rectangle.left * fortressScaleX,
                    centerViewY + rectangle.top * fortressScaleY,
                    centerViewX + rectangle.right * fortressScaleX,
                    centerViewY + rectangle.bottom * fortressScaleY,
                    hitboxPaint,
                )
            }
        }

        state.projectiles.forEach { projectile ->
            val centerX = worldToViewX(projectile.x, viewportWidth)
            val centerY = worldToViewY(projectile.y, viewportHeight)
            canvas.drawCircle(centerX, centerY, PROJECTILE_DEBUG_RADIUS, hitboxPaint)
        }
    }

    private fun drawDebugAssetBounds(
        canvas: Canvas,
        bitmap: Bitmap,
        left: Float,
        top: Float,
        width: Float,
        kind: BattlefieldAssetKind,
        rotationDegrees: Float = 0f,
    ) {
        val height = width * bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (kind) {
                BattlefieldAssetKind.TREE -> Color.rgb(70, 255, 120)
                BattlefieldAssetKind.ROCK -> Color.rgb(190, 210, 255)
                BattlefieldAssetKind.PROP -> Color.rgb(80, 230, 255)
                BattlefieldAssetKind.RELIC -> Color.rgb(255, 100, 240)
            }
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.save()
        canvas.rotate(rotationDegrees, left + width / 2f, top + height / 2f)
        canvas.drawRect(left, top, left + width, top + height, paint)
        canvas.restore()
    }

    private fun drawFortressLabels(
        canvas: Canvas,
        blueBarLeft: Float,
        redBarRight: Float,
        blueBarTop: Float,
        redBarTop: Float,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            "YOU",
            blueBarLeft,
            blueBarTop - FORTRESS_LABEL_GAP,
            paint,
        )
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            "ENEMY",
            redBarRight,
            redBarTop - FORTRESS_LABEL_GAP,
            paint,
        )
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

    private fun worldToViewWidth(worldWidth: Float, viewportWidth: Float): Float =
        worldWidth / WORLD_WIDTH * viewportWidth

    private fun worldToViewHeight(worldHeight: Float, viewportHeight: Float): Float =
        worldHeight / WORLD_HEIGHT * viewportHeight

    companion object {
        private const val WORLD_WIDTH = WorldDimensions.WIDTH
        private const val WORLD_HEIGHT = WorldDimensions.HEIGHT
        private const val SOIL_TILE_WIDTH = 256f
        private const val HEALTH_BAR_HEIGHT = 8f
        private const val HEALTH_BAR_CORNER_RADIUS = 3f
        private const val FORTRESS_HEALTH_BAR_WIDTH_RATIO = 0.68f
        private const val FORTRESS_HEALTH_BAR_TOP_OFFSET = 18f
        private const val FORTRESS_RELOAD_BADGE_GAP = 24f
        private const val FORTRESS_LABEL_GAP = 6f
        private const val UNIT_HEALTH_BAR_WIDTH_WORLD = 46.2f
        private const val BOAR_SOURCE_CROP_HEIGHT_RATIO = 301f / 512f
        private const val CATAPULT_RENDER_WIDTH = WorldScale.CATAPULT_RENDER_WIDTH
        private const val PROJECTILE_DEBUG_RADIUS = 30f
        private const val PROJECTILE_RENDER_SIZE = 36f
        private const val DECORATION_RENDER_SIZE = 94f
        private const val DECORATION_GROUND_SINK = 12f
    }
}
