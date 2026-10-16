package com.urkaaaz.ui

import com.urkaaaz.contracts.InitialCameraAnchor
import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.WorldScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattlefieldCameraControllerTest {
    @Test
    fun initialFramingWaitsForAUsableViewport() {
        val camera = BattlefieldCameraController()
        val state = RenderStateMapper.map(
            MatchSnapshot(
                status = MatchStatus.RUNNING,
                phase = MatchPhase.PLAYER_TURN,
                activeTeam = Team.BLUE,
            ),
        )

        camera.update(state, 0f, 0f)
        camera.update(state, 1_000f, 600f)

        assertEquals(1f, camera.transform.scale)
    }

    @Test
    fun initialFramingUsesWorldScaleAndKeepsTheFortressInView() {
        val camera = BattlefieldCameraController()
        val state = RenderStateMapper.map(
            MatchSnapshot(
                status = MatchStatus.RUNNING,
                phase = MatchPhase.PLAYER_TURN,
                activeTeam = Team.BLUE,
            ),
        )

        camera.update(state, 1_000f, 600f)

        assertEquals(1f, camera.transform.scale)
        val fortressLeftOnScreen =
            (state.blueFortressX - WorldScale.FORTRESS_WIDTH / 2f) /
                7_200f * 1_000f * camera.transform.scale +
                camera.transform.offsetX
        assertTrue(fortressLeftOnScreen in 0f..1_000f)
    }

    @Test
    fun initialFramingUsesAuthoredAnchorAndZoom() {
        val baseState = RenderStateMapper.map(
            MatchSnapshot(
                status = MatchStatus.RUNNING,
                phase = MatchPhase.PLAYER_TURN,
                activeTeam = Team.BLUE,
            ),
        )

        val playerCamera = BattlefieldCameraController()
        playerCamera.update(
            baseState.copy(
                initialCameraAnchor = InitialCameraAnchor.PLAYER_FORTRESS,
                initialCameraZoom = 1.3f,
            ),
            1_000f,
            600f,
        )
        assertEquals(1.3f, playerCamera.transform.scale)
        val playerFortressCenter = baseState.blueFortressX / 7_200f * 1_000f *
            playerCamera.transform.scale + playerCamera.transform.offsetX
        assertTrue(playerFortressCenter < 500f)

        val enemyCamera = BattlefieldCameraController()
        enemyCamera.update(
            baseState.copy(
                initialCameraAnchor = InitialCameraAnchor.ENEMY_FORTRESS,
                initialCameraZoom = 1.3f,
            ),
            1_000f,
            600f,
        )
        val enemyFortressCenter = baseState.redFortressX / 7_200f * 1_000f *
            enemyCamera.transform.scale + enemyCamera.transform.offsetX
        assertTrue(enemyFortressCenter > 500f)

        val centerCamera = BattlefieldCameraController()
        centerCamera.update(
            baseState.copy(
                initialCameraAnchor = InitialCameraAnchor.CENTER,
                initialCameraZoom = 1.3f,
            ),
            1_000f,
            600f,
        )
        val centerWorld = centerCamera.screenToGameWorld(500f, 300f, 1_000f, 600f)
        assertEquals(3_600f, centerWorld.first, 0.01f)
        assertEquals(2_400f, centerWorld.second, 0.01f)
    }

    @Test
    fun zoomKeepsTheGestureFocusInPlaceAcrossCameraUpdates() {
        val camera = BattlefieldCameraController()
        val state = RenderStateMapper.map(
            MatchSnapshot(
                status = MatchStatus.RUNNING,
                phase = MatchPhase.PLAYER_TURN,
                activeTeam = Team.BLUE,
            ),
        )
        val viewportWidth = 1_000f
        val viewportHeight = 600f
        val focusX = 270f
        val focusY = 190f

        camera.update(state, viewportWidth, viewportHeight)
        val worldXAtFocus = camera.screenToWorldX(focusX)
        val worldYAtFocus = camera.screenToWorldY(focusY)

        camera.applyZoom(
            scaleFactor = 1.5f,
            focusX = focusX,
            focusY = focusY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
        camera.update(state, viewportWidth, viewportHeight)

        assertEquals(worldXAtFocus, camera.screenToWorldX(focusX), 0.001f)
        assertEquals(worldYAtFocus, camera.screenToWorldY(focusY), 0.001f)
    }

    @Test
    fun manualPanRemainsInPlaceAfterTheMatchPhaseChanges() {
        val camera = BattlefieldCameraController()
        val state = RenderStateMapper.map(
            MatchSnapshot(
                status = MatchStatus.RUNNING,
                phase = MatchPhase.PLAYER_TURN,
                activeTeam = Team.BLUE,
            ),
        )
        val viewportWidth = 1_000f
        val viewportHeight = 600f
        camera.update(state, viewportWidth, viewportHeight)
        camera.translateMap(-120f, -45f, viewportWidth, viewportHeight)
        val manuallyPannedTransform = camera.transform

        camera.update(
            state.copy(phaseLabel = "ENEMY_TURN", activeTeamLabel = "RED"),
            viewportWidth,
            viewportHeight,
        )
        camera.update(
            state.copy(phaseLabel = "PLAYER_TURN", activeTeamLabel = "BLUE"),
            viewportWidth,
            viewportHeight,
        )

        assertEquals(manuallyPannedTransform, camera.transform)
    }

    @Test
    fun screenToGameWorldCoordinatesRespectViewportAndCameraZoom() {
        val camera = BattlefieldCameraController()
        val viewportWidth = 1_000f
        val viewportHeight = 600f
        val cursorX = 250f
        val cursorY = 200f

        val beforeZoom = camera.screenToGameWorld(
            cursorX,
            cursorY,
            viewportWidth,
            viewportHeight,
        )
        camera.applyZoom(2f, cursorX, cursorY, viewportWidth, viewportHeight)
        val afterZoom = camera.screenToGameWorld(
            cursorX,
            cursorY,
            viewportWidth,
            viewportHeight,
        )

        assertEquals(1_800f, beforeZoom.first, 0.001f)
        assertEquals(1_600f, beforeZoom.second, 0.001f)
        assertEquals(beforeZoom.first, afterZoom.first, 0.001f)
        assertEquals(beforeZoom.second, afterZoom.second, 0.001f)
    }

    @Test
    fun zoomCanReachTheExpandedMaximumScale() {
        val camera = BattlefieldCameraController()

        repeat(2) {
            camera.applyZoom(
                scaleFactor = 2f,
                focusX = 500f,
                focusY = 300f,
                viewportWidth = 1_000f,
                viewportHeight = 600f,
            )
        }

        assertEquals(4f, camera.transform.scale)
    }
}
