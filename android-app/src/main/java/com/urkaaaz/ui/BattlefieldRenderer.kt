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
import com.urkaaaz.contracts.FortressShieldGeometry
import com.urkaaaz.contracts.RunicBastionGeometry
import com.urkaaaz.contracts.WorldDimensions
import com.urkaaaz.contracts.WorldScale

class BattlefieldRenderer(
    private val assets: BattlefieldAssetCatalog,
    private val textTypeface: Typeface,
    private val drawTiledTexture: (Canvas, Bitmap?, Float, Float, Float, Int) -> Unit,
    private val drawTexture: (Canvas, Bitmap?, Float, Float, Float) -> Unit,
    private val drawTextureAtBaseline: (Canvas, Bitmap?, Float, Float, Float) -> Unit,
    private val drawTextureAtWorldSize: (Canvas, Bitmap?, Float, Float, Float, Float) -> Unit,
    private val drawReloadIndicator: (Canvas, Float, Float, Float, Boolean) -> Unit,
    private val drawTrajectoryPreview: (Canvas) -> Unit,
    private val drawProjectileTrails: (Canvas, Set<String>) -> Unit,
    private val drawAimGesture: (Canvas, Float) -> Unit,
) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fortressShieldPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun render(
        canvas: Canvas,
        state: RenderState,
        camera: CameraTransform,
        activeImpact: ImpactRenderState?,
        animationFrame: Int,
        deathAnimationClock: UnitDeathAnimationClock,
        impactFrame: Int,
        fortressShieldHitAnimation: FortressShieldHitAnimationFrame?,
        runicBastionPresentation: RunicBastionPresentationFrame,
        fireRainPresentation: FireRainPresentationFrame,
        plagueCauldronPresentation: PlagueCauldronPresentationFrame,
        isAiming: Boolean,
        viewportWidth: Float,
        viewportHeight: Float,
        debugHitboxesVisible: Boolean,
        combatFeedback: List<CombatFeedbackRenderState>,
        showOutcomeLabel: Boolean = true,
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
        val soilTextureTop = state.terrainHeightSamples.minOrNull()?.let {
            worldToViewY(it, viewportHeight) - 16f
        } ?: groundTop
        drawTerrainSurface(
            canvas = canvas,
            bitmap = theme.soil,
            samples = state.terrainHeightSamples,
            sampleSpacing = state.terrainSampleSpacing,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            fallbackColor = theme.fallbackColor,
            textureTop = soilTextureTop,
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
        val blueCatapultCenterX = worldToViewX(state.blueCatapultX, viewportWidth)
        val redCatapultCenterX = worldToViewX(state.redCatapultX, viewportWidth)
        val catapultRenderWidth = worldToViewWidth(CATAPULT_RENDER_WIDTH, viewportWidth)
        val catapultBaseline = worldToViewY(state.blueCatapultY, viewportHeight)
        val redCatapultBaseline = worldToViewY(state.redCatapultY, viewportHeight)
        val blueCatapultHeight = assets.blueCatapult?.let {
            catapultRenderWidth * it.height / it.width.coerceAtLeast(1)
        } ?: catapultRenderWidth
        val redCatapultHeight = assets.redCatapult?.let {
            catapultRenderWidth * it.height / it.width.coerceAtLeast(1)
        } ?: catapultRenderWidth
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
        val blueShieldBounds = drawFortressShield(
            canvas = canvas,
            health = state.blueFortressShieldHealth,
            animationFrame = animationFrame,
            teamLabel = "BLUE",
            hitAnimation = fortressShieldHitAnimation,
            centerX = blueFortressCenterX,
            centerY = blueFortressCenterY,
            width = fortressRenderWidth,
            height = fortressRenderHeight,
        )
        val redShieldBounds = drawFortressShield(
            canvas = canvas,
            health = state.redFortressShieldHealth,
            animationFrame = animationFrame,
            teamLabel = "RED",
            hitAnimation = fortressShieldHitAnimation,
            centerX = redFortressCenterX,
            centerY = redFortressCenterY,
            width = fortressRenderWidth,
            height = fortressRenderHeight,
        )
        blueShieldBounds?.let { bounds ->
            drawSoilOverShield(
                canvas = canvas,
                shieldBounds = bounds,
                bitmap = theme.soil,
                samples = state.terrainHeightSamples,
                sampleSpacing = state.terrainSampleSpacing,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                fallbackColor = theme.fallbackColor,
                textureTop = soilTextureTop,
            )
        }
        redShieldBounds?.let { bounds ->
            drawSoilOverShield(
                canvas = canvas,
                shieldBounds = bounds,
                bitmap = theme.soil,
                samples = state.terrainHeightSamples,
                sampleSpacing = state.terrainSampleSpacing,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                fallbackColor = theme.fallbackColor,
                textureTop = soilTextureTop,
            )
        }

        drawRunicBastions(
            canvas = canvas,
            bastions = state.runicBastions,
            presentation = runicBastionPresentation,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
        drawDeformedCap(
            canvas = canvas,
            bitmap = theme.cap,
            samples = state.terrainHeightSamples,
            sampleSpacing = state.terrainSampleSpacing,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            fallbackColor = theme.fallbackColor,
        )
        drawFireRainFields(
            canvas = canvas,
            fields = fireRainPresentation.fireFields,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )

        drawReloadIndicator(
            canvas,
            blueCatapultCenterX - catapultRenderWidth / 2f -
                RELOAD_INDICATOR_RADIUS - CATAPULT_RELOAD_BADGE_GAP,
            catapultBaseline - blueCatapultHeight / 2f,
            state.playerReloadRemainingSeconds,
            state.playerProjectileActive,
        )
        drawReloadIndicator(
            canvas,
            redCatapultCenterX + catapultRenderWidth / 2f +
                RELOAD_INDICATOR_RADIUS + CATAPULT_RELOAD_BADGE_GAP,
            redCatapultBaseline - redCatapultHeight / 2f,
            state.enemyReloadRemainingSeconds,
            state.projectiles.any { it.teamLabel == "RED" },
        )

        drawTrajectoryPreview(canvas)
        drawProjectileTrails(canvas, fireRainPresentation.hiddenProjectileIds)
        state.projectiles.forEach { projectile ->
            if (projectile.projectileId in fireRainPresentation.hiddenProjectileIds) {
                return@forEach
            }
            val projectileX = worldToViewX(projectile.x, viewportWidth)
                .coerceIn(0f, viewportWidth)
            val projectileY = worldToViewY(projectile.y, viewportHeight)
            val isFireRainFragment =
                projectile.projectileId in fireRainPresentation.fragmentProjectileIds
            val animation = if (isFireRainFragment) {
                null
            } else {
                assets.animationFor(projectile.ammunitionType)
            }
            val frames = animation?.flightFrames
                ?: assets.fireRainAnimations.fragmentFlightFrames
            val projectileRenderSize = if (isFireRainFragment) {
                FIRE_RAIN_FRAGMENT_RENDER_SIZE
            } else {
                requireNotNull(animation).flightRenderSize
            }
            val frameIndex = if (
                isFireRainFragment ||
                projectile.ammunitionType == FIRE_RAIN_AMMUNITION
            ) {
                fireRainPresentation.projectileFrameIndex
            } else {
                animationFrame
            }
            val frame = frames.getOrNull(frameIndex % frames.size.coerceAtLeast(1))
            val projectileHeight = if (frame == null) {
                projectileRenderSize
            } else {
                projectileRenderSize * frame.height.toFloat() / frame.width.coerceAtLeast(1)
            }
            drawTexture(
                canvas,
                frame,
                projectileX - projectileRenderSize / 2f,
                projectileY - projectileHeight / 2f,
                projectileRenderSize,
            )
        }
        drawFireRainSplitFlashes(
            canvas = canvas,
            splitFlashes = fireRainPresentation.splitFlashes,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
        drawUnits(
            canvas,
            state,
            viewportWidth,
            viewportHeight,
            groundTop,
            animationFrame,
            deathAnimationClock,
        )
        drawPlagueCauldronClouds(
            canvas = canvas,
            clouds = plagueCauldronPresentation.clouds,
            activeImpact = activeImpact,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
        activeImpact?.let { impact ->
            val animation = assets.animationFor(impact.ammunitionType)
            val frames = animation.impactFrames
            if (frames.isNotEmpty()) {
                val frame = frames[impactFrame % frames.size]
                val impactX = worldToViewX(impact.x, viewportWidth).coerceIn(0f, viewportWidth)
                val impactY = worldToViewY(impact.y, viewportHeight)
                if (
                    impact.ammunitionType == PLAGUE_CAULDRON_AMMUNITION &&
                    impact.radius > 0f
                ) {
                    val growthProgress = (
                        impactFrame.toFloat() /
                            PlagueCauldronVisualContract.IMPACT_GROWTH_END_FRAME
                        ).coerceIn(0f, 1f).let(::smoothstep)
                    val startWidth = animation.impactRenderSize
                    val startHeight =
                        startWidth * frame.height.toFloat() / frame.width.coerceAtLeast(1)
                    val targetWidth = impact.radius *
                        PlagueCauldronVisualContract.CANVAS_WIDTH_TO_RADIUS
                    val targetHeight = impact.radius *
                        PlagueCauldronVisualContract.CANVAS_HEIGHT_TO_RADIUS
                    drawTextureAtWorldSizeWithAlpha(
                        canvas = canvas,
                        bitmap = frame,
                        centerX = impactX,
                        baselineY = impactY,
                        width = worldToViewWidth(
                            lerp(startWidth, targetWidth, growthProgress),
                            viewportWidth,
                        ),
                        height = worldToViewHeight(
                            lerp(startHeight, targetHeight, growthProgress),
                            viewportHeight,
                        ),
                        opacity = plagueImpactOpacity(impactFrame),
                    )
                } else {
                    val impactRenderSize = animation.impactRenderSizeAt(impactFrame)
                    val impactRenderHeight =
                        impactRenderSize * frame.height.toFloat() / frame.width.coerceAtLeast(1)
                    drawTexture(
                        canvas,
                        frame,
                        impactX - impactRenderSize / 2f,
                        impactY - impactRenderHeight / 2f,
                        impactRenderSize,
                    )
                }
            }
        }
        drawCombatFeedback(canvas, combatFeedback, viewportWidth, viewportHeight, groundTop)

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
        if (showOutcomeLabel) {
            drawOutcome(canvas, state.outcomeLabel, viewportWidth, viewportHeight)
        }
        if (isAiming) drawAimGesture(canvas, groundTop)
        canvas.restore()
    }

    private fun drawFortressShield(
        canvas: Canvas,
        health: Int?,
        animationFrame: Int,
        teamLabel: String,
        hitAnimation: FortressShieldHitAnimationFrame?,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
    ): RectF? {
        val activeHit = hitAnimation?.takeIf { it.teamLabel == teamLabel }
        if (!FortressShieldPresentation.shouldRenderShield(health, activeHit != null)) return null
        val horizontalRadius =
            FortressShieldGeometry.horizontalRadius(width)
        val verticalRadius =
            FortressShieldGeometry.verticalRadius(height)
        val frame = activeHit?.let {
            assets.fortressShieldHitFrames[it.frameIndex]
        } ?: assets.fortressShieldDomeFrames[
            FortressShieldPresentation.frameIndex(animationFrame),
        ]
        val sprite = FortressShieldPresentation.spriteBounds(
            ellipseLeft = centerX - horizontalRadius,
            ellipseTop = centerY - verticalRadius,
            ellipseWidth = horizontalRadius * 2f,
            ellipseHeight = verticalRadius * 2f,
        )
        fortressShieldPaint.alpha = FortressShieldPresentation.ACTIVE_DOME_ALPHA
        val spriteBounds = RectF(sprite.left, sprite.top, sprite.right, sprite.bottom)
        val saveCount = canvas.save()
        if (activeHit != null && FortressShieldPresentation.shouldMirrorHitHorizontally(teamLabel)) {
            canvas.scale(-1f, 1f, centerX, centerY)
        }
        canvas.drawBitmap(
            frame,
            null,
            spriteBounds,
            fortressShieldPaint,
        )
        canvas.restoreToCount(saveCount)
        return spriteBounds
    }

    private fun drawSoilOverShield(
        canvas: Canvas,
        shieldBounds: RectF,
        bitmap: Bitmap?,
        samples: List<Float>,
        sampleSpacing: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        fallbackColor: Int,
        textureTop: Float,
    ) {
        val saveCount = canvas.save()
        canvas.clipRect(shieldBounds)
        if (samples.isEmpty()) {
            canvas.clipRect(0f, viewportHeight * 0.76f, viewportWidth, viewportHeight)
        }
        drawTerrainSurface(
            canvas = canvas,
            bitmap = bitmap,
            samples = samples,
            sampleSpacing = sampleSpacing,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            fallbackColor = fallbackColor,
            textureTop = textureTop,
            textureBottom = viewportHeight,
        )
        canvas.restoreToCount(saveCount)
    }

    private fun drawFireRainFields(
        canvas: Canvas,
        fields: List<FireRainFieldAnimation>,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        fields.forEach { field ->
            val frame = assets.fireRainAnimations.fireFieldFrames.getOrNull(field.frameIndex)
                ?: error("FIRE_RAIN fire field frame ${field.frameIndex} is unavailable")
            val centerX = worldToViewX(field.x, viewportWidth)
            val centerY = worldToViewY(field.y, viewportHeight)
            val canvasDiameter = field.radius * 2f * FIRE_RAIN_FIELD_CANVAS_DIAMETER_MULTIPLIER
            val width = worldToViewWidth(canvasDiameter, viewportWidth)
            val height = worldToViewHeight(canvasDiameter, viewportHeight)
            drawTextureAtWorldSize(
                canvas,
                frame,
                centerX,
                centerY + height * (1f - FIRE_RAIN_FIELD_GROUND_ANCHOR_Y_RATIO),
                width,
                height,
            )
        }
    }

    private fun drawRunicBastions(
        canvas: Canvas,
        bastions: List<RunicBastionRenderState>,
        presentation: RunicBastionPresentationFrame,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        bastions.forEach { bastion ->
            val idleFrameIndex = presentation.idleFrameIndices[bastion.entityId]
            val frame = idleFrameIndex?.let {
                assets.runicBastionIdleFrames.getOrNull(it)
                    ?: error("Runic Bastion idle frame $it is unavailable")
            } ?: run {
                val creationFrameIndex = presentation.creationFrameIndices[bastion.entityId]
                    ?: RunicBastionPresentation.FRAME_COUNT - 1
                assets.runicBastionCreationFrames.getOrNull(creationFrameIndex)
                    ?: error("Runic Bastion creation frame $creationFrameIndex is unavailable")
            }
            drawRunicBastionFrame(
                canvas = canvas,
                bastion = bastion,
                frame = frame,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            )
        }
        presentation.destructionAnimations.forEach { animation ->
            val frame = assets.runicBastionDestructionFrames.getOrNull(animation.frameIndex)
                ?: error(
                    "Runic Bastion destruction frame ${animation.frameIndex} is unavailable",
                )
            drawRunicBastionFrame(
                canvas = canvas,
                bastion = animation.bastion,
                frame = frame,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                visibleArtBounds = RunicBastionPresentation.DESTRUCTION_VISIBLE_ART_BOUNDS,
            )
        }
    }

    private fun drawRunicBastionFrame(
        canvas: Canvas,
        bastion: RunicBastionRenderState,
        frame: Bitmap,
        viewportWidth: Float,
        viewportHeight: Float,
        visibleArtBounds: RunicBastionVisibleArtBounds =
            RunicBastionPresentation.DEFAULT_VISIBLE_ART_BOUNDS,
    ) {
        val bounds = RunicBastionPresentation.spriteBounds(
            centerX = worldToViewX(bastion.centerX, viewportWidth),
            foundationY = worldToViewY(
                RunicBastionGeometry.raisedFoundationY(bastion.foundationY),
                viewportHeight,
            ),
            visibleWidth = worldToViewWidth(RunicBastionGeometry.WIDTH, viewportWidth),
            visibleHeight = worldToViewHeight(RunicBastionGeometry.HEIGHT, viewportHeight),
            visibleArtBounds = visibleArtBounds,
        )
        canvas.drawBitmap(
            frame,
            null,
            RectF(bounds.left, bounds.top, bounds.right, bounds.bottom),
            spritePaint,
        )
    }

    private fun drawPlagueCauldronClouds(
        canvas: Canvas,
        clouds: List<PlagueCauldronCloudAnimation>,
        activeImpact: ImpactRenderState?,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        clouds.forEach { cloud ->
            if (
                activeImpact?.ammunitionType == PLAGUE_CAULDRON_AMMUNITION &&
                cloud.sourceProjectileId == activeImpact.projectileId
            ) {
                return@forEach
            }
            val frame = assets.animationFor(PLAGUE_CAULDRON_AMMUNITION)
                .impactFrames.getOrNull(cloud.frameIndex)
                ?: error("PLAGUE_CAULDRON cloud frame ${cloud.frameIndex} is unavailable")
            val width = lerp(
                cloud.radius * PlagueCauldronVisualContract.GROWTH_START_WIDTH_TO_RADIUS,
                cloud.radius * PlagueCauldronVisualContract.CANVAS_WIDTH_TO_RADIUS,
                cloud.growthProgress,
            )
            val height = lerp(
                cloud.radius * PlagueCauldronVisualContract.GROWTH_START_HEIGHT_TO_RADIUS,
                cloud.radius * PlagueCauldronVisualContract.CANVAS_HEIGHT_TO_RADIUS,
                cloud.growthProgress,
            )
            drawTextureAtWorldSizeWithAlpha(
                canvas = canvas,
                bitmap = frame,
                centerX = worldToViewX(cloud.x, viewportWidth),
                baselineY = worldToViewY(cloud.y, viewportHeight),
                width = worldToViewWidth(width, viewportWidth),
                height = worldToViewHeight(height, viewportHeight),
                opacity = cloud.opacity,
            )
        }
    }

    private fun drawTextureAtWorldSizeWithAlpha(
        canvas: Canvas,
        bitmap: Bitmap?,
        centerX: Float,
        baselineY: Float,
        width: Float,
        height: Float,
        opacity: Float,
    ) {
        if (bitmap == null || opacity <= 0f) return
        val left = centerX - width / 2f
        val top = baselineY - height
        val saveCount = canvas.saveLayerAlpha(
            left,
            top,
            left + width,
            baselineY,
            (opacity.coerceIn(0f, 1f) * 255f).toInt(),
        )
        drawTextureAtWorldSize(canvas, bitmap, centerX, baselineY, width, height)
        canvas.restoreToCount(saveCount)
    }

    private fun plagueImpactOpacity(frameIndex: Int): Float {
        if (frameIndex <= PlagueCauldronVisualContract.IMPACT_OPACITY_FIRST_FRAME) {
            return 1f
        }
        val transitionProgress = (
            (frameIndex - PlagueCauldronVisualContract.IMPACT_OPACITY_FIRST_FRAME).toFloat() /
                (PlagueCauldronVisualContract.IMPACT_OPACITY_LAST_FRAME -
                    PlagueCauldronVisualContract.IMPACT_OPACITY_FIRST_FRAME)
            ).coerceIn(0f, 1f)
        return lerp(1f, PlagueCauldronVisualContract.MAX_OPACITY, transitionProgress)
    }

    private fun smoothstep(progress: Float): Float =
        progress * progress * (3f - 2f * progress)

    private fun lerp(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress

    private fun drawFireRainSplitFlashes(
        canvas: Canvas,
        splitFlashes: List<FireRainSplitFlash>,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        splitFlashes.forEach { flash ->
            val frame = assets.fireRainAnimations.splitFlashFrames.getOrNull(flash.frameIndex)
                ?: error("FIRE_RAIN split flash frame ${flash.frameIndex} is unavailable")
            val centerX = worldToViewX(flash.x, viewportWidth)
            val centerY = worldToViewY(flash.y, viewportHeight)
            val width = worldToViewWidth(FIRE_RAIN_SPLIT_FLASH_RENDER_SIZE, viewportWidth)
            val height = worldToViewHeight(FIRE_RAIN_SPLIT_FLASH_RENDER_SIZE, viewportHeight)
            drawTextureAtWorldSize(
                canvas,
                frame,
                centerX,
                centerY + height / 2f,
                width,
                height,
            )
        }
    }

    private fun drawRelics(
        canvas: Canvas,
        state: RenderState,
        viewportWidth: Float,
        viewportHeight: Float,
        debugHitboxesVisible: Boolean,
    ) {
        state.mapObjects
            .filter { it.relicSize == "SMALL" || it.relicSize == "MEDIUM" }
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
        deathAnimationClock: UnitDeathAnimationClock,
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
                deathAnimationClock.frameIndex(unit.entityId, frames.size)
                    ?.let(frames::get)
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
                val baseSpriteHeightWorld = when (unit.unitType) {
                    "SAPPER" -> 144f
                    "RAGING_BOAR" -> 220f * 1.6f * BOAR_SOURCE_CROP_HEIGHT_RATIO
                    "SLINGMASTER" -> 170f
                    else -> 150f
                } * 0.96f * 0.308f * WorldScale.UNIT_SCALE
                val usesGeneratedDefenderAnimation =
                    frame != null &&
                        unit.unitType == "DEFENDER" &&
                        (unit.teamLabel == "BLUE" || unit.teamLabel == "RED")
                val usesGeneratedDemolisherAnimation =
                    frame != null &&
                        unit.unitType == "DEMOLISHER" &&
                        (unit.teamLabel == "BLUE" || unit.teamLabel == "RED")
                val generatedAnimationCanvasScale = when {
                    usesGeneratedDefenderAnimation -> GENERATED_DEFENDER_CANVAS_SCALE
                    usesGeneratedDemolisherAnimation ->
                        GENERATED_DEMOLISHER_CANVAS_SCALE
                    else -> 1f
                }
                val spriteHeightWorld =
                    baseSpriteHeightWorld * generatedAnimationCanvasScale
                val spriteHeight = spriteHeightWorld * worldToViewportScale
                val spriteWidth = spriteHeight * bitmap.width / bitmap.height
                val generatedAnimationBottomPaddingRatio = when {
                    usesGeneratedDemolisherAnimation ->
                        GENERATED_DEMOLISHER_BOTTOM_PADDING_RATIO
                    !usesGeneratedDefenderAnimation -> 0f
                    defeated ->
                        GENERATED_BLUE_DEFENDER_DEATH_BOTTOM_PADDING_RATIO
                    unit.moving -> GENERATED_DEFENDER_JOG_BOTTOM_PADDING_RATIO
                    attacking ->
                        GENERATED_BLUE_DEFENDER_COMBAT_BOTTOM_PADDING_RATIO
                    else -> GENERATED_BLUE_DEFENDER_IDLE_BOTTOM_PADDING_RATIO
                }
                val transparentBottomPadding =
                    spriteHeight * generatedAnimationBottomPaddingRatio
                val healthBarSpriteHeightWorld = if (
                    usesGeneratedDefenderAnimation || usesGeneratedDemolisherAnimation
                ) {
                    baseSpriteHeightWorld
                } else {
                    spriteHeightWorld
                }
                if (
                    HasteOrbitingChevronPresentation.shouldRender(
                        unit.hasted,
                        unit.moving,
                        defeated,
                    )
                ) {
                    drawHasteOrbitingChevronEffect(
                        canvas = canvas,
                        unit = unit,
                        animationFrame = animationFrame,
                        centerX = centerX,
                        baselineY = baselineY,
                        unitSpriteWidth = spriteWidth,
                    )
                }
                canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(
                        centerX - spriteWidth / 2f,
                        baselineY - spriteHeight + transparentBottomPadding,
                        centerX + spriteWidth / 2f,
                        baselineY + transparentBottomPadding,
                    ),
                    spritePaint,
                )
                drawHealthBar(
                    canvas = canvas,
                    centerX = centerX,
                    centerY = baselineY -
                        (healthBarSpriteHeightWorld + 14f) * worldToViewportScale,
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

    private fun drawHasteOrbitingChevronEffect(
        canvas: Canvas,
        unit: UnitRenderState,
        animationFrame: Int,
        centerX: Float,
        baselineY: Float,
        unitSpriteWidth: Float,
    ) {
        val frame = assets.hasteOrbitingChevronFrames[
            HasteOrbitingChevronPresentation.frameIndex(animationFrame)
        ]
        val bounds =
            HasteOrbitingChevronPresentation.spriteBounds(centerX, baselineY, unitSpriteWidth)
        val saveCount = canvas.save()
        if (HasteOrbitingChevronPresentation.shouldMirrorHorizontally(unit.facingDirection)) {
            canvas.scale(-1f, 1f, centerX, baselineY)
        }
        canvas.drawBitmap(
            frame,
            null,
            RectF(bounds.left, bounds.top, bounds.right, bounds.bottom),
            spritePaint,
        )
        canvas.restoreToCount(saveCount)
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
                        typeface = textTypeface
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
                        typeface = textTypeface
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
            typeface = textTypeface
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
            typeface = textTypeface
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
        private const val RELOAD_INDICATOR_RADIUS = 12f
        private const val CATAPULT_RELOAD_BADGE_GAP = 8f
        private const val UNIT_HEALTH_BAR_WIDTH_WORLD = 46.2f
        private const val BOAR_SOURCE_CROP_HEIGHT_RATIO = 301f / 512f
        private const val GENERATED_DEFENDER_CANVAS_SCALE = 2f
        private const val GENERATED_DEMOLISHER_CANVAS_SCALE = 2f
        private const val GENERATED_DEMOLISHER_BOTTOM_PADDING_RATIO = 75f / 512f
        private const val GENERATED_BLUE_DEFENDER_IDLE_BOTTOM_PADDING_RATIO = 68f / 512f
        private const val GENERATED_DEFENDER_JOG_BOTTOM_PADDING_RATIO = 76f / 512f
        private const val GENERATED_BLUE_DEFENDER_COMBAT_BOTTOM_PADDING_RATIO = 69f / 512f
        private const val GENERATED_BLUE_DEFENDER_DEATH_BOTTOM_PADDING_RATIO = 70f / 512f
        private const val CATAPULT_RENDER_WIDTH = WorldScale.CATAPULT_RENDER_WIDTH
        private const val PROJECTILE_DEBUG_RADIUS = 30f
        private const val DECORATION_RENDER_SIZE = 94f
        private const val DECORATION_GROUND_SINK = 12f
        private const val FIRE_RAIN_AMMUNITION = "FIRE_RAIN"
        private const val PLAGUE_CAULDRON_AMMUNITION = "PLAGUE_CAULDRON"
        private const val FIRE_RAIN_FRAGMENT_RENDER_SIZE = 28f
        private const val FIRE_RAIN_SPLIT_FLASH_RENDER_SIZE = 120f
        // Generated frames are mostly transparent; align their visible base to terrain.
        private const val FIRE_RAIN_FIELD_CANVAS_DIAMETER_MULTIPLIER = 4f
        private const val FIRE_RAIN_FIELD_GROUND_ANCHOR_Y_RATIO = 374f / 512f
    }
}
