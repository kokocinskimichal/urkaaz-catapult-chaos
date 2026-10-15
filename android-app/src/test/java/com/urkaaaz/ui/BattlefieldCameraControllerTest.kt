package com.urkaaaz.ui

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
