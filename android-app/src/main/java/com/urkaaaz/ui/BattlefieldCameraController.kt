package com.urkaaaz.ui

import kotlin.math.abs
import kotlin.math.hypot

data class CameraTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
)

class BattlefieldCameraController(
    private val worldWidth: Float = 1_600f,
    private val worldHeight: Float = 900f,
) {
    var transform: CameraTransform = CameraTransform(1f, 0f, 0f)
        private set

    var wasFollowingProjectile: Boolean = false
        private set

    private var trackedTeamLabel: String? = null
    private var lastFramedTeamLabel: String? = null
    private var lastPhaseLabel: String? = null
    private var postImpactHoldSeconds = 0f
    private var cameraReturnActive = false
    private var manualPanActive = false
    private var panningMap = false
    private var panDragging = false
    private var panLastX = 0f
    private var panLastY = 0f
    private var panStartX = 0f
    private var panStartY = 0f

    fun update(state: RenderState, viewportWidth: Float, viewportHeight: Float) {
        val trackedProjectile = state.projectiles.firstOrNull { it.teamLabel == "BLUE" }
        if (trackedProjectile != null) {
            manualPanActive = false
            if (!wasFollowingProjectile) {
                transform = transform.copy(scale = maxOf(transform.scale, AUTO_TRACK_SCALE))
            }
            wasFollowingProjectile = true
            cameraReturnActive = false
            trackedTeamLabel = trackedProjectile.teamLabel
            centerOnWorld(trackedProjectile.x, trackedProjectile.y, viewportWidth, viewportHeight)
        } else {
            val phaseChanged = lastPhaseLabel != null && lastPhaseLabel != state.phaseLabel
            if (phaseChanged) manualPanActive = false
            if (manualPanActive || panningMap) {
                lastPhaseLabel = state.phaseLabel
                clampOffset(viewportWidth, viewportHeight)
                return
            }
            if (wasFollowingProjectile) {
                wasFollowingProjectile = false
                postImpactHoldSeconds = POST_IMPACT_HOLD_SECONDS
            } else if (postImpactHoldSeconds > 0f) {
                postImpactHoldSeconds =
                    (postImpactHoldSeconds - RENDER_STEP_SECONDS).coerceAtLeast(0f)
                if (postImpactHoldSeconds == 0f) cameraReturnActive = true
            } else if (cameraReturnActive) {
                val targetTeam = trackedTeamLabel ?: "BLUE"
                if (animateToTeam(state, targetTeam, viewportWidth, viewportHeight)) {
                    cameraReturnActive = false
                    lastFramedTeamLabel = targetTeam
                }
            } else {
                val targetTeam = state.activeTeamLabel.takeUnless { it == "NONE" }
                    ?: trackedTeamLabel
                    ?: "BLUE"
                if (phaseChanged || trackedTeamLabel == null || lastFramedTeamLabel != targetTeam) {
                    centerOnTeam(state, targetTeam, viewportWidth, viewportHeight)
                    lastFramedTeamLabel = targetTeam
                }
            }
        }
        lastPhaseLabel = state.phaseLabel
        clampOffset(viewportWidth, viewportHeight)
    }

    fun applyZoom(
        scaleFactor: Float,
        focusX: Float,
        focusY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        if (!scaleFactor.isFinite() || scaleFactor <= 0f) return
        val oldScale = transform.scale
        val newScale = (oldScale * scaleFactor).coerceIn(MIN_MAP_SCALE, MAX_MAP_SCALE)
        val contentX = (focusX - transform.offsetX) / oldScale
        val contentY = (focusY - transform.offsetY) / oldScale
        transform = CameraTransform(
            scale = newScale,
            offsetX = focusX - contentX * newScale,
            offsetY = focusY - contentY * newScale,
        )
        clampOffset(viewportWidth, viewportHeight)
    }

    fun beginPan(x: Float, y: Float): Boolean {
        if (wasFollowingProjectile) return false
        panningMap = true
        panDragging = false
        manualPanActive = true
        panStartX = x
        panStartY = y
        panLastX = x
        panLastY = y
        return true
    }

    fun movePan(x: Float, y: Float, viewportWidth: Float, viewportHeight: Float): Boolean {
        if (!panningMap) return false
        if (!panDragging) {
            if (hypot(x - panStartX, y - panStartY) < PAN_START_DISTANCE) return true
            panDragging = true
            panLastX = x
            panLastY = y
            return true
        }
        translateMap(x - panLastX, y - panLastY, viewportWidth, viewportHeight)
        panLastX = x
        panLastY = y
        return true
    }

    fun endPan() {
        panningMap = false
        panDragging = false
    }

    fun translateMap(deltaX: Float, deltaY: Float, viewportWidth: Float, viewportHeight: Float) {
        if (wasFollowingProjectile) return
        transform = transform.copy(
            offsetX = transform.offsetX + deltaX,
            offsetY = transform.offsetY + deltaY,
        )
        clampOffset(viewportWidth, viewportHeight)
    }

    fun screenToWorldX(screenX: Float): Float =
        (screenX - transform.offsetX) / transform.scale

    fun screenToWorldY(screenY: Float): Float =
        (screenY - transform.offsetY) / transform.scale

    private fun centerOnTeam(
        state: RenderState,
        teamLabel: String,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        val (x, y) = if (teamLabel == "RED") {
            state.redCatapultX to state.redCatapultY
        } else {
            state.blueCatapultX to state.blueCatapultY
        }
        centerOnWorld(x, y, viewportWidth, viewportHeight)
    }

    private fun animateToTeam(
        state: RenderState,
        teamLabel: String,
        viewportWidth: Float,
        viewportHeight: Float,
    ): Boolean {
        val (x, y) = if (teamLabel == "RED") {
            state.redCatapultX to state.redCatapultY
        } else {
            state.blueCatapultX to state.blueCatapultY
        }
        val targetX = viewportWidth / 2f -
            (x / worldWidth * viewportWidth) * transform.scale
        val targetY = viewportHeight / 2f -
            (y / worldHeight * viewportHeight) * transform.scale
        transform = transform.copy(
            offsetX = transform.offsetX + (targetX - transform.offsetX) * CAMERA_RETURN_LERP,
            offsetY = transform.offsetY + (targetY - transform.offsetY) * CAMERA_RETURN_LERP,
        )
        return abs(targetX - transform.offsetX) < CAMERA_RETURN_EPSILON &&
            abs(targetY - transform.offsetY) < CAMERA_RETURN_EPSILON
    }

    private fun centerOnWorld(
        worldX: Float,
        worldY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        transform = transform.copy(
            offsetX = viewportWidth / 2f - (worldX / worldWidth * viewportWidth) * transform.scale,
            offsetY = viewportHeight / 2f - (worldY / worldHeight * viewportHeight) * transform.scale,
        )
    }

    private fun clampOffset(viewportWidth: Float, viewportHeight: Float) {
        val minOffsetX = viewportWidth * (1f - transform.scale)
        val minOffsetY = viewportHeight * (1f - transform.scale)
        transform = transform.copy(
            offsetX = transform.offsetX.coerceIn(minOf(minOffsetX, 0f), maxOf(minOffsetX, 0f)),
            offsetY = transform.offsetY.coerceIn(minOf(minOffsetY, 0f), maxOf(minOffsetY, 0f)),
        )
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
    }
}
