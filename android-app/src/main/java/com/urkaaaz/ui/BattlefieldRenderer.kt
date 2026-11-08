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
import android.util.Log
import com.urkaaaz.android.R
import com.urkaaaz.contracts.FortressHitboxProfile
import com.urkaaaz.contracts.FortressShieldGeometry
import com.urkaaaz.contracts.RunicBastionGeometry
import com.urkaaaz.contracts.UnitHitboxProfile
import com.urkaaaz.contracts.WorldDimensions
import com.urkaaaz.contracts.WorldScale

internal class BattlefieldRenderer(
    private val assets: BattlefieldAssetCatalog,
    private val textTypeface: Typeface,
    private val stringResolver: (Int, List<Any>) -> String,
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
    private val bossAnimationFrameHold = BossAnimationFrameHold<BossDrawableFrame>()
    private val unitDrawableFrameHold = UnitDrawableFrameHold<UnitDrawableFrame>(
        sizeOf = { it.bitmap.allocationByteCount },
    )
    private val unitSpriteFallbackLogStateByEntity = mutableMapOf<String, String>()

    private data class BossDrawableFrame(
        val bitmap: Bitmap,
        val animation: BossSpriteAnimationDefinition?,
    )

    private data class UnitDrawableFrame(
        val bitmap: Bitmap,
        val animationName: String,
        val spriteWidthWorld: Float,
        val spriteHeightWorld: Float,
        val bottomPaddingWorld: Float,
        val centerOffsetWorld: Float,
        val healthBarSpriteHeightWorld: Float,
    )

    fun render(
        canvas: Canvas,
        state: RenderState,
        camera: CameraTransform,
        activeImpact: ImpactRenderState?,
        animationFrame: Int,
        deathAnimationClock: UnitDeathAnimationClock,
        sapperReturnAnimationClock: SapperReturnAnimationClock,
        sapperMoveAnimationClock: SapperMoveAnimationClock,
        impactFrame: Int,
        fortressShieldHitAnimation: FortressShieldHitAnimationFrame?,
        runicBastionPresentation: RunicBastionPresentationFrame,
        bossPresentation: BossPresentationFrame,
        fireRainPresentation: FireRainPresentationFrame,
        plagueCauldronPresentation: PlagueCauldronPresentationFrame,
        sapperBombPresentation: SapperBombPresentationFrame,
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
        drawBoss(
            canvas = canvas,
            boss = state.boss,
            presentation = bossPresentation,
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
            val frame = if (frames.frameCount == 0) {
                null
            } else {
                frames.bitmapIfReady(frameIndex % frames.frameCount)
            }
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
        drawSapperBombFlights(
            canvas = canvas,
            flights = sapperBombPresentation.flights,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
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
            sapperReturnAnimationClock,
            sapperMoveAnimationClock,
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
            if (frames.frameCount > 0) {
                val frame = frames.bitmapIfReady(impactFrame % frames.frameCount)
                    ?: return@let
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
        drawSapperBombImpacts(
            canvas = canvas,
            impacts = sapperBombPresentation.impacts,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
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
        val frame = if (activeHit != null) {
            assets.fortressShieldHitFrames.bitmapIfReady(activeHit.frameIndex)
        } else {
            assets.fortressShieldDomeFrames.bitmapIfReady(
                FortressShieldPresentation.frameIndex(animationFrame),
            )
        } ?: return null
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
            val frame = assets.fireRainAnimations.fireFieldFrames
                .bitmapIfReady(field.frameIndex) ?: return@forEach
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
            val frame = if (idleFrameIndex != null) {
                assets.runicBastionIdleFrames.bitmapIfReady(idleFrameIndex)
            } else {
                val creationFrameIndex = presentation.creationFrameIndices[bastion.entityId]
                    ?: RunicBastionPresentation.FRAME_COUNT - 1
                assets.runicBastionCreationFrames.bitmapIfReady(creationFrameIndex)
            } ?: return@forEach

            drawRunicBastionFrame(
                canvas = canvas,
                bastion = bastion,
                frame = frame,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            )
        }
        presentation.destructionAnimations.forEach { animation ->
            val frame = assets.runicBastionDestructionFrames
                .bitmapIfReady(animation.frameIndex) ?: return@forEach
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

    private fun drawBoss(
        canvas: Canvas,
        boss: BossRenderState?,
        presentation: BossPresentationFrame,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        if (boss == null || (boss.health <= 0 && !presentation.deathPresentationActive)) {
            bossAnimationFrameHold.clear()
            return
        }
        val definition = BossPresentationCatalog.definition(boss.presentationKey)
        val deathAnimation = if (presentation.deathPresentationActive) {
            definition.deathAnimation()
        } else {
            null
        }
        val attackDirection = presentation.attackDirection
        val attackFrameIndex = presentation.attackFrameIndex
        val attackAnimation = if (
            deathAnimation == null &&
            attackDirection != null &&
            attackFrameIndex != null
        ) {
            definition.attackAnimation(attackDirection)
        } else {
            null
        }
        val walkAnimation = if (presentation.walkFrameIndex != null) {
            definition.walkAnimation
        } else {
            null
        }
        val requestedAnimation =
            deathAnimation ?: attackAnimation ?: walkAnimation ?: definition.idleAnimation
        val requestedFrame = when {
            deathAnimation != null -> assets.bossDeathFrame(
                presentationKey = boss.presentationKey,
                frameIndex = requireNotNull(presentation.deathAnimationFrameIndex),
            )
            attackAnimation != null -> assets.bossAttackFrame(
                presentationKey = boss.presentationKey,
                direction = requireNotNull(attackDirection),
                frameIndex = requireNotNull(attackFrameIndex),
            )
            walkAnimation != null -> assets.bossWalkFrame(
                presentationKey = boss.presentationKey,
                frameIndex = requireNotNull(presentation.walkFrameIndex),
            )
            definition.idleAnimation != null -> assets.bossIdleFrame(
                presentationKey = boss.presentationKey,
                frameIndex = presentation.idleFrameIndex ?: 0,
            )
            else -> null
        }
        val fallbackAnimation = if (walkAnimation != null) {
            definition.idleAnimation
        } else {
            requestedAnimation
        }
        val drawableFrame = if (requestedAnimation != null) {
            bossAnimationFrameHold.resolve(
                bossEntityId = boss.entityId,
                presentationKey = boss.presentationKey,
                currentFrame = requestedFrame?.let {
                    BossDrawableFrame(it, requestedAnimation)
                },
            ) ?: assets.bossSprite(boss.presentationKey, boss.phase)
                ?.let { BossDrawableFrame(it, fallbackAnimation) } ?: return
        } else {
            bossAnimationFrameHold.clear()
            val bitmap = assets.bossSprite(boss.presentationKey, boss.phase) ?: return
            BossDrawableFrame(bitmap, null)
        }
        val bitmap = drawableFrame.bitmap
        val animation = drawableFrame.animation
        val centerX = worldToViewX(boss.x, viewportWidth)
        val baselineY = worldToViewY(boss.groundY, viewportHeight)
        val hitboxWidth = worldToViewWidth(boss.collisionWidth, viewportWidth)
        val hitboxHeight = worldToViewHeight(boss.collisionHeight, viewportHeight)
        // Fit the reference art into the hitbox, so wider attack canvases do not
        // change the body size between idle and attack frames.
        val sourceAspect = animation?.referenceCanvasAspect
            ?: (bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
        val hitboxAspect = hitboxWidth / hitboxHeight
        val visualWidth: Float
        val visualHeight: Float
        if (sourceAspect >= hitboxAspect) {
            visualWidth = hitboxWidth
            visualHeight = hitboxWidth / sourceAspect
        } else {
            visualHeight = hitboxHeight
            visualWidth = hitboxHeight * sourceAspect
        }
        val presentationWidth = visualWidth * definition.visualScale
        val presentationHeight = visualHeight * definition.visualScale
        val drawBounds = animation?.let {
            BossPresentationLayout.frameDrawBounds(
                centerX = centerX,
                baselineY = baselineY,
                visualWidth = presentationWidth,
                visualHeight = presentationHeight,
                animation = it,
            )
        }
        if (drawBounds == null) {
            drawTextureAtWorldSize(
                canvas,
                bitmap,
                centerX,
                baselineY,
                presentationWidth,
                presentationHeight,
            )
        } else {
            drawTextureAtWorldSize(
                canvas,
                bitmap,
                drawBounds.centerX,
                drawBounds.baselineY,
                drawBounds.width,
                drawBounds.height,
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
                .impactFrames.bitmapIfReady(cloud.frameIndex) ?: return@forEach
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
            val frame = assets.fireRainAnimations.splitFlashFrames
                .bitmapIfReady(flash.frameIndex) ?: return@forEach
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
        sapperReturnAnimationClock: SapperReturnAnimationClock,
        sapperMoveAnimationClock: SapperMoveAnimationClock,
    ) {
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val worldToViewportScale = viewportWidth / WORLD_WIDTH
        val unitFrameKeys = state.units.mapTo(mutableSetOf()) { unit ->
            UnitDrawableFrameKey(
                matchId = state.matchId,
                matchGeneration = state.matchGeneration,
                entityId = unit.entityId,
                unitType = unit.unitType,
                teamLabel = unit.teamLabel,
            )
        }
        unitDrawableFrameHold.retainOnly(unitFrameKeys)
        val unitEntityIds = state.units.asSequence()
            .map(UnitRenderState::entityId)
            .toSet()
        unitSpriteFallbackLogStateByEntity.keys.retainAll(unitEntityIds)
        state.units.forEach { unit ->
            val centerX = worldToViewX(unit.x, viewportWidth)
            val baselineY =
                worldToViewY(unit.y, viewportHeight) + 4f * worldToViewportScale
            val defeated = unit.actionState == "DEAD"
            val deathAnimationPlaying = defeated &&
                !(unit.unitType == "DEMOLISHER" && unit.demolisherThrowFrameIndex != null)
            val attacking = unit.actionState == "WINDING_UP" ||
                unit.actionState == "ATTACKING" ||
                unit.actionState == "RECOVERING" ||
                (
                    unit.actionState == "SEEKING_TARGET" &&
                        unit.targetId != null &&
                        !unit.moving &&
                        (unit.attackCycleId > 0 || unit.attackGroupId != null)
                    )
            val usesDemolisherAttackAnimation =
                unit.unitType == "DEMOLISHER" &&
                    unit.demolisherThrowFrameIndex != null
            val isDemolisherAttackingUnit =
                unit.unitType == "DEMOLISHER" &&
                    unit.targetId != null &&
                    unit.targetId in unitEntityIds &&
                    !deathAnimationPlaying
            val movingAnimation =
                unit.moving || isDemolisherAttackingUnit
            val useAttackAnimation =
                if (unit.unitType == "DEMOLISHER") {
                    usesDemolisherAttackAnimation
                } else {
                    attacking
                }
            val sapperReturnPosition = if (
                !deathAnimationPlaying &&
                unit.unitType == "SAPPER" &&
                unit.actionState == "RETURNING"
            ) {
                val turnFrames = assets.sapperTurnAnimationFrames(unit.teamLabel)
                val returnFrames = assets.sapperReturnAnimationFrames(unit.teamLabel)
                sapperReturnAnimationClock.frame(
                    unit.entityId,
                    turnFrames.frameCount,
                    returnFrames.frameCount,
                )
            } else {
                null
            }
            val sapperUsesMoveAnimation =
                sapperReturnPosition == null &&
                    sapperMoveAnimationClock.shouldUseMoveAnimation(unit, attacking)
            val sapperReturnFrames = when (sapperReturnPosition?.phase) {
                SapperReturnAnimationFrame.Phase.TURN ->
                    assets.sapperTurnAnimationFrames(unit.teamLabel)
                SapperReturnAnimationFrame.Phase.RETURN ->
                    assets.sapperReturnAnimationFrames(unit.teamLabel)
                null -> null
            }
            val animationName = when {
                deathAnimationPlaying -> "death"
                sapperReturnPosition?.phase == SapperReturnAnimationFrame.Phase.TURN -> "turn"
                sapperReturnPosition?.phase == SapperReturnAnimationFrame.Phase.RETURN -> "return"
                usesDemolisherAttackAnimation -> "throw"
                useAttackAnimation -> "attack"
                unit.unitType == "SAPPER" && sapperUsesMoveAnimation -> "move"
                movingAnimation -> "move"
                else -> "idle"
            }
            val frames = if (deathAnimationPlaying) {
                assets.unitDeathAnimationFrames(unit.unitType, unit.teamLabel)
            } else if (sapperReturnPosition != null) {
                requireNotNull(sapperReturnFrames)
            } else {
                assets.unitAnimationFrames(
                    unit.unitType,
                    unit.teamLabel,
                    if (unit.unitType == "SAPPER") sapperUsesMoveAnimation else movingAnimation,
                    useAttackAnimation,
                )
            }
            val animationFrameIndex = if (frames.frameCount == 0) {
                null
            } else if (deathAnimationPlaying) {
                deathAnimationClock.frameIndex(unit.entityId, frames.frameCount)
            } else if (sapperReturnPosition != null) {
                sapperReturnPosition.index
            } else if (usesDemolisherAttackAnimation) {
                requireNotNull(unit.demolisherThrowFrameIndex)
                    .coerceIn(0, frames.frameCount - 1)
            } else if (attacking && unit.unitType != "DEMOLISHER") {
                (unit.attackProgress * frames.frameCount)
                    .toInt()
                    .coerceIn(0, frames.frameCount - 1)
            } else {
                val renderFramesPerAnimationFrame = if (
                    unit.unitType == "RAGING_BOAR" && unit.moving
                ) {
                    GENERATED_RAGING_BOAR_MOVE_RENDER_FRAMES_PER_FRAME
                } else {
                    DEFAULT_UNIT_ANIMATION_RENDER_FRAMES_PER_FRAME
                }
                val unitAnimationFrame = if (unit.moving || sapperUsesMoveAnimation) {
                    (animationFrame * state.gameplaySpeedMultiplier).toInt()
                } else {
                    animationFrame
                }
                (unitAnimationFrame / renderFramesPerAnimationFrame +
                    Math.floorMod(unit.entityId.hashCode(), frames.frameCount)) %
                    frames.frameCount
            }
            val animationRead = animationFrameIndex?.let(frames::requestFrame)
            val frame = animationRead?.bitmap
            val fallbackRead = if (frame == null) {
                assets.unitBitmapRead(unit.unitType, unit.teamLabel)
            } else {
                null
            }
            val bitmap = frame ?: fallbackRead?.bitmap
            val drawableFrameKey = UnitDrawableFrameKey(
                matchId = state.matchId,
                matchGeneration = state.matchGeneration,
                entityId = unit.entityId,
                unitType = unit.unitType,
                teamLabel = unit.teamLabel,
            )
            val lastReadyFrame = unitDrawableFrameHold.lastReady(drawableFrameKey)
            val animationFrameUnavailable = when (animationRead?.status) {
                BitmapReadStatus.PENDING,
                BitmapReadStatus.CACHE_MISS_AFTER_READY,
                BitmapReadStatus.QUEUE_REJECTED -> true
                else -> false
            }
            val shouldHoldLastReadyFrame =
                frame == null && animationFrameUnavailable && lastReadyFrame != null
            val bitmapForPresentation = bitmap.takeUnless { shouldHoldLastReadyFrame }
            val candidateFrame = bitmapForPresentation?.let {
                if (lastReadyFrame?.bitmap === it &&
                    lastReadyFrame.animationName == animationName
                ) {
                    lastReadyFrame
                } else {
                    createUnitDrawableFrame(
                        unit = unit,
                        bitmap = it,
                        animationName = animationName,
                        frameIsAnimated = frame != null,
                        attacking = attacking,
                        defeated = defeated,
                        deathAnimationPlaying = deathAnimationPlaying,
                        sapperUsesMoveAnimation = sapperUsesMoveAnimation,
                        sapperReturnPosition = sapperReturnPosition,
                        sapperReturnFrames = sapperReturnFrames,
                    )
                }
            }
            val drawableFrame = unitDrawableFrameHold.resolve(drawableFrameKey, candidateFrame)
            val isHoldingLastReadyFrame =
                drawableFrame != null && candidateFrame == null
            if (drawableFrame != null) {
                if (isHoldingLastReadyFrame) {
                    val heldState = listOf(
                        "held",
                        state.matchGeneration,
                        unit.unitType,
                        unit.teamLabel,
                        unit.actionState,
                        animationName,
                        animationRead?.status ?: BitmapReadStatus.NO_SEQUENCE,
                        animationRead?.assetKey,
                        fallbackRead?.status,
                        fallbackRead?.assetKey,
                    ).joinToString("|")
                    if (
                        assets.debugLoggingEnabled &&
                        unitSpriteFallbackLogStateByEntity[unit.entityId] != heldState
                    ) {
                        Log.d(
                            UNIT_ANIMATION_FALLBACK_LOG_TAG,
                            "unit_sprite_frame_held entity=${unit.entityId} " +
                                "type=${unit.unitType} team=${unit.teamLabel} " +
                                "state=${unit.actionState} requestedAnimation=$animationName " +
                                "heldAnimation=${drawableFrame.animationName} " +
                                "frame=${animationFrameIndex ?: "none"}/${frames.frameCount} " +
                                "frameStatus=${animationRead?.status ?: BitmapReadStatus.NO_SEQUENCE} " +
                                "frameAsset=${animationRead?.assetKey ?: "none"} " +
                                "fallbackStatus=${fallbackRead?.status ?: BitmapReadStatus.NO_SEQUENCE} " +
                                "fallbackAsset=${fallbackRead?.assetKey ?: "none"}",
                        )
                    }
                    unitSpriteFallbackLogStateByEntity[unit.entityId] = heldState
                } else {
                    unitSpriteFallbackLogStateByEntity.remove(unit.entityId)
                }
                val spriteHeight = drawableFrame.spriteHeightWorld * worldToViewportScale
                val spriteWidth = drawableFrame.spriteWidthWorld * worldToViewportScale
                val transparentBottomPadding =
                    drawableFrame.bottomPaddingWorld * worldToViewportScale
                val spriteCenterX =
                    centerX + drawableFrame.centerOffsetWorld * worldToViewportScale
                if (
                    HasteOrbitingChevronPresentation.shouldRender(
                        unit.hasted,
                        unit.moving,
                        deathAnimationPlaying,
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
                    drawableFrame.bitmap,
                    null,
                    RectF(
                        spriteCenterX - spriteWidth / 2f,
                        baselineY - spriteHeight + transparentBottomPadding,
                        spriteCenterX + spriteWidth / 2f,
                        baselineY + transparentBottomPadding,
                    ),
                    spritePaint,
                )
                drawHealthBar(
                    canvas = canvas,
                    centerX = centerX,
                    centerY = baselineY -
                        (drawableFrame.healthBarSpriteHeightWorld + 14f) *
                        worldToViewportScale,
                    width = UNIT_HEALTH_BAR_WIDTH_WORLD * WorldScale.UNIT_SCALE *
                        worldToViewportScale,
                    health = unit.health,
                    maxHealth = unit.maxHealth,
                )
                if (unit.unitType == "SAPPER") {
                    drawSapperBombIndicator(
                        canvas,
                        centerX,
                        baselineY -
                            (drawableFrame.healthBarSpriteHeightWorld +
                                30f * WorldScale.UNIT_SCALE) *
                            worldToViewportScale,
                        unit.sapperHasBomb,
                    )
                }
            } else {
                val resolvedFallbackRead = requireNotNull(fallbackRead)
                val frameStatus = animationRead?.status ?: BitmapReadStatus.NO_SEQUENCE
                val fallbackState = listOf(
                    "fallback",
                    state.matchGeneration,
                    unit.unitType,
                    unit.teamLabel,
                    unit.actionState,
                    animationName,
                    frameStatus,
                    resolvedFallbackRead.status,
                ).joinToString("|")
                if (
                    assets.debugLoggingEnabled &&
                    unitSpriteFallbackLogStateByEntity[unit.entityId] != fallbackState
                ) {
                    Log.w(
                        UNIT_ANIMATION_FALLBACK_LOG_TAG,
                        "unit_sprite_fallback entity=${unit.entityId} " +
                            "type=${unit.unitType} team=${unit.teamLabel} " +
                            "state=${unit.actionState} moving=${unit.moving} " +
                            "attacking=$attacking animation=$animationName " +
                            "frame=${animationFrameIndex ?: "none"}/${frames.frameCount} " +
                            "frameStatus=$frameStatus " +
                            "frameAsset=${animationRead?.assetKey ?: "none"} " +
                            "fallbackStatus=${resolvedFallbackRead.status} " +
                            "fallbackAsset=${resolvedFallbackRead.assetKey ?: "none"}",
                    )
                }
                unitSpriteFallbackLogStateByEntity[unit.entityId] = fallbackState
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

    private fun createUnitDrawableFrame(
        unit: UnitRenderState,
        bitmap: Bitmap,
        animationName: String,
        frameIsAnimated: Boolean,
        attacking: Boolean,
        defeated: Boolean,
        deathAnimationPlaying: Boolean,
        sapperUsesMoveAnimation: Boolean,
        sapperReturnPosition: SapperReturnAnimationFrame?,
        sapperReturnFrames: BitmapFrameSequence?,
    ): UnitDrawableFrame {
        val baseSpriteHeightWorld = when (unit.unitType) {
            "SAPPER" -> 144f
            "RAGING_BOAR" -> 220f * 1.6f * BOAR_SOURCE_CROP_HEIGHT_RATIO
            "SLINGMASTER" -> 170f
            else -> 150f
        } * 0.96f * 0.308f * WorldScale.UNIT_SCALE
        val usesBlueOrRedTexture = unit.teamLabel == "BLUE" || unit.teamLabel == "RED"
        val usesGeneratedDefenderAnimation =
            frameIsAnimated && unit.unitType == "DEFENDER" && usesBlueOrRedTexture
        val usesGeneratedDemolisherAnimation =
            frameIsAnimated && unit.unitType == "DEMOLISHER" && usesBlueOrRedTexture
        val usesGeneratedSapperAnimation =
            frameIsAnimated &&
                !deathAnimationPlaying &&
                unit.unitType == "SAPPER" &&
                usesBlueOrRedTexture
        val usesGeneratedRagingBoarIdleAnimation =
            unit.unitType == "RAGING_BOAR" &&
                usesBlueOrRedTexture &&
                !deathAnimationPlaying &&
                (!frameIsAnimated || (!unit.moving && !attacking))
        val usesGeneratedRagingBoarAttackAnimation =
            frameIsAnimated &&
                unit.unitType == "RAGING_BOAR" &&
                usesBlueOrRedTexture &&
                !deathAnimationPlaying &&
                attacking
        val usesGeneratedRagingBoarMoveAnimation =
            frameIsAnimated &&
                unit.unitType == "RAGING_BOAR" &&
                usesBlueOrRedTexture &&
                !deathAnimationPlaying &&
                unit.moving &&
                !attacking
        val usesGeneratedRagingBoarDeathAnimation =
            frameIsAnimated &&
                unit.unitType == "RAGING_BOAR" &&
                usesBlueOrRedTexture &&
                deathAnimationPlaying
        val usesGeneratedSapperAttackAnimation = usesGeneratedSapperAnimation && attacking
        val usesGeneratedSapperMoveAnimation =
            usesGeneratedSapperAnimation && sapperUsesMoveAnimation
        val usesGeneratedSapperTurnAnimation =
            usesGeneratedSapperAnimation &&
                sapperReturnPosition?.phase == SapperReturnAnimationFrame.Phase.TURN
        val usesGeneratedSapperReturnAnimation =
            usesGeneratedSapperAnimation &&
                sapperReturnPosition?.phase == SapperReturnAnimationFrame.Phase.RETURN
        val usesGeneratedSlingmasterAnimation =
            frameIsAnimated &&
                unit.unitType == "SLINGMASTER" &&
                usesBlueOrRedTexture &&
                !deathAnimationPlaying
        val usesGeneratedSlingmasterDeathAnimation =
            frameIsAnimated &&
                unit.unitType == "SLINGMASTER" &&
                usesBlueOrRedTexture &&
                deathAnimationPlaying
        val usesGeneratedSlingmasterAttackAnimation =
            usesGeneratedSlingmasterAnimation && attacking
        val generatedAnimationCanvasScale = when {
            usesGeneratedDefenderAnimation -> GENERATED_DEFENDER_CANVAS_SCALE
            usesGeneratedSlingmasterDeathAnimation ->
                GENERATED_SLINGMASTER_DEATH_CANVAS_SCALE
            usesGeneratedSlingmasterAttackAnimation ->
                GENERATED_SLINGMASTER_ATTACK_CANVAS_SCALE
            usesGeneratedSlingmasterAnimation ->
                GENERATED_SLINGMASTER_CANVAS_SCALE
            usesGeneratedRagingBoarDeathAnimation ->
                GENERATED_RAGING_BOAR_DEATH_CANVAS_SCALE
            usesGeneratedRagingBoarIdleAnimation ||
                usesGeneratedRagingBoarMoveAnimation ||
                usesGeneratedRagingBoarAttackAnimation ->
                GENERATED_RAGING_BOAR_IDLE_CANVAS_SCALE
            usesGeneratedDemolisherAnimation -> GENERATED_DEMOLISHER_CANVAS_SCALE
            usesGeneratedSapperTurnAnimation ->
                sapperTurnCanvasScale(
                    frameIndex = requireNotNull(sapperReturnPosition).index,
                    frameCount = requireNotNull(sapperReturnFrames).frameCount,
                )
            usesGeneratedSapperReturnAnimation -> GENERATED_SAPPER_RETURN_CANVAS_SCALE
            usesGeneratedSapperAttackAnimation -> GENERATED_SAPPER_ATTACK_CANVAS_SCALE
            usesGeneratedSapperMoveAnimation -> GENERATED_SAPPER_MOVE_CANVAS_SCALE
            usesGeneratedSapperAnimation -> GENERATED_SAPPER_CANVAS_SCALE
            else -> 1f
        }
        val spriteHeightWorld = baseSpriteHeightWorld * generatedAnimationCanvasScale
        val spriteWidthWorld = spriteHeightWorld * bitmap.width / bitmap.height
        val generatedAnimationBottomPaddingRatio = when {
            usesGeneratedSlingmasterDeathAnimation ->
                GENERATED_SLINGMASTER_DEATH_BOTTOM_PADDING_RATIO
            usesGeneratedSlingmasterAttackAnimation ->
                GENERATED_SLINGMASTER_ATTACK_BOTTOM_PADDING_RATIO
            usesGeneratedSlingmasterAnimation ->
                GENERATED_SLINGMASTER_BOTTOM_PADDING_RATIO
            usesGeneratedRagingBoarDeathAnimation ->
                GENERATED_RAGING_BOAR_DEATH_BOTTOM_PADDING_RATIO
            usesGeneratedRagingBoarIdleAnimation ||
                usesGeneratedRagingBoarMoveAnimation ||
                usesGeneratedRagingBoarAttackAnimation ->
                GENERATED_RAGING_BOAR_IDLE_BOTTOM_PADDING_RATIO
            usesGeneratedDemolisherAnimation ->
                GENERATED_DEMOLISHER_BOTTOM_PADDING_RATIO
            usesGeneratedSapperTurnAnimation -> GENERATED_SAPPER_TURN_BOTTOM_PADDING_RATIO
            usesGeneratedSapperReturnAnimation -> GENERATED_SAPPER_RETURN_BOTTOM_PADDING_RATIO
            usesGeneratedSapperAttackAnimation -> GENERATED_SAPPER_ATTACK_BOTTOM_PADDING_RATIO
            usesGeneratedSapperMoveAnimation -> GENERATED_SAPPER_MOVE_BOTTOM_PADDING_RATIO
            usesGeneratedSapperAnimation -> GENERATED_SAPPER_BOTTOM_PADDING_RATIO
            !usesGeneratedDefenderAnimation -> 0f
            defeated -> GENERATED_BLUE_DEFENDER_DEATH_BOTTOM_PADDING_RATIO
            unit.moving -> GENERATED_DEFENDER_JOG_BOTTOM_PADDING_RATIO
            attacking -> GENERATED_BLUE_DEFENDER_COMBAT_BOTTOM_PADDING_RATIO
            else -> GENERATED_BLUE_DEFENDER_IDLE_BOTTOM_PADDING_RATIO
        }
        val centerOffsetWorld = if (usesGeneratedSlingmasterAttackAnimation) {
            val teamDirection = if (unit.teamLabel == "RED") -1f else 1f
            spriteWidthWorld *
                GENERATED_SLINGMASTER_ATTACK_HORIZONTAL_OFFSET_RATIO *
                teamDirection
        } else {
            0f
        }
        val healthBarSpriteHeightWorld = if (
            usesGeneratedDefenderAnimation ||
            usesGeneratedRagingBoarDeathAnimation ||
            usesGeneratedRagingBoarIdleAnimation ||
            usesGeneratedRagingBoarMoveAnimation ||
            usesGeneratedRagingBoarAttackAnimation ||
            usesGeneratedDemolisherAnimation ||
            usesGeneratedSapperAnimation ||
            usesGeneratedSlingmasterAnimation ||
            usesGeneratedSlingmasterDeathAnimation
        ) {
            baseSpriteHeightWorld
        } else {
            spriteHeightWorld
        }
        return UnitDrawableFrame(
            bitmap = bitmap,
            animationName = animationName,
            spriteWidthWorld = spriteWidthWorld,
            spriteHeightWorld = spriteHeightWorld,
            bottomPaddingWorld = spriteHeightWorld * generatedAnimationBottomPaddingRatio,
            centerOffsetWorld = centerOffsetWorld,
            healthBarSpriteHeightWorld = healthBarSpriteHeightWorld,
        )
    }

    private fun drawHasteOrbitingChevronEffect(
        canvas: Canvas,
        unit: UnitRenderState,
        animationFrame: Int,
        centerX: Float,
        baselineY: Float,
        unitSpriteWidth: Float,
    ) {
        val frame = assets.hasteOrbitingChevronFrames.bitmapIfReady(
            HasteOrbitingChevronPresentation.frameIndex(animationFrame),
        ) ?: return
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

                    val damageLabel = stringResolver(
                        R.string.match_damage_amount,
                        listOf(item.amount),
                    )
                    canvas.drawText(
                        damageLabel,
                        x - paint.measureText(damageLabel) / 2f,
                        y - 42f,
                        paint,
                    )
                    item.synergyMultiplier?.let { multiplier ->
                        paint.color = Color.WHITE
                        paint.textSize = 20f
                        val synergyLabel = stringResolver(
                            R.string.match_damage_synergy_multiplier,
                            listOf(multiplier),
                        )
                        canvas.drawText(
                            synergyLabel,
                            x - paint.measureText(synergyLabel) / 2f,
                            y - 66f,
                            paint,
                        )
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
                    val explosionFrame = if (item.sourceUnitType == "DEMOLISHER") {
                        item.animationFrameIndex
                            ?.let(assets.demolisherExplosionFrames::bitmapIfReady)
                    } else {
                        null
                    }
                    if (explosionFrame != null) {
                        val explosionSize =
                            item.radius * 2f * viewportWidth / WORLD_WIDTH
                        val explosionHeight =
                            explosionSize * explosionFrame.height / explosionFrame.width
                        val destination = RectF(
                            x - explosionSize / 2f,
                            y - explosionHeight / 2f,
                            x + explosionSize / 2f,
                            y + explosionHeight / 2f,
                        )
                        canvas.drawBitmap(
                            explosionFrame,
                            null,
                            destination,
                            spritePaint,
                        )
                    } else {
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
                }
                CombatFeedbackRenderState.Kind.SYNERGY -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(160, 235, 255)
                        textSize = 18f
                        typeface = textTypeface
                        setShadowLayer(3f, 1f, 1f, Color.BLACK)
                    }
                    val label = stringResolver(R.string.match_battlefield_group, emptyList())
                    canvas.drawText(label, x - paint.measureText(label) / 2f, y - 105f, paint)
                }
                CombatFeedbackRenderState.Kind.GOLD_REWARD -> {
                    val goldAmount = item.goldAmount ?: return@forEach
                    val progress = item.progress.coerceIn(0f, 1f)
                    val easedProgress = 1f - (1f - progress) * (1f - progress) *
                        (1f - progress)
                    val fade = if (progress < 0.7f) {
                        1f
                    } else {
                        ((1f - progress) / 0.3f).coerceIn(0f, 1f)
                    }
                    val label = stringResolver(
                        R.string.match_reward_gold_floating,
                        listOf(goldAmount),
                    )
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(255, 220, 110)
                        alpha = (255f * fade).toInt()
                        textSize = 26f
                        typeface = Typeface.create(textTypeface, Typeface.BOLD)
                        setShadowLayer(5f, 1f, 2f, Color.BLACK)
                    }
                    val liftedY = y - 50f - easedProgress * 70f
                    canvas.drawText(label, x - paint.measureText(label) / 2f, liftedY, paint)
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
        val label = stringResolver(
            if (hasBomb) R.string.match_battlefield_bomb else R.string.match_battlefield_empty,
            emptyList(),
        )
        canvas.drawText(label, centerX - paint.measureText(label) / 2f, centerY, paint)
    }

    private fun drawSapperBombFlights(
        canvas: Canvas,
        flights: List<SapperBombFlightFrame>,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        if (flights.isEmpty()) return
        val animation = assets.animationFor(SIEGE_BOMB_AMMUNITION)
        flights.forEach { flight ->
            if (animation.flightFrames.frameCount == 0) return@forEach
            val bitmap = animation.flightFrames.bitmapIfReady(
                flight.frameIndex % animation.flightFrames.frameCount,
            ) ?: return@forEach
            val centerX = worldToViewX(flight.x, viewportWidth)
            val centerY = worldToViewY(flight.y, viewportHeight)
            val width = worldToViewWidth(SAPPER_BOMB_FLIGHT_RENDER_SIZE_WORLD, viewportWidth)
            val height = width * bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1)
            canvas.save()
            canvas.rotate(flight.rotationDegrees, centerX, centerY)
            drawTexture(
                canvas,
                bitmap,
                centerX - width / 2f,
                centerY - height / 2f,
                width,
            )
            canvas.restore()
        }
    }

    private fun drawSapperBombImpacts(
        canvas: Canvas,
        impacts: List<SapperBombImpactFrame>,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        if (impacts.isEmpty()) return
        val animation = assets.animationFor(POWDER_BARREL_AMMUNITION)
        val frames = animation.impactFrames
        if (frames.frameCount == 0) return
        val impactSize = animation.impactRenderSizeAt(frames.frameCount - 1) *
            SAPPER_BOMB_IMPACT_RENDER_SCALE
        impacts.forEach { impact ->
            val bitmap = frames.bitmapIfReady(impact.frameIndex) ?: return@forEach
            val centerX = worldToViewX(impact.x, viewportWidth)
            val centerY = worldToViewY(impact.y, viewportHeight)
            val height = impactSize * bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1)
            drawTexture(
                canvas,
                bitmap,
                centerX - impactSize / 2f,
                centerY - height / 2f,
                impactSize,
            )
        }
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

        state.runicBastions.forEach { bastion ->
            val centerX = worldToViewX(bastion.centerX, viewportWidth)
            val centerY = worldToViewY(bastion.centerY, viewportHeight)
            val halfWidth = worldToViewWidth(
                RunicBastionGeometry.WIDTH / 2f,
                viewportWidth,
            )
            val halfHeight = worldToViewHeight(
                RunicBastionGeometry.HEIGHT / 2f,
                viewportHeight,
            )
            canvas.drawRect(
                centerX - halfWidth,
                centerY - halfHeight,
                centerX + halfWidth,
                centerY + halfHeight,
                hitboxPaint,
            )
        }

        state.units.forEach { unit ->
            val centerX = worldToViewX(unit.x, viewportWidth)
            val hitbox = UnitHitboxProfile.forUnitType(unit.unitType)
            val halfWidth = worldToViewWidth(hitbox.halfWidth, viewportWidth)
            val top = worldToViewY(unit.y - hitbox.height, viewportHeight)
            val bottom = worldToViewY(unit.y, viewportHeight)
            canvas.drawRect(
                RectF(
                    centerX - halfWidth,
                    top,
                    centerX + halfWidth,
                    bottom,
                ),
                hitboxPaint,
            )
        }

        state.boss?.takeIf { it.health > 0 }?.let { boss ->
            val centerX = worldToViewX(boss.x, viewportWidth)
            val centerY = worldToViewY(
                boss.groundY - boss.collisionHeight / 2f,
                viewportHeight,
            )
            canvas.drawRect(
                centerX - worldToViewWidth(boss.collisionWidth, viewportWidth) / 2f,
                centerY - worldToViewHeight(boss.collisionHeight, viewportHeight) / 2f,
                centerX + worldToViewWidth(boss.collisionWidth, viewportWidth) / 2f,
                centerY + worldToViewHeight(boss.collisionHeight, viewportHeight) / 2f,
                hitboxPaint,
            )
        }

        state.projectiles.forEach { projectile ->
            val centerX = worldToViewX(projectile.x, viewportWidth)
            val centerY = worldToViewY(projectile.y, viewportHeight)
            val radiusX = worldToViewWidth(projectile.collisionRadius, viewportWidth)
            val radiusY = worldToViewHeight(projectile.collisionRadius, viewportHeight)
            canvas.drawOval(
                RectF(
                    centerX - radiusX,
                    centerY - radiusY,
                    centerX + radiusX,
                    centerY + radiusY,
                ),
                hitboxPaint,
            )
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
            "BLUE_WIN" -> stringResolver(R.string.match_battlefield_victory, emptyList())
            "RED_WIN" -> stringResolver(R.string.match_battlefield_defeat, emptyList())
            else -> stringResolver(R.string.match_battlefield_draw, emptyList())
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
        private const val UNIT_ANIMATION_FALLBACK_LOG_TAG = "UnitAnimationFallback"
        private const val RELOAD_INDICATOR_RADIUS = 12f
        private const val CATAPULT_RELOAD_BADGE_GAP = 8f
        private const val UNIT_HEALTH_BAR_WIDTH_WORLD = 46.2f
        private const val BOAR_SOURCE_CROP_HEIGHT_RATIO = 301f / 512f
        private const val GENERATED_DEFENDER_CANVAS_SCALE = 2f
        internal const val GENERATED_SLINGMASTER_CANVAS_SCALE = 2.18f
        internal const val GENERATED_SLINGMASTER_BOTTOM_PADDING_RATIO = 0.0677f
        internal const val GENERATED_SLINGMASTER_ATTACK_CANVAS_SCALE = 2.88f
        internal const val GENERATED_SLINGMASTER_ATTACK_BOTTOM_PADDING_RATIO = 14f / 256f
        internal const val GENERATED_SLINGMASTER_ATTACK_HORIZONTAL_OFFSET_RATIO = 0.068f
        internal const val GENERATED_SLINGMASTER_DEATH_CANVAS_SCALE = 2.88f
        internal const val GENERATED_SLINGMASTER_DEATH_BOTTOM_PADDING_RATIO = 0.0533f
        internal const val DEFAULT_UNIT_ANIMATION_RENDER_FRAMES_PER_FRAME = 4
        internal const val GENERATED_RAGING_BOAR_MOVE_RENDER_FRAMES_PER_FRAME = 1
        internal const val GENERATED_RAGING_BOAR_IDLE_CANVAS_SCALE = 1.22f
        internal const val GENERATED_RAGING_BOAR_IDLE_BOTTOM_PADDING_RATIO = 37f / 301f
        internal const val GENERATED_RAGING_BOAR_DEATH_CANVAS_SCALE = 1.6f
        internal const val GENERATED_RAGING_BOAR_DEATH_BOTTOM_PADDING_RATIO = 98f / 480f
        internal const val GENERATED_DEMOLISHER_CANVAS_SCALE = 2f
        internal const val GENERATED_SAPPER_CANVAS_SCALE = 2f
        internal const val GENERATED_SAPPER_MOVE_CANVAS_SCALE = 2f
        internal const val GENERATED_SAPPER_ATTACK_CANVAS_SCALE = 2f
        internal const val GENERATED_SAPPER_RETURN_CANVAS_SCALE = 2.38f
        private const val GENERATED_DEMOLISHER_BOTTOM_PADDING_RATIO = 75f / 512f
        internal const val GENERATED_SAPPER_BOTTOM_PADDING_RATIO = 31f / 256f
        internal const val GENERATED_SAPPER_MOVE_BOTTOM_PADDING_RATIO = 33f / 256f
        internal const val GENERATED_SAPPER_ATTACK_BOTTOM_PADDING_RATIO = 32f / 256f
        internal const val GENERATED_SAPPER_TURN_BOTTOM_PADDING_RATIO = 29f / 256f
        internal const val GENERATED_SAPPER_RETURN_BOTTOM_PADDING_RATIO = 27f / 256f
        private const val GENERATED_BLUE_DEFENDER_IDLE_BOTTOM_PADDING_RATIO = 68f / 512f
        private const val GENERATED_DEFENDER_JOG_BOTTOM_PADDING_RATIO = 76f / 512f
        private const val GENERATED_BLUE_DEFENDER_COMBAT_BOTTOM_PADDING_RATIO = 69f / 512f
        private const val GENERATED_BLUE_DEFENDER_DEATH_BOTTOM_PADDING_RATIO = 70f / 512f

        internal fun sapperTurnCanvasScale(frameIndex: Int, frameCount: Int): Float {
            require(frameCount > 1) { "Sapper turn animation must contain multiple frames" }
            val progress = frameIndex.coerceIn(0, frameCount - 1).toFloat() / (frameCount - 1)
            return GENERATED_SAPPER_ATTACK_CANVAS_SCALE +
                (GENERATED_SAPPER_RETURN_CANVAS_SCALE -
                    GENERATED_SAPPER_ATTACK_CANVAS_SCALE) * progress
        }
        private const val CATAPULT_RENDER_WIDTH = WorldScale.CATAPULT_RENDER_WIDTH
        private const val DECORATION_RENDER_SIZE = 94f
        private const val DECORATION_GROUND_SINK = 12f
        private const val FIRE_RAIN_AMMUNITION = "FIRE_RAIN"
        private const val PLAGUE_CAULDRON_AMMUNITION = "PLAGUE_CAULDRON"
        private const val SIEGE_BOMB_AMMUNITION = "SIEGE_BOMB"
        private const val POWDER_BARREL_AMMUNITION = "POWDER_BARREL"
        internal const val SAPPER_BOMB_FLIGHT_RENDER_SIZE_WORLD = 30f
        private const val SAPPER_BOMB_IMPACT_RENDER_SCALE = 0.5f
        private const val FIRE_RAIN_FRAGMENT_RENDER_SIZE = 28f
        private const val FIRE_RAIN_SPLIT_FLASH_RENDER_SIZE = 120f
        // Generated frames are mostly transparent; align their visible base to terrain.
        private const val FIRE_RAIN_FIELD_CANVAS_DIAMETER_MULTIPLIER = 4f
        private const val FIRE_RAIN_FIELD_GROUND_ANCHOR_Y_RATIO = 374f / 512f
    }
}
